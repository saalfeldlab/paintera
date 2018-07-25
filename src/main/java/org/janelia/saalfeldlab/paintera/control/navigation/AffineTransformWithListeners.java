package org.janelia.saalfeldlab.paintera.control.navigation;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class AffineTransformWithListeners
{

	private final AffineTransform3D transform;

	private final List<TransformListener<AffineTransform3D>> listeners = new ArrayList<>();

	public AffineTransformWithListeners()
	{
		this(new AffineTransform3D());
	}

	public AffineTransformWithListeners(final AffineTransform3D transform)
	{
		this.transform = transform;
	}

	public void addListener(final TransformListener<AffineTransform3D> listener)
	{
		listeners.add(listener);
		notifyListener(listener);
	}

	public boolean removeListener(final TransformListener<AffineTransform3D> listener)
	{
		return listeners.remove(listener);
	}

	private void notifyListener(final TransformListener<AffineTransform3D> listener)
	{
		listener.transformChanged(transform);
	}

	private void notifyListeners()
	{
		listeners.forEach(this::notifyListener);
	}

	public void setTransform(final AffineTransform3D affine)
	{
		this.transform.set(affine);
		notifyListeners();
	}

	public AffineTransform3D getTransformCopy()
	{
		return transform.copy();
	}

	public void getTransformCopy(final AffineTransform3D target)
	{
		target.set(transform);
	}

	@Override
	public String toString()
	{
		return transform + " with " + listeners.size() + " listeners";
	}

}
