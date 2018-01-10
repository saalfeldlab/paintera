package bdv.bigcat.viewer.atlas;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.composite.ARGBCompositeAlphaAdd;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.ClearingCompositeProjector.ClearingCompositeProjectorFactory;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.ViewerActor;
import bdv.bigcat.viewer.atlas.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.atlas.data.ConverterDataSource;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetDataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.mode.Highlights;
import bdv.bigcat.viewer.atlas.mode.Merges;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.atlas.mode.ModeUtil;
import bdv.bigcat.viewer.atlas.mode.NavigationOnly;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState.RawSourceState;
import bdv.bigcat.viewer.atlas.source.ResizeOnLeftSide;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.atlas.source.SourceTabs;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.ortho.OrthoView;
import bdv.bigcat.viewer.ortho.OrthoViewState;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.AbstractHighlightingARGBStream;
import bdv.bigcat.viewer.stream.HighlightincConverterIntegerType;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.bigcat.viewer.viewer3d.OrthoSliceFX;
import bdv.bigcat.viewer.viewer3d.Viewer3DControllerFX;
import bdv.bigcat.viewer.viewer3d.Viewer3DFX;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.converter.Converter;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Intervals;

public class Atlas
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final BorderPane root;

	private final OrthoView view;

	VBox statusRoot = new VBox();

	private final HBox status = new HBox();

	private final Slider time = new Slider( 0, 0, 0 );

	private final BooleanProperty showTime = new SimpleBooleanProperty( true );

	private final AtlasFocusHandler focusHandler = new AtlasFocusHandler();

	private final AtlasValueDisplayListener valueDisplayListener;

	private final SourceInfo sourceInfo = new SourceInfo();

	private final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > composites = new HashMap<>();

	private final ViewerOptions viewerOptions;

	private final ArrayList< Mode > modes = new ArrayList<>();

	private final Viewer3DFX renderView;

	private final Viewer3DControllerFX controller;

	private final List< OrthoSliceFX > orthoSlices = new ArrayList<>();

	private Stage primaryStage;

	private final KeyTracker keyTracker = new KeyTracker();

	private final SimpleObjectProperty< Mode > currentMode = new SimpleObjectProperty<>();

	private final ARGBStreamSeedSetter seedSetter;

	private final SharedQueue cellCache;

	private final SourceTabs sourceTabs;

	private final ResizeOnLeftSide sourceTabsResizer;

	public Atlas( final SharedQueue cellCache )
	{
		this( ViewerOptions.options(), cellCache );
	}

	public Atlas( final ViewerOptions viewerOptions, final SharedQueue cellCache )
	{
		super();
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory( new ClearingCompositeProjectorFactory<>( composites, new ARGBType() ) )
				.numRenderingThreads( Math.min( 3, Math.max( 1, Runtime.getRuntime().availableProcessors() / 3 ) ) );
		this.view = new OrthoView( focusHandler.onEnter(), focusHandler.onExit(), new OrthoViewState( this.viewerOptions ), cellCache, keyTracker );
		this.view.setMinWidth( 100 );
		this.view.setMinHeight( 100 );
		this.sourceTabs = new SourceTabs(
				this.sourceInfo.currentSourceIndexProperty(),
				source -> {
					this.sourceInfo.removeSource( source );
					// this.view.getState().removeSource( source );
				},
				// this.view.getState().removeSource( source ),
				this.sourceInfo );
		this.sourceTabsResizer = new ResizeOnLeftSide( sourceTabs.getTabs(), sourceTabs.widthProperty(), ( diff ) -> diff > 0 && diff < 10 );
		this.view.getState().currentSourceProperty().bindBidirectional( this.sourceInfo.currentSourceProperty() );

		this.sourceInfo.trackVisibleSourcesAndConverters().addListener( ( ListChangeListener< SourceAndConverter< ? > > ) change -> {
			this.view.getState().removeAllSources();
			this.view.getState().addSources( sourceInfo.trackVisibleSourcesAndConverters() );
		} );
		this.root = new BorderPane( this.view );
		this.root.setBottom( statusRoot );
		this.statusRoot.getChildren().addAll( status, this.time );
		this.time.valueProperty().addListener( ( obs, oldv, newv ) -> this.time.setValue( ( int ) ( newv.doubleValue() + 0.5 ) ) );
		this.view.getState().timeProperty().bind( Bindings.createIntegerBinding( () -> ( int ) ( time.getValue() + 0.5 ), time.valueProperty() ) );
//		this.sourceTabs.setOrientation( Orientation.VERTICAL );
//		toggleSourcesTabs();

		this.root.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.TAB ) )
			{
				sourceInfo.incrementCurrentSourceIndex();
				event.consume();
			}
			else if ( keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.TAB ) )
			{
				sourceInfo.decrementCurrentSourceIndex();
				event.consume();
			}
		} );

		this.time.visibleProperty().bind( this.time.minProperty().isEqualTo( this.time.maxProperty() ).not().and( this.showTime ) );
		this.view.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( this.keyTracker.areOnlyTheseKeysDown( KeyCode.N ) )
			{
				this.time.setValue( this.time.getValue() - 1 );
				event.consume();
			}
		} );
		this.view.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( this.keyTracker.areOnlyTheseKeysDown( KeyCode.M ) )
			{
				this.time.setValue( this.time.getValue() + 1 );
				event.consume();
			}
		} );

		this.root.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( this.keyTracker.areOnlyTheseKeysDown( KeyCode.V ) )
			{
				final Source< ? > currentSource = this.sourceInfo.currentSourceProperty().get();
				if ( currentSource != null )
				{
					final BooleanProperty visibleProperty = this.sourceInfo.getState( currentSource ).visibleProperty();
					visibleProperty.set( !visibleProperty.get() );
				}
			}
		} );

		this.cellCache = cellCache;

		this.renderView = new Viewer3DFX( 100, 100 );
		this.controller = new Viewer3DControllerFX( renderView );
		this.view.setInfoNode( renderView );
		this.renderView.scene().addEventHandler( MouseEvent.MOUSE_CLICKED, event -> renderView.scene().requestFocus() );

		final Mode[] initialModes = { new NavigationOnly(), new Highlights( controller, baseView().getState().transformManager(), sourceInfo, keyTracker ), new Merges( sourceInfo ) };
		Arrays.stream( initialModes ).forEach( modes::add );

		for ( final Mode mode : modes )
			addOnEnterOnExit( mode.onEnter(), mode.onExit(), true );

		final ComboBox< Mode > modeSelector = ModeUtil.comboBox( modes );
		this.currentMode.bind( modeSelector.valueProperty() );
		modeSelector.setPromptText( "Mode" );
		this.status.getChildren().add( modeSelector );
		modeSelector.getSelectionModel().select( initialModes[ 2 ] );

		final Label coordinates = new Label();
		final AtlasMouseCoordinatePrinter coordinatePrinter = new AtlasMouseCoordinatePrinter( coordinates );
		this.status.getChildren().add( coordinates );
		addOnEnterOnExit( coordinatePrinter.onEnter(), coordinatePrinter.onExit(), true );

		final Label label = new Label();
		valueDisplayListener = new AtlasValueDisplayListener( label, sourceInfo.currentSourceProperty(), sourceInfo.currentSourceIndexInVisibleSources() );
		this.status.getChildren().add( label );

		addOnEnterOnExit( valueDisplayListener.onEnter(), valueDisplayListener.onExit(), true );

		this.seedSetter = new ARGBStreamSeedSetter( sourceInfo, keyTracker, currentMode );
		addOnEnterOnExit( this.seedSetter.onEnter(), this.seedSetter.onEnter(), true );

		for ( final Node child : this.baseView().getChildren() )
			if ( child instanceof ViewerNode )
			{
				final ViewerNode vn = ( ViewerNode ) child;
				final OrthoSliceFX orthoSlice = new OrthoSliceFX( renderView.meshesGroup(), vn.getViewer(), sourceInfo );
				orthoSlices.add( orthoSlice );
				orthoSlice.toggleVisibility();
			}

		this.baseView().addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( event.getCode().equals( KeyCode.O ) && event.isShiftDown() && !event.isAltDown() && !event.isControlDown() )
				orthoSlices.forEach( OrthoSliceFX::toggleVisibility );
		} );

		this.root.sceneProperty().addListener( ( obs, oldv, newv ) -> {
			if ( oldv != null )
				this.keyTracker.removeFrom( oldv );
			if ( newv != null )
				this.keyTracker.installInto( newv );
		} );

		this.root.addEventHandler( KeyEvent.KEY_PRESSED, new OpenDialogEventHandler( this, cellCache, e -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.O ) ) );

		this.root.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( keyTracker.areOnlyTheseKeysDown( KeyCode.ALT, KeyCode.S ) )
			{
				toggleSourcesTabs();
				event.consume();
			}
		} );

	}

	public void toggleSourcesTabs()
	{
		if ( this.root.getRight() == null )
		{
			this.root.setRight( this.sourceTabs.getTabs() );
			this.sourceTabsResizer.install();
		}
		else
		{
			this.sourceTabsResizer.remove();
			this.root.setRight( null );
		}
	}

	public void start( final Stage primaryStage ) throws InterruptedException
	{
		this.primaryStage = primaryStage;
		start( primaryStage, "ATLAS" );
	}

	public void start( final Stage primaryStage, final String title ) throws InterruptedException
	{

		final Scene scene = new Scene( this.root, 800, 600 );

		primaryStage.setTitle( title );
		primaryStage.setScene( scene );
		primaryStage.sizeToScene();

		primaryStage.setOnCloseRequest( confirmCloseEventHandler );

		final Button closeButton = new Button( "Close BigCat" );
		closeButton.setOnAction( event -> primaryStage.fireEvent(
				new WindowEvent(
						primaryStage,
						WindowEvent.WINDOW_CLOSE_REQUEST ) ) );

		primaryStage.show();

	}

	private final EventHandler< WindowEvent > confirmCloseEventHandler = event -> {
		final Alert closeConfirmation = new Alert(
				Alert.AlertType.CONFIRMATION,
				"Are you sure you want to exit?" );
		final Button exitButton = ( Button ) closeConfirmation.getDialogPane().lookupButton(
				ButtonType.OK );
		exitButton.setText( "Exit" );
		closeConfirmation.setHeaderText( "Confirm Exit" );
		closeConfirmation.initModality( Modality.APPLICATION_MODAL );
		closeConfirmation.initOwner( primaryStage );

		final Optional< ButtonType > closeResponse = closeConfirmation.showAndWait();
		if ( !ButtonType.OK.equals( closeResponse.get() ) )
			event.consume();
		else
			exitButton.setOnAction(
					e -> primaryStage.fireEvent(
							new WindowEvent(
									primaryStage,
									WindowEvent.WINDOW_CLOSE_REQUEST ) ) );
	};

	public void addOnEnterOnExit( final Consumer< ViewerPanelFX > onEnter, final Consumer< ViewerPanelFX > onExit, final boolean onExitRemovable )
	{
		this.addOnEnterOnExit( new OnEnterOnExit( onEnter, onExit ), onExitRemovable );
	}

	public void addOnEnterOnExit( final OnEnterOnExit onEnterOnExit, final boolean onExitRemovable )
	{
		this.focusHandler.add( onEnterOnExit, onExitRemovable );
	}

	private < T, U, V > void addSource( final Source< ? > src, final Composite< ARGBType, ARGBType > comp, final int tMin, final int tMax )
	{
		if ( sourceInfo.numSources() == 0 )
		{
			final double[] min = Arrays.stream( Intervals.minAsLongArray( src.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
			final double[] max = Arrays.stream( Intervals.maxAsLongArray( src.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
			final AffineTransform3D affine = new AffineTransform3D();
			src.getSourceTransform( 0, 0, affine );
			affine.apply( min, min );
			affine.apply( max, max );
			final FinalInterval interval = new FinalInterval( Arrays.stream( min ).mapToLong( Math::round ).toArray(), Arrays.stream( max ).mapToLong( Math::round ).toArray() );
			centerForInterval( interval );
			this.time.setValue( tMin );
		}
		this.composites.put( src, comp );
//		this.baseView().getState().addSource( src );
		this.time.setMin( Math.min( tMin, this.time.getMin() ) );
		this.time.setMax( Math.max( tMax, this.time.getMax() ) );
	}

	public < T, VT > void removeSource( final DataSource< T, VT > spec )
	{
		this.composites.remove( spec );
		this.sourceInfo.removeSource( spec );
	}

	public void addLabelSource( final LabelDataSource< LabelMultisetType, VolatileLabelMultisetType > spec )
	{
		final FragmentSegmentAssignmentState< ? > assignment = spec.getAssignment();
		final CurrentModeConverter< VolatileLabelMultisetType > converter = new CurrentModeConverter<>();
		final HashMap< Mode, SelectedIds > selIdsMap = new HashMap<>();
		final HashMap< Mode, ARGBStream > streamsMap = new HashMap<>();
		for ( final Mode mode : this.modes )
		{
			final SelectedIds selId = new SelectedIds();
			selIdsMap.put( mode, selId );
			final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment );
			stream.addListener( () -> baseView().requestRepaint() );
			streamsMap.put( mode, stream );
		}
		final DataSource< LabelMultisetType, VolatileARGBType > vsource = new ConverterDataSource<>(
				spec,
				converter,
				method -> method.equals( Interpolation.NLINEAR ) ? new ClampingNLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				new VolatileARGBType( 0 ) );
		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();

		final Consumer< Mode > setConverter = mode -> {
			final AbstractHighlightingARGBStream argbStream = ( AbstractHighlightingARGBStream ) sourceInfo.stream( vsource, mode ).get();
			converter.setConverter( new HDF5LabelMultisetDataSource.HighlightingStreamConverter( argbStream ) );
			baseView().requestRepaint();
		};

		currentMode.addListener( ( obs, oldv, newv ) -> {
			Optional.ofNullable( newv ).ifPresent( setConverter::accept );
		} );

		addSource( vsource, comp, spec.tMin(), spec.tMax() );
		sourceInfo.addLabelSource(
				vsource,
				ToIdConverter.fromLabelMultisetType(),
				( Function< LabelMultisetType, Converter< LabelMultisetType, BoolType > > ) sel -> createBoolConverter( sel, assignment ),
				( FragmentSegmentAssignmentState ) assignment,
				streamsMap,
				selIdsMap,
				( s, t ) -> t.set( s.get() ) );
		Optional.ofNullable( currentMode.get() ).ifPresent( setConverter::accept );

		final LabelMultisetType t = vsource.getDataType();
		final Function< LabelMultisetType, String > valueToString = valueToString( t );
		final AffineTransform3D affine = new AffineTransform3D();
		vsource.getSourceTransform( 0, 0, affine );
		this.valueDisplayListener.addSource( vsource, Optional.of( valueToString ) );

		view.addActor( new ViewerActor()
		{

			@Override
			public Consumer< ViewerPanelFX > onRemove()
			{
				return vp -> {};
			}

			@Override
			public Consumer< ViewerPanelFX > onAdd()
			{
				return vp -> assignment.addListener( () -> vp.requestRepaint() );
			}
		} );

		view.addActor( new ViewerActor()
		{
			@Override
			public Consumer< ViewerPanelFX > onRemove()
			{
				return vp -> {};
			}

			@Override
			public Consumer< ViewerPanelFX > onAdd()
			{
				return vp -> {
					selIdsMap.values().forEach( ids -> ids.addListener( () -> vp.requestRepaint() ) );
				};
			}
		} );

	}

	// TODO Is there a better bound for V than AbstractVolatileRealType? V
	// extends Volatile< I > & IntegerType< V > did not work with
	// VolatileUnsignedLongType
	public < I extends IntegerType< I >, V extends AbstractVolatileRealType< I, V > > void addLabelSource( final LabelDataSource< I, V > spec, final ToLongFunction< V > toLong )
	{
		final FragmentSegmentAssignmentState< ? > assignment = spec.getAssignment();
		final CurrentModeConverter< V > converter = new CurrentModeConverter<>();
		final HashMap< Mode, SelectedIds > selIdsMap = new HashMap<>();
		final HashMap< Mode, ARGBStream > streamsMap = new HashMap<>();
		for ( final Mode mode : this.modes )
		{
			final SelectedIds selId = new SelectedIds();
			selIdsMap.put( mode, selId );
			final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment );
			stream.addListener( () -> baseView().requestRepaint() );
			streamsMap.put( mode, stream );
		}
		final DataSource< I, VolatileARGBType > vsource = new ConverterDataSource<>(
				spec,
				converter,
				method -> method.equals( Interpolation.NLINEAR ) ? new ClampingNLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				new VolatileARGBType( 0 ) );
		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();

		final Consumer< Mode > setConverter = mode -> {
			sourceInfo.stream( vsource, mode ).ifPresent( stream -> {
				final AbstractHighlightingARGBStream argbStream = ( AbstractHighlightingARGBStream ) stream;
				final HighlightincConverterIntegerType< V > conv = new HighlightincConverterIntegerType<>( argbStream, toLong );
				converter.setConverter( conv );
				baseView().requestRepaint();
			} );
		};

		currentMode.addListener( ( obs, oldv, newv ) -> {
			Optional.ofNullable( newv ).ifPresent( setConverter::accept );
		} );
		addSource( vsource, comp, spec.tMin(), spec.tMax() );
		sourceInfo.addLabelSource(
				vsource,
				spec.getDataType() instanceof IntegerType ? ToIdConverter.fromIntegerType() : ToIdConverter.fromRealType(),
				( Function< I, Converter< I, BoolType > > ) sel -> createBoolConverter( sel, assignment ),
				( FragmentSegmentAssignmentState ) assignment,
				streamsMap,
				selIdsMap,
				( s, t ) -> {
					t.set( s.get() );
				} );
		Optional.ofNullable( currentMode.get() ).ifPresent( setConverter::accept );

		final I t = vsource.getDataType();
		final Function< I, String > valueToString = valueToString( t );
		final AffineTransform3D affine = new AffineTransform3D();
		vsource.getSourceTransform( 0, 0, affine );
		this.valueDisplayListener.addSource( vsource, Optional.of( valueToString ) );

		view.addActor( new ViewerActor()
		{

			@Override
			public Consumer< ViewerPanelFX > onRemove()
			{
				return vp -> {};
			}

			@Override
			public Consumer< ViewerPanelFX > onAdd()
			{
				return vp -> assignment.addListener( () -> vp.requestRepaint() );
			}
		} );

		view.addActor( new ViewerActor()
		{
			@Override
			public Consumer< ViewerPanelFX > onRemove()
			{
				return vp -> {};
			}

			@Override
			public Consumer< ViewerPanelFX > onAdd()
			{
				return vp -> {
					selIdsMap.values().forEach( ids -> ids.addListener( () -> vp.requestRepaint() ) );
				};
			}
		} );

	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSources(
			final Collection< ? extends DataSource< T, U > > specs,
			final double min,
			final double max )
	{
		final int numSources = specs.size();
		final double factor = 360.0 / numSources;
		if ( numSources == 1 )
			addRawSource( specs.iterator().next(), min, max );
		else
		{
			final List< ARGBType > colors = IntStream
					.range( 0, numSources )
					.mapToDouble( i -> i * factor )
					.mapToObj( hue -> Color.hsb( 60 + hue, 1.0, 1.0, 1.0 ) )
					.map( Atlas::toARGBType )
					.collect( Collectors.toList() );
			addRawSources( specs, colors, DoubleStream.generate( () -> min ).limit( numSources ).toArray(), DoubleStream.generate( () -> max ).limit( numSources ).toArray() );
		}
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSources(
			final Collection< ? extends DataSource< T, U > > specs,
			final Collection< ARGBType > colors,
			final double[] min,
			final double[] max )
	{
		final Iterator< ? extends DataSource< T, U > > specIt = specs.iterator();
		final Iterator< ARGBType > colorIt = colors.iterator();
		for ( int i = 0; specIt.hasNext(); ++i )
			addRawSource( specIt.next(), min[ i ], max[ i ], colorIt.next() );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource( final DataSource< T, U > spec, final double min, final double max )
	{
		addRawSource( spec, min, max, new ARGBType( 0xffffffff ) );
	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource( final DataSource< T, U > spec, final double min, final double max, final ARGBType color )
	{
		final Composite< ARGBType, ARGBType > comp = new ARGBCompositeAlphaAdd();
		addSource( spec, comp, spec.tMin(), spec.tMax() );

		final Color colorFX = Color.rgb( ARGBType.red( color.get() ), ARGBType.green( color.get() ), ARGBType.blue( color.get() ), ARGBType.alpha( color.get() ) / 255.0 );
		final RawSourceState< U, T > state = sourceInfo.addRawSource( spec, min, max, colorFX );
		final T t = spec.getDataType();
		final Function< T, String > valueToString = valueToString( t );
		this.valueDisplayListener.addSource( spec, Optional.of( valueToString ) );
		state.colorProperty().addListener( ( obs, oldv, newv ) -> baseView().requestRepaint() );
		state.minProperty().addListener( ( obs, oldv, newv ) -> baseView().requestRepaint() );
		state.maxProperty().addListener( ( obs, oldv, newv ) -> baseView().requestRepaint() );
	}

	public OrthoView baseView()
	{
		return this.view;
	}

	public BorderPane root()
	{
		return this.root;
	}

	public static < T > Function< T, String > valueToString( final T t )
	{
		final Function< T, String > valueToString;
		if ( t instanceof ARGBType )
			valueToString = ( Function< T, String > ) Object::toString;
		else if ( t instanceof IntegerType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%d", ( ( IntegerType< ? > ) rt ).getIntegerLong() );
		else if ( t instanceof RealType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%.3f", ( ( RealType< ? > ) rt ).getRealDouble() );
		else if ( t instanceof LabelMultisetType )
			valueToString = ( Function< T, String > ) rt -> {
				final StringBuilder sb = new StringBuilder( "{" );
				final Iterator< Entry< bdv.labels.labelset.Label > > it = ( ( LabelMultisetType ) rt ).entrySet().iterator();
				if ( it.hasNext() )
				{
					final Entry< bdv.labels.labelset.Label > entry = it.next();
					sb.append( entry.getElement().id() ).append( ":" ).append( entry.getCount() );
				}
				while ( it.hasNext() )
				{
					final Entry< bdv.labels.labelset.Label > entry = it.next();
					sb.append( " " ).append( entry.getElement().id() ).append( ":" ).append( entry.getCount() );
				}
				sb.append( "}" );
				return sb.toString();
			};
		else
			valueToString = rt -> "Do not understand type!";
		return valueToString;
	}

	public void setTransform( final AffineTransform3D transform )
	{
		this.baseView().setTransform( transform );
	}

	public static Converter< LabelMultisetType, BoolType > createBoolConverter( final LabelMultisetType selection, final FragmentSegmentAssignmentState< ? > assignment )
	{
		final long id = maxCountId( selection );
		final long segmentId = assignment.getSegment( id );
		return ( s, t ) -> t.set( assignment.getSegment( maxCountId( s ) ) == segmentId );
	}

	public static < T extends RealType< T > > Converter< T, BoolType > createBoolConverter( final T selection, final FragmentSegmentAssignmentState< ? > assignment )
	{
		final boolean isInteger = selection instanceof IntegerType< ? >;
		final long id = isInteger ? ( ( IntegerType< ? > ) selection ).getIntegerLong() : ( long ) selection.getRealDouble();
		final long segmentId = assignment.getSegment( id );
		return isInteger ? ( Converter< T, BoolType > ) ( Converter< IntegerType< ? >, BoolType > ) ( s, t ) -> t.set( assignment.getSegment( s.getIntegerLong() ) == segmentId ) : ( s, t ) -> t.set( assignment.getSegment( ( long ) s.getRealDouble() ) == segmentId );

	}

	public static long maxCountId( final LabelMultisetType t )
	{
		long argMaxLabel = bdv.labels.labelset.Label.INVALID;
		long argMaxCount = 0;
		for ( final Entry< bdv.labels.labelset.Label > entry : t.entrySet() )
		{
			final int count = entry.getCount();
			if ( count > argMaxCount )
			{
				argMaxCount = count;
				argMaxLabel = entry.getElement().id();
			}
		}
		return argMaxLabel;
	}

	public void centerForInterval( final Interval interval )

	{
		final AffineTransform3D tf = new AffineTransform3D();
		final long[] sums = {
				interval.max( 0 ) + interval.min( 0 ),
				interval.max( 1 ) + interval.min( 1 ),
				interval.max( 2 ) + interval.min( 2 )
		};
		tf.translate( Arrays.stream( sums ).mapToDouble( sum -> -0.5 * sum ).toArray() );
		final ViewerNode vn = this.baseView().getChildren().stream().filter( child -> child instanceof ViewerNode ).map( n -> ( ViewerNode ) n ).findFirst().get();
		vn.manager().setCanvasSize( 1, 1, true );
		tf.scale( 1.0 / interval.dimension( 0 ) );
		vn.manager().setCanvasSize( ( int ) vn.getWidth(), ( int ) vn.getHeight(), true );
		this.baseView().setTransform( tf );
		this.renderView.setInitialTransformToInterval( interval );
	}

	private static < T extends RealType< T > > double getTypeMin( final T t )
	{
		if ( t instanceof IntegerType< ? > )
			return t.getMinValue();
		else if ( t instanceof AbstractVolatileRealType< ?, ? > && ( ( AbstractVolatileRealType< ?, ? > ) t ).get() instanceof IntegerType< ? > )
			return t.getMinValue();
		return 0.0;
	}

	private static < T extends RealType< T > > double getTypeMax( final T t )
	{
		if ( t instanceof IntegerType< ? > )
			return t.getMaxValue();
		else if ( t instanceof AbstractVolatileRealType< ?, ? > && ( ( AbstractVolatileRealType< ?, ? > ) t ).get() instanceof IntegerType< ? > )
			return t.getMaxValue();
		return 1.0;
	}

	private static ARGBType toARGBType( final Color color )
	{
		return toARGBType( color, new ARGBType() );
	}

	private static ARGBType toARGBType( final Color color, final ARGBType argb )
	{
		final int r = ( int ) ( 255 * color.getRed() + 0.5 );
		final int g = ( int ) ( 255 * color.getGreen() + 0.5 );
		final int b = ( int ) ( 255 * color.getBlue() + 0.5 );
		final int a = ( int ) ( 255 * color.getOpacity() + 0.5 );
		argb.set( a << 24 | r << 16 | g << 8 | b << 0 );
		return argb;
	}

	private static Converter< VolatileARGBType, ARGBType > identity()
	{
		return ( s, t ) -> t.set( s.get() );
	}
}
