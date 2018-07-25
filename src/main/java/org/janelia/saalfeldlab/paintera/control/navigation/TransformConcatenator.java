package org.janelia.saalfeldlab.paintera.control.navigation;

import java.lang.invoke.MethodHandles;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransformConcatenator
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final GlobalTransformManager manager;

	private final AffineTransform3D globalTransform = new AffineTransform3D();

	private final AffineTransformWithListeners global = new AffineTransformWithListeners(globalTransform);

	private final AffineTransformWithListeners concatenated = new AffineTransformWithListeners();

	private final AffineTransformWithListeners displayTransform;

	private final AffineTransformWithListeners globalToViewer;

	private TransformListener<AffineTransform3D> listener;

	private final TransformListener<AffineTransform3D> globalTransformTracker;

	private final Object lock;

	public TransformConcatenator(
			final GlobalTransformManager manager,
			final AffineTransformWithListeners displayTransform,
			final AffineTransformWithListeners globalToViewer,
			final Object lock)
	{
		super();
		this.manager = manager;
		this.displayTransform = displayTransform;
		this.globalToViewer = globalToViewer;
		this.globalTransformTracker = tf -> global.setTransform(tf);
		this.lock = lock;
		this.manager.addListener(this.globalTransformTracker);

		this.global.addListener(tf -> update());
		this.displayTransform.addListener(tf -> update());
		this.globalToViewer.addListener(tf -> update());
		this.concatenated.addListener(tf -> notifyListener());
	}

	private void notifyListener()
	{
		if (listener != null)
			listener.transformChanged(concatenated.getTransformCopy());
	}

	private void update()
	{
		synchronized (lock)
		{
			final AffineTransform3D concatenated = new AffineTransform3D();
			LOG.debug("Concatenating: {} {} {}", displayTransform, globalToViewer, globalTransform); //
			LOG.debug("Concatening with global-to-viewer={} this={}", globalToViewer, this);
			concatenated.set(globalTransform);
			concatenated.preConcatenate(globalToViewer.getTransformCopy());
			concatenated.preConcatenate(displayTransform.getTransformCopy());
			LOG.debug("Concatenated transform: {}", concatenated);
			this.concatenated.setTransform(concatenated);
		}
	}

	public synchronized AffineTransform3D getTransform()
	{
		return concatenated.getTransformCopy();
	}

	public void setTransformListener(final TransformListener<AffineTransform3D> transformListener)
	{
		this.listener = transformListener;
		notifyListener();
	}

	public void forceUpdate()
	{
		update();
	}

}
