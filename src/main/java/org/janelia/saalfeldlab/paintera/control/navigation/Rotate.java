package org.janelia.saalfeldlab.paintera.control.navigation;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import net.imglib2.realtransform.AffineTransform3D;

public class Rotate
{

	private static final double ROTATION_STEP = Math.PI / 180;

	private final DoubleSupplier speed;

	private final AffineTransform3D globalTransform;

	private final AffineTransform3D displayTransform;

	private final AffineTransform3D globalToViewerTransform;

	private final Consumer<AffineTransform3D> submitTransform;

	private final Object lock;

	private final AffineTransform3D affineDragStart = new AffineTransform3D();

	public Rotate(
			final DoubleSupplier speed,
			final AffineTransform3D globalTransform,
			final AffineTransform3D displayTransform,
			final AffineTransform3D globalToViewerTransform,
			final Consumer<AffineTransform3D> submitTransform,
			final Object lock)
	{
		super();
		this.speed = speed;
		this.globalTransform = globalTransform;
		this.displayTransform = displayTransform;
		this.globalToViewerTransform = globalToViewerTransform;
		this.submitTransform = submitTransform;
		this.lock = lock;
	}

	public void initialize()
	{
		synchronized (lock)
		{
			affineDragStart.set(globalTransform);
		}
	}

	public void rotate(final double x, final double y, final double startX, final double startY)
	{
		final AffineTransform3D affine = new AffineTransform3D();
		synchronized (lock)
		{
			final double v = ROTATION_STEP * this.speed.getAsDouble();
			affine.set(affineDragStart);
			final double[] point  = new double[] {x, y, 0};
			final double[] origin = new double[] {startX, startY, 0};

			displayTransform.applyInverse(point, point);
			displayTransform.applyInverse(origin, origin);

			final double[] delta = new double[] {point[0] - origin[0], point[1] - origin[1], 0};
			// TODO do scaling separately. need to swap .get( 0, 0 ) and
			// .get( 1, 1 ) ?
			final double[] rotation = new double[] {
					+delta[1] * v * displayTransform.get(0, 0),
					-delta[0] * v * displayTransform.get(1, 1),
					0};

			globalToViewerTransform.applyInverse(origin, origin);
			globalToViewerTransform.applyInverse(rotation, rotation);

			// center shift
			for (int d = 0; d < origin.length; ++d)
				affine.set(affine.get(d, 3) - origin[d], d, 3);

			for (int d = 0; d < rotation.length; ++d)
				affine.rotate(d, rotation[d]);

			// center un-shift
			for (int d = 0; d < origin.length; ++d)
				affine.set(affine.get(d, 3) + origin[d], d, 3);

			submitTransform.accept(affine);
		}
	}
}
