package bdv.bigcat.viewer.atlas;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.viewer.atlas.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.atlas.control.Navigation;
import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.DisplayTransformUpdateOnResize;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.ortho.OrthogonalViews;
import bdv.bigcat.viewer.ortho.OrthogonalViews.ViewerAndTransforms;
import bdv.bigcat.viewer.ortho.PainteraBaseView;
import bdv.bigcat.viewer.panel.CrossHair;
import bdv.bigcat.viewer.viewer3d.OrthoSliceFX;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.Interpolation;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.ARGBType;

public class Paintera extends Application
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final SimpleObjectProperty< Interpolation > interpolation = new SimpleObjectProperty<>( Interpolation.NEARESTNEIGHBOR );

	private final PainteraBaseView baseView = new PainteraBaseView( 8, s -> interpolation.get() );

	private final SourceInfo sourceInfo = baseView.sourceInfo();

	private final KeyTracker keyTracker = new KeyTracker();

	private final Map< ViewerPanelFX, ViewerAndTransforms > viewerToTransforms = new HashMap<>();
	{
		viewerToTransforms.put( baseView.orthogonalViews().topLeft().viewer(), baseView.orthogonalViews().topLeft() );
		viewerToTransforms.put( baseView.orthogonalViews().topRight().viewer(), baseView.orthogonalViews().topRight() );
		viewerToTransforms.put( baseView.orthogonalViews().bottomLeft().viewer(), baseView.orthogonalViews().bottomLeft() );
	}

	private final Navigation navigation = new Navigation(
			baseView.manager(),
			v -> viewerToTransforms.get( v ).displayTransform(),
			v -> viewerToTransforms.get( v ).globalToViewerTransform(),
			keyTracker );

	private final ObservableObjectValue< ViewerAndTransforms > currentFocusHolderWithState = currentFocusHolder( baseView.orthogonalViews() );

	private final Consumer< OnEnterOnExit > onEnterOnExit = createOnEnterOnExit( currentFocusHolderWithState );

	private final Map< ViewerAndTransforms, CrossHair > crossHairs = makeCrosshairs( baseView.orthogonalViews(), Color.ORANGE, Color.WHITE );

	private final Map< ViewerAndTransforms, OrthoSliceFX > orthoSlices = makeOrthoSlices( baseView.orthogonalViews(), baseView.viewer3D().meshesGroup(), sourceInfo );

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final ArrayImg< ARGBType, IntArray > data = ArrayImgs.argbs( 100, 200, 300 );
		final Random rng = new Random();
		data.forEach( d -> d.set( rng.nextInt() ) );
		final DataSource< ARGBType, ARGBType > source = DataSource.fromSource( new RandomAccessibleIntervalSource<>( data, data.createLinkedType(), "data" ) );
		final AtlasSourceState< ARGBType, ARGBType > rngState = sourceInfo.makeGenericSourceState( source, new TypeIdentity< ARGBType >(), new CompositeCopy<>() );
		sourceInfo.addState( source, rngState );
		baseView.viewer3D().setInitialTransformToInterval( data );

		updateDisplayTransformOnResize( baseView.orthogonalViews(), baseView.manager() );

		onEnterOnExit.accept( navigation.onEnterOnExit() );

		grabFocusOnMouseOver(
				baseView.orthogonalViews().topLeft().viewer(),
				baseView.orthogonalViews().topRight().viewer(),
				baseView.orthogonalViews().bottomLeft().viewer() );

		final Stage stage = new Stage();
		final Scene scene = new Scene( baseView.pane() );

		keyTracker.installInto( scene );
		stage.setScene( scene );
		stage.show();
		stage.setWidth( 800 );
		stage.setHeight( 600 );
	}

	public static void main( final String[] args )
	{
		launch( args );
	}

	public static DisplayTransformUpdateOnResize[] updateDisplayTransformOnResize( final OrthogonalViews< ? > views, final Object lock )
	{
		return new DisplayTransformUpdateOnResize[] {
				updateDisplayTransformOnResize( views.topLeft(), lock ),
				updateDisplayTransformOnResize( views.topRight(), lock ),
				updateDisplayTransformOnResize( views.bottomLeft(), lock )
		};
	}

	public static DisplayTransformUpdateOnResize updateDisplayTransformOnResize( final ViewerAndTransforms vat, final Object lock )
	{
		final ViewerPanelFX viewer = vat.viewer();
		final AffineTransformWithListeners displayTransform = vat.displayTransform();
		final DisplayTransformUpdateOnResize updater = new DisplayTransformUpdateOnResize( displayTransform, viewer.widthProperty(), viewer.heightProperty(), lock );
		updater.listen();
		return updater;
	}

	public static ObservableObjectValue< ViewerAndTransforms > currentFocusHolder( final OrthogonalViews< ? > views )
	{
		final ViewerAndTransforms tl = views.topLeft();
		final ViewerAndTransforms tr = views.topRight();
		final ViewerAndTransforms bl = views.bottomLeft();
		final ReadOnlyBooleanProperty focusTL = tl.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusTR = tr.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusBL = bl.viewer().focusedProperty();

		return Bindings.createObjectBinding(
				() -> {
					return focusTL.get() ? tl : focusTR.get() ? tr : focusBL.get() ? bl : null;
				},
				focusTL,
				focusTR,
				focusBL );

	}

	public static Consumer< OnEnterOnExit > createOnEnterOnExit( final ObservableObjectValue< ViewerAndTransforms > currentFocusHolder )
	{
		final List< OnEnterOnExit > onEnterOnExits = new ArrayList<>();

		final ChangeListener< ViewerAndTransforms > onEnterOnExit = ( obs, oldv, newv ) -> {
			if ( oldv != null )
				onEnterOnExits.stream().map( OnEnterOnExit::onExit ).forEach( e -> e.accept( oldv.viewer() ) );
			if ( newv != null )
				onEnterOnExits.stream().map( OnEnterOnExit::onEnter ).forEach( e -> e.accept( newv.viewer() ) );
		};

		currentFocusHolder.addListener( onEnterOnExit );

		return onEnterOnExits::add;
	}

	public static void grabFocusOnMouseOver( final Node... nodes )
	{
		grabFocusOnMouseOver( Arrays.asList( nodes ) );
	}

	public static void grabFocusOnMouseOver( final Collection< Node > nodes )
	{
		nodes.forEach( Paintera::grabFocusOnMouseOver );
	}

	public static void grabFocusOnMouseOver( final Node node )
	{
		node.addEventFilter( MouseEvent.MOUSE_ENTERED, e -> node.requestFocus() );
	}

	public static Map< ViewerAndTransforms, CrossHair > makeCrosshairs(
			final OrthogonalViews< ? > views,
			final Color onFocusColor,
			final Color offFocusColor )
	{
		final Map< ViewerAndTransforms, CrossHair > map = new HashMap<>();
		map.put( views.topLeft(), makeCrossHairForViewer( views.topLeft().viewer(), onFocusColor, offFocusColor ) );
		map.put( views.topRight(), makeCrossHairForViewer( views.topRight().viewer(), onFocusColor, offFocusColor ) );
		map.put( views.bottomLeft(), makeCrossHairForViewer( views.bottomLeft().viewer(), onFocusColor, offFocusColor ) );
		return map;
	}

	public static CrossHair makeCrossHairForViewer(
			final ViewerPanelFX viewer,
			final Color onFocusColor,
			final Color offFocusColor )
	{
		final CrossHair ch = new CrossHair();
		viewer.getDisplay().addOverlayRenderer( ch );
		viewer.focusedProperty().addListener( ( obs, oldv, newv ) -> ch.setColor( newv ? onFocusColor : offFocusColor ) );
		ch.wasChangedProperty().addListener( ( obs, oldv, newv ) -> viewer.getDisplay().drawOverlays() );
		return ch;
	}

	public static Map< ViewerAndTransforms, OrthoSliceFX > makeOrthoSlices(
			final OrthogonalViews< ? > views,
			final Group scene,
			final SourceInfo sourceInfo )
	{
		final Map< ViewerAndTransforms, OrthoSliceFX > map = new HashMap<>();
		map.put( views.topLeft(), new OrthoSliceFX( scene, views.topLeft().viewer(), sourceInfo ) );
		map.put( views.topRight(), new OrthoSliceFX( scene, views.topRight().viewer(), sourceInfo ) );
		map.put( views.bottomLeft(), new OrthoSliceFX( scene, views.bottomLeft().viewer(), sourceInfo ) );
		map.values().forEach( OrthoSliceFX::toggleVisibility );
		return map;
	}

