package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.cache.SharedQueue;
import bdv.viewer.Interpolation;
import com.google.gson.annotations.Expose;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.Invalidate;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.NativeImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class N5ChannelDataSource<
		D extends NativeType<D> & RealType<D>,
		T extends AbstractVolatileRealType<D, T> & NativeType<T>>
		implements ChannelDataSource<RealComposite<D>, VolatileWithSet<RealComposite<T>>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Expose
	private final MetadataState metadataState;

	@Expose
	private final int channelDimension;

	@Expose
	private final long[] channels;

	@Expose
	private final AffineTransform3D[] transforms;

	private final long numChannels;

	private final String name;

	private final Interval[] intervals;

	private final RandomAccessible<RealComposite<D>>[] data;

	private final RandomAccessible<RealComposite<T>>[] viewerData;

	private final Invalidate<Long> invalidate;

	private final Function<Interpolation, InterpolatorFactory<RealComposite<D>, RandomAccessible<RealComposite<D>>>> interpolation;

	private final Function<Interpolation, InterpolatorFactory<RealComposite<T>, RandomAccessible<RealComposite<T>>>> viewerInterpolation;

	private final Converter<RealComposite<T>, VolatileWithSet<RealComposite<T>>> viewerConverter = (source, target) -> {
		target.setT(source);
		boolean isValid = true;
		int numChannels = (int)this.numChannels();
		// TODO exchange this with only check for first index if block size == num channels
		for (int i = 0; i < numChannels && isValid; ++i) {
			isValid &= source.get(i).isValid();
		}
		target.setValid(isValid);
	};

	/**
	 * @param metadataState
	 * @param dataExtension
	 * @param extension
	 * @param name
	 * @param priority
	 * @param channelDimension
	 * @param channels
	 * @throws IOException
	 * @throws DataTypeNotSupported
	 */
	public N5ChannelDataSource(
			final MetadataState metadataState,
			final D dataExtension,
			final T extension,
			final String name,
			final SharedQueue queue,
			final int priority,
			final int channelDimension,
			final long[] channels) {

		final ImagesWithTransform<D, T>[] data = metadataState.getData(
				queue,
				priority);
		final RandomAccessibleIntervalDataSource.DataWithInvalidate<D, T> dataWithInvalidate = RandomAccessibleIntervalDataSource.asDataWithInvalidate(data);
		this.metadataState = metadataState;
		this.channelDimension = channelDimension;
		this.name = name;
		this.transforms = dataWithInvalidate.transforms;
		this.invalidate = dataWithInvalidate.invalidate;

		this.channels = channels == null ? range((int)dataWithInvalidate.data[0].dimension(channelDimension)) : channels;
		this.numChannels = this.channels.length;

		this.intervals = dataWithInvalidate.data;
		extension.setValid(true);
		this.data = collapseDimension(dataWithInvalidate.data, this.channelDimension, this.channels, dataExtension);
		this.viewerData = collapseDimension(dataWithInvalidate.viewData, this.channelDimension, this.channels, extension);

		this.interpolation = ipol -> new NearestNeighborInterpolatorFactory<>();
		this.viewerInterpolation = ipol -> new NearestNeighborInterpolatorFactory<>();

		LOG.debug("Channel dimension {} has {} channels", channelDimension, numChannels);
	}

	public long[] getChannels() {

		return this.channels.clone();
	}

	public static <
			D extends RealType<D> & NativeType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> valueExtended(
			final MetadataState metadata,
			final String name,
			final SharedQueue queue,
			final int priority,
			final int channelDimension,
			final long channelMin,
			final long channelMax,
			final boolean reverseChannelOrder,
			final double value) {

		return extended(
				metadata,
				name,
				queue,
				priority,
				channelDimension,
				channelMin,
				channelMax,
				reverseChannelOrder,
				d -> d.setReal(value),
				t -> t.setReal(value)
		);
	}

	public static <
			D extends RealType<D> & NativeType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> zeroExtended(
			final MetadataState metadata,
			final String name,
			final SharedQueue queue,
			final int priority,
			final int channelDimension,
			final long channelMin,
			final long channelMax,
			final boolean reverseChannelOrder) {

		return extended(
				metadata,
				name,
				queue,
				priority,
				channelDimension,
				channelMin,
				channelMax,
				reverseChannelOrder,
				RealType::setZero,
				RealType::setZero
		);
	}

	public static <
			D extends NativeType<D> & RealType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> extended(
			final MetadataState metadataState,
			final String name,
			final SharedQueue queue,
			final int priority,
			final int channelDimension,
			final long channelMin,
			final long channelMax,
			final boolean reverseChannelOrder,
			final Consumer<D> extendData,
			final Consumer<T> extendViewer) {

		final ImagesWithTransform<D, T>[] data = metadataState.getData(
				queue,
				priority);
		D d = data[0].data().getType().createVariable();
		T t = data[0].vdata().getType().createVariable();
		long numChannels = data[0].data().dimension(channelDimension);

		LOG.debug("Channel dimension {} has {} channels", channelDimension, numChannels);
		extendData.accept(d);
		extendViewer.accept(t);
		t.setValid(true);
		final long min = Math.min(Math.max(channelMin, 0), numChannels - 1);
		final long max = Math.min(Math.max(channelMax, 0), numChannels - 1);
		final long[] channels = getChannels(min, max, reverseChannelOrder);
		return new N5ChannelDataSource<>(metadataState, d, t, name, queue, priority, channelDimension, channels);
	}

	public static <
			D extends RealType<D> & NativeType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> valueExtended(
			final MetadataState metadata,
			final String name,
			final SharedQueue queue,
			final int priority,
			final int channelDimension,
			final long[] channels,
			final double extension) throws IOException, DataTypeNotSupported {

		return extended(
				metadata,
				name,
				queue,
				priority,
				channelDimension,
				channels,
				d -> d.setReal(extension),
				t -> t.setReal(extension)
		);
	}

	public static <
			D extends RealType<D> & NativeType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> zeroExtended(
			final MetadataState metadata,
			final String name,
			final SharedQueue queue,
			final int priority,
			final int channelDimension,
			final long[] channels) throws IOException, DataTypeNotSupported {

		return extended(
				metadata,
				name,
				queue,
				priority,
				channelDimension,
				channels,
				RealType::setZero,
				RealType::setZero);
	}

	public static <
			D extends NativeType<D> & RealType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> extended(
			final MetadataState metadataState,
			final String name,
			final SharedQueue queue,
			final int priority,
			final int channelDimension,
			final long[] channels,
			final Consumer<D> extendData,
			final Consumer<T> extendViewer) throws IOException, DataTypeNotSupported {

		final ImagesWithTransform<D, T>[] data = metadataState.getData(
				queue,
				priority);
		D d = data[0].data().getType().createVariable();
		T t = data[0].vdata().getType().createVariable();
		long numChannels = data[0].data().dimension(channelDimension);

		LOG.debug("Channel dimension {} has {} channels", channelDimension, numChannels);
		extendData.accept(d);
		extendViewer.accept(t);
		t.setValid(true);
		return new N5ChannelDataSource<>(metadataState, d, t, name, queue, priority, channelDimension, channels);
	}

	public MetadataState meta() {

		return metadataState;
	}

	public N5Reader reader() throws IOException {

		return metadataState.getReader();
	}

	public N5Writer writer() throws IOException {

		return metadataState.getWriter();
	}

	public int getChannelDimension() {

		return this.channelDimension;
	}

	public String dataset() {

		return metadataState.getGroup();
	}

	@Override
	public long numChannels() {

		return this.numChannels;
	}

	@Override
	public RandomAccessibleInterval<RealComposite<D>> getDataSource(int t, int level) {

		return Views.interval(data[level], intervals[level]);
	}

	@Override
	public RealRandomAccessible<RealComposite<D>> getInterpolatedDataSource(int t, int level, Interpolation method) {

		return Views.interpolate(getDataSource(t, level), interpolation.apply(method));
	}

	@Override
	public RealComposite<D> getDataType() {

		return getDataSource(0, 0).getType();
	}

	@Override
	public boolean isPresent(int t) {

		return false;
	}

	@Override
	public RandomAccessibleInterval<VolatileWithSet<RealComposite<T>>> getSource(int t, int level) {

		VolatileWithSet<RealComposite<T>> var = new VolatileWithSet<>(null, true);
		return Converters.convert((RandomAccessibleInterval<RealComposite<T>>)Views.interval(viewerData[level], intervals[level]), viewerConverter, var);
	}

	@Override
	public RealRandomAccessible<VolatileWithSet<RealComposite<T>>> getInterpolatedSource(int t, int level, Interpolation method) {

		final RealRandomAccessible<RealComposite<T>> interpolated = Views.interpolate(viewerData[level], viewerInterpolation.apply(method));
		return Converters.convert(interpolated, viewerConverter, new VolatileWithSet<>(null, true));
	}

	@Override
	public void getSourceTransform(int t, int level, AffineTransform3D transform) {

		transform.set(transforms[level]);
	}

	@Override
	public VolatileWithSet<RealComposite<T>> getType() {

		return new VolatileWithSet<>(null, true);
	}

	@Override
	public String getName() {

		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {

		return null;
	}

	@Override
	public int getNumMipmapLevels() {

		return viewerData.length;
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<D>> RealComposite<D> createExtension(
			final D d,
			final long size
	) {

		return createExtension(d, d.createVariable(), new TypeIdentity<>(), size);
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<T>> RealComposite<T> createExtension(
			final D d,
			final T t,
			final Converter<D, T> converter,
			final long size
	) {

		return createExtension(d, t, converter, size, channel -> d);
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<T>> RealComposite<T> createExtension(
			final D d,
			final T t,
			final Converter<D, T> converter,
			final long size,
			IntFunction<D> valueAtIndex
	) {

		LOG.debug("Creating extension with size {}", size);
		final ArrayImg<D, ?> img = new ArrayImgFactory<>(d).create(1, size);
		img.setLinkedType((D)d.getNativeTypeFactory().createLinkedType((NativeImg)img));
		final CompositeIntervalView<D, RealComposite<D>> collapsed = Views.collapseReal(img);
		RealComposite<D> extensionCopy = collapsed.randomAccess().get();
		for (int channel = 0; channel < size; ++channel) {
			extensionCopy.get(channel).set(valueAtIndex.apply(channel));
		}
		return Views.collapseReal(Converters.convert((RandomAccessibleInterval<D>)img, converter, t.createVariable())).randomAccess().get();
	}

	private static <D extends NativeType<D> & RealType<D>> RealComposite<D> copyExtension(
			final RealComposite<D> extension,
			final long size
	) {

		return copyExtension(extension, extension.get(0).createVariable(), new TypeIdentity<>(), size);
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<T>> RealComposite<T> copyExtension(
			final RealComposite<D> extension,
			final T t,
			final Converter<D, T> converter,
			final long size
	) {

		return createExtension(extension.get(0).createVariable(), t, converter, size, extension::get);
	}

	private static <T extends RealType<T>> RandomAccessible<RealComposite<T>>[] collapseDimension(
			final RandomAccessibleInterval<T>[] rais,
			final int dimension,
			final long[] channels,
			final T extension
	) {

		return Stream.of(rais).map(rai -> collapseDimension(rai, dimension, channels, extension)).toArray(RandomAccessible[]::new);
	}

	private static <T extends RealType<T>> RandomAccessible<RealComposite<T>> collapseDimension(
			final RandomAccessibleInterval<T> rai,
			final int dimension,
			final long[] channels,
			final T extension
	) {

		final int lastDim = rai.numDimensions() - 1;
		final int numChannels = (int)rai.dimension(dimension);

		long[] min = Intervals.minAsLongArray(rai);
		long[] max = Intervals.maxAsLongArray(rai);

		assert LongStream.of(channels).filter(c -> c > max[dimension] && c < min[dimension]).count() == 0;

		final RandomAccessibleInterval<T> relevantRai = isFullRange(channels, numChannels)
				? rai
				: Views.stack(LongStream.of(channels).mapToObj(channel -> Views.hyperSlice(rai, dimension, channel)).collect(Collectors.toList()));

		final RandomAccessible<T> ra = Views.extendValue(lastDim == dimension
				? relevantRai
				: Views.moveAxis(relevantRai, dimension, lastDim), extension);
		return Views.collapseReal(ra, numChannels);
	}

	private static long[] getChannels(final long min, final long max, boolean reverseChannelOrder) {

		if (reverseChannelOrder)
			return LongStream.rangeClosed(-max, -min).map(v -> -v).toArray();
		else
			return LongStream.rangeClosed(min, max).toArray();
	}

	private static boolean isFullRange(final long[] channels, final int dim) {

		if (channels.length != dim)
			return false;

		for (int n = 0; n < dim; ++n) {
			if (channels[n] != n)
				return false;
		}

		return true;
	}

	private static long[] range(final int stop) {

		long[] range = new long[stop];
		Arrays.setAll(range, d -> d);
		return range;
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
