package org.janelia.saalfeldlab.paintera.ui;

import javafx.scene.control.Alert;
import org.janelia.saalfeldlab.paintera.Paintera;

public class PainteraAlerts {

	/**
	 *
	 * @param type type of alert
	 * @return {@link Alert} with the title set to {@link Paintera#NAME}
	 */
	public static Alert alert(final Alert.AlertType type) {
		final Alert alert = new Alert(type);
		alert.setTitle(Paintera.NAME);
		return alert;
	}
}
