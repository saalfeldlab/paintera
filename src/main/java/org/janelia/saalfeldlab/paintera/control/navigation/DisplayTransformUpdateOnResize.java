package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableDoubleValue;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class DisplayTransformUpdateOnResize
{

	private double canvasW = 1;

	private final ObservableDoubleValue width;

	private final ObservableDoubleValue height;

	private final Object lock;

	private final ChangeListener<Number> onResize;

	private final AffineTransform3D displayTransform = new AffineTransform3D();

	private final TransformListener<AffineTransform3D> displayTransformListener = displayTransform::set;

	private final AffineTransformWithListeners displayTransformUpdater;

	public DisplayTransformUpdateOnResize(
			final AffineTransformWithListeners displayTransformUpdater,
			final ObservableDoubleValue width,
			final ObservableDoubleValue height,
			final Object lock)
	{
		super();
		this.displayTransformUpdater = displayTransformUpdater;
		this.width = width;
		this.height = height;
		this.lock = lock;

		this.onResize = (obs, oldv, newv) -> {
			synchronized (lock)
			{
				setCanvasSize(this.width.doubleValue(), this.height.doubleValue(), true);
			}
		};

		listen();

		setCanvasSize(width.get(), height.get(), false);

	}

	public void listen()
	{
		this.width.addListener(onResize);
		this.height.addListener(onResize);
		this.displayTransformUpdater.addListener(displayTransformListener);
	}

	public void stopListening()
	{
		this.width.removeListener(onResize);
		this.height.removeListener(onResize);
		this.displayTransformUpdater.removeListener(displayTransformListener);
	}

	private void setCanvasSize(final double width, final double height, final boolean updateTransform)
	{
		if (width == 0 || height == 0)
			return;
		if (updateTransform) // && false )
		{
			synchronized (lock)
			{
				final AffineTransform3D transform = this.displayTransform;
				transform.set(0, 0, 3);
				transform.set(0, 1, 3);
				transform.scale(width / canvasW);
				transform.set(width / 2, 0, 3);
				transform.set(height / 2, 1, 3);
				displayTransformUpdater.setTransform(transform);
			}
		}
		canvasW = width;
	}

}
