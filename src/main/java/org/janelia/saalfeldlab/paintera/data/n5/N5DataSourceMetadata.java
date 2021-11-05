package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.SingleScaleMetadataState;
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleGroup;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public class N5DataSourceMetadata<D extends NativeType<D>, T extends Volatile<D> & NativeType<T>> extends RandomAccessibleIntervalDataSource<D, T> {

  private final MetadataState metadataState;

  public N5DataSourceMetadata(
		  final MetadataState metadataState,
		  final String name,
		  final SharedQueue queue,
		  final int priority) throws IOException {

	this(
			metadataState,
			name,
			queue,
			priority,
			interpolation(metadataState),
			interpolation(metadataState));
  }

  public N5DataSourceMetadata(
		  final MetadataState metadataState,
		  final String name,
		  final SharedQueue queue,
		  final int priority,
		  final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
		  final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation) throws
		  IOException {

	super(
			RandomAccessibleIntervalDataSource.asDataWithInvalidate((ImagesWithTransform<D, T>[])getData(metadataState, queue, priority)),
			dataInterpolation,
			interpolation,
			name);

	this.metadataState = metadataState;
  }

  public MetadataState metaDataState() {

	return metadataState;
  }

  public N5Reader reader() throws IOException {

	return metadataState.getReader();
  }

  public Optional<N5Writer> writer() throws IOException {

	return metadataState.getWriter();
  }

  public String dataset() {

	return metadataState.getGroup();
  }

  static <T extends NativeType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
  interpolation(MetadataState metadataState) throws IOException {

	return metadataState.isLabelMultiset()
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
		  final MetadataState metadataState,
		  final SharedQueue queue,
		  final int priority) throws IOException {

	final var metadata = metadataState.getMetadata();
	final boolean isLabelMultiset = metadataState.isLabelMultiset();

	if (metadata instanceof N5PainteraDataMultiScaleGroup) {
	  final var metadataAsPainteraDataGroup = (N5PainteraDataMultiScaleGroup)metadata;
	  final var dataMetadataState = new MultiScaleMetadataState(metadataState.getN5ContainerState(), metadataAsPainteraDataGroup.getDataGroupMetadata());
	  return getData(
			  dataMetadataState,
			  queue,
			  priority);
	}


	if (isLabelMultiset) {
	  return metadataState instanceof MultiScaleMetadataState
			  ? (ImagesWithTransform[])N5Data.openLabelMultisetMultiscale((MultiScaleMetadataState)metadataState, queue, priority)
			  : new ImagesWithTransform[]{N5Data.openLabelMultiset((SingleScaleMetadataState)metadataState, queue, priority)};
	} else {
	  return metadataState instanceof MultiScaleMetadataState
			  ? N5Data.openRawMultiscale((MultiScaleMetadataState)metadataState, queue, priority)
			  : new ImagesWithTransform[]{N5Data.openRaw((SingleScaleMetadataState)metadataState, queue, priority)};
	}
  }
}