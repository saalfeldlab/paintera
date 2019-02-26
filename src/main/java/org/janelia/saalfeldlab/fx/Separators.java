package org.janelia.saalfeldlab.fx;

import javafx.geometry.Orientation;
import javafx.scene.control.Separator;

public class Separators {

	public static Separator vertical() {
		return withOrientation(Orientation.VERTICAL);
	}

	public static Separator horizontal() {
		return withOrientation(Orientation.HORIZONTAL);
	}

	public static Separator withOrientation(final Orientation orientation) {
		final Separator separator = new Separator();
		separator.setOrientation(orientation);
		return separator;
	}

}
