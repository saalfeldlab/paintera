package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OnEnterOnExit;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.control.FitToInterval;
import org.janelia.saalfeldlab.paintera.control.Merges;
import org.janelia.saalfeldlab.paintera.control.Navigation;
import org.janelia.saalfeldlab.paintera.control.OrthogonalViewsValueDisplayListener;
import org.janelia.saalfeldlab.paintera.control.Paint;
import org.janelia.saalfeldlab.paintera.control.Selection;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.navigation.AffineTransformWithListeners;
import org.janelia.saalfeldlab.paintera.control.navigation.DisplayTransformUpdateOnResize;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.ui.Crosshair;
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs;
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX;
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
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.ObservableList;
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
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class PainteraExample extends Application
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final SimpleObjectProperty< Interpolation > interpolation = new SimpleObjectProperty<>( Interpolation.NEARESTNEIGHBOR );

	private final PainteraBaseView baseView = new PainteraBaseView( 8, si -> s -> interpolation.get() );

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

	private final CrosshairConfig crosshairConfig = new CrosshairConfig();

	private final Map< ViewerAndTransforms, Crosshair > crossHairs = Paintera.makeCrosshairs( baseView.orthogonalViews(), crosshairConfig, Color.ORANGE, Color.WHITE );

	private final Map< ViewerAndTransforms, OrthoSliceFX > orthoSlices = Paintera.makeOrthoSlices( baseView.orthogonalViews(), baseView.viewer3D().meshesGroup(), sourceInfo );

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{

		final Random rng = new Random();
		final ArrayImg< UnsignedByteType, ByteArray > data = ArrayImgs.unsignedBytes( 100, 200, 300 );
		rng.nextBytes( data.update( null ).getCurrentStorageArray() );

		final ArrayImg< UnsignedLongType, LongArray > labels = ArrayImgs.unsignedLongs( Intervals.dimensionsAsLongArray( data ) );
		Arrays.setAll( labels.update( null ).getCurrentStorageArray(), d -> d );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< UnsignedByteType >[] srcs = new RandomAccessibleInterval[] {
				data,
				Views.subsample( data, 2 )
		};

		final Function< Interpolation, InterpolatorFactory< UnsignedByteType, RandomAccessible< UnsignedByteType > > > ipol = i -> i.equals( Interpolation.NLINEAR )
				? new NLinearInterpolatorFactory<>()
				: new NearestNeighborInterpolatorFactory<>();

		final AffineTransform3D[] mipmapTransforms = {
				new AffineTransform3D(),
				new AffineTransform3D().concatenate( new Scale3D( 2.0, 2.0, 2.0 ) )
		};

		final RandomAccessibleIntervalDataSource< UnsignedByteType, UnsignedByteType > source =
				new RandomAccessibleIntervalDataSource<>( srcs, srcs, mipmapTransforms, ipol, ipol, "data" );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< UnsignedLongType >[] lsrcs = new RandomAccessibleInterval[] {
				labels
		};

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< VolatileUnsignedLongType >[] vlsrcs = new RandomAccessibleInterval[] {
				Converters.convert( lsrcs[ 0 ], ( s, t ) -> t.get().set( s ), new VolatileUnsignedLongType() )
		};

		final RandomAccessibleIntervalDataSource< UnsignedLongType, VolatileUnsignedLongType > labelSource = new RandomAccessibleIntervalDataSource<>(
				lsrcs,
				vlsrcs,
				new AffineTransform3D[] { new AffineTransform3D() },
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				"labels" );

		final MaskedSource< UnsignedLongType, VolatileUnsignedLongType > maskedSource = Masks.fromIntegerType( labelSource, ( canvas, blocks ) -> {} );

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
		final OrthogonalViewsValueDisplayListener vdl = new OrthogonalViewsValueDisplayListener( valueStatus::setText, cs, s -> interpolation.get() );
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

		sourceInfo.currentSourceProperty().set( maskedSource );

		sourceInfo.trackSources().addListener( FitToInterval.fitToIntervalWhenSourceAddedListener( baseView.manager(), baseView.orthogonalViews().topLeft().viewer().widthProperty()::get ) );

		baseView.addRawSource( source, 0, 255, Colors.toARGBType( Color.TEAL ) );
		baseView.addLabelSource( maskedSource, new FragmentSegmentAssignmentOnlyLocal( ( a, b ) -> {} ), ToIdConverter.fromIntegerType() );
		baseView.viewer3D().setInitialTransformToInterval( data );

		EventFX.KEY_PRESSED( "interpolation", e -> toggleInterpolation(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.I ) ).installInto( borderPane );
		interpolation.addListener( ( obs, oldv, newv ) -> baseView.orthogonalViews().requestRepaint() );

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
		nodes.forEach( PainteraExample::grabFocusOnMouseOver );
	}

	public static void grabFocusOnMouseOver( final Node node )
	{
		node.addEventFilter( MouseEvent.MOUSE_ENTERED, e -> node.requestFocus() );
	}

	private void toggleInterpolation()
	{
		this.interpolation.set( this.interpolation.get().equals( Interpolation.NLINEAR ) ? Interpolation.NEARESTNEIGHBOR : Interpolation.NLINEAR );
	}

}
