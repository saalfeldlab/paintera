package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.util.Duration;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class Rotate {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final double DEFAULT_STEP = Math.PI / 180;
	private double speed = 1.0;

	private final GlobalTransformManager manager;
	private final AffineTransformWithListeners globalToViewerTransformListener;

	private final AffineTransform3D affineDragStart = new AffineTransform3D();
	private boolean busy = false;

	public Rotate(
			final GlobalTransformManager globalTransformManager,
			final AffineTransformWithListeners globalToViewerTransformListener) {

		super();
		this.globalToViewerTransformListener = globalToViewerTransformListener;
		this.manager = globalTransformManager;
	}

	public void setSpeed(double speed) {

		this.speed = speed;
	}

	public void initialize(final double displayStartX, final double displayStartY) {

		synchronized (manager) {
			affineDragStart.set(manager.getTransform());
		}
	}

	public enum Axis {
		X, Y, Z
	}

	public synchronized void rotateAroundAxis(final double x, final double y, final Axis axis) {

		if (busy)
			return;

		synchronized (manager) {

			final AffineTransform3D rotatedViewerTransform = globalToViewerTransformListener.getTransformCopy();
			final var step = speed * DEFAULT_STEP;

			rotatedViewerTransform.translate(-x, -y, 0.0);
			rotatedViewerTransform.rotate(axis.ordinal(), step);
			rotatedViewerTransform.translate(x, y, 0.0);

			final AffineTransform3D rotatedGlobalTransform = globalToViewerTransformListener.getTransformCopy()
					.concatenate(manager.getTransform().inverse())
					.inverse()
					.concatenate(rotatedViewerTransform);

			final double magnitude = Math.abs(speed);
			if (magnitude > 1) {
				busy = true;
				manager.setTransformAndAnimate(rotatedGlobalTransform, Duration.millis(Math.log10(magnitude) * 100), () -> this.busy = false);
			} else {
				manager.setTransform(rotatedGlobalTransform);
			}
		}
	}

	public void rotate3D(final double x, final double y, final double startX, final double startY) {

		synchronized (manager) {
			final double v = DEFAULT_STEP * this.speed;

			final double[] start = new double[]{startX, startY, 0};
			final double[] end = new double[]{x, y, 0};

			// TODO do scaling separately. need to swap .get( 0, 0 ) and .get( 1, 1 ) ?
			final double[] rotation = new double[]{
					(end[1] - start[1]) * v,
					-(end[0] - start[0]) * v,
					0};

			final AffineTransform3D rotatedViewerTransform = globalToViewerTransformListener.getTransformCopy()
					.concatenate(manager.getTransform().inverse())
					.concatenate(affineDragStart);


			// center shift
			rotatedViewerTransform.translate(-start[0], -start[1], -start[2]);
			// rotate
			for (int d = 0; d < rotation.length; ++d)
				rotatedViewerTransform.rotate(d, rotation[d]);
			// center un-shift
			rotatedViewerTransform.translate(start);

			final AffineTransform3D rotatedGlobalTransform = globalToViewerTransformListener.getTransformCopy()
					.concatenate(manager.getTransform().inverse())
					.inverse()
					.concatenate(rotatedViewerTransform);

			manager.setTransform(rotatedGlobalTransform);
		}
	}
}
