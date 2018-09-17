package org.janelia.saalfeldlab.paintera.data;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.Collection;
import java.util.function.Predicate;

public class DelegatingDataSource<D, T> implements DataSource<D, T>
{

	private final DataSource<D, T> delegate;

	public DelegatingDataSource(final DataSource<D, T> delegate)
	{
		super();
		this.delegate = delegate;
	}

	@Override
	public boolean isPresent(final int t)
	{
		return this.delegate.isPresent(t);
	}

	@Override
	public RandomAccessibleInterval<T> getSource(final int t, final int level)
	{
		return this.delegate.getSource(t, level);
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method)
	{
		return this.delegate.getInterpolatedSource(t, level, method);
	}

	@Override
	public void getSourceTransform(final int t, final int level, final AffineTransform3D transform)
	{
		this.delegate.getSourceTransform(t, level, transform);
	}

	@Override
	public T getType()
	{
		return this.delegate.getType();
	}

	@Override
	public String getName()
	{
		return this.delegate.getName();
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return this.delegate.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return this.delegate.getNumMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval<D> getDataSource(final int t, final int level)
	{
		return this.delegate.getDataSource(t, level);
	}

	@Override
	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method)
	{
		return this.delegate.getInterpolatedDataSource(t, level, method);
	}

	@Override
	public D getDataType()
	{
		return this.delegate.getDataType();
	}

	@Override
	public void invalidateAll() {
		delegate.invalidateAll();
	}
}
