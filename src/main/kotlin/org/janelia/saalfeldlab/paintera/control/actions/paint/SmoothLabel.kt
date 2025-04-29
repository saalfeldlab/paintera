package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import net.imglib2.Interval
import net.imglib2.RealInterval
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval


object SmoothLabel : MenuAction("_Smooth...") {

	internal var smoothJob: Deferred<List<Interval>?>? = null
	internal var smoothTaskLoop: Deferred<List<Interval>?>? = null

	internal var finalizeSmoothing = false
	private var resmooth : Resmooth? = null

	private lateinit var updateSmoothMask: suspend ((Boolean, Resmooth) -> List<RealInterval>)

	internal object SmoothScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
		private val updateOnPulse = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

		private val channel = Channel<Job>(capacity = CONFLATED)
		private var currentJob: Job? = null


		fun submit(block: suspend CoroutineScope.() -> Unit): Job {
			val job = launch(start = CoroutineStart.LAZY) {
				block()
			}

			runBlocking {
				currentJob?.cancel()
				channel.send(job)
				currentJob = job
			}
			return job
		}

		fun submitUI(block: suspend CoroutineScope.() -> Unit): Job = updateOnPulse.submit(block = block)

		init {
			launch {
				for (msg in channel) {
					runCatching {
						msg.start()
						msg.join()
					}
				}
			}
		}
	}

	init {
		verifyPermission(PaintActionType.Smooth, PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onActionWithState<SmoothLabelState<*, *>> {
			finalizeSmoothing = false
			progress = 0.0
			val sub = progressStatusSubscription()
			startSmoothTask()
			getDialog(name?.replace("_", "") ?: "Smooth Label").showAndWait()
			sub.unsubscribe()
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

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun SmoothLabelState<*, *>.startSmoothTask() {
		val prevScales = viewer.screenScales
		val smoothTriggerListener = { reason: String, type: Resmooth ->
			Runnable {
				resmooth = type
				smoothJob?.cancel()
			}
		}
		var smoothTriggerSubscription: Subscription = Subscription.EMPTY

		smoothTaskLoop = SmoothScope.async {
			val kernelSizeChangeSubscription = kernelSizeProperty.subscribe(smoothTriggerListener("Kernel Size Changed", Resmooth.Full))
			val replacementLabelChangeSubscription = replacementLabelProperty.subscribe(smoothTriggerListener("Replacement Label Changed", Resmooth.Partial))
			val labelSelectionSubscription = labelSelectionProperty.subscribe { selection ->
				smoothJob?.cancel()
				smoothing = true
				initializeSmoothLabel()
				smoothing = false
			}
			val infillStrategyChangeSubscription = infillStrategyProperty.subscribe(smoothTriggerListener("Infill Strategy Changed", Resmooth.Partial))
			val smoothDirectionChangeSubscription = smoothDirectionProperty.subscribe(smoothTriggerListener("Smooth Direction Changed", Resmooth.Partial))
			val smoothThresholdChangeSubscription = smoothThresholdProperty.subscribe(smoothTriggerListener("Smooth Threshold Changed", Resmooth.Partial))

			smoothTriggerSubscription.unsubscribe()
			smoothTriggerSubscription = kernelSizeChangeSubscription
				.and(replacementLabelChangeSubscription)
				.and(infillStrategyChangeSubscription)
				.and(smoothDirectionChangeSubscription)
				.and(smoothThresholdChangeSubscription)
				.and(labelSelectionSubscription)

			paintera.baseView.orthogonalViews().setScreenScales(doubleArrayOf(prevScales[0]))
			var intervals: List<Interval>? = emptyList()
			while (true) {
				val resmoothType = resmooth
				if (resmoothType != null || finalizeSmoothing) {
					val preview = !finalizeSmoothing
					try {
						smoothing = true
						resmooth = null
						smoothJob = SmoothScope.async {
							updateSmoothMask(preview, resmoothType!! ).map { it.smallestContainingInterval }
						}
						intervals = smoothJob?.await()
						if (!preview)
							break
					} catch (_: CancellationException) {
						intervals = null
						if (resmoothType == Resmooth.Full) {
							dataSource.resetMasks()
							paintera.baseView.orthogonalViews().requestRepaint()
						}
					} finally {
						smoothing = false
						/* reset for the next loop */
						finalizeSmoothing = false
					}
				}
				delay(100)
			}
			return@async intervals
		}.also { task ->
			paintera.baseView.disabledPropertyBindings[task] = smoothingProperty
			task.invokeOnCompletion { cause ->
				if (cause != null) {
					dataSource.resetMasks()
					paintera.baseView.orthogonalViews().requestRepaint()
				} else {
					val intervals = task.getCompleted()
					dataSource.apply {
						val applyProgressProperty = SimpleDoubleProperty()
						applyProgressProperty.addListener { _, _, applyProgress -> progress = applyProgress.toDouble() }
						applyMaskOverIntervals(currentMask, intervals, applyProgressProperty) { it >= 0 }
					}
					requestRepaintOverIntervals(intervals)
					refreshMeshes()
				}

				paintera.baseView.disabledPropertyBindings -= task
				smoothTriggerSubscription.unsubscribe()
				paintera.baseView.orthogonalViews().setScreenScales(prevScales)
			}
		}
	}

	private fun SmoothLabelState<*, *>.initializeSmoothLabel() {

		updateSmoothMask = updateSmoothMaskFunction()
		resmooth = Resmooth.Full
	}
}