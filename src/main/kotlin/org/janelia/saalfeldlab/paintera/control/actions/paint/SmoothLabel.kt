package org.janelia.saalfeldlab.paintera.control.actions.paint

import bsh.commands.dir
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import net.imglib2.Interval
import net.imglib2.RealInterval
import org.janelia.saalfeldlab.fx.ChannelLoop
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval


object SmoothLabel : MenuAction("_Smooth...") {

	internal var smoothTaskLoop: Deferred<List<Interval>?>? = null
	private lateinit var updateSmoothMask: suspend ((Boolean, Resmooth) -> List<RealInterval>)

	internal object SmoothScope : ChannelLoop(capacity = CONFLATED) {
		private val pulseConflatedUILoop = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

		fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = pulseConflatedUILoop.submit(block = block)

		fun cancelCurrent() = currentJob?.cancel()
	}

	init {
		verifyPermission(PaintActionType.Smooth, PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onActionWithState<SmoothLabelState<*, *>> {
			startSmoothTask()
			getDialog(name?.replace("_", "") ?: "Smooth Label") {
				runBlocking {
					resmoothNotification.send(Resmooth.Finish)
				}
			}.showAndWait()
		}
	}


	private val smoothingProperty = SimpleBooleanProperty("Smoothing", "Smooth Action is Running", false)

	/**
	 * Smoothing flag to indicate if a smoothing task is actively running.
	 * Bound to Paintera.isDisabled, so if set to `true` then Paintera will
	 * be "busy" until set to `false` again. This is to block unpermitted state
	 * changes while waiting for smoothing to finish.
	 */
	private var smoothing by smoothingProperty.nonnull()

	private val resmoothNotification = Channel<Resmooth>(CONFLATED)

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun SmoothLabelState<*, *>.startSmoothTask() {
		val prevScales = viewer.screenScales

		/* update status based on progress */
		val progressStatusSubscription = progressStatusSubscription()

		/* these should only trigger on change */
		val replacementLabelChangeSubscription = replacementLabelProperty.subscribe { _, _ -> resmoothNotification.trySend(Resmooth.Partial) }
		val infillStrategyChangeSubscription = infillStrategyProperty.subscribe { _, _ -> resmoothNotification.trySend(Resmooth.Partial) }
		val kernelSizeChangeSubscription = kernelSizeProperty.subscribe { _, _ -> resmoothNotification.trySend(Resmooth.Full) }
		val smoothDirectionChangeSubscription = smoothDirectionProperty.subscribe { _, direction ->
			val defaultKernelSize = direction.defaultKernelSize(getLevelResolution())
			kernelSizeProperty.set(defaultKernelSize)
			resmoothNotification.trySend(Resmooth.Full)
		}

		/* Initialize the kernelSize */
		val defaultKernelSize = smoothDirectionProperty.get().defaultKernelSize(getLevelResolution())
		kernelSizeProperty.set(defaultKernelSize)

		/* This should trigger smoothing immediately and on future changes */
		val labelSelectionSubscription = labelsToSmoothProperty.subscribe { _ ->
			updateSmoothMask = updateSmoothMaskFunction()
			resmoothNotification.trySend(Resmooth.Full)
		}

		val subscriptions = kernelSizeChangeSubscription
			.and(replacementLabelChangeSubscription)
			.and(infillStrategyChangeSubscription)
			.and(smoothDirectionChangeSubscription)
			.and(labelSelectionSubscription)
			.and(progressStatusSubscription)

		paintera.baseView.orthogonalViews().setScreenScales(doubleArrayOf(prevScales[0]))

		smoothTaskLoop = SmoothScope.async {

			var job: Job? = null
			var intervals: List<Interval>? = null
			for (resmoothType in resmoothNotification) {
				job?.cancelAndJoin()
				smoothing = true
				job = when (resmoothType) {
					Resmooth.Cancel -> continue // already cancelled, just continue
					Resmooth.Partial -> SmoothScope.submit { intervals = updateSmoothMask(true, resmoothType).map { it.smallestContainingInterval } }
					Resmooth.Full -> SmoothScope.submit { intervals = updateSmoothMask(true, resmoothType).map { it.smallestContainingInterval } }
					Resmooth.Finish -> SmoothScope.submit { intervals = updateSmoothMask(false, resmoothType).map { it.smallestContainingInterval } }.apply {
						invokeOnCompletion { cause ->
							when (cause) {
								null -> Unit
								is CancellationException -> {
									SmoothScope.submitUI { progress = 0.0 }
									maskedSource.resetMasks()
									paintera.baseView.orthogonalViews().requestRepaint()
								}

								else -> throw cause
							}
						}
					}
				}.apply { invokeOnCompletion { cause -> smoothing = false  } }

				if (resmoothType == Resmooth.Finish) {
					try {
						job.join()
					} catch (_: CancellationException) {
						SmoothScope.submitUI { progress = 0.0 }
						continue
					}
					break
				}
			}
			return@async intervals!!
		}.also { task ->
			paintera.baseView.disabledPropertyBindings[task] = smoothingProperty
			task.invokeOnCompletion { cause ->

				if (cause == null) {
					val intervals = task.getCompleted()
					maskedSource.apply {
						val applyProgressProperty = SimpleDoubleProperty().apply {
							subscribe { applyProgress ->
								SmoothScope.submitUI { progress = applyProgress.toDouble() }
							}
						}
						applyMaskOverIntervals(currentMask, intervals, applyProgressProperty) { it >= 0 }
					}
					requestRepaintOverIntervals(intervals)
					refreshMeshes()
				}
				try {
					/* reset if an exception; throw unless cancellation */
					cause?.let {
						maskedSource.resetMasks()
						paintera.baseView.orthogonalViews().requestRepaint()
						SmoothScope.cancelCurrent()
						it.takeUnless { it is CancellationException }?.let { throw it }
					}
				} finally {
					paintera.baseView.disabledPropertyBindings -= task
					subscriptions.unsubscribe()
					maskedSource.resetMasks()
					paintera.baseView.orthogonalViews().setScreenScales(prevScales)
				}
			}
		}
	}
}