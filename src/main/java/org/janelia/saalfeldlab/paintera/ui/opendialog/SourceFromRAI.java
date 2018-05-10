package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.mask.TmpDirectoryCreator;
import org.janelia.saalfeldlab.paintera.data.meta.LabelMeta;
import org.janelia.saalfeldlab.paintera.data.meta.RawMeta;
import org.janelia.saalfeldlab.paintera.data.meta.n5.MetaInstantiationFailed;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignment;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;

public interface SourceFromRAI extends BackendDialog
{

	public static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public < T extends NativeType< T >, V extends Volatile< T > & NativeType< V > > Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException;

	public boolean isLabelType() throws Exception;

	public boolean isLabelMultisetType() throws Exception;

	public boolean isIntegerType() throws Exception;

	public FragmentSegmentAssignmentState assignments();

	public IdService idService();

	public default ToIdConverter toIdConverter() throws Exception
	{
		if ( isLabelType() )
		{
			if ( isLabelMultisetType() )
				return ToIdConverter.fromLabelMultisetType();

			if ( isIntegerType() )
				return ToIdConverter.fromIntegerType();
		}
		return null;
	}

	public BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > commitCanvas();

	public default InterruptibleFunction< Long, Interval[] >[] blocksThatContainId()
	{
		return null;
	}

	public default InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache()
	{
		return null;
	}

	public default String initialCanvasPath()
	{
		return new TmpDirectoryCreator( null, null ).get();
	}

	public default Supplier< String > canvasCacheDirUpdate()
	{
		return new TmpDirectoryCreator( null, null );
	}

	@SuppressWarnings( "unchecked" )
	public default < D > LongFunction< Converter< D, BoolType > > maskForId() throws Exception
	{
		if ( isLabelMultisetType() )
			return id -> ( Converter< D, BoolType > ) maskForIdLabelMultisetType( id );

		if ( isIntegerType() )
			return id -> ( Converter< D, BoolType > ) maskForIdIntegerType( id );

		return null;
	}

	public static Converter< LabelMultisetType, BoolType > maskForIdLabelMultisetType( final long id )
	{
		return ( s, t ) -> t.set( s.contains( id ) );
	}

	public static < I extends IntegerType< I > > Converter< I, BoolType > maskForIdIntegerType( final long id )
	{
		return ( s, t ) -> t.set( s.getIntegerLong() == id );
	}

	public < D extends NativeType< D >, T extends Volatile< D > & NativeType< T > & RealType< T > > RawMeta< D, T > getRawMeta() throws MetaInstantiationFailed;

	public < D extends NativeType< D >, T extends Volatile< D > & NativeType< T > > LabelMeta< D, T > getLabelMeta() throws MetaInstantiationFailed;

	@Override
	public default < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > & NativeType< V > > RawSourceState< T, V > getRaw(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException, MetaInstantiationFailed
	{
		final Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > dataAndVolatile = getDataAndVolatile( sharedQueue, priority );
		LOG.debug( "Got data: {}", dataAndVolatile );
		return new RawSourceState<>(
				getCached( dataAndVolatile.getA(), dataAndVolatile.getB(), dataAndVolatile.getC(), name, sharedQueue, priority ),
				new ARGBColorConverter.InvertingImp1<>( 0, 255 ),
				new CompositeCopy<>(),
				name,
				getRawMeta() );
	}

	default < T extends NativeType< T >, V extends Volatile< T > & NativeType< V > > DataSource< T, V > getSourceNearestNeighborInterpolationOnly(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > dataAndVolatile = getDataAndVolatile( sharedQueue, priority );
		return getCached(
				dataAndVolatile.getA(),
				dataAndVolatile.getB(),
				dataAndVolatile.getC(),
				interpolation -> new NearestNeighborInterpolatorFactory<>(),
				interpolation -> new NearestNeighborInterpolatorFactory<>(),
				name,
				sharedQueue,
				priority );
	}

	public ExecutorService propagationExecutor();

	@Override
	public default < D extends NativeType< D >, T extends Volatile< D > & NativeType< T > > LabelSourceState< D, T > getLabels(
			final String name,
			final SharedQueue sharedQueue,
			final int priority,
			final Group meshesGroup,
			final ExecutorService manager,
			final ExecutorService workers ) throws Exception

	{
		final DataSource< D, T > source = Masks.mask(
				this.< D, T >getSourceNearestNeighborInterpolationOnly( name, sharedQueue, priority ),
				initialCanvasDirectory(),
				nextCanvasDirectory(),
				commitCanvas(),
				propagationExecutor() );

		SelectedIds selectedIds = new SelectedIds();
		FragmentSegmentAssignmentState assignments = assignments();
		SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignments );
		FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments( selectedSegments, assignments );
		ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignments );
		
