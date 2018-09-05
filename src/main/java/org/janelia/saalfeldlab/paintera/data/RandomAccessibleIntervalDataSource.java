package org.janelia.saalfeldlab.paintera.data;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import bdv.viewer.Interpolation;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.transform.integer.MixedTransform;
import net.imglib2.type.Type;
import net.imglib2.util.Triple;
import net.imglib2.util.Util;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.mask.AxisOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomAccessibleIntervalDataSource<D extends Type<D>, T extends Type<T>> implements DataSource<D, T>, HasModifiableAxisOrder
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final AffineTransform3D[] mipmapTransforms;

	private final ObjectProperty<AxisOrder> axisOrder = new SimpleObjectProperty<>();

	private final RandomAccessibleInterval<D>[] dataSources;

	private final RandomAccessibleInterval<T>[] sources;

	private final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation;

	private final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation;

	private final Supplier<D> dataTypeSupplier;

	private final Supplier<T> typeSupplier;

	private final String name;

	public RandomAccessibleIntervalDataSource(
			final Triple<RandomAccessibleInterval<D>[], RandomAccessibleInterval<T>[], AffineTransform3D[]> data,
			final AxisOrder axisOrder,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name)
	{
		this(data.getA(), data.getB(), data.getC(), axisOrder, dataInterpolation, interpolation, name);
	}

	@SuppressWarnings("unchecked")
	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D> dataSource,
			final RandomAccessibleInterval<T> source,
			final AffineTransform3D mipmapTransform,
			final AxisOrder axisOrder,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name)
	{
		this(
				new RandomAccessibleInterval[] {dataSource},
				new RandomAccessibleInterval[] {source},
				new AffineTransform3D[] {mipmapTransform},
				axisOrder,
				dataInterpolation,
				interpolation,
				name
		    );
	}

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D>[] dataSources,
			final RandomAccessibleInterval<T>[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final AxisOrder axisOrder,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name)
	{
		this(
				dataSources,
				sources,
				mipmapTransforms,
				axisOrder,
				dataInterpolation,
				interpolation,
				() -> Util.getTypeFromInterval(dataSources[0]).createVariable(),
				() -> Util.getTypeFromInterval(sources[0]).createVariable(),
				name
		    );
	}

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D>[] dataSources,
			final RandomAccessibleInterval<T>[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final AxisOrder axisOrder,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final Supplier<D> dataTypeSupplier,
			final Supplier<T> typeSupplier,
			final String name)
	{
		super();
		this.mipmapTransforms = mipmapTransforms;
		this.dataSources = dataSources;
		this.sources = sources;
		this.dataInterpolation = dataInterpolation;
		this.interpolation = interpolation;
		this.dataTypeSupplier = dataTypeSupplier;
		this.typeSupplier = typeSupplier;
		this.name = name;
		this.axisOrder.set(axisOrder);
	}

	@Override
	public ObjectProperty<AxisOrder> axisOrderProperty()
	{
		return this.axisOrder;
	}

	@Override
	public boolean isPresent(final int t)
	{
		return true;
	}

	@Override
	public RandomAccessibleInterval<T> getSource(final int t, final int level)
	{
		LOG.debug("Requesting source at t={}, level={}", t, level);
		return permute(sources[level], axisOrder.get());
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method)
	{
		LOG.debug("Requesting source at t={}, level={} with interpolation {}: ", t, level, method);
		return Views.interpolate(
				Views.extendValue(getSource(t, level), typeSupplier.get()),
				interpolation.apply(method)
		                        );
	}

	@Override
	public void getSourceTransform(final int t, final int level, final AffineTransform3D transform)
	{
		LOG.trace("Requesting mipmap transform for level {} at time {}: {}", level, t, mipmapTransforms[level]);
		transform.set(mipmapTransforms[level]);
	}

	@Override
	public T getType()
	{
		return typeSupplier.get();
	}

	@Override
	public String getName()
	{
		return name;
	}

	// TODO VoxelDimensions is the only class pulled in by spim_data
	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		// TODO What to do about this? Do we need this at all?
		return null;
	}

	@Override
	public int getNumMipmapLevels()
	{
		return mipmapTransforms.length;
	}

	@Override
	public RandomAccessibleInterval<D> getDataSource(final int t, final int level)
	{
		LOG.debug("Requesting data source at t={}, level={}", t, level);
		return permute(dataSources[level], axisOrder.get());
	}

	@Override
	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method)
	{
		LOG.debug("Requesting data source at t={}, level={} with interpolation {}: ", t, level, method);
		return Views.interpolate(
				Views.extendValue(getDataSource(t, level), dataTypeSupplier.get()),
				dataInterpolation.apply(method)
		                        );
	}

	@Override
	public D getDataType()
	{
		return dataTypeSupplier.get();
	}

	public RandomAccessibleIntervalDataSource<D, T> copy()
	{
		return new RandomAccessibleIntervalDataSource<>(
				dataSources,
				sources,
				mipmapTransforms,
				axisOrder.get(),
				dataInterpolation,
				interpolation,
				dataTypeSupplier,
				typeSupplier,
				name
		);
	}

	private static <T> RandomAccessibleInterval<T> permute(RandomAccessibleInterval<T> rai, AxisOrder axisOrder)
	{
		assert rai.numDimensions() == axisOrder.numDimensions();
		assert axisOrder.numDimensions() == axisOrder.numSpaceDimensions();

		if (AxisOrder.XYZ.equals(axisOrder))
			return rai;

		int[] indicesAsRAIDimensions = axisOrder.indices(AxisOrder.Axis.X, AxisOrder.Axis.Y, AxisOrder.Axis.Z);
		MixedTransform tf = new MixedTransform(rai.numDimensions(), rai.numDimensions());
		tf.setComponentMapping(indicesAsRAIDimensions);
		RandomAccessible<T> view = new MixedTransformView<>(rai, tf);
		long[] min = new long[rai.numDimensions()];
		long[] max = new long[rai.numDimensions()];
		Arrays.setAll(min, d -> rai.min(indicesAsRAIDimensions[d]));
		Arrays.setAll(max, d -> rai.max(indicesAsRAIDimensions[d]));
		return Views.interval(view, new FinalInterval(min, max));
	}

}
