package org.janelia.saalfeldlab.paintera.data;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.Invalidate;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import org.janelia.saalfeldlab.net.imglib2.util.Triple;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.cache.InvalidateDelegates;
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RandomAccessibleIntervalDataSource<D extends Type<D>, T extends Type<T>> implements DataSource<D, T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Supplier<AffineTransform3D[]> getMipmapTransforms;

	private final RandomAccessibleInterval<T>[] sources;

	private final RandomAccessibleInterval<D>[] dataSources;

	private final Invalidate<Long> invalidate;

	private final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation;

	private final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation;

	private final D dataTypeSupplier;

	private final T typeSupplier;

	private final String name;

	public static class DataWithInvalidate<D, T> {

		public final RandomAccessibleInterval<D>[] data;

		public final RandomAccessibleInterval<T>[] viewData;

		public final AffineTransform3D[] transforms;

		public final Invalidate<Long> invalidate;

		public DataWithInvalidate(
				final RandomAccessibleInterval<D>[] data,
				final RandomAccessibleInterval<T>[] viewData,
				final AffineTransform3D[] transforms,
				final Invalidate<Long> invalidate) {

			this.data = data;
			this.viewData = viewData;
			this.transforms = transforms;
			this.invalidate = invalidate;
		}
	}

	public RandomAccessibleIntervalDataSource(
			final DataWithInvalidate<D, T> dataWithInvalidate,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {

		this(
				dataWithInvalidate.data,
				dataWithInvalidate.viewData,
				() -> dataWithInvalidate.transforms,
				dataWithInvalidate.invalidate,
				dataInterpolation,
				interpolation,
				name);
	}

	public RandomAccessibleIntervalDataSource(
			final Triple<RandomAccessibleInterval<D>[], RandomAccessibleInterval<T>[], AffineTransform3D[]> data,
			final Invalidate<Long> invalidate,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {

		this(data.getA(), data.getB(), data::getC, invalidate, dataInterpolation, interpolation, name);
	}

	@SuppressWarnings("unchecked")
	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D> dataSource,
			final RandomAccessibleInterval<T> source,
			final Supplier<AffineTransform3D> mipmapTransform,
			final Invalidate<Long> invalidate,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {

		this(
				new RandomAccessibleInterval[]{dataSource},
				new RandomAccessibleInterval[]{source},
				() -> new AffineTransform3D[]{mipmapTransform.get()},
				invalidate,
				dataInterpolation,
				interpolation,
				name);
	}

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D>[] dataSources,
			final RandomAccessibleInterval<T>[] sources,
			final Supplier<AffineTransform3D[]> getMipmapTransforms,
			final Invalidate<Long> invalidate,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {

		this(
				dataSources,
				sources,
				getMipmapTransforms,
				invalidate,
				dataInterpolation,
				interpolation,
				dataSources[0].getType().createVariable(),
				sources[0].getType().createVariable(),
				name
		);
	}

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D>[] dataSources,
			final RandomAccessibleInterval<T>[] sources,
			final Supplier<AffineTransform3D[]> getMipmapTransforms,
			final Invalidate<Long> invalidate,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final D dataTypeSupplier,
			final T typeSupplier,
			final String name) {

		super();
		this.getMipmapTransforms = getMipmapTransforms;
		this.dataSources = dataSources;
		this.sources = sources;
		this.invalidate = invalidate;
		this.dataInterpolation = dataInterpolation;
		this.interpolation = interpolation;
		this.dataTypeSupplier = dataTypeSupplier;
		this.typeSupplier = typeSupplier;
		this.name = name;
	}

	@SuppressWarnings("unchecked")
	public static <D, T>
	RandomAccessibleIntervalDataSource.DataWithInvalidate<D, T> asDataWithInvalidate(final ImagesWithTransform<D, T>[] imagesWithTransform) {

		final RandomAccessibleInterval<T>[] data = Stream.of(imagesWithTransform).map(i -> i.data).toArray(RandomAccessibleInterval[]::new);
		final RandomAccessibleInterval<T>[] vdata = Stream.of(imagesWithTransform).map(i -> i.vdata).toArray(RandomAccessibleInterval[]::new);
		final AffineTransform3D[] transforms = Stream.of(imagesWithTransform).map(i -> i.transform).toArray(AffineTransform3D[]::new);
		final Invalidate<Long> invalidate = new InvalidateDelegates<>(
				Stream
						.of(imagesWithTransform)
						.flatMap(iwt -> Stream.of(iwt.invalidateData, iwt.invalidateVData)).filter(Objects::nonNull)
						.collect(Collectors.toList()));
		return new RandomAccessibleIntervalDataSource.DataWithInvalidate(data, vdata, transforms, invalidate);
	}

	@Override
	public boolean isPresent(final int t) {

		return true;
	}

	@Override
	public RandomAccessibleInterval<T> getSource(final int t, final int level) {

		LOG.trace("Requesting source at t={}, level={}", t, level);
		return sources[level];
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method) {

		LOG.trace("Requesting source at t={}, level={} with interpolation {}: ", t, level, method);
		return Views.interpolate(
				Views.extendValue(getSource(t, level), typeSupplier),
				interpolation.apply(method)
		);
	}

	@Override
	public void getSourceTransform(final int t, final int level, final AffineTransform3D transform) {

		final AffineTransform3D[] transforms = getMipmapTransforms.get();
		LOG.trace("Requesting mipmap transform for level {} at time {}: {}", level, t, transforms[level]);
		transform.set(transforms[level]);
	}

	@Override
	public T getType() {

		return typeSupplier;
	}

	@Override
	public String getName() {

		return name;
	}

	// TODO VoxelDimensions is the only class pulled in by spim_data
	@Override
	public VoxelDimensions getVoxelDimensions() {
		// TODO What to do about this? Do we need this at all?
		return null;
	}

	@Override
	public int getNumMipmapLevels() {

		return getMipmapTransforms.get().length;
	}

	@Override
	public RandomAccessibleInterval<D> getDataSource(final int t, final int level) {

		LOG.trace("Requesting data source at t={}, level={}", t, level);
		return dataSources[level];
	}

	@Override
	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method) {

		LOG.trace("Requesting data source at t={}, level={} with interpolation {}: ", t, level, method);
		return Views.interpolate(
				Views.extendValue(getDataSource(t, level), dataTypeSupplier),
				dataInterpolation.apply(method)
		);
	}

	@Override
	public D getDataType() {

		return dataTypeSupplier;
	}

	public RandomAccessibleIntervalDataSource<D, T> copy() {

		return new RandomAccessibleIntervalDataSource<>(
				dataSources,
				sources,
				getMipmapTransforms,
				invalidate,
				dataInterpolation,
				interpolation,
				dataTypeSupplier,
				typeSupplier,
				name
		);
	}

	@Override
	public void invalidate(Long key) {

		this.invalidate.invalidate(key);
	}

	@Override
	public void invalidateIf(long parallelismThreshold, Predicate<Long> condition) {

		this.invalidate.invalidateIf(parallelismThreshold, condition);
	}

	@Override
	public void invalidateIf(Predicate<Long> condition) {

		this.invalidate.invalidateIf(condition);
	}

	@Override
	public void invalidateAll(long parallelismThreshold) {

		this.invalidate.invalidateAll(parallelismThreshold);
	}

	@Override
	public void invalidateAll() {

		this.invalidate.invalidateAll();
	}
}