		InterruptibleFunction< Long, Interval[] >[] blocksCache = Optional
				.ofNullable( blocksThatContainId() )
				.orElseGet( () -> PainteraBaseView.generateLabelBlocksForLabelCache( source, PainteraBaseView.scaleFactorsFromAffineTransforms( source ) ) );
		InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache = CacheUtils.meshCacheLoaders( source, PainteraBaseView.equalsMaskForType( source.getDataType() ), CacheUtils::toCacheSoftRefLoaderCache );
		MeshManagerWithAssignment meshManager = new MeshManagerWithAssignment(
				source, 
				blocksCache,
				meshCache,
				meshesGroup,
				assignments,
				fragmentsInSelectedSegments, 
				stream, 
				new SimpleIntegerProperty(), 
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				manager,
				workers );

		MeshInfos meshInfos = new MeshInfos( selectedSegments, assignments, meshManager, source.getNumMipmapLevels() );

		return new LabelSourceState< D, T >(
				source,
				HighlightingStreamConverter.forType( stream, source.getType() ),
				new ARGBCompositeAlphaYCbCr(),
				name,
				getLabelMeta(),
				PainteraBaseView.equalsMaskForType( source.getDataType() ),
				assignments,
				toIdConverter(),
				selectedIds,
				idService(),
				meshManager,
				meshInfos );
	}

	public default String initialCanvasDirectory()
	{
		return null;
	}

	public default Supplier< String > nextCanvasDirectory()
	{
		return new TmpDirectoryCreator( null, null );
	}

	public default < T extends NumericType< T >, V extends NumericType< V > > DataSource< T, V > getCached(
			final RandomAccessibleInterval< T >[] rai,
			final RandomAccessibleInterval< V >[] vrai,
			final AffineTransform3D[] transforms,
			final String nameOrPattern,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return getCached(
				rai,
				vrai,
				transforms,
				interpolation -> interpolation.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				interpolation -> interpolation.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				nameOrPattern,
				sharedQueue,
				priority );
	}

	public default < T extends Type< T >, V extends Type< V > > DataSource< T, V > getCached(
			final RandomAccessibleInterval< T >[] rai,
			final RandomAccessibleInterval< V >[] vrai,
			final AffineTransform3D[] transforms,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > vinterpolation,
			final String nameOrPattern,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		LOG.debug( "Using source transforms {} for {} sources", Arrays.toString( transforms ), rai.length );

		return getAsSource( rai, vrai, transforms, interpolation, vinterpolation, nameOrPattern );
	}

	public default < T extends IntegerType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > & NativeType< V > > DataSource< T, V > getIntegerTypeSource(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{

		final Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > dataAndVolatile = getDataAndVolatile( sharedQueue, priority );
		final DataSource< T, V > source = getCached(
				dataAndVolatile.getA(),
				dataAndVolatile.getB(),
				dataAndVolatile.getC(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name,
				sharedQueue,
				priority );
		return source;
	}

	public default DataSource< LabelMultisetType, VolatileLabelMultisetType > getLabelMultisetTypeSource(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{

		final Triple< RandomAccessibleInterval< LabelMultisetType >[], RandomAccessibleInterval< VolatileLabelMultisetType >[], AffineTransform3D[] > dataAndVolatile =
				getDataAndVolatile( sharedQueue, priority );
		final DataSource< LabelMultisetType, VolatileLabelMultisetType > source = getCached(
				dataAndVolatile.getA(),
				dataAndVolatile.getB(),
				dataAndVolatile.getC(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name,
				sharedQueue,
				priority );
		return source;
	}

	public static < T extends Type< T >, V extends Type< V > > DataSource< T, V > getAsSource(
			final RandomAccessibleInterval< T >[] rais,
			final RandomAccessibleInterval< V >[] vrais,
			final AffineTransform3D[] transforms,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > vinterpolation,
			final String name )
	{

		assert rais.length == vrais.length;
		assert rais.length == transforms.length;

		LOG.debug( "Getting RandomAccessibleIntervalDataSource" );

		return new RandomAccessibleIntervalDataSource<>( rais, vrais, transforms, interpolation, vinterpolation, name );
	}

	public static AffineTransform3D permutedSourceTransform( final double[] resolution, final double[] offset, final int[] componentMapping )
	{
		final AffineTransform3D rawTransform = new AffineTransform3D();
		final double[] matrixContent = new double[ 12 ];
		LOG.debug( "component mapping={}", Arrays.toString( componentMapping ) );
		for ( int i = 0, contentOffset = 0; i < offset.length; ++i, contentOffset += 4 )
		{
			matrixContent[ 4 * componentMapping[ i ] + i ] = resolution[ i ];
			matrixContent[ contentOffset + 3 ] = offset[ i ];
		}
		rawTransform.set( matrixContent );
		LOG.debug( "permuted transform={}", rawTransform );
		return rawTransform;
	}

}
