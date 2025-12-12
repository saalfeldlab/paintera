package org.janelia.saalfeldlab.paintera.data;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.view.Views;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ConvertedDataSource<D, T, U, V> implements DataSource<U, V> {

	private final DataSource<D, T> source;

	private final Converter<D, U> dataTypeConverter;

	private final Converter<T, V> typeConverter;

	private final Supplier<U> dataTypeExtensionSupplier;

	private final Supplier<V> typeExtensionSupplier;

	private final Function<Interpolation, InterpolatorFactory<U, RandomAccessible<U>>> dataInterpolation;

	private final Function<Interpolation, InterpolatorFactory<V, RandomAccessible<V>>> interpolation;

	private final String name;

	public ConvertedDataSource(
			final DataSource<D, T> source,
			final Converter<D, U> dataTypeConverter,
			final Converter<T, V> typeConverter,
			final Supplier<U> dataTypeSupplier,
			final Supplier<V> typeSupplier,
			final Function<Interpolation, InterpolatorFactory<U, RandomAccessible<U>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<V, RandomAccessible<V>>> interpolation,
			final String name) {

		super();
		this.source = source;
		this.dataTypeConverter = dataTypeConverter;
		this.typeConverter = typeConverter;
		this.dataTypeExtensionSupplier = dataTypeSupplier;
		this.typeExtensionSupplier = typeSupplier;
		this.dataInterpolation = dataInterpolation;
		this.interpolation = interpolation;
		this.name = name;
	}

	@Override
	public boolean isPresent(final int t) {

		return source.isPresent(t);
	}

	@Override
	public RandomAccessibleInterval<V> getSource(final int t, final int level) {

		return Converters.convert2(source.getSource(t, level), typeConverter, typeExtensionSupplier);
	}

	@Override
	public RealRandomAccessible<V> getInterpolatedSource(final int t, final int level, final Interpolation method) {

		return Views.interpolate(
				Views.extendValue(getSource(t, level), typeExtensionSupplier.get()),
				interpolation.apply(method)
		);
	}

	@Override
	public void getSourceTransform(final int t, final int level, final AffineTransform3D transform) {

		source.getSourceTransform(t, level, transform);
	}

	@Override
	public V getType() {

		return typeExtensionSupplier.get();
	}

	@Override
	public String getName() {

		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {

		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels() {

		return source.getNumMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval<U> getDataSource(final int t, final int level) {

		return Converters.convert2(
				source.getDataSource(t, level),
				dataTypeConverter,
				dataTypeExtensionSupplier
		);
	}

	@Override
	public RealRandomAccessible<U> getInterpolatedDataSource(final int t, final int level, final Interpolation method) {

		return Views.interpolate(
				Views.extendValue(getDataSource(t, level), dataTypeExtensionSupplier.get()),
				dataInterpolation.apply(method)
		);
	}

	@Override
	public U getDataType() {

		return dataTypeExtensionSupplier.get();
	}

	@Override
	public void invalidate(Long key) {

		source.invalidate(key);
	}

	@Override
	public void invalidateIf(long parallelismThreshold, Predicate<Long> condition) {

		source.invalidateIf(parallelismThreshold, condition);
	}

	@Override
	public void invalidateIf(Predicate<Long> condition) {

		source.invalidateIf(condition);
	}

	@Override
	public void invalidateAll(long parallelismThreshold) {

		source.invalidateAll(parallelismThreshold);
	}

	@Override
	public void invalidateAll() {

		source.invalidateAll();
	}
}
