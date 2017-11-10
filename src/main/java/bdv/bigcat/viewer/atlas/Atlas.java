package bdv.bigcat.viewer.atlas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.ortho.OrthoView;
import bdv.bigcat.viewer.ortho.OrthoViewState;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.AbstractHighlightingARGBStream;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.bigcat.viewer.viewer3d.OrthoSliceFX;
import bdv.bigcat.viewer.viewer3d.Viewer3DControllerFX;
import bdv.bigcat.viewer.viewer3d.Viewer3DFX;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class Atlas
{

	private final BorderPane root;

	private final OrthoView view;

	private final HBox status = new HBox();

	private final AtlasFocusHandler focusHandler = new AtlasFocusHandler();

	private final AtlasValueDisplayListener valueDisplayListener;

	private final SourceInfo sourceInfo = new SourceInfo();

	private final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > composites = new HashMap<>();

	private final ViewerOptions viewerOptions;

//	private final Mode[] modes = { new NavigationOnly(), new Highlights( selectedIds ), new Merges( selectedIds, assignments ) };

	private final ArrayList< Mode > modes = new ArrayList<>();

//	private final SourcesTab sourcesTab = new SourcesTab( specs );

	private final Viewer3DFX renderView;

	private final Viewer3DControllerFX controller;

	private final List< OrthoSliceFX > orthoSlices = new ArrayList<>();

	private Stage primaryStage;

	private final KeyTracker keyTracker = new KeyTracker();

	private final SimpleObjectProperty< Mode > currentMode = new SimpleObjectProperty<>();

	public Atlas( final Interval interval, final VolatileGlobalCellCache cellCache )
	{
		this( ViewerOptions.options(), interval, cellCache );
	}

	public Atlas( final ViewerOptions viewerOptions, final Interval interval, final VolatileGlobalCellCache cellCache )
	{
		super();
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory( new ClearingCompositeProjectorFactory<>( composites, new ARGBType() ) )
				.numRenderingThreads( Math.min( 3, Math.max( 1, Runtime.getRuntime().availableProcessors() / 3 ) ) );
		this.view = new OrthoView( focusHandler.onEnter(), focusHandler.onExit(), new OrthoViewState( this.viewerOptions ), cellCache, keyTracker );
		this.root = new BorderPane( this.view );
		this.root.setBottom( status );
//		this.root.setRight( sourcesTab );
//		this.view.heightProperty().addListener( ( ChangeListener< Number > ) ( observable, old, newVal ) -> {
//			this.sourcesTab.prefHeightProperty().set( newVal.doubleValue() );
//		} );
//		this.sourcesTab.listen( this.specs );

//		this.view.addCurrentSourceListener( ( observable, oldValue, newValue ) -> {
//			final Optional< SourceState< ?, ?, ? > > state = specs.getState( newValue );
//			if ( state.isPresent() )
//				specs.selectedSourceProperty().setValue( Optional.of( state.get().spec() ) );
//		} );

		this.renderView = new Viewer3DFX( 100, 100, interval );
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
		valueDisplayListener = new AtlasValueDisplayListener( label );
		this.status.getChildren().add( label );

//		final AtlasMouseCoordinatePrinter mcp = new AtlasMouseCoordinatePrinter( this.status );
//		addOnEnterOnExit( mcp.onEnter(), mcp.onExit(), true );
		addOnEnterOnExit( valueDisplayListener.onEnter(), valueDisplayListener.onExit(), true );
//		this.specs.addVisibilityChangedListener( () -> {
//			final List< SourceState< ?, ?, ? > > states = this.specs.sourceStates();
//			final List< SourceAndConverter< ? > > onlyVisible = states.stream().filter( SourceState::isVisible ).map( SourceState::sourceAndConverter ).collect( Collectors.toList() );
//			this.baseView().getState().removeAllSources();
//			this.baseView().getState().addSources( onlyVisible );
//		} );

//		this.specs.addListChangeListener( ( ListChangeListener< Specs.SourceState< ?, ?, ? > > ) c -> {
//			while ( c.next() )
//				if ( c.wasRemoved() )
//					for ( final SourceState< ?, ?, ? > removed : c.getRemoved() )
//					{
//						final Source< ? > source = removed.source();
//						this.sourceInfo.removeSource( source );
//						this.composites.remove( source );
//						this.baseView().getState().removeSource( source );
//					}
//				else if ( c.wasAdded() )
//					this.baseView().getState().addSources( c.getAddedSubList().stream().map( Specs.SourceState::sourceAndConverter ).collect( Collectors.toList() ) );
//		} );

//		this.specs.addListener( () -> {
//			this.baseView().removeAllSources();
//			final List< SourceAndConverter< ? > > sacs = this.specs.getSourceAndConverters();
//			this.baseView().addSource( sacs.get( 0 ) );
//		} );

//		this.baseView().addEventHandler( KeyEvent.KEY_PRESSED, event -> {
//			if ( !event.isConsumed() && event.isAltDown() && event.getCode().equals( KeyCode.S ) )
//			{
//				toggleSourcesTable();
//				event.consume();
//			}
//		} );

		{
			final AffineTransform3D tf = new AffineTransform3D();
			final long[] sums = {
					interval.max( 0 ) + interval.min( 0 ),
					interval.max( 1 ) + interval.min( 1 ),
					interval.max( 2 ) + interval.min( 2 )
			};
			tf.translate( Arrays.stream( sums ).mapToDouble( sum -> -0.5 * sum ).toArray() );
			final ViewerNode vn = this.baseView().getChildren().stream().filter( child -> child instanceof ViewerNode ).map( n -> ( ViewerNode ) n ).findFirst().get();
			final double w = vn.getWidth();
			vn.manager().setCanvasSize( 1, 1, true );
			System.out.println( w + " " + interval.dimension( 0 ) );
			tf.scale( 1.0 / interval.dimension( 0 ) );
			vn.manager().setCanvasSize( ( int ) vn.getWidth(), ( int ) vn.getHeight(), true );
			this.baseView().setTransform( tf );
		}

//		this.baseView().getState().addCurrentSourceListener( ( observable, oldValue, newValue ) -> {
//			if ( newValue.isPresent() )
//			{
//				final Optional< SourceState< ?, ?, ? > > spec = this.specs.getState( newValue.get() );
//				this.specs.selectedSourceProperty().setValue( spec.isPresent() ? Optional.of( spec.get().spec() ) : Optional.empty() );
//			}
//			else
//				this.specs.selectedSourceProperty().setValue( Optional.empty() );
//		} );

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

	}

//	public void toggleSourcesTable()
//	{
//		this.root.setRight( this.root.getRight() == null ? this.sourcesTab : null );
//	}

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

		new Thread( controller::init ).start();
		// test the look and feel with both Caspian and Modena
		Application.setUserAgentStylesheet( Application.STYLESHEET_CASPIAN );
//		Application.setUserAgentStylesheet( Application.STYLESHEET_MODENA );
		// initialize the default styles for the dock pane and undocked nodes
		// using the DockFX
		// library's internal Default.css stylesheet
		// unlike other custom control libraries this allows the user to
		// override them globally
		// using the style manager just as they can with internal JavaFX
		// controls
		// this must be called after the primary stage is shown
		// https://bugs.openjdk.java.net/browse/JDK-8132900

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

		// normally, you would just use the default alert positioning,
		// but for this simple sample the main stage is small,
		// so explicitly position the alert so that the main window can still be
		// seen.
		closeConfirmation.setX( primaryStage.getX() );
		closeConfirmation.setY( primaryStage.getY() + primaryStage.getHeight() );

		final Optional< ButtonType > closeResponse = closeConfirmation.showAndWait();
		if ( !ButtonType.OK.equals( closeResponse.get() ) )
			// stage.setOnCloseRequest(
//			event -> {
//			Platform.runLater( Platform::exit );
//			}
//			);
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

	private < T, U, V > void addSource( final SourceAndConverter< T > src, final Composite< ARGBType, ARGBType > comp )
	{
		this.composites.put( src.getSpimSource(), comp );
		this.baseView().getState().addSource( src );
	}

	public < T, VT > void removeSource( final DataSource< T, VT > spec )
	{
		this.composites.remove( spec );
		this.sourceInfo.removeSource( spec );
	}

	public void addLabelSource( final LabelDataSource< LabelMultisetType, VolatileLabelMultisetType > spec )
	{
		final FragmentSegmentAssignmentState< ? > assignment = spec.getAssignment();
		final CurrentModeConverter converter = new CurrentModeConverter();
		final HashMap< Mode, SelectedIds > selIdsMap = new HashMap<>();
		final HashMap< Mode, ARGBStream > streamsMap = new HashMap<>();
		for ( final Mode mode : this.modes )
		{
			final SelectedIds selId = new SelectedIds();
			selIdsMap.put( mode, selId );
			streamsMap.put( mode, new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment ) );
		}
		final DataSource< LabelMultisetType, VolatileARGBType > vsource = new ConverterDataSource<>(
				spec,
				converter,
				method -> method.equals( Interpolation.NLINEAR ) ? new ClampingNLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				new VolatileARGBType( 0 ) );
		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();
		final SourceAndConverter< VolatileARGBType > src = new SourceAndConverter<>( vsource, ( s, t ) -> t.set( s.get() ) );

		final Consumer< Mode > setConverter = mode -> {
			final AbstractHighlightingARGBStream argbStream = ( AbstractHighlightingARGBStream ) sourceInfo.stream( vsource, mode ).get();
			converter.setConverter( new HDF5LabelMultisetDataSource.HighlightingStreamConverter( argbStream ) );
			baseView().requestRepaint();
		};

		currentMode.addListener( ( obs, oldv, newv ) -> {
			Optional.ofNullable( newv ).ifPresent( setConverter::accept );
		} );

		addSource( src, comp );
		sourceInfo.addLabelSource( vsource, ToIdConverter.fromLabelMultisetType(), ( Function< LabelMultisetType, Converter< LabelMultisetType, BoolType > > ) sel -> createBoolConverter( sel, assignment ), assignment, streamsMap, selIdsMap );
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

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource( final DataSource< T, ? extends Volatile< U > > spec, final double min, final double max )
	{
		final RealARGBConverter< U > realARGBConv = new RealARGBConverter<>( min, max );
		final SourceAndConverter< ? > src = new SourceAndConverter<>( spec, ( s, t ) -> realARGBConv.convert( s.get(), t ) );
		final Composite< ARGBType, ARGBType > comp = new ARGBCompositeAlphaAdd();
		addSource( src, comp );

		sourceInfo.addRawSource( spec );
		final T t = spec.getDataType();
		final Function< T, String > valueToString = valueToString( t );
		this.valueDisplayListener.addSource( spec, Optional.of( valueToString ) );
	}

	public < T > void addARGBSource( final DataSource< T, VolatileARGBType > spec )
	{
		final Composite< ARGBType, ARGBType > comp = new ARGBCompositeAlphaAdd();
		final SourceAndConverter< ? > src = new SourceAndConverter<>( spec, ( s, t ) -> t.set( s.get() ) );
		addSource( src, comp );

		sourceInfo.addRawSource( spec );
		final T t = spec.getDataType();
		final Function< T, String > valueToString = valueToString( t );
		this.valueDisplayListener.addSource( spec, Optional.of( valueToString ) );
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
}
