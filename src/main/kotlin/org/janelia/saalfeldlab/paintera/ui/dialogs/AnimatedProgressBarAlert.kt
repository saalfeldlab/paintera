package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.binding.DoubleExpression
import javafx.beans.binding.StringExpression
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

class AnimatedProgressBarAlert(
	title: String,
	header: String,
	private var progressLabelBinding: StringExpression,
	private val progressBinding: DoubleExpression,
) {

	private val progressBar = AnimatedProgressBar().apply {
		progressTargetProperty.bind(progressBinding)
	}

	private val progressAlert = PainteraAlerts.information("Ok", false).apply {
		this.title = title
		this.headerText = header
		val progressBar = progressBar.apply {
			prefWidth = 300.0
		}

		val doneLabel = Label("Done!")
		doneLabel.visibleProperty().bind(progressBar.progressProperty().greaterThanOrEqualTo(1.0))
		dialogPane.content = VBox(10.0, createProgressLabel(), progressBar, HBox(doneLabel))
		isResizable = false
	}

	private fun createProgressLabel() = Label().apply {
		textProperty().bind(progressLabelBinding)
	}


	fun showAndStart() = InvokeOnJavaFXApplicationThread {
		progressAlert.showAndWait()
	}

	fun finish() = InvokeOnJavaFXApplicationThread {
		progressBar.finish()
	}

	fun stopAndClose() = InvokeOnJavaFXApplicationThread {
		progressBar.stop()
		progressAlert.close()
	}
}