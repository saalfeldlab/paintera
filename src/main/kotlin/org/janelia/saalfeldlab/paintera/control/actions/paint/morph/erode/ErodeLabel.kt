package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.erode

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import net.imglib2.Interval
import org.janelia.saalfeldlab.fx.ChannelLoop
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.subscribe
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
import kotlin.math.ceil

object ErodeLabel : MenuAction("_Shrink...") {

	internal var mainTaskLoop: Deferred<List<Interval>?>? = null

	internal object ErodeScope : ChannelLoop(
		capacity = CONFLATED
	) {
		private val pulseConflatedUILoop = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

		fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = pulseConflatedUILoop.submit(block = block)

		fun cancelCurrent() = currentJob?.cancel()
	}

	init {
		verifyPermission(PaintActionType.Erode, PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onActionWithState<ErodeLabelState<*, *>> {

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
			startErodeTask()
			ErodeLabelUI.getDialog(this, "Shrink Label") {
				runBlocking<Unit> {
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
	}

	/**
	 * Flag to indicate if a task is actively running.
	 * Bound to Paintera.isDisabled, so if set to `true` then Paintera will
	 * be "busy" until set to `false` again. This is to block unpermitted state
	 * changes while waiting for eroding to finish.
	 */
	private val isBusyProperty = SimpleBooleanProperty("Shrinking", "Shrink Action is Running", false)
	private var isBusy by isBusyProperty.nonnull()

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun ErodeLabelState<*, *>.startErodeTask() {
		val prevScales = viewer.screenScales

		/* update status based on progress */
		val progressStatusSubscription = progressStatusSubscription()

		/* these should only trigger on change */
		val updateSubscription = listOf(replacementLabelProperty, infillStrategyProperty, kernelSizeProperty).subscribe {
			updateChannel.trySend(UpdateSignal.Full)
		}

		/* Initialize the kernelSize */
		val resolution = getLevelResolution(scaleLevel)
		kernelSizeProperty.set(ceil(resolution.min()).toInt())

		val subscriptions = updateSubscription
			.and(progressStatusSubscription)

		paintera.baseView.orthogonalViews().setScreenScales(doubleArrayOf(prevScales[0]))
		mainTaskLoop = ErodeScope.async {

			var updateErodeJob: Job? = null
			var intervals: List<Interval>? = null
			for (reerodeType in updateChannel) {
				updateErodeJob?.cancelAndJoin()
				isBusy = true
				updateErodeJob = when (reerodeType) {
					UpdateSignal.Cancel -> {
						// cancel the mainTaskLoop and break out
						mainTaskLoop?.cancelAndJoin()
						break
					}

					UpdateSignal.Partial, UpdateSignal.Full -> ErodeScope.submit {
						intervals = erodeMask(true, reerodeType).map { it.smallestContainingInterval }
					}

					UpdateSignal.Finish -> {
						val finisErodeJob = ErodeScope.submit {
							intervals = erodeMask(false, reerodeType).map { it.smallestContainingInterval }
						}
						finisErodeJob.invokeOnCompletion { cause ->
							when (cause) {
								null -> Unit
								is CancellationException -> {
									ErodeScope.submitUI {
										progress = 0.0
									}
									maskedSource.resetMasks()
									paintera.baseView.orthogonalViews().requestRepaint()
								}

								else -> throw cause
							}
						}
						finisErodeJob
					}
				}

				updateErodeJob.invokeOnCompletion { cause -> isBusy = false }

				if (reerodeType == UpdateSignal.Finish) {
					try {
						updateErodeJob.join()
					} catch (_: CancellationException) {
						ErodeScope.submitUI { progress = 0.0 }
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
							ErodeScope.submitUI { progress = it.toDouble() }
						}
						applyMaskOverIntervals(currentMask, intervals, applyProgressProperty) { it >= 0 }
						applyUpdateSubscription.unsubscribe()
						ErodeScope.submitUI { progress = 1.0 }
					}
					requestRepaintOverIntervals(intervals)
					refreshMeshes()
				}
				try {
					/* reset if an exception; throw unless cancellation */
					cause?.let {
						maskedSource.resetMasks()
						paintera.baseView.orthogonalViews().requestRepaint()
						ErodeScope.cancelCurrent()
						it.takeUnless { it is CancellationException }?.let { throw it }
					}
				} finally {
					ErodeScope.submitUI { subscriptions.unsubscribe() }
					paintera.baseView.disabledPropertyBindings -= task
					maskedSource.resetMasks()
					paintera.baseView.orthogonalViews().setScreenScales(prevScales)
				}
			}
		}
	}
}