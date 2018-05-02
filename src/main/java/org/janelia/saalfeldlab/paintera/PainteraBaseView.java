package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignment;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.UniqueLabelListLabelMultisetCacheLoader;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg.LazyCells;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValueTriple;

public class PainteraBaseView
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final SourceInfo sourceInfo = new SourceInfo();

	private final GlobalTransformManager manager = new GlobalTransformManager();

	private final SharedQueue cacheControl;

	private final ViewerOptions viewerOptions;

	private final Viewer3DFX viewer3D = new Viewer3DFX( 1, 1 );

	private final OrthogonalViews< Viewer3DFX > views;

	private final ObservableList< SourceAndConverter< ? > > visibleSourcesAndConverters = sourceInfo.trackVisibleSourcesAndConverters();

	private final ListChangeListener< SourceAndConverter< ? > > vsacUpdate;

	private final ExecutorService generalPurposeExecutorService = Executors.newFixedThreadPool( 3, new NamedThreadFactory( "paintera-thread-%d" ) );

	private final ExecutorService meshManagerExecutorService = Executors.newFixedThreadPool( 1, new NamedThreadFactory( "paintera-mesh-manager-%d" ) );

	private final ExecutorService meshWorkerExecutorService = Executors.newFixedThreadPool( 3, new NamedThreadFactory( "paintera-mesh-worker-%d" ) );

	private final ExecutorService paintQueue = Executors.newFixedThreadPool( 1 );

	private final ExecutorService propagationQueue = Executors.newFixedThreadPool( 1 );

	public PainteraBaseView( final int numFetcherThreads, final Function< SourceInfo, Function< Source< ? >, Interpolation > > interpolation )
	{
		this( numFetcherThreads, ViewerOptions.options(), interpolation );
	}

	public PainteraBaseView(
			final int numFetcherThreads,
			final ViewerOptions viewerOptions,
			final Function< SourceInfo, Function< Source< ? >, Interpolation > > interpolation )
	{
		super();
		this.cacheControl = new SharedQueue( numFetcherThreads );
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory( new CompositeProjectorPreMultiply.CompositeProjectorFactory( sourceInfo.composites() ) )
				// .accumulateProjectorFactory( new
				// ClearingCompositeProjector.ClearingCompositeProjectorFactory<>(
				// sourceInfo.composites(), new ARGBType() ) )
				.numRenderingThreads( Math.min( 3, Math.max( 1, Runtime.getRuntime().availableProcessors() / 3 ) ) );
		this.views = new OrthogonalViews<>( manager, cacheControl, this.viewerOptions, viewer3D, interpolation.apply( sourceInfo ) );
		this.vsacUpdate = change -> views.setAllSources( visibleSourcesAndConverters );
		visibleSourcesAndConverters.addListener( vsacUpdate );
	}

	public OrthogonalViews< Viewer3DFX > orthogonalViews()
	{
		return this.views;
	}

	public Viewer3DFX viewer3D()
	{
		return this.viewer3D;
	}

	public SourceInfo sourceInfo()
	{
		return this.sourceInfo;
	}

	public Pane pane()
	{
		return orthogonalViews().pane();
	}

	public GlobalTransformManager manager()
	{
		return this.manager;
	}

	public < T extends RealType< T > & NativeType< T >, U extends Volatile< T > & RealType< U > > Optional< DataSource< T, U > > addRawSource(
			final N5Reader n5,
			final String dataset ) throws IOException
	{
		final DatasetAttributes attributes = n5.getDatasetAttributes(
				N5Helpers.isMultiScale( n5, dataset )
						? Paths.get( dataset, N5Helpers.listAndSortScaleDatasets( n5, dataset )[ 0 ] ).toString()
						: dataset );
		final DataType type = attributes.getDataType();
		return addRawSource( n5, dataset, Color.WHITE, N5Helpers.minForType( type ), N5Helpers.maxForType( type ) );
	}

	public < T extends RealType< T > & NativeType< T >, U extends Volatile< T > & RealType< U > > Optional< DataSource< T, U > > addRawSource(
			final N5Reader n5,
			final String dataset,
			final Color color,
			final double min,
			final double max ) throws IOException
	{
		return addRawSource(
				n5,
				dataset,
				N5Helpers.getResolution( n5, dataset ),
				N5Helpers.getOffset( n5, dataset ),
				color,
				min,
				max );
	}

	public < T extends RealType< T > & NativeType< T >, U extends Volatile< T > & RealType< U > > Optional< DataSource< T, U > > addRawSource(
			final N5Reader n5,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final Color color,
			final double min,
			final double max ) throws IOException
	{
		final boolean isMultiScale = N5Helpers.isMultiScale( n5, dataset );
		final String name = dataset;
		final RandomAccessibleIntervalDataSource< T, U > source;
		if ( isMultiScale )
		{
			final ValueTriple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< U >[], AffineTransform3D[] > data = N5Helpers.openRawMultiscale(
					n5,
					dataset,
					resolution,
					offset,
					getQueue(),
					0 );
			source = new RandomAccessibleIntervalDataSource<>(
					data.getA(),
					data.getB(),
					data.getC(),
					i -> Interpolation.NLINEAR.equals( i ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
					i -> Interpolation.NLINEAR.equals( i ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
					name );
		}
		else
		{
			source = DataSource.createN5Source( name, n5, dataset, resolution, offset, getQueue(), 0 );
		}
		addRawSource( source, min, max, color );
		return Optional.of( source );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final DataSource< T, U > spec,
			final double min,
			final double max,
			final Color color )
	{
		addRawSource( spec, min, max, color, null );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final DataSource< T, U > spec,
			final double min,
			final double max,
			final Color color,
			final Object metaData )
	{
		addRawSource( spec, min, max, Colors.toARGBType( color ), metaData );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final DataSource< T, U > spec,
			final double min,
			final double max,
			final ARGBType color )
	{
		addRawSource( spec, min, max, color, null );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final DataSource< T, U > spec,
			final double min,
			final double max,
			final ARGBType color,
			final Object metaData )
	{
		final Composite< ARGBType, ARGBType > comp = new ARGBCompositeAlphaAdd();
		final SourceState< T, U > state = sourceInfo.addRawSource( spec, min, max, color, comp, metaData );
		final Converter< U, ARGBType > conv = state.converter();
		if ( conv instanceof ARGBColorConverter< ? > )
		{
			final ARGBColorConverter< U > colorConv = ( net.imglib2.converter.ARGBColorConverter< U > ) conv;
			colorConv.colorProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.minProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.maxProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.alphaProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
		}
	}

	public < D extends Type< D >, T extends Type< T > > Optional< DataSource< D, T > > addLabelSource(
			final N5Writer n5,
			final String dataset ) throws IOException
	{
		return addLabelSource(
				n5,
				dataset,
				N5Helpers.getResolution( n5, dataset ),
				N5Helpers.getOffset( n5, dataset ) );
	}

	public < D extends Type< D >, T extends Type< T > > Optional< DataSource< D, T > > addLabelSource(
			final N5Writer n5,
			final String dataset,
			final double[] resolution,
			final double[] offset ) throws IOException
	{

		LOG.debug( "Adding label source n5={} dataset={} resolution={} offset={}", n5, dataset, resolution, offset );

		if ( !N5Helpers.isMultiScale( n5, dataset ) && !N5Helpers.isLabelMultisetType( n5, Paths.get( dataset, N5Helpers.listAndSortScaleDatasets( n5, dataset )[ 0 ] ).toString() ) )
		{
			LOG.warn( "Only multiscale label multisets supported at the moment. Not adding any data set" );
			return Optional.empty();
		}

		final ValueTriple< RandomAccessibleInterval< LabelMultisetType >[], RandomAccessibleInterval< VolatileLabelMultisetType >[], AffineTransform3D[] > labels =
				N5Helpers.openLabelMultisetMultiscale( n5, dataset, resolution, offset, getQueue(), 1 );

		final RandomAccessibleIntervalDataSource< LabelMultisetType, VolatileLabelMultisetType > labelSource = new RandomAccessibleIntervalDataSource<>(
				labels.getA(),
				labels.getB(),
				labels.getC(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				dataset );

		addLabelSource(
				labelSource,
				N5Helpers.assignments( n5, dataset ),
				N5Helpers.idService( n5, dataset ),
				ToIdConverter.fromLabelMultisetType(),
				null,
				null );

		@SuppressWarnings( "unchecked" )
		final DataSource< D, T > returnedSource = ( DataSource< D, T > ) labelSource;
		return Optional.of( returnedSource );
	}

	public < D extends Type< D >, T extends Type< T > > void addLabelSource(
			final DataSource< D, T > source,
			final FragmentSegmentAssignmentState assignment,
			final ToIdConverter toIdConverter )
	{
		addLabelSource( source, assignment, null, toIdConverter, null, null, equalsMaskForType( source.getDataType() ) );
	}

	public < D extends Type< D >, T extends Type< T > > void addLabelSource(
			final DataSource< D, T > source,
			final FragmentSegmentAssignmentState assignment,
			final ToIdConverter toIdConverter,
			final LongFunction< Converter< D, BoolType > > equalsMask )
	{
		addLabelSource( source, assignment, null, toIdConverter, null, null, equalsMask );
	}

	public < D extends Type< D >, T extends Type< T > > void addLabelSource(
			final DataSource< D, T > source,
			final FragmentSegmentAssignmentState assignment,
			final IdService idService,
			final ToIdConverter toIdConverter,
			final InterruptibleFunction< Long, Interval[] >[] blocksThatContainId,
			final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache )
	{
		addLabelSource( source, assignment, idService, toIdConverter, blocksThatContainId, meshCache, equalsMaskForType( source.getDataType() ) );
	}

	public < D extends Type< D >, T extends Type< T > > void addLabelSource(
			final DataSource< D, T > source,
			final FragmentSegmentAssignmentState assignment,
			final IdService idService,
			final ToIdConverter toIdConverter,
			InterruptibleFunction< Long, Interval[] >[] blocksThatContainId,
			InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache,
			final LongFunction< Converter< D, BoolType > > equalsMask )
	{
		final SelectedIds selId = new SelectedIds();
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment );
		stream.addListener( obs -> orthogonalViews().requestRepaint() );
		final Converter< T, ARGBType > converter = HighlightingStreamConverter.forType( stream, source.getType() );

		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();
		final AffineTransform3D affine = new AffineTransform3D();
		source.getSourceTransform( 0, 0, affine );

		final SelectedSegments selectedSegments = new SelectedSegments( selId, assignment );
		final FragmentsInSelectedSegments fragmentsInSelection = new FragmentsInSelectedSegments( selectedSegments, assignment );

		blocksThatContainId = blocksThatContainId == null
				? generateLabelBlocksForLabelCache( source, scaleFactorsFromAffineTransforms( source ), generalPurposeExecutorService )
				: blocksThatContainId;

		meshCache = meshCache == null
				? CacheUtils.meshCacheLoaders(
						source,
						Stream.generate( () -> new int[] { 1, 1, 1 } ).limit( source.getNumMipmapLevels() ).toArray( int[][]::new ),
						equalsMask,
						CacheUtils::toCacheSoftRefLoaderCache )
				: meshCache;

		final MeshManager meshManager = new MeshManagerWithAssignment(
				source,
				blocksThatContainId,
				meshCache,
				viewer3D.meshesGroup(),
				assignment,
				fragmentsInSelection,
				stream,
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				meshManagerExecutorService,
				meshWorkerExecutorService );

		final MeshInfos meshInfos = new MeshInfos( selectedSegments, assignment, meshManager, source.getNumMipmapLevels() );

		final LabelSourceState< D, T > state = sourceInfo.makeLabelSourceState(
				source,
				converter,
				comp,
				null,
				equalsMask,
				assignment,
				toIdConverter,
				selId,
				idService,
				meshManager,
				meshInfos );// converter );

		orthogonalViews().applyToAll( vp -> assignment.addListener( obs -> vp.requestRepaint() ) );
		orthogonalViews().applyToAll( vp -> selId.addListener( obs -> vp.requestRepaint() ) );

		sourceInfo.addState( source, state );

	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static < D > LongFunction< Converter< D, BoolType > > equalsMaskForType( final D d )
	{
		if ( d instanceof LabelMultisetType ) { return ( LongFunction ) equalMaskForLabelMultisetType(); }

		if ( d instanceof IntegerType< ? > ) { return ( LongFunction ) equalMaskForIntegerType(); }

		if ( d instanceof RealType< ? > ) { return ( LongFunction ) equalMaskForRealType(); }

		return null;
	}

	public static LongFunction< Converter< LabelMultisetType, BoolType > > equalMaskForLabelMultisetType()
	{
		return id -> ( s, t ) -> t.set( s.contains( id ) );
	}

	public static < D extends IntegerType< D > > LongFunction< Converter< D, BoolType > > equalMaskForIntegerType()
	{
		return id -> ( s, t ) -> t.set( s.getIntegerLong() == id );
	}

	public static < D extends RealType< D > > LongFunction< Converter< D, BoolType > > equalMaskForRealType()
	{
		return id -> ( s, t ) -> t.set( s.getRealDouble() == id );
	}

	public ExecutorService generalPurposeExecutorService()
	{
		return this.generalPurposeExecutorService;
	}

	private static < D extends Type< D >, T extends Type< T > > InterruptibleFunction< Long, Interval[] >[] generateLabelBlocksForLabelCache(
			final DataSource< D, T > spec,
			final double[][] scalingFactors,
			final ExecutorService es )
	{
		final boolean isMaskedSource = spec instanceof MaskedSource< ?, ? >;
		final boolean isLabelMultisetType = spec.getDataType() instanceof LabelMultisetType;
		final boolean isCachedCellImg = ( isMaskedSource
				? ( ( MaskedSource< ?, ? > ) spec ).underlyingSource().getDataSource( 0, 0 )
				: spec.getDataSource( 0, 0 ) ) instanceof CachedCellImg< ?, ? >;

		if ( isLabelMultisetType && isCachedCellImg )
		{
			@SuppressWarnings( "unchecked" )
			final DataSource< LabelMultisetType, T > source =
					( DataSource< LabelMultisetType, T > ) ( isMaskedSource ? ( ( MaskedSource< ?, ? > ) spec ).underlyingSource() : spec );
			return generateBlocksForLabelCacheLabelMultisetTypeCachedImg( source, scalingFactors, es );
		}

		return generateLabelBlocksForLabelCacheGeneric( spec, scalingFactors, collectLabels( spec.getDataType() ), es );
	}

	private static < D extends Type< D >, T extends Type< T > > InterruptibleFunction< Long, Interval[] >[] generateLabelBlocksForLabelCacheGeneric(
			final DataSource< D, T > spec,
			final double[][] scalingFactors,
			final BiConsumer< D, TLongHashSet > collectLabels,
			final ExecutorService es )
	{

		final int[][] blockSizes = Stream.generate( () -> new int[] { 64, 64, 64 } ).limit( spec.getNumMipmapLevels() ).toArray( int[][]::new );

		final InterruptibleFunction< HashWrapper< long[] >, long[] >[] uniqueLabelLoaders = CacheUtils.uniqueLabelCaches(
				spec,
				blockSizes,
				collectLabels,
				CacheUtils::toCacheSoftRefLoaderCache );

		final InterruptibleFunction< Long, Interval[] >[] blocksForLabelCache = CacheUtils.blocksForLabelCaches(
				spec,
				uniqueLabelLoaders,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache,
				es );

		return blocksForLabelCache;

	}

	private static < T extends Type< T > > InterruptibleFunction< Long, Interval[] >[] generateBlocksForLabelCacheLabelMultisetTypeCachedImg(
			final DataSource< LabelMultisetType, T > spec,
			final double[][] scalingFactors,
			final ExecutorService es )
	{
		@SuppressWarnings( "unchecked" )
		final InterruptibleFunction< HashWrapper< long[] >, long[] >[] uniqueLabelLoaders = new InterruptibleFunction[ spec.getNumMipmapLevels() ];
		final int[][] blockSizes = new int[ spec.getNumMipmapLevels() ][];
		for ( int level = 0; level < spec.getNumMipmapLevels(); ++level )
		{
			LOG.debug( "Generating unique label list loaders at level {} for LabelMultisetType source: {}", level, spec );
			final RandomAccessibleInterval< LabelMultisetType > img = spec.getDataSource( 0, level );
			if ( !( img instanceof CachedCellImg ) ) { throw new RuntimeException( "Source at level " + level + " is not a CachedCellImg for " + spec ); }
			@SuppressWarnings( "unchecked" )
			final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > cachedImg = ( CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > ) img;
			final LazyCells< Cell< VolatileLabelMultisetArray > > cells = cachedImg.getCells();
			final CellGrid grid = cachedImg.getCellGrid();
			final UniqueLabelListLabelMultisetCacheLoader loader = new UniqueLabelListLabelMultisetCacheLoader( cells );
			final Cache< HashWrapper< long[] >, long[] > cache = CacheUtils.toCacheSoftRefLoaderCache( loader );
			uniqueLabelLoaders[ level ] = CacheUtils.fromCache( cache, loader );
			blockSizes[ level ] = IntStream.range( 0, grid.numDimensions() ).map( grid::cellDimension ).toArray();
		}

		final InterruptibleFunction< Long, Interval[] >[] blocksForLabelCache = CacheUtils.blocksForLabelCaches(
				spec,
				uniqueLabelLoaders,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache,
				es );

		return blocksForLabelCache;
	}

	public static double[][] scaleFactorsFromAffineTransforms( final Source< ? > source )
	{
		final double[][] scaleFactors = new double[ source.getNumMipmapLevels() ][ 3 ];
		final AffineTransform3D reference = new AffineTransform3D();
		source.getSourceTransform( 0, 0, reference );
		for ( int level = 0; level < scaleFactors.length; ++level )
		{
			final double[] factors = scaleFactors[ level ];
			final AffineTransform3D transform = new AffineTransform3D();
			source.getSourceTransform( 0, level, transform );
			factors[ 0 ] = transform.get( 0, 0 ) / reference.get( 0, 0 );
			factors[ 1 ] = transform.get( 1, 1 ) / reference.get( 1, 1 );
			factors[ 2 ] = transform.get( 2, 2 ) / reference.get( 2, 2 );
		}

		{
			LOG.debug( "Generated scaling factors:" );
			Arrays.stream( scaleFactors ).map( Arrays::toString ).forEach( LOG::debug );
		}

		return scaleFactors;
	}

	@SuppressWarnings( "unchecked" )
	public static < T > BiConsumer< T, TLongHashSet > collectLabels( final T type )
	{
		if ( type instanceof LabelMultisetType ) { return ( BiConsumer< T, TLongHashSet > ) collectLabelsFromLabelMultisetType(); }
		if ( type instanceof IntegerType< ? > ) { return ( BiConsumer< T, TLongHashSet > ) collectLabelsFromIntegerType(); }
		if ( type instanceof RealType< ? > ) { return ( BiConsumer< T, TLongHashSet > ) collectLabelsFromRealType(); }
		return null;
	}

	private static BiConsumer< LabelMultisetType, TLongHashSet > collectLabelsFromLabelMultisetType()
	{
		return ( lbl, set ) -> lbl.entrySet().forEach( entry -> set.add( entry.getElement().id() ) );
	}

	private static < I extends IntegerType< I > > BiConsumer< I, TLongHashSet > collectLabelsFromIntegerType()
	{
		return ( lbl, set ) -> set.add( lbl.getIntegerLong() );
	}

	private static < R extends RealType< R > > BiConsumer< R, TLongHashSet > collectLabelsFromRealType()
	{
		return ( lbl, set ) -> set.add( ( long ) lbl.getRealDouble() );
	}

	public ExecutorService getPaintQueue()
	{
		return this.paintQueue;
	}

	public ExecutorService getPropagationQueue()
	{
		return this.propagationQueue;
	}

	public void stop()
	{
		LOG.debug( "Stopping everything" );
		this.generalPurposeExecutorService.shutdownNow();
		this.meshManagerExecutorService.shutdown();
		this.meshWorkerExecutorService.shutdownNow();
		this.paintQueue.shutdownNow();
		this.propagationQueue.shutdownNow();
		this.orthogonalViews().topLeft().viewer().stop();
		this.orthogonalViews().topRight().viewer().stop();
		this.orthogonalViews().bottomLeft().viewer().stop();
		this.cacheControl.shutdown();
		LOG.debug( "Sent stop requests everywhere" );
	}

	public static int reasonableNumFetcherThreads()
	{
		return Math.min( 8, Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );
	}

	public SharedQueue getQueue()
	{
		return this.cacheControl;
	}

}
