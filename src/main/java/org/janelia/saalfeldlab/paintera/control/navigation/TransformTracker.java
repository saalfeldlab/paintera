package org.janelia.saalfeldlab.paintera.control.navigation;

import java.util.function.Consumer;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

final class TransformTracker implements TransformListener<AffineTransform3D>
{

	private final AffineTransform3D transform;

	private final Object lock;

	private final Consumer<AffineTransform3D> onChange;

	public TransformTracker(
			final AffineTransform3D transform,
			final Object lock)
	{
		this(transform, tf -> {
		}, lock);
	}

	public TransformTracker(
			final AffineTransform3D transform,
			final Consumer<AffineTransform3D> onChange,
			final Object lock)
	{
		super();
		this.transform = transform;
		this.lock = lock;
		this.onChange = onChange;
	}

	@Override
	public void transformChanged(final AffineTransform3D t)
	{
		synchronized (lock)
		{
			transform.set(t);
			onChange.accept(t);
		}
	}
}
