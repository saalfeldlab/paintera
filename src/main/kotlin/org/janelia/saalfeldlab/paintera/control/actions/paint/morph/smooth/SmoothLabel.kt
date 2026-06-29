package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.smooth

import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.InfillStrategy
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.Status
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.UpdateSignal
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.requestRepaintOverIntervals
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.setStatus
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull

object SmoothLabel : MenuAction("_Smooth...") {

	private val LOG = KotlinLogging.logger {  }

	internal var mainTaskLoop: Deferred<List<Interval>?>? = null
	internal var smoothScope : SmoothScope = SmoothScope()
	internal fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = smoothScope.submitUI(block)

	internal class SmoothScope : ChannelLoop(
		coroutineScope = CoroutineScope(
			SupervisorJob()
			+ Dispatchers.Default
			+ CoroutineExceptionHandler { _, e -> LOG.error(e) { "Smooth Action Failed" }}
		),
		capacity = CONFLATED
	) {
		private val pulseConflatedUILoop = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

		fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = pulseConflatedUILoop.submit(block = block)

		fun cancelCurrent() = currentJob?.cancel()
	}

	init {
		verifyPermission(PaintActionType.Smooth, PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onActionWithState<SmoothLabelState<*, *>> {

			resetAsyncState()
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
					smoothScope.cancel()
					deactivateReplacementLabel()
				}
				return@onActionWithState
			}
		}
	}

	private var updateChannel = Channel<UpdateSignal>(CONFLATED)

	private fun resetAsyncState() {
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

		/* preview toggle: re-show the cached mask if still valid, recompute if stale, hide when turned off */
		val previewSubscription = listOf(previewProperty).addListener {
			updateChannel.trySend(
				when {
					!previewProperty.get() -> UpdateSignal.HidePreview
					previewMaskValid && previewMask != null -> UpdateSignal.ShowPreview
					else -> UpdateSignal.Full
				}
			)
		}

		/* Initialize the kernelSize */
		val resolution = getLevelResolution(scaleLevel)
		val initKernelSize = morphDirectionProperty.get().defaultKernelSize(resolution)
		kernelSizeProperty.set(initKernelSize)

		val subscriptions = updateSubscription.and(previewSubscription)

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
						smoothScope.cancelCurrent()
						break
					}

					UpdateSignal.HidePreview -> smoothScope.submit {
						withContext(NonCancellable) {
							maskedSource.hideCurrentMask()
							requestRepaintOverIntervals(intervals)
							submitUI { progress = 0.0 }
							setStatus(Status.Ready)
						}
					}

					UpdateSignal.ShowPreview -> smoothScope.submit {
						withContext(NonCancellable) {
							previewMask?.let { mask ->
								if (maskedSource.currentMask !== mask) maskedSource.setMask(mask) { it >= 0 }
								requestRepaintOverIntervals(intervals)
								setStatus(Status.Ready)
							}
						}
					}

					UpdateSignal.Partial, UpdateSignal.Full -> smoothScope.submit {
						intervals = smoothMask(true, resmoothType).map { it.smallestContainingInterval }
					}

					UpdateSignal.Finish -> {
						smoothScope.submit {
							try {
								intervals = smoothMask(false, resmoothType).map { it.smallestContainingInterval }
							} catch (e: CancellationException) {
								withContext(NonCancellable) {
									smoothScope.submitUI { progress = 0.0 }
									maskedSource.resetMasks()
									paintera.baseView.orthogonalViews().requestRepaint()
								}
								throw e
							}
						}
					}
				}

				updateSmoothJob.invokeOnCompletion { isBusy = false }

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
				try {
					if (cause == null) {
						setStatus(Status.Applying)
						val intervals = task.getCompleted()
						maskedSource.apply {
							val applyProgressProperty = SimpleDoubleProperty()
							val applyUpdateSubscription = applyProgressProperty.subscribe { it ->
								submitUI { progress = it.toDouble() }
							}
							applyMaskOverIntervals(currentMask, intervals, applyProgressProperty) { it >= 0 }
							applyUpdateSubscription.unsubscribe()
						}
						requestRepaintOverIntervals(intervals)
						refreshMeshes()
						setStatus(Status.Done)
					}
					/* reset on any exception; log + surface non-cancellation errors to the user */
					cause?.let {
						setStatus(Status.Empty)
						maskedSource.resetMasks()
						paintera.baseView.orthogonalViews().requestRepaint()
						smoothScope.cancelCurrent()
						it.takeUnless { it is CancellationException }?.let { error ->
							LOG.error(error) { "Smooth failed" }
							InvokeOnJavaFXApplicationThread {
								Exceptions.exceptionAlert(Constants.NAME, "Smooth failed", error).show()
							}
						}
					}
				} catch (applyError: Throwable) {
					/* the apply phase failed; reset so the dialog isn't stuck at Applying */
					setStatus(Status.Empty)
					maskedSource.resetMasks()
					paintera.baseView.orthogonalViews().requestRepaint()
					smoothScope.cancelCurrent()
					LOG.error(applyError) { "Smooth apply failed" }
					InvokeOnJavaFXApplicationThread {
						Exceptions.exceptionAlert(Constants.NAME, "Smooth apply failed", applyError).show()
					}
				} finally {
					submitUI {
						cause ?: run { progress = 1.0 }
						subscriptions.unsubscribe()
					}
					paintera.baseView.disabledPropertyBindings -= task
					/* shut down a preview mask that was hidden */
					previewMask?.takeIf { it !== maskedSource.currentMask }?.shutdown?.run()
					previewMask = null
					previewMaskValid = false
					maskedSource.resetMasks()
					paintera.baseView.orthogonalViews().setScreenScales(prevScales)
				}
			}
		}
	}
}