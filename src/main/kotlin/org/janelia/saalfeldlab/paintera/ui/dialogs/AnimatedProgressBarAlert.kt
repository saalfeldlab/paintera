package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.animation.AnimationTimer
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.util.Duration
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

class AnimatedProgressBarAlert(
	title: String,
	header: String,
	private var progressLabelPrefix: String,
	private val currentCountSupplier: () -> Int,
	initialMax: Int
) {

	private val progressProperty = SimpleDoubleProperty(0.0)
	private var progress by progressProperty.nonnull()

	val totalCountProperty = SimpleIntegerProperty(initialMax)
	private var totalCount by totalCountProperty.nonnull()

	private val currentCountProperty = SimpleIntegerProperty(0)
	private var currentCount by currentCountProperty.nonnull()

	private val progressAlert = PainteraAlerts.information("Ok", false).apply {
		this.title = title
		this.headerText = header
		val progressBar = ProgressBar(0.0).apply {
			progressProperty().bind(progressProperty)
			prefWidth = 300.0
		}

		val doneLabel = Label("Done!")
		doneLabel.visibleProperty().bind(progressProperty.isEqualTo(1.0, 0.0001))
		dialogPane.content = VBox(10.0, createProgressLabel(), progressBar, HBox(doneLabel))
		isResizable = false
	}

	private fun createProgressLabel() = Label().apply {
		val textBinding = currentCountProperty.createNonNullValueBinding(totalCountProperty) {
			"$progressLabelPrefix : $currentCount / $totalCount"
		}
		textProperty().bind(textBinding)
	}

	private val updater = object : AnimationTimer() {

		val timeline = Timeline().apply {
			cycleCount = Timeline.INDEFINITE
		}

		override fun handle(now: Long) {
			currentCount = currentCountSupplier()

			val newProgress = currentCount.toDouble() / totalCount
			val delta = (newProgress - progress).coerceIn(0.0, 1.0)
			if (delta <= 0)
				return

			val duration = Duration.millis((200.0 / delta).coerceIn(50.0, 1000.0))

			timeline.keyFrames.setAll(KeyFrame(duration, KeyValue(progressProperty, newProgress)))
			timeline.playFromStart()
		}

		override fun stop() {
			timeline.stop()
			progress = 1.0
		}

	}

	fun showAndStart() = InvokeOnJavaFXApplicationThread {
		updater.start()
		progressAlert.showAndWait()
	}

	fun finish() = InvokeOnJavaFXApplicationThread {
		updater.stop()
	}

	fun stopAndClose() = InvokeOnJavaFXApplicationThread {
		updater.stop()
		progressAlert.close()
	}

}