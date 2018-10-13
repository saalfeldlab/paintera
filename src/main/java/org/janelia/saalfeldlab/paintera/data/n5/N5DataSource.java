package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.IOException;
import java.util.function.Function;

import bdv.viewer.Interpolation;
import com.google.gson.annotations.Expose;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.util.n5.ImagesWithInvalidate;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.util.n5.N5Types;

public class N5DataSource<D extends NativeType<D>, T extends Volatile<D> & NativeType<T>>
		extends RandomAccessibleIntervalDataSource<D, T>
{

	@Expose
	private final N5Meta meta;

	public N5DataSource(
			final N5Meta meta,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final String name,
			final int priority) throws IOException {
		this(
				meta,
				transform,
				globalCache,
				name,
				priority,
				interpolation(meta.reader(), meta.dataset()),
				interpolation(meta.reader(), meta.dataset())
		    );
	}

	public N5DataSource(
			final N5Meta meta,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final String name,
			final int priority,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation) throws
			IOException {
		super(
				RandomAccessibleIntervalDataSource.asDataWithInvalidate((ImagesWithInvalidate<D, T>[])getData(meta.reader(), meta.dataset(), transform, globalCache, priority)),
				dataInterpolation,
				interpolation,
				name
		     );

		this.meta = meta;
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

	public String dataset()
	{
		return meta.dataset();
	}

	private static <T extends NativeType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	interpolation(final N5Reader n5, final String dataset)
	throws IOException
	{
		return N5Types.isLabelMultisetType(n5, dataset)
		       ? i -> new NearestNeighborInterpolatorFactory<>()
		       : (Function) realTypeInterpolation();
	}

	private static <T extends RealType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	realTypeInterpolation()
	{
		return i -> i.equals(Interpolation.NLINEAR)
		            ? new NLinearInterpolatorFactory<>()
		            : new NearestNeighborInterpolatorFactory<>();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <D extends NativeType<D>, T extends Volatile<D> & NativeType<T>>
	ImagesWithInvalidate<D, T>[] getData(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		if (N5Helpers.isPainteraDataset(reader, dataset))
		{
			return getData(reader, dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET, transform, globalCache, priority);
		}
		final boolean isMultiscale = N5Helpers.isMultiScale(reader, dataset);
		final boolean isLabelMultiset = N5Types.isLabelMultisetType(reader, dataset, isMultiscale);

		if (isLabelMultiset)
		{
			return isMultiscale
			       ? (ImagesWithInvalidate[]) N5Data.openLabelMultisetMultiscale(reader, dataset, transform, globalCache, priority)
			       : new ImagesWithInvalidate[] {N5Data.openLabelMultiset(
					       reader,
					       dataset,
					       transform,
					       globalCache,
					       priority)};
		}
		else
		{
			return isMultiscale
			       ? N5Data.openRawMultiscale(reader, dataset, transform, globalCache, priority)
			       : new ImagesWithInvalidate[] {N5Data.openRaw(
					       reader,
					       dataset,
					       transform,
					       globalCache,
					       priority)};
		}
	}
}
