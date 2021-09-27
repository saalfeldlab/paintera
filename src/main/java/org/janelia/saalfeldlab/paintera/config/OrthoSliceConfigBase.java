package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

public class OrthoSliceConfigBase {

  private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);

  private final SimpleBooleanProperty showTopLeft = new SimpleBooleanProperty(true);

  private final SimpleBooleanProperty showTopRight = new SimpleBooleanProperty(true);

  private final SimpleBooleanProperty showBottomLeft = new SimpleBooleanProperty(true);

  private final SimpleDoubleProperty opacity = new SimpleDoubleProperty(1.0);

  private final SimpleDoubleProperty shading = new SimpleDoubleProperty(0.1);

  /* Note: when maximizing the 2x2 grid for a single row (i.e. one orthoview, one for the 3D render) the desired
   * 	orthoview is maximized by swapping it into the bottom left cell, then maximizing the bottom row.
   * 	To track this, we have an integer property that specifies which view is currently in the bottom left.
   * 	Going across and then down, we get the starting index of the botom left cell to be 2. If this integer
   * 	property is changed, it indicates that the views as initialized should be swapped so that the cell
   * 	in the index represented by this property is swapped with the3 cell that is initialized in the bottom left.
   * */
  private final SimpleIntegerProperty bottomLeftViewIndex = new SimpleIntegerProperty(2);

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

  public IntegerProperty bottomLeftViewIndexProperty() {

	return this.bottomLeftViewIndex;
  }
}
