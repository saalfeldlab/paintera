package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.binding.DoubleExpression
import javafx.beans.binding.StringExpression
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType.CONFIRMATION
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.DialogEvent
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import java.util.concurrent.CancellationException
import kotlin.jvm.optionals.getOrNull

class AnimatedProgressBarAlert(
	title: String,
	header: String,
	private var progressLabelBinding: StringExpression,
	private val progressBinding: DoubleExpression,
) : Alert(CONFIRMATION) {

	val progressBar = AnimatedProgressBar().apply {
		progressTargetProperty.bind(progressBinding)
		prefWidth = 300.0
	}

	val canCancelProperty = SimpleBooleanProperty(true)
	private val inProgressBinding = progressBar.progressProperty().greaterThanOrEqualTo(1.0).not()
	private val canCloseBinding = inProgressBinding.not().or(canCancelProperty)
	var cancelled = false
		private set

	init {
		initAppDialog()
		this.title = title
		this.headerText = header
		onCloseRequest = EventHandler {
			if (progressBar.progress < 1.0)
				cancelled = true
			stop()
		}
		(dialogPane.lookupButton(ButtonType.OK) as Button).apply {
			disableProperty().bind(inProgressBinding)
		}

		onCloseRequest = EventHandler {
			if (!canCloseBinding.get())
				it.consume()
		}

		(dialogPane.lookupButton(ButtonType.CANCEL) as Button).apply {
			disableProperty().bind(canCancelProperty.not())
			onAction = EventHandler {
				cancelled = true
				stopAndClose()
			}
		}

		val doneLabel = Label("Done!")
		doneLabel.visibleProperty().bind(progressBar.progressProperty().greaterThanOrEqualTo(1.0))
		dialogPane.content = VBox(10.0, createProgressLabel(), progressBar, HBox(doneLabel))
		dialogPane.addEventFilter(DialogEvent.DIALOG_CLOSE_REQUEST) {
			if (!canCloseBinding.get())
				it.consume()
		}
		isResizable = true
	}

	private fun createProgressLabel() = Label().apply {
		textProperty().bind(progressLabelBinding)
	}




	/**
	 * Show Dialog and wait for it to finish. Should be called on the JavaFx Thread.
	 *
	 */
	fun showAndStart() = InvokeOnJavaFXApplicationThread {
		when (showAndWait().getOrNull()) {
			ButtonType.OK -> Unit
			ButtonType.CANCEL -> throw CancellationException("Progress Dialog was Cancelled")
			else -> throw RuntimeException("Unexpected button type")
		}
	}

	/**
	 * finish the progressBar and close the dialog
	 *
	 */
	fun finish() = progressBar.finish()

	/**
	 * Stop progress at its current state without finishing and close the dialog
	 *
	 */
	fun stopAndClose() {
		progressBar.stop()
		close()
	}

	/**
	 * Stop progress without finishing, leave the dialog open, but allow it to be closed.
	 *
	 */
	fun stop() {
		progressBar.stop()
	}
}