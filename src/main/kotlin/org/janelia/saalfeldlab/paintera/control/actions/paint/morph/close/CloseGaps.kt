package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.util.Subscription
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
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.Status
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.UpdateSignal
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.requestRepaintOverIntervals
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.setStatus
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull

object CloseGaps : MenuAction("_Close Gaps...") {

	private val LOG = KotlinLogging.logger {  }

	internal var mainTaskLoop: Deferred<List<Interval>?>? = null
	internal var closeScope : CloseScope = CloseScope()
	internal fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = closeScope.submitUI(block)

	internal class CloseScope : ChannelLoop(
		coroutineScope = CoroutineScope(
			SupervisorJob()
			+ Dispatchers.Default
			+ CoroutineExceptionHandler { _, e -> LOG.error(e) { "Close Gaps Action Failed" }}
		),
		capacity = CONFLATED
	) {
		private val pulseConflatedUILoop = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

		fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = pulseConflatedUILoop.submit(block = block)

		fun cancelCurrent() = currentJob?.cancel()
	}

	init {
		verifyPermission(PaintActionType.Paint, PaintActionType.Fill)
		onActionWithState<CloseLabelState<*, *>> {

			resetAsyncState()
			isBusy = false

			/* seed with segment ids so gaps bridge between fragments of each active segment */
			labelsToClose.setAll(allActiveSegments.toList())

			/* activate the specified fill label so the preview renders it */
			val activatedFillLabel = AtomicLong(0)
			fun deactivateFillLabel() {
				activatedFillLabel.getAndUpdate {
					selectedIds.deactivate(it)
					0
				}
			}

			fun activateFillLabel(fillLabel: Long) {
				activatedFillLabel.getAndUpdate {
					if (it != 0L)
						selectedIds.deactivate(it)

					if (fillLabel != 0L) {
						selectedIds.activateAlso(*selectedIds.activeIds.toArray(), fillLabel)
						fillLabel
					} else
						0
				}
			}

			val specifySub = specifyFillLabelProperty.subscribe { _, specify ->
				if (specify)
					activateFillLabel(fillLabelProperty.get())
				else
					deactivateFillLabel()
				updateChannel.trySend(UpdateSignal.Full)
			}
			val fillLabelSub = fillLabelProperty.subscribe { _, new ->
				/* irrelevant unless specified */
				if (!specifyFillLabelProperty.get())
					return@subscribe

				val fillLabel = new.toLong()
				if (fillLabel > 0 && !selectedIds.isActive(fillLabel))
					activateFillLabel(fillLabel)
				else if (fillLabel == 0L)
					deactivateFillLabel()

				updateChannel.trySend(UpdateSignal.Full)
			}
			val subs = specifySub.and(fillLabelSub)

			startCloseTask()
			CloseLabelUI.getDialog(this, "Close Gaps") {
				runBlocking {
					updateChannel.send(UpdateSignal.Finish)
				}
			}.showAndWait().getOrNull().let { success ->
				subs.unsubscribe()
				if (success != true) {
					updateChannel.trySend(UpdateSignal.Cancel)
					closeScope.cancel()
					deactivateFillLabel()
				}
				return@onActionWithState
			}
		}
	}

	private var updateChannel = Channel<UpdateSignal>(CONFLATED)

	private fun resetAsyncState() {
		updateChannel.close()
		updateChannel = Channel(CONFLATED)
		closeScope.cancel("New Close Gaps Scope Started")
		closeScope = CloseScope()
	}

