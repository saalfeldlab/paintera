package org.janelia.saalfeldlab.paintera.control.paint;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.ui.overlays.BrushOverlay;

public class PaintActions2D {

  private static final double BRUSH_RADIUS_SCALE_FACTOR = 1.1;

  private final ViewerPanelFX viewer;

  private final BrushOverlay brushOverlay;

  private final SimpleDoubleProperty brushRadius = new SimpleDoubleProperty(5.0);

  private final SimpleDoubleProperty brushDepth = new SimpleDoubleProperty(1.0);

  public PaintActions2D(final ViewerPanelFX viewer, final GlobalTransformManager manager) {

	super();
	this.viewer = viewer;
	this.brushOverlay = new BrushOverlay(this.viewer);
	this.brushOverlay.getPhysicalRadiusProperty().bind(brushRadius);
	this.brushOverlay.getBrushDepthProperty().bind(brushDepth);
  }

  public void hideBrushOverlay() {

	setBrushOverlayVisible(false);
  }

  public void showBrushOverlay() {

	setBrushOverlayVisible(true);
  }

  public void setBrushOverlayVisible(final boolean visible) {

	this.brushOverlay.setVisible(visible);
	viewer.getDisplay().drawOverlays();
  }

  public void setBrushOverlayVisible(final boolean visible, final double x, final double y) {

	this.brushOverlay.setPosition(x, y);
	this.brushOverlay.setVisible(visible);
	viewer.getDisplay().drawOverlays();
  }

  public void setBrushOverlayValid(final boolean valid, final String reason) {

	brushOverlay.setCanPaint(valid);
	brushOverlay.setReason(reason);
  }

  public void setBrushOverlayValid(final boolean valid) {

	brushOverlay.setCanPaint(valid);
  }

  public void changeBrushRadius(final double sign) {

	if (sign > 0) {
	  decreaseBrushRadius();
	} else if (sign < 0) {
	  increaseBrushRadius();
	}
  }

  public void changeBrushDepth(final double sign) {

	final double newDepth = brushDepth.get() + (sign > 0 ? -1 : 1);
	this.brushDepth.set(Math.max(Math.min(newDepth, 2.0), 1.0));
  }

  public void decreaseBrushRadius() {

	setBrushRadius(brushRadius.get() / BRUSH_RADIUS_SCALE_FACTOR);
  }

  public void increaseBrushRadius() {

	setBrushRadius(brushRadius.get() * BRUSH_RADIUS_SCALE_FACTOR);
  }

  public void setBrushRadius(final double radius) {

	if (radius > 0)
	  this.brushRadius.set(radius);
  }

  public DoubleProperty brushRadiusProperty() {

	return this.brushRadius;
  }

  public DoubleProperty brushDepthProperty() {

	return this.brushDepth;
  }
}
