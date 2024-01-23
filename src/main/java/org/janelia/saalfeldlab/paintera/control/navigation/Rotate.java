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

	private final AffineTransform3D globalTransform = new AffineTransform3D();

	private final AffineTransformWithListeners displayTransformUpdater;

	private final AffineTransformWithListeners globalToViewerTransformUpdater;

	private final GlobalTransformManager manager;

	private final AffineTransform3D affineDragStart = new AffineTransform3D();

	private final TranslationController.TransformTracker globalTransformTracker;

	private final TranslationController.TransformTracker globalToViewerTransformTracker;

	private final TranslationController.TransformTracker displayTransformTracker;

	private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

	private final AffineTransform3D displayTransform = new AffineTransform3D();
	private boolean busy = false;

	public Rotate(
			final AffineTransformWithListeners displayTransformUpdater,
			final AffineTransformWithListeners globalToViewerTransformUpdater,
			final GlobalTransformManager globalTransformManager) {

		super();

		this.globalTransformTracker = new TranslationController.TransformTracker(globalTransform, globalTransformManager);
		this.globalToViewerTransformTracker = new TranslationController.TransformTracker(globalToViewerTransform, globalTransformManager);
		this.displayTransformTracker = new TranslationController.TransformTracker(displayTransform, globalTransformManager);

		this.displayTransformUpdater = displayTransformUpdater;
		this.globalToViewerTransformUpdater = globalToViewerTransformUpdater;
		this.manager = globalTransformManager;
		listenOnTransformChanges();
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}



	public void initialize(final double displayStartX, final double displayStartY) {

		synchronized (manager) {
			affineDragStart.set(globalTransform);
		}
	}

	public void listenOnTransformChanges() {

		this.manager.addListener(this.globalTransformTracker);
		this.displayTransformUpdater.addListener(this.displayTransformTracker);
		this.globalToViewerTransformUpdater.addListener(this.globalToViewerTransformTracker);
	}

	public void stopListeningOnTransformChanges() {

		this.manager.removeListener(this.globalTransformTracker);
		this.displayTransformUpdater.removeListener(this.displayTransformTracker);
		this.globalToViewerTransformUpdater.removeListener(this.globalToViewerTransformTracker);
	}

	public enum Axis {
		X, Y, Z
	}

	public synchronized void rotateAroundAxis(final double x, final double y, final Axis axis) {
		if (busy) return;

		final AffineTransform3D concatenated = displayTransform.copy()
				.concatenate(globalToViewerTransform)
				.concatenate(globalTransform);

		concatenated.set(concatenated.get(0, 3) - x, 0, 3);
		concatenated.set(concatenated.get(1, 3) - y, 1, 3);

		final var step = speed * DEFAULT_STEP;
		LOG.debug("Rotating {} around axis={} by angley={}", concatenated, axis, step);
		concatenated.rotate(axis.ordinal(), step);
		concatenated.set(concatenated.get(0, 3) + x, 0, 3);
		concatenated.set(concatenated.get(1, 3) + y, 1, 3);

		final AffineTransform3D rotatedTransform = displayTransform.copy()
				.concatenate(globalToViewerTransform)
				.inverse()
				.concatenate(concatenated);

		final double magnitude = Math.abs(speed);
		if (magnitude > 1) {
			busy = true;
			manager.setTransform(rotatedTransform, Duration.millis(Math.log10(magnitude) * 100), () -> this.busy = false);
		} else {
			manager.setTransform(rotatedTransform);
		}
	}

	public void rotate3D(final double x, final double y, final double startX, final double startY) {

		final AffineTransform3D rotated = new AffineTransform3D();
		synchronized (manager) {
			final double v = DEFAULT_STEP * this.speed;
			rotated.set(affineDragStart);
			final double[] start = new double[]{startX, startY, 0};
			final double[] end = new double[]{x, y, 0};

			displayTransform.applyInverse(end, end);
			displayTransform.applyInverse(start, start);

			final double[] delta = new double[]{end[0] - start[0], end[1] - start[1], 0};
			// TODO do scaling separately. need to swap .get( 0, 0 ) and
			// .get( 1, 1 ) ?
			final double[] rotation = new double[]{
					delta[1] * v * displayTransform.get(0, 0),
					-delta[0] * v * displayTransform.get(1, 1),
					0};

			globalToViewerTransform.applyInverse(start, start);
			globalToViewerTransform.applyInverse(rotation, rotation);

			// center shift
			for (int d = 0; d < start.length; ++d) {
				rotated.set(rotated.get(d, 3) - start[d], d, 3);
			}

			for (int d = 0; d < rotation.length; ++d) {
				rotated.rotate(d, rotation[d]);
			}

			// center un-shift
			for (int d = 0; d < start.length; ++d) {
				rotated.set(rotated.get(d, 3) + start[d], d, 3);
			}

			manager.setTransform(rotated);
		}
	}
}
