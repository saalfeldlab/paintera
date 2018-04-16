package bdv.bigcat.viewer.ortho;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.composite.ARGBCompositeAlphaAdd;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.ClearingCompositeProjector;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ARGBColorConverter;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.mask.MaskedSource;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.meshes.MeshGenerator.ShapeKey;
import bdv.bigcat.viewer.meshes.MeshInfos;
import bdv.bigcat.viewer.meshes.MeshManager;
import bdv.bigcat.viewer.meshes.MeshManagerWithAssignment;
import bdv.bigcat.viewer.meshes.cache.CacheUtils;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.FragmentsInSelectedSegments;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.state.SelectedSegments;
import bdv.bigcat.viewer.stream.HighlightingStreamConverter;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.bigcat.viewer.util.Colors;
import bdv.bigcat.viewer.util.HashWrapper;
import bdv.bigcat.viewer.viewer3d.Viewer3DFX;
import bdv.util.IdService;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import net.imglib2.Interval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;

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
				.accumulateProjectorFactory( new ClearingCompositeProjector.ClearingCompositeProjectorFactory<>( sourceInfo.composites(), new ARGBType() ) )
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

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final DataSource< T, U > spec,
			final double min,
			final double max,
			final Color color )
	{
		addRawSource( spec, min, max, Colors.toARGBType( color ) );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource(
			final DataSource< T, U > spec,
			final double min,
			final double max,
			final ARGBType color )
	{
		final Composite< ARGBType, ARGBType > comp = new ARGBCompositeAlphaAdd();
		final AtlasSourceState< U, T > state = sourceInfo.addRawSource( spec, min, max, color, comp );
		final Converter< U, ARGBType > conv = state.converterProperty().get();
		if ( conv instanceof ARGBColorConverter< ? > )
		{
			final ARGBColorConverter< U > colorConv = ( ARGBColorConverter< U > ) conv;
			colorConv.colorProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.minProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.maxProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
			colorConv.alphaProperty().addListener( ( obs, oldv, newv ) -> orthogonalViews().requestRepaint() );
		}
	}

	public < D extends Type< D >, T extends Type< T >, F extends FragmentSegmentAssignmentState< F > > void addLabelSource(
			final DataSource< D, T > source,
			final F assignment,
			final ToIdConverter toIdConverter )
	{
		addLabelSource( source, assignment, null, toIdConverter, null, null, equalsMaskForType( source.getDataType() ) );
	}

	public < D extends Type< D >, T extends Type< T >, F extends FragmentSegmentAssignmentState< F > > void addLabelSource(
			final DataSource< D, T > source,
			final F assignment,
			final ToIdConverter toIdConverter,
			final LongFunction< Converter< D, BoolType > > equalsMask )
	{
		addLabelSource( source, assignment, null, toIdConverter, null, null, equalsMask );
	}

	public < D extends Type< D >, T extends Type< T >, F extends FragmentSegmentAssignmentState< F > > void addLabelSource(
			final DataSource< D, T > source,
			final F assignment,
			final IdService idService,
			final ToIdConverter toIdConverter,
			final Function< Long, Interval[] >[] blocksThatContainId,
			final Function< ShapeKey, Pair< float[], float[] > >[] meshCache )
	{
		addLabelSource( source, assignment, idService, toIdConverter, blocksThatContainId, meshCache, equalsMaskForType( source.getDataType() ) );
	}

	public < D extends Type< D >, T extends Type< T >, F extends FragmentSegmentAssignmentState< F > > void addLabelSource(
			final DataSource< D, T > source,
			final F assignment,
			final IdService idService,
			final ToIdConverter toIdConverter,
			final Function< Long, Interval[] >[] blocksThatContainId,
			final Function< ShapeKey, Pair< float[], float[] > >[] meshCache,
			final LongFunction< Converter< D, BoolType > > equalsMask )
	{
		final SelectedIds selId = new SelectedIds();
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment );
		stream.addListener( () -> orthogonalViews().requestRepaint() );
		final Converter< T, ARGBType > converter = HighlightingStreamConverter.forType( stream, source.getType() );

		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();

		final AtlasSourceState< T, D > state = sourceInfo.makeLabelSourceState(
				source,
				toIdConverter,
				equalsMask,
				assignment,
				stream,
				selId,
				converter,
				comp );// converter );
		state.idServiceProperty().set( idService );
		if ( source instanceof MaskedSource< ?, ? > )
		{
			state.maskedSourceProperty().set( ( MaskedSource< ?, ? > ) source );
		}

		final AffineTransform3D affine = new AffineTransform3D();
		source.getSourceTransform( 0, 0, affine );

		final SelectedSegments< F > selectedSegments = new SelectedSegments<>( selId, assignment );
		final FragmentsInSelectedSegments< F > fragmentsInSelection = new FragmentsInSelectedSegments<>( selectedSegments, assignment );

		final MeshManager meshManager = new MeshManagerWithAssignment(
				source,
				state,
				viewer3D.meshesGroup(),
				fragmentsInSelection,
				new SimpleIntegerProperty(),
				Executors.newFixedThreadPool( 1 ) );

		final MeshInfos meshInfos = new MeshInfos( state, selectedSegments, assignment, meshManager, source.getNumMipmapLevels() );
		state.meshManagerProperty().set( meshManager );
		state.meshInfosProperty().set( meshInfos );

		orthogonalViews().applyToAll( vp -> assignment.addListener( vp::requestRepaint ) );
		orthogonalViews().applyToAll( vp -> selId.addListener( vp::requestRepaint ) );

		LOG.debug( "Adding mesh and block list caches: {} {}", meshCache, blocksThatContainId );
		if ( meshCache != null && blocksThatContainId != null )
		{
			state.meshesCacheProperty().set( meshCache );
			state.blocklistCacheProperty().set( blocksThatContainId );
		}
		else
		{
			// off-diagonal in case of permutations)
			generateMeshCaches(
					source,
					state,
					scaleFactorsFromAffineTransforms( source ),
					collectLabels( source.getDataType() ),
					equalsMask,
					generalPurposeExecutorService );
		}

		sourceInfo.addState( source, state );

	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static < D > LongFunction< Converter< D, BoolType > > equalsMaskForType( final D d )
	{
		if ( d instanceof LabelMultisetType ) {
			return ( LongFunction ) equalMaskForLabelMultisetType();
		}

		if ( d instanceof IntegerType< ? > ) {
			return ( LongFunction ) equalMaskForIntegerType();
		}

		if ( d instanceof RealType< ? > ) {
			return ( LongFunction ) equalMaskForRealType();
		}

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

	private static < D extends Type< D >, T extends Type< T > > void generateMeshCaches(
			final DataSource< D, T > spec,
			final AtlasSourceState< T, D > state,
			final double[][] scalingFactors,
			final BiConsumer< D, TLongHashSet > collectLabels,
			final LongFunction< Converter< D, BoolType > > getMaskGenerator,
			final ExecutorService es )
	{

		final int[][] blockSizes = Stream.generate( () -> new int[] { 64, 64, 64 } ).limit( spec.getNumMipmapLevels() ).toArray( int[][]::new );
		final int[][] cubeSizes = Stream.generate( () -> new int[] { 1, 1, 1 } ).limit( spec.getNumMipmapLevels() ).toArray( int[][]::new );

		final Function< HashWrapper< long[] >, long[] >[] uniqueLabelLoaders = CacheUtils.uniqueLabelCaches(
				spec,
				blockSizes,
				collectLabels,
				CacheUtils::toCacheSoftRefLoaderCache );

		final Function< Long, Interval[] >[] blocksForLabelCache = CacheUtils.blocksForLabelCaches(
				spec,
				uniqueLabelLoaders,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache,
				es );

		final Function< ShapeKey, Pair< float[], float[] > >[] meshCaches = CacheUtils.meshCacheLoaders(
				spec,
				cubeSizes,
				getMaskGenerator,
				CacheUtils::toCacheSoftRefLoaderCache );

		state.blocklistCacheProperty().set( blocksForLabelCache );
		state.meshesCacheProperty().set( meshCaches );
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

		if ( LOG.isDebugEnabled() )
		{
			LOG.debug( "Generated scaling factors:" );
			Arrays.stream( scaleFactors ).map( Arrays::toString ).forEach( LOG::debug );
		}

		return scaleFactors;
	}

	@SuppressWarnings( "unchecked" )
	public static < T > BiConsumer< T, TLongHashSet > collectLabels( final T type )
	{
		if ( type instanceof LabelMultisetType )
		{
			return ( BiConsumer< T, TLongHashSet > ) collectLabelsFromLabelMultisetType();
		}
		if ( type instanceof IntegerType< ? > )
		{
			return ( BiConsumer< T, TLongHashSet > ) collectLabelsFromIntegerType();
		}
		if ( type instanceof RealType< ? > )
		{
			return ( BiConsumer< T, TLongHashSet > ) collectLabelsFromRealType();
		}
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


}
