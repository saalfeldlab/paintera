package org.janelia.saalfeldlab.fx.ui

import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.scene.control.ProgressBar
import javafx.util.Duration
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import kotlin.time.Duration.Companion.milliseconds

open class AnimatedProgressBar(
	val reverseBehavior: ReverseBehavior = ReverseBehavior.SKIP
) : ProgressBar() {

	companion object {
		enum class ReverseBehavior {
			SKIP,
			SET,
			ANIMATE
		}

		private const val END_CUE = "END"
	}

	/**
	 *
	 * As soon as [progressTargetProperty] reaches `1.0` the progress bar will animate a final time
	 * with target duration of [finishAnimationDuration]. If duration is zero or negative,
	 * No animation will be attempted, and the progress bar will be set to `1.0`.
	 */
	var finishAnimationDuration = 100.milliseconds

	init {
		maxWidth = Double.MAX_VALUE
	}

	private val timeline = Timeline()

	var baseDuration: Duration = Duration.seconds(1.0)

	private val conflatedPulseLoop = InvokeOnJavaFXApplicationThread.conflatedPulseLoop(10)

	val progressTargetProperty: DoubleProperty = SimpleDoubleProperty().apply {
		subscribe { progress ->
			conflatedPulseLoop.submit { updateTimeline(progress.toDouble()) }
		}
	}

	private var lastUpdateTime: Long? = null
	private var runningAverageBetweenUpdates = 0.0

	private fun animateProgress(target: Double) {

		val progressProperty = progressProperty()
		val resultDuration: Duration = calculateTargetDuration(target)
		if (resultDuration.toMillis() <= 0)
			finish(target)
		timeline.keyFrames.setAll(
			KeyFrame(Duration.ZERO, KeyValue(progressProperty, progressProperty.value)),
			KeyFrame(resultDuration, KeyValue(progressProperty, target))
		)
		timeline.cuePoints[END_CUE] = resultDuration
		timeline.play()
	}


	protected open fun updateTimeline(newTarget: Double) {

		timeline.stop()
		val curProgress = progress
		when {
			newTarget == curProgress -> return /* nothing to do */
			newTarget == 0.0 -> progress = 0.0 /* immediately set to zero */
			newTarget == INDETERMINATE_PROGRESS -> progress = INDETERMINATE_PROGRESS /* immediately set to INDETERMINATE value */
			newTarget > curProgress -> animateProgress(newTarget) /* normal case; animate to newTarget*/
			newTarget < curProgress -> when (reverseBehavior) {
				ReverseBehavior.SKIP -> Unit
				ReverseBehavior.SET -> progress = newTarget
				ReverseBehavior.ANIMATE -> animateProgress(newTarget)
			}
		}
	}

	private fun calculateTargetDuration(newTarget: Double): Duration {
		val thisPortion = lastUpdateTime?.let { System.currentTimeMillis() - it }?.div(2.0) ?: 0.0
		runningAverageBetweenUpdates = runningAverageBetweenUpdates / 2.0 + thisPortion
		lastUpdateTime = System.currentTimeMillis()

		val resultDuration: Duration =
			if (newTarget >= 1.0) {
				val ms = minOf(finishAnimationDuration.inWholeMilliseconds, runningAverageBetweenUpdates.toLong())
				Duration.millis(ms.toDouble())
			} else {
				baseDuration.add(Duration.millis(runningAverageBetweenUpdates))
			}
		return resultDuration
	}

	fun finish(progress: Double = 1.0) = InvokeOnJavaFXApplicationThread {
		timeline.stop()
		progressProperty().unbind()
		progressProperty().value = progress
	}

	fun stop() {
		timeline.stop()
	}
}