package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.geometry.Pos
import javafx.stage.Window
import org.controlsfx.control.Notifications
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.state.InvalidLabelReason
import org.janelia.saalfeldlab.paintera.paintera

object PainteraNotifications {

	/**
     * Show a warning notification at the bottom center of the provided window (or most recent focus holder if not provided).
     *
     * @param title of the warning notification
     * @param text of the warning notification
	 * @param window to show the notification on; defaults to the window of the most recently focused viewer
	 */
	fun warn(
        title: String,
        text: String,
        window: Window? = null
    ) = InvokeOnJavaFXApplicationThread {
        val owner = window ?: paintera.baseView.mostRecentFocusHolder.value.viewer().scene.window
		val notification = Notifications.create()
			.title(title)
			.text(text)
			.styleClass("warning")
			.position(Pos.BOTTOM_CENTER)
			.owner(owner)
		notification
			.threshold(1, notification)
			.showWarning()
	}
}

fun invalidPaintSelectionWarning(invalidReason: InvalidLabelReason, window: Window? = null) = PainteraNotifications.warn(
    invalidReason.reason,
    invalidReason.solution,
    window
)