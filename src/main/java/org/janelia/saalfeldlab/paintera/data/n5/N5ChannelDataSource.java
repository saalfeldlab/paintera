package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.util.volatiles.SharedQueue;
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
import net.imglib2.util.Triple;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.IntFunction;
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



	private final long numChannels;

	private final String name;

	private final AffineTransform3D[] transforms;

	private final Interval[] intervals;

	private final RandomAccessible<RealComposite<D>>[] data;

	private final RandomAccessible<RealComposite<T>>[] viewerData;

	private final Function<Interpolation,  InterpolatorFactory<RealComposite<D>, RandomAccessible<RealComposite<D>>>> interpolation;

	private final Function<Interpolation,  InterpolatorFactory<RealComposite<T>, RandomAccessible<RealComposite<T>>>> viewerInterpolation;

	private final Converter<RealComposite<T>, VolatileWithSet<RealComposite<T>>> viewerConverter = (source, target ) -> {
		target.setT(source);
		target.setValid(source.get(0).isValid());
	};

	private N5ChannelDataSource(
			final N5Meta meta,
			final AffineTransform3D transform,
			final SharedQueue sharedQueue,
			final D dataExtension,
			final T extension,
			final String name,
			final int priority,
			final int channelDimension,
			final long channelMin,
			final long channelMax) throws
			IOException, DataTypeNotSupported {

		Triple<RandomAccessibleInterval<D>[], RandomAccessibleInterval<T>[], AffineTransform3D[]> dataTriple = getData(
				meta.reader(),
				meta.dataset(),
				transform,
				sharedQueue,
				priority);
		this.meta = meta;
		this.channelDimension = channelDimension;
		this.name = name;
		this.transforms = dataTriple.getC();

		this.channelMin = Math.max(channelMin, dataTriple.getA()[0].min(channelDimension));
		this.channelMax = Math.min(channelMax, dataTriple.getA()[0].max(channelDimension));
		this.numChannels = this.channelMax - this.channelMin + 1;

		this.intervals = dataTriple.getA();
		extension.setValid(true);
		this.data = collapseDimension(dataTriple.getA(), this.channelDimension, dataExtension, this.channelMin, this.channelMax);
		this.viewerData = collapseDimension(dataTriple.getB(), this.channelDimension, extension, this.channelMin, this.channelMax);

		this.interpolation = ipol -> new NearestNeighborInterpolatorFactory<>();
		this.viewerInterpolation = ipol -> Interpolation.NLINEAR.equals(ipol) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>();

		LOG.debug("Channel dimension {} has {} channels", channelDimension, numChannels);
	}

	public static <
			D extends NativeType<D> & RealType<D>,
			T extends AbstractVolatileRealType<D, T> & NativeType<T>> N5ChannelDataSource<D, T> zeroExtended(
			final N5Meta meta,
			final AffineTransform3D transform,
			final SharedQueue sharedQueue,
			final String name,
			final int priority,
			final int channelDimension,
			final long channelMin,
			final long channelMax) throws IOException, DataTypeNotSupported {

		Triple<RandomAccessibleInterval<D>[], RandomAccessibleInterval<T>[], AffineTransform3D[]> data = getData(
				meta.reader(),
				meta.dataset(),
				transform,
				sharedQueue,
				priority);
		D d = Util.getTypeFromInterval(data.getA()[0]).createVariable();
		T t = Util.getTypeFromInterval(data.getB()[0]).createVariable();
		long numChannels = data.getA()[0].dimension(channelDimension);

		LOG.debug("Channel dimension {} has {} channels", channelDimension, numChannels);
		d.setZero();
		t.setZero();
		t.setValid(true);
		return new N5ChannelDataSource<>(meta, transform, sharedQueue, d, t, name, priority, channelDimension, channelMin, channelMax);
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
	Triple<RandomAccessibleInterval<D>[], RandomAccessibleInterval<T>[], AffineTransform3D[]> getData(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final SharedQueue sharedQueue,
			final int priority) throws IOException, DataTypeNotSupported
	{
		if (N5Helpers.isPainteraDataset(reader, dataset))
		{
			return getData(
					reader,
					dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET,
					transform,
					sharedQueue,
					priority);
		}
		final boolean isMultiscale = N5Helpers.isMultiScale(reader, dataset);
		final boolean isLabelMultiset = N5Helpers.isLabelMultisetType(reader, dataset, isMultiscale);
		if (isLabelMultiset)
			throw new DataTypeNotSupported("Label multiset data not supported!");

		return isMultiscale
				? N5Helpers.openRawMultiscale(reader, dataset, transform, sharedQueue, priority)
				: N5Helpers.asArrayTriple(N5Helpers.openRaw(
				reader,
				dataset,
				transform,
				sharedQueue,
				priority));
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
		LOG.warn("Creating extension with size {}", size);
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
			final long channelMax
	)
	{
		return Stream.of(rais).map(rai -> collapseDimension(rai, dimension, extension, channelMin, channelMax)).toArray(RandomAccessible[]::new);
	}

	private static <T extends RealType<T>> RandomAccessible<RealComposite<T>>  collapseDimension(
			final RandomAccessibleInterval<T> rai,
			final int dimension,
			final T extension,
			final long channelMin,
			final long channelMax
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

		RandomAccessibleInterval<T> relevantRai = min[dimension] > rai.min(dimension) || max[dimension] < rai.max(dimension)
				? Views.offsetInterval(rai, new FinalInterval(min, max))
				: rai;

		final RandomAccessible<T> ra = Views.extendValue(lastDim == dimension ? relevantRai : Views.moveAxis(relevantRai, dimension, lastDim), extension);
		return Views.collapseReal(ra, numChannels);
	}
}
