package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.smooth

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import net.imglib2.Interval
import org.janelia.saalfeldlab.fx.ChannelLoop
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.addListener
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.InfillStrategy
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.UpdateSignal
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.requestRepaintOverIntervals
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull

object SmoothLabel : MenuAction("_Smooth...") {

	internal var mainTaskLoop: Deferred<List<Interval>?>? = null
	internal var smoothScope : SmoothScope = SmoothScope()
	internal fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = smoothScope.submitUI(block)

	internal class SmoothScope : ChannelLoop(
		capacity = CONFLATED
	) {
		private val pulseConflatedUILoop = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

		fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = pulseConflatedUILoop.submit(block = block)

		fun cancelCurrent() = currentJob?.cancel()
	}

	init {
		verifyPermission(PaintActionType.Smooth, PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onActionWithState<SmoothLabelState<*, *>> {

			resetUpdateChannel()
			isBusy = false

			val activatedReplacementLabel = AtomicLong(0)
			fun deactivateReplacementLabel() {
				activatedReplacementLabel.getAndUpdate {
					selectedIds.deactivate(it)
					0
				}
			}

			fun activateReplacementLabel(replaceLabel: Long) {
				activatedReplacementLabel.getAndUpdate {
					if (it != 0L)
						selectedIds.deactivate(it)

					if (replaceLabel != 0L) {
						selectedIds.activateAlso(*selectedIds.activeIds.toArray(), replaceLabel)
						replaceLabel
					} else
						0
				}
			}

			val infillSub = infillStrategyProperty.subscribe { _, strategy ->
				if (strategy != InfillStrategy.Replace)
					deactivateReplacementLabel()
				else
					activateReplacementLabel(replacementLabelProperty.get())
				updateChannel.trySend(UpdateSignal.Full)
			}
			val replaceLabelSub = replacementLabelProperty.subscribe { old, new ->
				/* irrelevant unless replace */
				if (infillStrategyProperty.get() != InfillStrategy.Replace)
					return@subscribe

				val replaceLabel = new.toLong()
				if (replaceLabel > 0 && !selectedIds.isActive(replaceLabel))
					activateReplacementLabel(replaceLabel)
				else if (replaceLabel == 0L)
					deactivateReplacementLabel()

				updateChannel.trySend(UpdateSignal.Full)
			}
			val subs = infillSub.and(replaceLabelSub)
			startSmoothTask()
			SmoothLabelUI.getDialog(this, "Smooth Label") {
				runBlocking {
					updateChannel.send(UpdateSignal.Finish)
				}
			}.showAndWait().getOrNull().let { success ->
				subs.unsubscribe()
				if (success != true) {
					updateChannel.trySend(UpdateSignal.Cancel)
					deactivateReplacementLabel()
				}
				return@onActionWithState
			}
		}
	}

	private var updateChannel = Channel<UpdateSignal>(CONFLATED)

	private fun resetUpdateChannel() {
		updateChannel.close()
		updateChannel = Channel(CONFLATED)
		smoothScope.cancel("New Smooth Scope Started")
		smoothScope = SmoothScope()
	}

	/**
	 * Flag to indicate if a task is actively running.
	 * Bound to Paintera.isDisabled, so if set to `true` then Paintera will
	 * be "busy" until set to `false` again. This is to block unpermitted state
	 * changes while waiting for smoothing to finish.
	 */
	private val isBusyProperty = SimpleBooleanProperty("Smoothing", "Smooth Action is Running", false)
	private var isBusy by isBusyProperty.nonnull()

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun SmoothLabelState<*, *>.startSmoothTask() {
		val prevScales = viewer.screenScales

		/* update status based on progress */
		val progressStatusSubscription = progressStatusSubscription()

		/* these should only trigger on change */
		val updateSubscription = listOf(
			replacementLabelProperty,
			infillStrategyProperty,
			kernelSizeProperty,
			gaussianThresholdProperty,
			morphDirectionProperty
		).addListener {
			updateChannel.trySend(UpdateSignal.Full)
		}

		/* Initialize the kernelSize */
		val resolution = getLevelResolution(scaleLevel)
		val initKernelSize = morphDirectionProperty.get().defaultKernelSize(resolution)
		kernelSizeProperty.set(initKernelSize)

		val subscriptions = updateSubscription.and(progressStatusSubscription)

		paintera.baseView.orthogonalViews().setScreenScales(doubleArrayOf(prevScales[0]))
		mainTaskLoop = smoothScope.async {

			var updateSmoothJob: Job? = null
			var intervals: List<Interval>? = null
			for (resmoothType in updateChannel) {
				updateSmoothJob?.cancelAndJoin()
				isBusy = true
				updateSmoothJob = when (resmoothType) {
					UpdateSignal.Cancel -> {
						// cancel the mainTaskLoop and break out
						mainTaskLoop?.cancelAndJoin()
						smoothScope.cancel()
						break
					}

					UpdateSignal.Partial, UpdateSignal.Full -> smoothScope.submit {
						intervals = smoothMask(true, resmoothType).map { it.smallestContainingInterval }
					}

					UpdateSignal.Finish -> {
						val finishSmoothJob = smoothScope.submit {
							intervals = smoothMask(false, resmoothType).map { it.smallestContainingInterval }
						}
						finishSmoothJob.invokeOnCompletion { cause ->
							when (cause) {
								null -> Unit
								is CancellationException -> {
									submitUI {
										progress = 0.0
									}
									maskedSource.resetMasks()
									paintera.baseView.orthogonalViews().requestRepaint()
								}

								else -> throw cause
							}
						}
						finishSmoothJob
					}
				}

				updateSmoothJob.invokeOnCompletion { _ -> isBusy = false }

				if (resmoothType == UpdateSignal.Finish) {
					try {
						updateSmoothJob.join()
					} catch (_: CancellationException) {
						submitUI { progress = 0.0 }
						continue
					}
					break
				}
			}
			return@async intervals!!
		}.also { task ->
			paintera.baseView.disabledPropertyBindings[task] = isBusyProperty
			task.invokeOnCompletion { cause ->

				if (cause == null) {
					val intervals = task.getCompleted()
					maskedSource.apply {
						val applyProgressProperty = SimpleDoubleProperty()
						val applyUpdateSubscription = applyProgressProperty.subscribe { it ->
							submitUI { progress = it.toDouble() }
						}
						applyMaskOverIntervals(currentMask, intervals, applyProgressProperty) { it >= 0 }
						applyUpdateSubscription.unsubscribe()
						submitUI { progress = 1.0 }
					}
					requestRepaintOverIntervals(intervals)
					refreshMeshes()
				}
				try {
					/* reset if an exception; throw unless cancellation */
					cause?.let {
						maskedSource.resetMasks()
						paintera.baseView.orthogonalViews().requestRepaint()
						smoothScope.cancelCurrent()
						it.takeUnless { it is CancellationException }?.let { throw it }
					}
				} finally {
					submitUI { subscriptions.unsubscribe() }
					paintera.baseView.disabledPropertyBindings -= task
					maskedSource.resetMasks()
					paintera.baseView.orthogonalViews().setScreenScales(prevScales)
				}
			}
		}
	}
}