	/**
	 * Flag to indicate if a task is actively running.
	 * Bound to Paintera.isDisabled, so if set to `true` then Paintera will
	 * be "busy" until set to `false` again. This is to block unpermitted state
	 * changes while waiting for the fill to finish.
	 */
	private val isBusyProperty = SimpleBooleanProperty("Closing Gaps", "Close Gaps Action is Running", false)
	private var isBusy by isBusyProperty.nonnull()

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun CloseLabelState<*, *>.startCloseTask() {
		val prevScales = viewer.screenScales

		/* these should only trigger on change */
		val updateSubscription = listOf(gapSizeProperty, iterationsProperty, replaceModeProperty).addListener {
			updateChannel.trySend(UpdateSignal.Full)
		}
		val listListener = InvalidationListener {
			updateChannel.trySend(UpdateSignal.Full)
		}
		labelsToClose.addListener(listListener)
		labelsToReplace.addListener(listListener)
		val listSubscription = Subscription {
			labelsToClose.removeListener(listListener)
			labelsToReplace.removeListener(listListener)
		}

		/* preview toggle: show/hide an existing preview */
		val previewSubscription = previewProperty.subscribe { _, _ ->
			if (previewMaskValid && previewMask != null)
				updateChannel.trySend(
					if (previewProperty.get())
						UpdateSignal.ShowPreview
					else
						UpdateSignal.HidePreview
				)
		}

		val subscriptions = updateSubscription.and(previewSubscription).and(listSubscription)

		/* no parameter initialization triggers the first compute, so request it explicitly */
		updateChannel.trySend(UpdateSignal.Full)

		paintera.baseView.orthogonalViews().setScreenScales(doubleArrayOf(prevScales[0]))
		mainTaskLoop = closeScope.async {

			var updateCloseJob: Job? = null
			var intervals: List<Interval>? = null
			for (recloseType in updateChannel) {
				updateCloseJob?.cancelAndJoin()
				isBusy = true
				updateCloseJob = when (recloseType) {
					UpdateSignal.Cancel -> {
						// cancel the mainTaskLoop and break out
						mainTaskLoop?.cancelAndJoin()
						closeScope.cancelCurrent()
						break
					}

					UpdateSignal.HidePreview -> closeScope.submit {
						withContext(NonCancellable) {
							maskedSource.hideCurrentMask()
							requestRepaintOverIntervals(intervals)
							closeScope.submitUI { progress = 0.0 }
							setStatus(Status.Ready)
						}
					}

					UpdateSignal.ShowPreview -> closeScope.submit {
						withContext(NonCancellable) {
							previewMask?.let { mask ->
								if (maskedSource.currentMask !== mask) maskedSource.setMask(mask) { it >= 0 }
								requestRepaintOverIntervals(intervals)
								setStatus(Status.Ready)
							}
						}
					}

					UpdateSignal.Partial, UpdateSignal.Full -> closeScope.submit {
						intervals = closeMask(true, recloseType).map { it.smallestContainingInterval }
					}

					UpdateSignal.Finish -> {
						closeScope.submit {
							try {
								intervals = closeMask(false, recloseType).map { it.smallestContainingInterval }
							} catch (e: CancellationException) {
								withContext(NonCancellable) {
									closeScope.submitUI { progress = 0.0 }
									maskedSource.resetMasks()
									paintera.baseView.orthogonalViews().requestRepaint()
								}
								throw e
							}
						}
					}
				}

				updateCloseJob.invokeOnCompletion { isBusy = false }

				if (recloseType == UpdateSignal.Finish) {
					try {
						updateCloseJob.join()
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
								closeScope.submitUI { progress = it.toDouble() }
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
						closeScope.cancelCurrent()
						it.takeUnless { it is CancellationException }?.let { error ->
							LOG.error(error) { "Close Gaps failed" }
							InvokeOnJavaFXApplicationThread {
								Exceptions.exceptionAlert(Constants.NAME, "Close Gaps failed", error).show()
							}
						}
					}
				} catch (applyError: Throwable) {
					/* the apply phase failed; reset so the dialog isn't stranded at Applying */
					setStatus(Status.Empty)
					maskedSource.resetMasks()
					paintera.baseView.orthogonalViews().requestRepaint()
					closeScope.cancelCurrent()
					LOG.error(applyError) { "Close Gaps apply failed" }
					InvokeOnJavaFXApplicationThread {
						Exceptions.exceptionAlert(Constants.NAME, "Close Gaps apply failed", applyError).show()
					}
				} finally {
					submitUI {
						cause ?: run { progress = 1.0 }
						subscriptions.unsubscribe()
					}
					paintera.baseView.disabledPropertyBindings -= task
					/* shut down a preview mask that was hidden (toggled off) so its store doesn't leak */
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
