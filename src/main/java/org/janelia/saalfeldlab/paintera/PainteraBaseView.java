package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.UniqueLabelListLabelMultisetCacheLoader;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg.LazyCells;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;

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

	private final ExecutorService meshManagerExecutorService = Executors.newFixedThreadPool( 3, new NamedThreadFactory( "paintera-mesh-manager-%d" ) );

	private final ExecutorService meshWorkerExecutorService = Executors.newFixedThreadPool( 10, new NamedThreadFactory( "paintera-mesh-worker-%d" ) );

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
		LOG.debug( "Meshes group={}", viewer3D.meshesGroup() );
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

	@SuppressWarnings( "unchecked" )
	public < D, T > void addState( final SourceState< D, T > state )
	{
		if ( state instanceof LabelSourceState< ?, ? > )
		{
			addLabelSource( ( LabelSourceState ) state );
		}
		else if ( state instanceof RawSourceState< ?, ? > )
		{
			addRawSource( ( RawSourceState ) state );
		}
		else
		{
			addGenericState( state );
		}
	}

	public < D, T > void addGenericState( final SourceState< D, T > state )
	{
		sourceInfo.addState( state );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final RawSourceState< T, U > state )
	{
		LOG.debug( "Adding raw state={}", state );
		sourceInfo.addState( state );
		final ARGBColorConverter< U > conv = state.converter();
		final ARGBColorConverter< U > colorConv = conv;
		colorConv.colorProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
		colorConv.minProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
		colorConv.maxProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
		colorConv.alphaProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
	}

	public < D extends Type< D >, T extends Type< T > > void addLabelSource(
			final LabelSourceState< D, T > state )
	{
		LOG.debug( "Adding label state={}", state );
		final Converter< T, ARGBType > converter = state.converter();
		if ( converter instanceof HighlightingStreamConverter< ? > )
		{
			final AbstractHighlightingARGBStream stream = ( ( HighlightingStreamConverter< ? > ) converter ).getStream();
			stream.addListener( obs -> orthogonalViews().requestRepaint() );
		}

		orthogonalViews().applyToAll( vp -> state.assignment().addListener( obs -> vp.requestRepaint() ) );
		orthogonalViews().applyToAll( vp -> state.selectedIds().addListener( obs -> vp.requestRepaint() ) );
		orthogonalViews().applyToAll( vp -> state.lockedSegments().addListener( obs -> vp.requestRepaint() ) );

		state.meshManager().areMeshesEnabledProperty().bind( viewer3D.isMeshesEnabledProperty() );

		sourceInfo.addState( state.getDataSource(), state );
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

	public static < D, T > InterruptibleFunctionAndCache< Long, Interval[] >[] generateLabelBlocksForLabelCache(
			final DataSource< D, T > spec )
	{
		return generateLabelBlocksForLabelCache( spec, scaleFactorsFromAffineTransforms( spec ) );
	}

	public static < D, T > InterruptibleFunctionAndCache< Long, Interval[] >[] generateLabelBlocksForLabelCache(
			final DataSource< D, T > spec,
			final double[][] scalingFactors )
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
			return generateBlocksForLabelCacheLabelMultisetTypeCachedImg( source, scalingFactors );
		}

		return generateLabelBlocksForLabelCacheGeneric( spec, scalingFactors, collectLabels( spec.getDataType() ) );
	}

	private static < D, T > InterruptibleFunctionAndCache< Long, Interval[] >[] generateLabelBlocksForLabelCacheGeneric(
			final DataSource< D, T > spec,
			final double[][] scalingFactors,
			final BiConsumer< D, TLongHashSet > collectLabels )
	{

		final int[][] blockSizes = Stream.generate( () -> new int[] { 64, 64, 64 } ).limit( spec.getNumMipmapLevels() ).toArray( int[][]::new );

		final InterruptibleFunction< HashWrapper< long[] >, long[] >[] uniqueLabelLoaders = CacheUtils.uniqueLabelCaches(
				spec,
				blockSizes,
				collectLabels,
				CacheUtils::toCacheSoftRefLoaderCache );

		final InterruptibleFunctionAndCache< Long, Interval[] >[] blocksForLabelCache = CacheUtils.blocksForLabelCachesLongKeys(
				spec,
				uniqueLabelLoaders,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache );

		return blocksForLabelCache;

	}

	private static < T > InterruptibleFunctionAndCache< Long, Interval[] >[] generateBlocksForLabelCacheLabelMultisetTypeCachedImg(
			final DataSource< LabelMultisetType, T > spec,
			final double[][] scalingFactors )
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

		final InterruptibleFunctionAndCache< Long, Interval[] >[] blocksForLabelCache = CacheUtils.blocksForLabelCachesLongKeys(
				spec,
				uniqueLabelLoaders,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache );

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

	public ExecutorService getMeshManagerExecutorService()
	{
		return this.meshManagerExecutorService;
	}

	public ExecutorService getMeshWorkerExecutorService()
	{
		return this.meshWorkerExecutorService;
	}

}
