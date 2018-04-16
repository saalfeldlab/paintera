package bdv.bigcat.viewer.atlas;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.atlas.control.FitToInterval;
import bdv.bigcat.viewer.atlas.control.Merges;
import bdv.bigcat.viewer.atlas.control.Navigation;
import bdv.bigcat.viewer.atlas.control.Paint;
import bdv.bigcat.viewer.atlas.control.RunWhenFirstElementIsAdded;
import bdv.bigcat.viewer.atlas.control.Selection;
import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.DisplayTransformUpdateOnResize;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.atlas.ui.source.SourceTabs;
import bdv.bigcat.viewer.bdvfx.EventFX;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.MultiBoxOverlayRendererFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.ortho.OrthogonalViews;
import bdv.bigcat.viewer.ortho.OrthogonalViews.ViewerAndTransforms;
import bdv.bigcat.viewer.ortho.OrthogonalViewsValueDisplayListener;
import bdv.bigcat.viewer.ortho.PainteraBaseView;
import bdv.bigcat.viewer.panel.CrossHair;
import bdv.bigcat.viewer.viewer3d.OrthoSliceFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;

public class Paintera extends Application
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final PainteraBaseView baseView = new PainteraBaseView( 8, si -> s -> si.getState( s ).interpolationProperty().get() );

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

	private final Merges merges = new Merges( sourceInfo, keyTracker );

	private final Paint paint = new Paint( sourceInfo, keyTracker, baseView.manager(), baseView.orthogonalViews()::requestRepaint );

	private final Selection selection = new Selection( sourceInfo, keyTracker );

	private final ObservableObjectValue< ViewerAndTransforms > currentFocusHolderWithState = currentFocusHolder( baseView.orthogonalViews() );

	private final Consumer< OnEnterOnExit > onEnterOnExit = createOnEnterOnExit( currentFocusHolderWithState );

	private final Map< ViewerAndTransforms, CrossHair > crossHairs = makeCrosshairs( baseView.orthogonalViews(), Color.ORANGE, Color.WHITE );

	private final Map< ViewerAndTransforms, OrthoSliceFX > orthoSlices = makeOrthoSlices( baseView.orthogonalViews(), baseView.viewer3D().meshesGroup(), sourceInfo );

	private final PainteraOpenDialogEventHandler openDialogHandler = new PainteraOpenDialogEventHandler(
			baseView,
			baseView.orthogonalViews().sharedQueue(),
			e -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.O ) );

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{

		updateDisplayTransformOnResize( baseView.orthogonalViews(), baseView.manager() );

		onEnterOnExit.accept( navigation.onEnterOnExit() );
		onEnterOnExit.accept( selection.onEnterOnExit() );
		onEnterOnExit.accept( merges.onEnterOnExit() );
		onEnterOnExit.accept( paint.onEnterOnExit() );

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

		final BorderPane borderPane = new BorderPane( baseView.pane() );
		final Label currentSourceStatus = new Label();
		final Label valueStatus = new Label();
		final CheckBox showStatusBar = new CheckBox();
		final ObjectProperty< Source< ? > > cs = sourceInfo.currentSourceProperty();
		final StringBinding csName = Bindings.createStringBinding( () -> Optional.ofNullable( cs.get() ).map( s -> s.getName() ).orElse( "<null>" ), cs );
		currentSourceStatus.textProperty().bind( csName );
		showStatusBar.setTooltip( new Tooltip( "If not selected, status bar will only show on mouse-over" ) );
		final OrthogonalViewsValueDisplayListener vdl = new OrthogonalViewsValueDisplayListener( valueStatus::setText, cs, s -> sourceInfo.getState( s ).interpolationProperty().get() );
		final AnchorPane statusBar = new AnchorPane( currentSourceStatus, valueStatus, showStatusBar );
		AnchorPane.setLeftAnchor( currentSourceStatus, 0.0 );
		AnchorPane.setLeftAnchor( valueStatus, 50.0 );
		AnchorPane.setRightAnchor( showStatusBar, 0.0 );

		final BooleanProperty isWithinMarginOfBorder = new SimpleBooleanProperty();
		borderPane.addEventFilter( MouseEvent.MOUSE_MOVED, e -> isWithinMarginOfBorder.set( e.getY() < borderPane.getHeight() && borderPane.getHeight() - e.getY() <= statusBar.getHeight() ) );
		statusBar.visibleProperty().addListener( ( obs, oldv, newv ) -> borderPane.setBottom( newv ? statusBar : null ) );
		statusBar.visibleProperty().bind( isWithinMarginOfBorder.or( showStatusBar.selectedProperty() ) );
		showStatusBar.setSelected( true );

		currentSourceStatus.setMaxWidth( 45 );

		onEnterOnExit.accept( new OnEnterOnExit( vdl.onEnter(), vdl.onExit() ) );

		final Stage stage = new Stage();
		final Scene scene = new Scene( borderPane );

		stage.addEventFilter( WindowEvent.WINDOW_CLOSE_REQUEST, event -> viewerToTransforms.keySet().forEach( ViewerPanelFX::stop ) );

		Platform.setImplicitExit( true );

		final SourceTabs sideBar = new SourceTabs(
				sourceInfo.currentSourceIndexProperty(),
				sourceInfo::removeSource,
				sourceInfo );
		sideBar.widthProperty().set( 200 );
		sideBar.get().setVisible( true );

		scene.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( keyTracker.areOnlyTheseKeysDown( KeyCode.P ) )
			{
				borderPane.setRight( borderPane.getRight() == null ? sideBar.get() : null );
				event.consume();
			}
		} );

		sourceInfo.trackSources().addListener( FitToInterval.fitToIntervalWhenSourceAddedListener( baseView.manager(), baseView.orthogonalViews().topLeft().viewer().widthProperty()::get ) );
		sourceInfo.trackSources().addListener( new RunWhenFirstElementIsAdded<>( c -> baseView.viewer3D().setInitialTransformToInterval( sourceIntervalInWorldSpace( c.getAddedSubList().get( 0 ) ) ) ) );

		EventFX.KEY_PRESSED( "toggle interpolation", e -> toggleInterpolation(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.I ) ).installInto( borderPane );
		EventFX.KEY_PRESSED( "cycle current source", e -> sourceInfo.incrementCurrentSourceIndex(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.TAB ) ).installInto( borderPane );
		EventFX.KEY_PRESSED( "backwards cycle current source", e -> sourceInfo.decrementCurrentSourceIndex(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.TAB ) ).installInto( borderPane );

		scene.addEventHandler( KeyEvent.KEY_PRESSED, openDialogHandler );

		keyTracker.installInto( scene );
		stage.setScene( scene );
		stage.setWidth( 800 );
		stage.setHeight( 600 );
		stage.show();
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
			{
				onEnterOnExits.stream().map( OnEnterOnExit::onExit ).forEach( e -> e.accept( oldv.viewer() ) );
			}
			if ( newv != null )
			{
				onEnterOnExits.stream().map( OnEnterOnExit::onEnter ).forEach( e -> e.accept( newv.viewer() ) );
			}
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
		final Source< ? > source = sourceInfo.currentSourceProperty().get();
		if ( source == null ) { return; }
		final ObjectProperty< Interpolation > ip = sourceInfo.getState( source ).interpolationProperty();
		ip.set( ip.get().equals( Interpolation.NLINEAR ) ? Interpolation.NEARESTNEIGHBOR : Interpolation.NLINEAR );
		baseView.orthogonalViews().requestRepaint();
	}

	private static Interval sourceIntervalInWorldSpace( final Source< ? > source )
	{
		final double[] min = Arrays.stream( Intervals.minAsLongArray( source.getSource( 0, 0 ) ) ).asDoubleStream().toArray();
		final double[] max = Arrays.stream( Intervals.maxAsLongArray( source.getSource( 0, 0 ) ) ).asDoubleStream().toArray();
		final AffineTransform3D tf = new AffineTransform3D();
		source.getSourceTransform( 0, 0, tf );
		tf.apply( min, min );
		tf.apply( max, max );
		return Intervals.smallestContainingInterval( new FinalRealInterval( min, max ) );
	}

}
