package bdv.bigcat.viewer.atlas;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.viewer.atlas.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.atlas.control.Navigation;
import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.DisplayTransformUpdateOnResize;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.MultiBoxOverlayRendererFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.ortho.OrthogonalViews;
import bdv.bigcat.viewer.ortho.OrthogonalViews.ViewerAndTransforms;
import bdv.bigcat.viewer.ortho.PainteraBaseView;
import bdv.bigcat.viewer.panel.CrossHair;
import bdv.bigcat.viewer.viewer3d.OrthoSliceFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.Views;

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

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< ARGBType >[] srcs = new RandomAccessibleInterval[] {
				data,
				Views.subsample( data, 2 )
		};

		final Function< Interpolation, InterpolatorFactory< ARGBType, RandomAccessible< ARGBType > > > ipol = i -> i.equals( Interpolation.NLINEAR )
				? new NLinearInterpolatorFactory<>()
				: new NearestNeighborInterpolatorFactory<>();

		final AffineTransform3D[] mipmapTransforms = {
				new AffineTransform3D(),
				new AffineTransform3D().concatenate( new Scale3D( 2.0, 2.0, 2.0 ) )
		};

		final RandomAccessibleIntervalDataSource< ARGBType, ARGBType > source = new RandomAccessibleIntervalDataSource<>( srcs, srcs, mipmapTransforms, ipol, ipol, "data" );
		final AtlasSourceState< ARGBType, ARGBType > rngState = sourceInfo.makeGenericSourceState( source, new TypeIdentity< ARGBType >(), new CompositeCopy<>() );
		sourceInfo.addState( source, rngState );
		baseView.viewer3D().setInitialTransformToInterval( data );

		updateDisplayTransformOnResize( baseView.orthogonalViews(), baseView.manager() );

		onEnterOnExit.accept( navigation.onEnterOnExit() );

		grabFocusOnMouseOver(
				baseView.orthogonalViews().topLeft().viewer(),
				baseView.orthogonalViews().topRight().viewer(),
				baseView.orthogonalViews().bottomLeft().viewer() );

		final ObservableList< Source< ? > > sources = sourceInfo.trackSources();
		final ObservableList< Source< ? > > visibleSources = sourceInfo.trackSources();

		final MultiBoxOverlayRendererFX[] multiBoxes = {
				new MultiBoxOverlayRendererFX( baseView.orthogonalViews().topLeft().viewer()::getState, sources, visibleSources ),
				new MultiBoxOverlayRendererFX( baseView.orthogonalViews().topRight().viewer()::getState, sources, visibleSources ),
				new MultiBoxOverlayRendererFX( baseView.orthogonalViews().bottomLeft().viewer()::getState, sources, visibleSources )
		};

		baseView.orthogonalViews().topLeft().viewer().getDisplay().addOverlayRenderer( multiBoxes[ 0 ] );
		baseView.orthogonalViews().topRight().viewer().getDisplay().addOverlayRenderer( multiBoxes[ 1 ] );
		baseView.orthogonalViews().bottomLeft().viewer().getDisplay().addOverlayRenderer( multiBoxes[ 2 ] );

		multiBoxes[ 0 ].isVisibleProperty().bind( baseView.orthogonalViews().topLeft().viewer().focusedProperty() );
		multiBoxes[ 1 ].isVisibleProperty().bind( baseView.orthogonalViews().topRight().viewer().focusedProperty() );
		multiBoxes[ 2 ].isVisibleProperty().bind( baseView.orthogonalViews().bottomLeft().viewer().focusedProperty() );

		final Stage stage = new Stage();
		final Scene scene = new Scene( baseView.pane() );

		stage.addEventFilter( WindowEvent.WINDOW_CLOSE_REQUEST, event -> viewerToTransforms.keySet().forEach( ViewerPanelFX::stop ) );

		Platform.setImplicitExit( true );

		final ScheduledExecutorService t = Executors.newScheduledThreadPool( 1 );
		t.scheduleAtFixedRate( this::toggleInterpolation, 0, 1000, TimeUnit.MILLISECONDS );
		this.interpolation.addListener( ( obs, oldv, newv ) -> baseView.orthogonalViews().requestRepaint() );

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

	private void toggleInterpolation()
	{
		this.interpolation.set( this.interpolation.get().equals( Interpolation.NLINEAR ) ? Interpolation.NEARESTNEIGHBOR : Interpolation.NLINEAR );
	}

}
