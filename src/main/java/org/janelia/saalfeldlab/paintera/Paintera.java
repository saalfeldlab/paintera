package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.GridResizer;
import org.janelia.saalfeldlab.fx.ortho.OnEnterOnExit;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfigNode;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigNode;
import org.janelia.saalfeldlab.paintera.control.FitToInterval;
import org.janelia.saalfeldlab.paintera.control.Merges;
import org.janelia.saalfeldlab.paintera.control.Navigation;
import org.janelia.saalfeldlab.paintera.control.OrthogonalViewsValueDisplayListener;
import org.janelia.saalfeldlab.paintera.control.Paint;
import org.janelia.saalfeldlab.paintera.control.RunWhenFirstElementIsAdded;
import org.janelia.saalfeldlab.paintera.control.Selection;
import org.janelia.saalfeldlab.paintera.control.navigation.AffineTransformWithListeners;
import org.janelia.saalfeldlab.paintera.control.navigation.DisplayTransformUpdateOnResize;
import org.janelia.saalfeldlab.paintera.ui.ARGBStreamSeedSetter;
import org.janelia.saalfeldlab.paintera.ui.Crosshair;
import org.janelia.saalfeldlab.paintera.ui.ToggleMaximize;
import org.janelia.saalfeldlab.paintera.ui.opendialog.PainteraOpenDialogEventHandler;
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs;
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.MultiBoxOverlayRendererFX;
import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerBinding;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
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

	private final PainteraBaseView baseView = new PainteraBaseView(
			Math.min( 8, Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) ),
			si -> s -> si.getState( s ).interpolationProperty().get() );

	private final OrthogonalViews< Viewer3DFX > orthoViews = baseView.orthogonalViews();

	private final SourceInfo sourceInfo = baseView.sourceInfo();

	private final KeyTracker keyTracker = new KeyTracker();

	private final IntegerBinding numSources = Bindings.size( sourceInfo.trackSources() );

	private final BooleanBinding hasSources = numSources.greaterThan( 0 );

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

	private final CrosshairConfig crosshairConfig = new CrosshairConfig();

	private final Map< ViewerAndTransforms, Crosshair > crossHairs = makeCrosshairs(
			baseView.orthogonalViews(),
			crosshairConfig,
			Colors.CREMI,
			Color.WHITE.deriveColor( 0, 1, 1, 0.5 ) );

	private final Map< ViewerAndTransforms, OrthoSliceFX > orthoSlices = makeOrthoSlices( baseView.orthogonalViews(), baseView.viewer3D().meshesGroup(), sourceInfo );

	private final OrthoSliceConfig orthoSliceConfig = new OrthoSliceConfig(
			orthoSlices.get( baseView.orthogonalViews().topLeft() ),
			orthoSlices.get( baseView.orthogonalViews().topRight() ),
			orthoSlices.get( baseView.orthogonalViews().bottomLeft() ),
			hasSources );

	private final PainteraOpenDialogEventHandler openDialogHandler = new PainteraOpenDialogEventHandler(
			baseView,
			baseView.orthogonalViews().sharedQueue(),
			e -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.O ) );

	private final ToggleMaximize toggleMaximizeTopLeft = new ToggleMaximize(
			orthoViews.topLeft().viewer(),
			orthoViews.pane().getChildren(),
			orthoViews.grid().constraintsManager(),
			0,
			0 );

	private final ToggleMaximize toggleMaximizeTopRight = new ToggleMaximize(
			orthoViews.topRight().viewer(),
			orthoViews.pane().getChildren(),
			orthoViews.grid().constraintsManager(),
			1,
			0 );

	private final ToggleMaximize toggleMaximizeBottomLeft = new ToggleMaximize(
			orthoViews.bottomLeft().viewer(),
			orthoViews.pane().getChildren(),
			orthoViews.grid().constraintsManager(),
			0,
			1 );

	private final ToggleMaximize toggleMaximizeBottomRight = new ToggleMaximize(
			baseView.viewer3D(),
			orthoViews.pane().getChildren(),
			orthoViews.grid().constraintsManager(),
			1,
			1 );

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

		onEnterOnExit.accept( new ARGBStreamSeedSetter( sourceInfo, keyTracker ).onEnterOnExit() );

		final Stage stage = new Stage();
		final Scene scene = new Scene( borderPane );

		stage.addEventFilter( WindowEvent.WINDOW_CLOSE_REQUEST, event -> viewerToTransforms.keySet().forEach( ViewerPanelFX::stop ) );

		Platform.setImplicitExit( true );

		final SourceTabs sourceTabs = new SourceTabs(
				sourceInfo.currentSourceIndexProperty(),
				sourceInfo::removeSource,
				sourceInfo );

		final TitledPane sourcesContents = new TitledPane( "sources", sourceTabs.get() );
		sourcesContents.setExpanded( false );

		final VBox settingsContents = new VBox(
				new CrosshairConfigNode( crosshairConfig ).getContents(),
				new OrthoSliceConfigNode( orthoSliceConfig ).getContents() );
		final TitledPane settings = new TitledPane( "settings", settingsContents );
		settings.setExpanded( false );

		final ScrollPane sideBar = new ScrollPane( new VBox( sourcesContents, settings ) );
		sideBar.setHbarPolicy( ScrollBarPolicy.NEVER );
		sideBar.setVbarPolicy( ScrollBarPolicy.AS_NEEDED );
		sideBar.setVisible( true );
		sideBar.prefWidthProperty().set( 250 );
		sourceTabs.widthProperty().bind( sideBar.prefWidthProperty() );
		settingsContents.prefWidthProperty().bind( sideBar.prefWidthProperty() );

		final ResizeOnLeftSide resizeSideBar = new ResizeOnLeftSide( sideBar, sideBar.prefWidthProperty(), dist -> Math.abs( dist ) < 5 );

		scene.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( keyTracker.areOnlyTheseKeysDown( KeyCode.P ) )
			{
				if ( borderPane.getRight() == null )
				{
					borderPane.setRight( sideBar );
					resizeSideBar.install();
				}
				else
				{
					resizeSideBar.remove();
					borderPane.setRight( null );
				}
				event.consume();
			}
		} );

		sourceInfo.trackSources().addListener( FitToInterval.fitToIntervalWhenSourceAddedListener( baseView.manager(), baseView.orthogonalViews().topLeft().viewer().widthProperty()::get ) );
		sourceInfo.trackSources().addListener( new RunWhenFirstElementIsAdded<>( c -> baseView.viewer3D().setInitialTransformToInterval( sourceIntervalInWorldSpace( c.getAddedSubList().get( 0 ) ) ) ) );

		EventFX.KEY_PRESSED( "toggle interpolation", e -> toggleInterpolation(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.I ) ).installInto( borderPane );
		EventFX.KEY_PRESSED( "cycle current source", e -> sourceInfo.incrementCurrentSourceIndex(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.TAB ) ).installInto( borderPane );
		EventFX.KEY_PRESSED( "backwards cycle current source", e -> sourceInfo.decrementCurrentSourceIndex(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.TAB ) ).installInto( borderPane );

		scene.addEventHandler( KeyEvent.KEY_PRESSED, openDialogHandler );

		final GridResizer resizer = new GridResizer( baseView.orthogonalViews().grid().constraintsManager(), 5, baseView.pane(), keyTracker );
		baseView.pane().addEventFilter( MouseEvent.MOUSE_MOVED, resizer.onMouseMovedHandler() );
		baseView.pane().addEventFilter( MouseEvent.MOUSE_CLICKED, resizer.onMouseDoubleClickedHandler() );
		baseView.pane().addEventFilter( MouseEvent.MOUSE_DRAGGED, resizer.onMouseDraggedHandler() );
		baseView.pane().addEventFilter( MouseEvent.MOUSE_PRESSED, resizer.onMousePressedHandler() );
		baseView.pane().addEventFilter( MouseEvent.MOUSE_RELEASED, resizer.onMouseReleased() );

		EventFX.KEY_PRESSED( "maximize", e -> toggleMaximizeTopLeft.toggleFullScreen(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.F ) ).installInto( orthoViews.topLeft().viewer() );
		EventFX.KEY_PRESSED( "maximize", e -> toggleMaximizeTopRight.toggleFullScreen(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.F ) ).installInto( orthoViews.topRight().viewer() );
		EventFX.KEY_PRESSED( "maximize", e -> toggleMaximizeBottomLeft.toggleFullScreen(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.F ) ).installInto( orthoViews.bottomLeft().viewer() );
		EventFX.KEY_PRESSED( "maximize", e -> toggleMaximizeBottomRight.toggleFullScreen(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.F ) ).installInto( baseView.viewer3D() );

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

	public static Map< ViewerAndTransforms, Crosshair > makeCrosshairs(
			final OrthogonalViews< ? > views,
			final CrosshairConfig config,
			final Color onFocusColor,
			final Color offFocusColor )
	{
		final Map< ViewerAndTransforms, Crosshair > map = new HashMap<>();
		map.put( views.topLeft(), makeCrossHairForViewer( views.topLeft().viewer(), config, onFocusColor, offFocusColor ) );
		map.put( views.topRight(), makeCrossHairForViewer( views.topRight().viewer(), config, onFocusColor, offFocusColor ) );
		map.put( views.bottomLeft(), makeCrossHairForViewer( views.bottomLeft().viewer(), config, onFocusColor, offFocusColor ) );
		config.setOnFocusColor( onFocusColor );
		config.setOutOfFocusColor( offFocusColor );
		return map;
	}

	public static Crosshair makeCrossHairForViewer(
			final ViewerPanelFX viewer,
			final CrosshairConfig config,
			final Color onFocusColor,
			final Color offFocusColor )
	{
		final Crosshair ch = new Crosshair();
		viewer.getDisplay().addOverlayRenderer( ch );
		ch.regularColorProperty().bind( config.outOfFocusColorProperty() );
		ch.highlightColorProperty().bind( config.onFocusColorProperty() );
		ch.wasChangedProperty().addListener( ( obs, oldv, newv ) -> viewer.getDisplay().drawOverlays() );
		ch.isVisibleProperty().bind( config.showCrosshairsProperty() );
		ch.isHighlightProperty().bind( viewer.focusedProperty() );
		return ch;
	}

	public static Map< ViewerAndTransforms, OrthoSliceFX > makeOrthoSlices(
			final OrthogonalViews< ? > views,
			final Group scene,
			final SourceInfo sourceInfo )
	{
		final Map< ViewerAndTransforms, OrthoSliceFX > map = new HashMap<>();
		map.put( views.topLeft(), new OrthoSliceFX( scene, views.topLeft().viewer() ) );
		map.put( views.topRight(), new OrthoSliceFX( scene, views.topRight().viewer() ) );
		map.put( views.bottomLeft(), new OrthoSliceFX( scene, views.bottomLeft().viewer() ) );
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
