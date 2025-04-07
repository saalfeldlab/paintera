package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.binding.DoubleExpression
import javafx.beans.binding.StringExpression
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.CancellationException
import org.janelia.saalfeldlab.fx.extensions.nullable
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

	private val canCloseBinding = SimpleBooleanProperty(true)
	var cancelled = false
		private set

	private val progressAlert : Alert  = PainteraAlerts.confirmation("Ok", "Cancel", false).apply {
		this.title = title
		this.headerText = header
		val progressBar = progressBar.apply {
			prefWidth = 300.0
		}

		onCloseRequest = EventHandler {
			if (progressBar.progress < 1.0)
				cancelled = true
				stop()
		}

		(dialogPane.lookupButton(ButtonType.OK) as Button).apply {
			disableProperty().bind(canCloseBinding.not())
		}

		(dialogPane.lookupButton(ButtonType.CANCEL) as Button).onAction = EventHandler {
			cancelled = true
			stopAndClose()
		}

		val doneLabel = Label("Done!")
		doneLabel.visibleProperty().bind(progressBar.progressProperty().greaterThanOrEqualTo(1.0))
		dialogPane.content = VBox(10.0, createProgressLabel(), progressBar, HBox(doneLabel))
		isResizable = true
	}

	private fun createProgressLabel() = Label().apply {
		textProperty().bind(progressLabelBinding)
	}


	/**
	 * Show Dialog and wait for it to finish. Should be called on the JavaFx Thread.
	 *
	 */
	fun showAndWait() = InvokeOnJavaFXApplicationThread{
		canCloseBinding.set(false)
		when (progressAlert.showAndWait().nullable) {
			ButtonType.OK -> Unit
			ButtonType.CANCEL -> throw CancellationException("Progress Dialog was Cancelled")
			else -> throw RuntimeException("Unexpected button type")
		}
	}

	/**
	 * Set progress to the end, and allow the dialog to be closed
	 *
	 */
	fun finish() = InvokeOnJavaFXApplicationThread {
		progressBar.finish()
		canCloseBinding.set(true)
	}

	/**
	 * Stop progress at its current state without finishing and close the dialog
	 *
	 */
	fun stopAndClose() = InvokeOnJavaFXApplicationThread {
		progressBar.stop()
		canCloseBinding.set(true)
		progressAlert.close()
	}

	/**
	 * Stop progress without finishing, leave the dialog open, but allow it to be closed.
	 *
	 */
	fun stop() = InvokeOnJavaFXApplicationThread {
		progressBar.stop()
		canCloseBinding.set(true)
	}
}