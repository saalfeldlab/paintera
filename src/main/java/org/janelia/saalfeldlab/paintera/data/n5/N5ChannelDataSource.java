package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.viewer.Interpolation;
import com.google.gson.annotations.Expose;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
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
import org.janelia.saalfeldlab.util.n5.ImagesWithInvalidate;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.util.n5.N5Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class N5ChannelDataSource<
		D extends NativeType<D> & RealType<D>,
		T extends AbstractVolatileRealType<D, T> & NativeType<T>>
		implements ChannelDataSource<RealComposite<D>, VolatileWithSet<RealComposite<T>>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Expose
	private final N5Meta meta;

	@Expose
	private final int channelDimension;

	@Expose
	private final long channelMin;

	@Expose
	private final long channelMax;

	private final AffineTransform3D[] transforms;

	private final long numChannels;

	private final String name;

	private final Interval[] intervals;

	private final RandomAccessible<RealComposite<D>>[] data;

	private final RandomAccessible<RealComposite<T>>[] viewerData;

	private final InvalidateAll invaldiateAll;

	private final Function<Interpolation,  InterpolatorFactory<RealComposite<D>, RandomAccessible<RealComposite<D>>>> interpolation;

	private final Function<Interpolation,  InterpolatorFactory<RealComposite<T>, RandomAccessible<RealComposite<T>>>> viewerInterpolation;

	private final Converter<RealComposite<T>, VolatileWithSet<RealComposite<T>>> viewerConverter = (source, target ) -> {
		target.setT(source);
		boolean isValid = true;
		int numChannels = (int) this.numChannels();
		// TODO exchange this with only check for first index if block size == num channels
		for (int i = 0; i < numChannels && isValid; ++i)
			isValid &= source.get(i).isValid();
		target.setValid(isValid);
	};

	private final boolean revertChannelOrder;

	/**
	 *
	 * @param meta
	 * @param transform
	 * @param globalCache
	 * @param dataExtension
	 * @param extension
	 * @param name
	 * @param priority
	 * @param channelDimension
	 * @param channelMin
	 * @param channelMax
	 * @param revertChannelOrder
	 * @throws IOException
	 * @throws DataTypeNotSupported
	 */
	private N5ChannelDataSource(
			final N5Meta meta,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final D dataExtension,
			final T extension,
			final String name,
			final int priority,
			final int channelDimension,
			final long channelMin,
			final long channelMax,
			final boolean revertChannelOrder) throws
			IOException, DataTypeNotSupported {

		final ImagesWithInvalidate<D, T>[] data = getData(
				meta.reader(),
				meta.dataset(),
				transform,
				globalCache,
				priority);
		final RandomAccessibleIntervalDataSource.DataWithInvalidate<D, T> dataWithInvalidate = RandomAccessibleIntervalDataSource.asDataWithInvalidate(data);
		this.meta = meta;
		this.channelDimension = channelDimension;
		this.name = name;
		this.transforms = dataWithInvalidate.transforms;
		this.invaldiateAll = dataWithInvalidate.invalidateAll;

		this.channelMin = Math.max(channelMin, dataWithInvalidate.data[0].min(channelDimension));
		this.channelMax = Math.min(channelMax, dataWithInvalidate.data[0].max(channelDimension));
		this.numChannels = this.channelMax - this.channelMin + 1;

		this.intervals = dataWithInvalidate.data;
		extension.setValid(true);
		this.data = collapseDimension(dataWithInvalidate.data, this.channelDimension, dataExtension, this.channelMin, this.channelMax, revertChannelOrder);
		this.viewerData = collapseDimension(dataWithInvalidate.viewData, this.channelDimension, extension, this.channelMin, this.channelMax, revertChannelOrder);

		this.interpolation = ipol -> new NearestNeighborInterpolatorFactory<>();
		this.viewerInterpolation = ipol -> Interpolation.NLINEAR.equals(ipol) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>();

		this.revertChannelOrder = revertChannelOrder;

		LOG.debug("Channel dimension {} has {} channels", channelDimension, numChannels);
	}

	public static <
			D extends NativeType<D> & RealType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> zeroExtended(
			final N5Meta meta,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final String name,
			final int priority,
			final int channelDimension,
			final long channelMin,
			final long channelMax,
			final boolean revertChannelOrder) throws IOException, DataTypeNotSupported {

		final ImagesWithInvalidate<D, T>[] data = getData(
				meta.reader(),
				meta.dataset(),
				transform,
				globalCache,
				priority);
		D d = Util.getTypeFromInterval(data[0].data).createVariable();
		T t = Util.getTypeFromInterval(data[0].vdata).createVariable();
		long numChannels = data[0].data.dimension(channelDimension);

		LOG.debug("Channel dimension {} has {} channels", channelDimension, numChannels);
		d.setZero();
		t.setZero();
		t.setValid(true);
		return new N5ChannelDataSource<>(meta, transform, globalCache, d, t, name, priority, channelDimension, channelMin, channelMax, revertChannelOrder);
	}

	public N5Meta meta()
	{
		return meta;
	}

	public N5Reader reader() throws IOException
	{
		return meta.reader();
	}

	public N5Writer writer() throws IOException
	{
		return meta.writer();
	}

	public long getChannelMin()
	{
		return this.channelMin;
	}

	public long getChannelMax()
	{
		return this.channelMax;
	}

	public int getChannelDimension()
	{
		return this.channelDimension;
	}

	public boolean doesRevertChannelOrder()
	{
		return this.revertChannelOrder;
	}

	public String dataset()
	{
		return meta.dataset();
	}

	@Override
	public long numChannels()
	{
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
		return Util.getTypeFromInterval(getDataSource(0, 0));
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <
			D extends NativeType<D> & RealType<D>,
			T extends Volatile<D> & NativeType<T> & RealType<T>>
	ImagesWithInvalidate<D, T>[] getData(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException, DataTypeNotSupported
	{
		if (N5Helpers.isPainteraDataset(reader, dataset))
		{
			return getData(
					reader,
					dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET,
					transform,
					globalCache,
					priority);
		}
		final boolean isMultiscale = N5Helpers.isMultiScale(reader, dataset);
		final boolean isLabelMultiset = N5Types.isLabelMultisetType(reader, dataset, isMultiscale);
		if (isLabelMultiset)
			throw new DataTypeNotSupported("Label multiset data not supported!");

		return isMultiscale
				? N5Data.openRawMultiscale(reader, dataset, transform, globalCache, priority)
				: new ImagesWithInvalidate[] {N5Data.openRaw(
				reader,
				dataset,
				transform,
				globalCache,
				priority)};
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<D>> RealComposite<D>  createExtension(
			final D d,
			final long size
	)
	{
		return createExtension(d, d.createVariable(), new TypeIdentity<>(), size);
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<T>> RealComposite<T>  createExtension(
			final D d,
			final T t,
			final Converter<D, T> converter,
			final long size
	) {
		return createExtension(d, t, converter, size, channel -> d);
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<T>> RealComposite<T>  createExtension(
			final D d,
			final T t,
			final Converter<D, T> converter,
			final long size,
			IntFunction<D> valueAtIndex
	)
	{
		LOG.debug("Creating extension with size {}", size);
		final ArrayImg<D, ?> img = new ArrayImgFactory<>(d).create(1, size);
		img.setLinkedType((D) d.getNativeTypeFactory().createLinkedType((NativeImg)img));
		final CompositeIntervalView<D, RealComposite<D>> collapsed = Views.collapseReal(img);
		RealComposite<D> extensionCopy = collapsed.randomAccess().get();
		for (int channel = 0; channel < size; ++channel)
			extensionCopy.get(channel).set(valueAtIndex.apply(channel));
		return Views.collapseReal(Converters.convert((RandomAccessibleInterval<D>)img, converter, t.createVariable())).randomAccess().get();
	}

	private static <D extends NativeType<D> & RealType<D>> RealComposite<D>  copyExtension(
			final RealComposite<D> extension,
			final long size
	)
	{
		return copyExtension(extension, extension.get(0).createVariable(), new TypeIdentity<>(), size);
	}

	private static <D extends NativeType<D> & RealType<D>, T extends RealType<T>> RealComposite<T>  copyExtension(
			final RealComposite<D> extension,
			final T t,
			final Converter<D, T> converter,
			final long size
	)
	{
		return createExtension(extension.get(0).createVariable(), t, converter, size, extension::get);
	}

	private static <T extends RealType<T>> RandomAccessible<RealComposite<T>>[] collapseDimension(
			final RandomAccessibleInterval<T>[] rais,
			final int dimension,
			final T extension,
			final long channelMin,
			final long channelMax,
			final boolean revertChannelOrder
	)
	{
		return Stream.of(rais).map(rai -> collapseDimension(rai, dimension, extension, channelMin, channelMax, revertChannelOrder)).toArray(RandomAccessible[]::new);
	}

	private static <T extends RealType<T>> RandomAccessible<RealComposite<T>>  collapseDimension(
			final RandomAccessibleInterval<T> rai,
			final int dimension,
			final T extension,
			final long channelMin,
			final long channelMax,
			final boolean revertChannelOrder
	)
	{
		final int lastDim = rai.numDimensions() - 1;
		final int numChannels = (int) rai.dimension(dimension);

		long[] min = Intervals.minAsLongArray(rai);
		long[] max = Intervals.maxAsLongArray(rai);

		assert channelMin <= channelMax;
		assert min[dimension] <= channelMin;
		assert max[dimension] >= channelMax;

		min[dimension] = channelMin;
		max[dimension] = channelMax;

		Views.invertAxis(rai, dimension);
		RandomAccessibleInterval<T> orderCorrected = revertChannelOrder
				? Views.translate(Views.invertAxis(rai, dimension), IntStream.range(0, rai.numDimensions()).mapToLong(dim -> dim == dimension ? rai.max(dim) : 0).toArray())
				: rai;

		RandomAccessibleInterval<T> relevantRai = min[dimension] > orderCorrected.min(dimension) || max[dimension] < orderCorrected.max(dimension)
				? Views.offsetInterval(orderCorrected, new FinalInterval(min, max))
				: orderCorrected;

		final RandomAccessible<T> ra = Views.extendValue(lastDim == dimension ? relevantRai : Views.moveAxis(relevantRai, dimension, lastDim), extension);
		return Views.collapseReal(ra, numChannels);
	}

	@Override
	public void invalidateAll() {
		this.invaldiateAll.invalidateAll();
	}
}
