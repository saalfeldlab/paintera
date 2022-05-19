package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.beans.binding.DoubleExpression;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;

public class Zoom {

  private final DoubleExpression speed;

  private final AffineTransform3D global = new AffineTransform3D();

  private final AffineTransform3D concatenated;

  private final GlobalTransformManager manager;

  public Zoom(
		  final DoubleExpression speed,
		  final GlobalTransformManager manager,
		  final AffineTransform3D concatenated) {

	this.speed = speed;
	this.manager = manager;
	this.concatenated = concatenated;

	this.manager.addListener(global::set);
  }

  public void zoomCenteredAt(final double delta, final double x, final double y) {

	if (delta == 0.0) {
	  return;
	}

	final AffineTransform3D global = new AffineTransform3D();
	synchronized (manager) {
	  global.set(this.global);
	}
	final double[] location = new double[]{x, y, 0};
	concatenated.applyInverse(location, location);
	global.apply(location, location);

	final double dScale = speed.get();
	final double scale = delta > 0 ? 1.0 / dScale : dScale;

	for (int d = 0; d < location.length; ++d) {
	  global.set(global.get(d, 3) - location[d], d, 3);
	}
	global.scale(scale);
	for (int d = 0; d < location.length; ++d) {
	  global.set(global.get(d, 3) + location[d], d, 3);
	}

	manager.setTransform(global);
  }
}
