package org.janelia.saalfeldlab.paintera.control.navigation;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyRotate
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static enum Axis
	{
		X(0),
		Y(1),
		Z(2);

		private final int axis;

		private Axis(final int axis)
		{
			this.axis = axis;
		}
	}

	private final Supplier<Axis> axis;

	private final DoubleSupplier step;

	private final AffineTransform3D displayTransform;

	private final AffineTransform3D globalToViewerTransform;

	private final AffineTransform3D globalTransform;

	private final Consumer<AffineTransform3D> submit;

	private final Object lock;

	public KeyRotate(
			final Supplier<Axis> axis,
			final DoubleSupplier step,
			final AffineTransform3D displayTransform,
			final AffineTransform3D globalToViewerTransform,
			final AffineTransform3D globalTransform,
			final Consumer<AffineTransform3D> submit,
			final Object lock)
	{
		super();
		this.axis = axis;
		this.step = step;
		this.displayTransform = displayTransform;
		this.globalToViewerTransform = globalToViewerTransform;
		this.globalTransform = globalTransform;
		this.submit = submit;
		this.lock = lock;
	}

	public void rotate(final double x, final double y)
	{
		final AffineTransform3D displayTransformCopy        = new AffineTransform3D();
		final AffineTransform3D globalToViewerTransformCopy = new AffineTransform3D();
		final AffineTransform3D globalTransformCopy         = new AffineTransform3D();

		synchronized (lock)
		{
			displayTransformCopy.set(displayTransform);
			globalToViewerTransformCopy.set(globalToViewerTransform);
			globalTransformCopy.set(globalTransform);
		}

		final AffineTransform3D concatenated = displayTransformCopy
				.copy()
				.concatenate(globalToViewerTransformCopy)
				.concatenate(globalTransformCopy);

		concatenated.set(concatenated.get(0, 3) - x, 0, 3);
		concatenated.set(concatenated.get(1, 3) - y, 1, 3);
		LOG.debug("Rotating {} around axis={} by angle={}", concatenated, axis, step);
		concatenated.rotate(axis.get().axis, step.getAsDouble());
		concatenated.set(concatenated.get(0, 3) + x, 0, 3);
		concatenated.set(concatenated.get(1, 3) + y, 1, 3);

		submit.accept(displayTransformCopy.copy().concatenate(globalToViewerTransformCopy).inverse().concatenate(
				concatenated));

	}

}
