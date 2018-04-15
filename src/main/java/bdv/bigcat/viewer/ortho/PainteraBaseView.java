package bdv.bigcat.viewer.ortho;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.composite.ARGBCompositeAlphaAdd;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.ClearingCompositeProjector;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.label.Label;
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
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.FragmentsInSelectedSegments;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.state.SelectedSegments;
import bdv.bigcat.viewer.stream.HighlightingStreamConverter;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.bigcat.viewer.viewer3d.Viewer3DFX;
import bdv.util.IdService;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import net.imglib2.Interval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.Multiset.Entry;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
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

	public PainteraBaseView( final int numFetcherThreads, final Function< Source< ? >, Interpolation > interpolation )
	{
		this( numFetcherThreads, ViewerOptions.options(), interpolation );
	}

	public PainteraBaseView(
			final int numFetcherThreads,
			final ViewerOptions viewerOptions,
			final Function< Source< ? >, Interpolation > interpolation )
	{
		super();
		this.cacheControl = new SharedQueue( numFetcherThreads );
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory( new ClearingCompositeProjector.ClearingCompositeProjectorFactory<>( sourceInfo.composites(), new ARGBType() ) )
				.numRenderingThreads( Math.min( 3, Math.max( 1, Runtime.getRuntime().availableProcessors() / 3 ) ) );
		this.views = new OrthogonalViews<>( manager, cacheControl, this.viewerOptions, viewer3D, interpolation );
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
			final Function< D, Converter< D, BoolType > > equalsMask )
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
			final Function< D, Converter< D, BoolType > > equalsMask )
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
//			generateMeshCaches(
//					spec,
//					state,
//					scaleFactorsFromAffineTransforms( spec ),
//					( lbl, set ) -> lbl.entrySet().forEach( entry -> set.add( entry.getElement().id() ) ),
//					lbl -> ( src, tgt ) -> tgt.set( src.contains( lbl ) ),
//					generalPurposeExecutorService );
		}

		sourceInfo.addState( source, state );

	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static < D > Function< D, Converter< D, BoolType > > equalsMaskForType( final D d )
	{
		if ( d instanceof LabelMultisetType )
			return ( Function ) equalMaskForLabelMultisetType();

		if ( d instanceof Type< ? > )
			return ( Function ) equalMaskForType();
		return null;
	}

	public static < D extends Type< D > > Function< D, Converter< D, BoolType > > equalMaskForType()
	{
		return initial -> ( s, t ) -> t.set( s.valueEquals( initial ) );
	}

	public static Function< LabelMultisetType, Converter< LabelMultisetType, BoolType > > equalMaskForLabelMultisetType()
	{
		return initial -> {
			long argMax = Label.INVALID;
			int maxCount = 0;
			for ( final Entry< net.imglib2.type.label.Label > entry : initial.entrySet() )
			{
				if ( entry.getCount() > maxCount )
				{
					argMax = entry.getElement().id();
					maxCount = entry.getCount();
				}
			}

			final long finalArgMax = argMax;
			if ( Label.regular( argMax ) )
				return ( s, t ) -> t.set( s.contains( finalArgMax ) );

			return ( s, t ) -> t.set( false );
		};
	}

}
