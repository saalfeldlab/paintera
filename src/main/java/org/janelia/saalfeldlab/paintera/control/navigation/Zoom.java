package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.util.Duration;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;

public class Zoom {

	private final AffineTransform3D global = new AffineTransform3D();

	private final AffineTransform3D concatenated;

	private final GlobalTransformManager manager;

	private boolean busy = false;

	public Zoom(
			final GlobalTransformManager manager,
			final AffineTransform3D concatenated) {

		this.manager = manager;
		this.concatenated = concatenated;

		this.manager.addListener(global::set);
	}

	public void zoomCenteredAt(final double scaleFactor, final double x, final double y) {
		if (Math.abs(1 - scaleFactor) > .2) {
			zoomCenteredAt(scaleFactor, x, y, Duration.millis(100));
		} else {
			zoomCenteredAt(scaleFactor, x, y, null);
		}
	}
	public void zoomCenteredAt(final double scaleFactor, final double x, final double y, final Duration animate) {

		if (scaleFactor == 0.0 || busy) {
			return;
		}

		final AffineTransform3D global = new AffineTransform3D();
		synchronized (manager) {
			global.set(this.global);
		}
		final double[] location = new double[]{x, y, 0};
		concatenated.applyInverse(location, location);
		global.apply(location, location);

		for (int d = 0; d < location.length; ++d) {
			global.set(global.get(d, 3) - location[d], d, 3);
		}
		global.scale(scaleFactor);
		for (int d = 0; d < location.length; ++d) {
			global.set(global.get(d, 3) + location[d], d, 3);
		}

		if (animate != null) {
			this.busy = true;
			manager.setTransformAndAnimate(global, animate, () -> this.busy = false);
		} else {
			manager.setTransform(global);
		}
	}
}
