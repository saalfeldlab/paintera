package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.util.volatiles.SharedQueue;
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
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5Types;

import java.io.IOException;
import java.util.function.Function;

public class N5DataSource<D extends NativeType<D>, T extends Volatile<D> & NativeType<T>>
		extends RandomAccessibleIntervalDataSource<D, T> {

  @Expose
  private final N5Meta meta;

  public N5DataSource(
		  final N5Meta meta,
		  final AffineTransform3D transform,
		  final String name,
		  final SharedQueue queue,
		  final int priority) throws IOException {

	this(
			meta,
			transform,
			name,
			queue,
			priority,
			interpolation(meta.getReader(), meta.getDataset()),
			interpolation(meta.getReader(), meta.getDataset()));
  }

  public N5DataSource(
		  final N5Meta meta,
		  final AffineTransform3D transform,
		  final String name,
		  final SharedQueue queue,
		  final int priority,
		  final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
		  final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation) throws
		  IOException {

	super(
			RandomAccessibleIntervalDataSource
					.asDataWithInvalidate((ImagesWithTransform<D, T>[])getData(meta.getReader(), meta.getDataset(), transform, queue, priority)),
			dataInterpolation,
			interpolation,
			name);

	this.meta = meta;
  }

  public N5Meta meta() {

	return meta;
  }

  public N5Reader reader() throws IOException {

	return meta.getReader();
  }

  public N5Writer writer() throws IOException {

	return meta.getWriter();
  }

  public String dataset() {

	return meta.getDataset();
  }

  private static <T extends NativeType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
  interpolation(final N5Reader n5, final String dataset)
		  throws IOException {

	return N5Types.isLabelMultisetType(n5, dataset)
			? i -> new NearestNeighborInterpolatorFactory<>()
			: (Function)realTypeInterpolation();
  }

  private static <T extends RealType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
  realTypeInterpolation() {

	return i -> i.equals(Interpolation.NLINEAR)
			? new NLinearInterpolatorFactory<>()
			: new NearestNeighborInterpolatorFactory<>();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <D extends NativeType<D>, T extends Volatile<D> & NativeType<T>>
  ImagesWithTransform<D, T>[] getData(
		  final N5Reader reader,
		  final String dataset,
		  final AffineTransform3D transform,
		  final SharedQueue queue,
		  final int priority) throws IOException {

	if (N5Helpers.isPainteraDataset(reader, dataset)) {
	  return getData(reader, dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET, transform, queue, priority);
	}
	final boolean isMultiscale = N5Helpers.isMultiScale(reader, dataset);
	final boolean isLabelMultiset = N5Types.isLabelMultisetType(reader, dataset, isMultiscale);

	if (isLabelMultiset) {
	  return isMultiscale
			  ? (ImagesWithTransform[])N5Data.openLabelMultisetMultiscale(reader, dataset, transform, queue, priority)
			  : new ImagesWithTransform[]{N5Data.openLabelMultiset(reader, dataset, transform, queue, priority)};
	} else {
	  return isMultiscale
			  ? N5Data.openRawMultiscale(reader, dataset, transform, queue, priority)
			  : new ImagesWithTransform[]{N5Data.openRaw(reader, dataset, transform, queue, priority)};
	}
  }
}
