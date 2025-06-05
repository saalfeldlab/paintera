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

open class AnimatedProgressBar : ProgressBar() {

	companion object {
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

	var reversible = false
	var baseDuration: Duration = Duration.seconds(1.0)

	private val conflatedPulseLoop = InvokeOnJavaFXApplicationThread.conflatedPulseLoop(10)

	val progressTargetProperty: DoubleProperty = SimpleDoubleProperty().apply {
		subscribe { progress ->
			conflatedPulseLoop.submit { updateTimeline(progress.toDouble()) }
		}
	}

	private var lastUpdateTime: Long? = null
	private var runningAverageBetweenUpdates = 0.0


	protected open fun updateTimeline(newTarget: Double) {

		val thisPortion = lastUpdateTime?.let { System.currentTimeMillis() - it }?.div(2.0) ?: 0.0
		runningAverageBetweenUpdates = runningAverageBetweenUpdates / 2.0 + thisPortion
		lastUpdateTime = System.currentTimeMillis()

		timeline.stop()
		val progressProperty = progressProperty()
		if (newTarget == 0.0) {
			progressProperty.value = 0.0
			return
		}


		if (!reversible && newTarget <= progressProperty.get()) return

		val resultDuration : Duration =
			if (newTarget >= 1.0) {
				val ms = minOf(finishAnimationDuration.inWholeMilliseconds, runningAverageBetweenUpdates.toLong())
				Duration.millis(ms.toDouble())
			}
			else {
				baseDuration.add(Duration.millis(runningAverageBetweenUpdates))
			}

		if (resultDuration.toMillis() <= 0)
			finish()


		timeline.keyFrames.setAll(
			KeyFrame(Duration.ZERO, KeyValue(progressProperty, progressProperty.value)),
			KeyFrame(resultDuration, KeyValue(progressProperty, newTarget))
		)
		timeline.cuePoints[END_CUE] = resultDuration
		timeline.play()
	}

	fun finish() = InvokeOnJavaFXApplicationThread {
		timeline.stop()
		progressProperty().unbind()
		progressProperty().value = 1.0
	}

	fun stop() {
		timeline.stop()
	}
}