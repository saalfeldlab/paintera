package org.janelia.saalfeldlab.paintera.ui;

import javafx.scene.control.Alert;
import org.janelia.saalfeldlab.paintera.Paintera;

public class PainteraAlerts {

	/**
	 *
	 * delegates to {@link #alert(Alert.AlertType, boolean)} with {@code isResizable = true}.
	 *
	 * @param type type of alert
	 * @return {@link Alert} with the title set to {@link Paintera#NAME}
	 */
	public static Alert alert(final Alert.AlertType type) {
		return alert(type, true);
	}

	/**
	 *
	 * @param type type of alert
	 * @param isResizable set to {@code true} if dialog should be resizable
	 * @return {@link Alert} with the title set to {@link Paintera#NAME}
	 */
	public static Alert alert(final Alert.AlertType type, boolean isResizable) {
		final Alert alert = new Alert(type);
		alert.setTitle(Paintera.NAME);
		alert.setResizable(isResizable);
		return alert;
	}
}
