package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5Types;
import org.janelia.saalfeldlab.util.n5.metadata.N5Metadata;

import java.io.IOException;
import java.util.function.Function;

public class N5DataSourceFromMetadata<D extends NativeType<D>, T extends Volatile<D> & NativeType<T>>
		extends RandomAccessibleIntervalDataSource<D, T> {

  private final N5Metadata meta;
  private final N5Reader reader;

  public N5DataSourceFromMetadata(
		  final N5Reader reader,
		  final N5Metadata meta,
		  final AffineTransform3D transform,
		  final String name,
		  final SharedQueue queue,
		  final int priority) throws IOException {

	this(
			reader,
			meta,
			transform,
			name,
			queue,
			priority,
			N5DataSource.interpolation(reader, meta.getPath()),
			N5DataSource.interpolation(reader, meta.getPath()));
  }

  public N5DataSourceFromMetadata(
		  final N5Reader reader,
		  final N5Metadata meta,
		  final AffineTransform3D transform,
		  final String name,
		  final SharedQueue queue,
		  final int priority,
		  final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
		  final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation) throws
		  IOException {

	super(
			RandomAccessibleIntervalDataSource.asDataWithInvalidate((ImagesWithTransform<D, T>[])getData(reader, meta.getPath(), transform, queue, priority)),
			dataInterpolation,
			interpolation,
			name);

	this.meta = meta;
	this.reader = reader;
  }

  public N5Metadata meta() {

	return meta;
  }

  public N5Reader reader(){

	return reader;
  }

  public N5Writer writer() throws IOException {
    if (reader instanceof N5Writer) {
	  return (N5Writer) reader;
	}
	throw new IOException("DataSource cannot access an N5Writer");
  }

  public String dataset() {

	return meta.getPath();
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
	  //FIXME meta
	  throw new UnsupportedOperationException("PainteraDataset with new Metadata not yet supported!");
//	  return getData(reader, dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET, transform, queue, priority);
	}
	final boolean isMultiscale = N5Helpers.isMultiScale(reader, dataset);
	final boolean isLabelMultiset = N5Types.isLabelMultisetType(reader, dataset, isMultiscale);

	if (isLabelMultiset) {
	  return isMultiscale
			  ? (ImagesWithTransform[])N5Data.openLabelMultisetMultiscale(reader, dataset, transform, queue, priority)
			  : new ImagesWithTransform[]{N5Data.openLabelMultiset(
			  reader,
			  dataset,
			  transform,
			  queue,
			  priority)};
	} else {
	  return isMultiscale
			  ? N5Data.openRawMultiscale(reader, dataset, transform, queue, priority)
			  : new ImagesWithTransform[]{N5Data.openRaw(
			  reader,
			  dataset,
			  transform,
			  queue,
			  priority)};
	}
  }
}