//	public < T, VT > void addLabelSource(
//			final DataSource< T, VT > spec,
//			final FragmentSegmentAssignmentState< ? > assignment,
//			final IdService idService,
//			final Function< Long, Interval[] >[] blocksThatContainId,
//			final Function< ShapeKey, Pair< float[], float[] > >[] meshCache )
//	{
//		final CurrentModeConverter< VolatileLabelMultisetType, HighlightingStreamConverterLabelMultisetType > converter = new CurrentModeConverter<>();
//		final SelectedIds selId = new SelectedIds();
//		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment );
//		stream.addListener( () -> baseView().requestRepaint() );
//		converter.setConverter( new HighlightingStreamConverterLabelMultisetType( stream ) );
//
//		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();
//
//		addSource( spec, comp, spec.tMin(), spec.tMax() );
//		final AtlasSourceState< VolatileLabelMultisetType, LabelMultisetType > state = sourceInfo.makeLabelSourceState(
//				spec,
//				ToIdConverter.fromLabelMultisetType(),
//				( Function< LabelMultisetType, Converter< LabelMultisetType, BoolType > > ) sel -> createBoolConverter( sel, assignment ),
//				( FragmentSegmentAssignmentState ) assignment,
//				stream,
//				selId,
//				converter,
//				comp );// converter );
//		state.idServiceProperty().set( idService );
//		if ( spec instanceof MaskedSource< ?, ? > )
//		{
//			state.maskedSourceProperty().set( ( MaskedSource< ?, ? > ) spec );
//		}
//
//		final LabelMultisetType t = spec.getDataType();
//		final Function< LabelMultisetType, String > valueToString = valueToString( t );
//		final AffineTransform3D affine = new AffineTransform3D();
//		spec.getSourceTransform( 0, 0, affine );
//		this.valueDisplayListener.addSource( spec, Optional.of( valueToString ) );
//
//		final SelectedSegments selectedSegments = new SelectedSegments( selId, assignment );
//		final FragmentsInSelectedSegments fragmentsInSelection = new FragmentsInSelectedSegments( selectedSegments, assignment );
//
//		final MeshManager meshManager = new MeshManagerWithAssignment(
//				spec,
//				state,
//				renderView.meshesGroup(),
//				fragmentsInSelection,
//				settings.meshSimplificationIterationsProperty(),
//				this.generalPurposeExecutorService );
//
//		final MeshInfos meshInfos = new MeshInfos( state, selectedSegments, assignment, meshManager, spec.getNumMipmapLevels() );
//		state.meshManagerProperty().set( meshManager );
//		state.meshInfosProperty().set( meshInfos );
//
//		view.addActor( new ViewerActor()
//		{
//
//			@Override
//			public Consumer< ViewerPanelFX > onRemove()
//			{
//				return vp -> {};
//			}
//
//			@Override
//			public Consumer< ViewerPanelFX > onAdd()
//			{
//				return vp -> assignment.addListener( () -> vp.requestRepaint() );
//			}
//		} );
//
//		view.addActor( new ViewerActor()
//		{
//			@Override
//			public Consumer< ViewerPanelFX > onRemove()
//			{
//				return vp -> {};
//			}
//
//			@Override
//			public Consumer< ViewerPanelFX > onAdd()
//			{
//				return vp -> {
//					selId.addListener( () -> vp.requestRepaint() );
//				};
//			}
//		} );
//
//		LOG.debug( "Adding mesh and block list caches: {} {}", meshCache, blocksThatContainId );
//		if ( meshCache != null && blocksThatContainId != null )
//		{
//			state.meshesCacheProperty().set( meshCache );
//			state.blocklistCacheProperty().set( blocksThatContainId );
//		}
//		else
//		{
//			// off-diagonal in case of permutations)
//			generateMeshCaches(
//					spec,
//					state,
//					scaleFactorsFromAffineTransforms( spec ),
//					( lbl, set ) -> lbl.entrySet().forEach( entry -> set.add( entry.getElement().id() ) ),
//					lbl -> ( src, tgt ) -> tgt.set( src.contains( lbl ) ),
//					generalPurposeExecutorService );
//		}
//
//		sourceInfo.addState( spec, state );
//	}

}
