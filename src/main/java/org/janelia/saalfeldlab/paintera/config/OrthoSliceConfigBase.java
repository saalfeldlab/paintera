package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class OrthoSliceConfigBase {

	private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showTopLeft = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showTopRight = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showBottomLeft = new SimpleBooleanProperty(true);

	private final SimpleDoubleProperty opacity = new SimpleDoubleProperty(0.5);

	private final SimpleDoubleProperty shading = new SimpleDoubleProperty(0.1);

	public BooleanProperty isEnabledProperty() {

		return this.enabled;
	}

	public BooleanProperty showTopLeftProperty() {

		return this.showTopLeft;
	}

	public BooleanProperty showTopRightProperty() {

		return this.showTopRight;
	}

	public BooleanProperty showBottomLeftProperty() {

		return this.showBottomLeft;
	}

	public DoubleProperty opacityProperty() {

		return this.opacity;
	}

	public DoubleProperty shadingProperty() {

		return this.shading;
	}

}
