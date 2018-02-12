package bdv.bigcat.viewer.atlas;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.composite.ARGBCompositeAlphaAdd;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.ClearingCompositeProjector.ClearingCompositeProjectorFactory;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ARGBColorConverter;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.ViewerActor;
import bdv.bigcat.viewer.atlas.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.atlas.data.mask.MaskedSource;
import bdv.bigcat.viewer.atlas.data.mask.PickOneAllIntegerTypes;
import bdv.bigcat.viewer.atlas.data.mask.PickOneAllIntegerTypesVolatile;
import bdv.bigcat.viewer.atlas.mode.Highlights;
import bdv.bigcat.viewer.atlas.mode.Merges;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.atlas.mode.NavigationOnly;
import bdv.bigcat.viewer.atlas.mode.paint.PaintMode;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.atlas.ui.ResizeOnLeftSide;
import bdv.bigcat.viewer.atlas.ui.source.SourceTabs;
import bdv.bigcat.viewer.bdvfx.EventFX;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.meshes.MeshGenerator.ShapeKey;
import bdv.bigcat.viewer.meshes.MeshInfos;
import bdv.bigcat.viewer.meshes.MeshManager;
import bdv.bigcat.viewer.meshes.cache.CacheUtils;
import bdv.bigcat.viewer.ortho.OrthoView;
import bdv.bigcat.viewer.ortho.OrthoViewState;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentOnlyLocal;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.FragmentsInSelectedSegments;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.state.SelectedSegments;
import bdv.bigcat.viewer.stream.HighlightingStreamConverterIntegerType;
import bdv.bigcat.viewer.stream.HighlightingStreamConverterLabelMultisetType;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.bigcat.viewer.util.Colors;
import bdv.bigcat.viewer.util.HashWrapper;
import bdv.bigcat.viewer.viewer3d.OrthoSliceFX;
import bdv.bigcat.viewer.viewer3d.Viewer3DFX;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
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
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;

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

	private final ViewerOptions viewerOptions;

	private final Viewer3DFX renderView;

	private final List< OrthoSliceFX > orthoSlices = new ArrayList<>();

	private Stage primaryStage;

	private final KeyTracker keyTracker = new KeyTracker();

	private final ARGBStreamSeedSetter seedSetter;

	private final SharedQueue cellCache;

	private final SourceTabs sourceTabs;

	private final ResizeOnLeftSide sourceTabsResizer;

	private final AtlasSettings settings = new AtlasSettings();

	private final Node settingsNode;

	private final VBox sourcesAndSettings;

	private final ExecutorService generalPurposeExecutorService = Executors.newFixedThreadPool( 3, new ThreadFactory()
	{
		final AtomicInteger i = new AtomicInteger();

		@Override
		public Thread newThread( final Runnable r )
		{
			final Thread thread = Executors.defaultThreadFactory().newThread( r );
			thread.setName( Atlas.class.getSimpleName() + "-general-purpose-thread-" + i.getAndIncrement() );
			return thread;
		}
	} );
	{
		try
		{
			generalPurposeExecutorService.invokeAll( Stream.generate( () -> ( Callable< Void > ) () -> null ).limit( 100 ).collect( Collectors.toList() ) );
		}
		catch (

		final InterruptedException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Atlas( final SharedQueue cellCache )
	{
		this( ViewerOptions.options(), cellCache );
	}

	public Atlas( final ViewerOptions viewerOptions, final SharedQueue cellCache )
	{
		super();
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory( new ClearingCompositeProjectorFactory<>( sourceInfo.composites(), new ARGBType() ) )
				.numRenderingThreads( Math.min( 3, Math.max( 1, Runtime.getRuntime().availableProcessors() / 3 ) ) );
		this.view = new OrthoView( focusHandler.onEnter(), focusHandler.onExit(), new OrthoViewState( this.viewerOptions ), cellCache, keyTracker );
		this.view.setMinWidth( 100 );
		this.view.setMinHeight( 100 );
		this.view.getState().allowRotationsProperty().bind( settings.allowRotationsProperty() );
		this.sourceTabs = new SourceTabs(
				this.sourceInfo.currentSourceIndexProperty(),
				source -> {
					this.sourceInfo.removeSource( source );
					// this.view.getState().removeSource( source );
				},
				// this.view.getState().removeSource( source ),
				this.sourceInfo );

		settingsNode = AtlasSettingsNode.getNode( settings, sourceTabs.widthProperty() );

		sourcesAndSettings = new VBox( sourceTabs.get(), new TitledPane( "Settings", settingsNode ) );
		this.sourceTabsResizer = new ResizeOnLeftSide( sourcesAndSettings, sourceTabs.widthProperty(), ( diff ) -> diff > 0 && diff < 10 );
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
		this.view.setInfoNode( renderView );
		this.renderView.scene().addEventHandler( MouseEvent.MOUSE_CLICKED, event -> renderView.scene().requestFocus() );

		final Mode[] initialModes = defaultModes( this );
		this.settings.availableModes().setAll( initialModes );

		for ( final Mode mode : this.settings.availableModes() )
			addOnEnterOnExit( mode.onEnter(), mode.onExit(), true );

		this.settings.currentModeProperty().set( initialModes[ 0 ] );

		final Label coordinates = new Label();
		final AtlasMouseCoordinatePrinter coordinatePrinter = new AtlasMouseCoordinatePrinter( coordinates );
		this.status.getChildren().add( coordinates );
		addOnEnterOnExit( coordinatePrinter.onEnter(), coordinatePrinter.onExit(), true );

		final Label label = new Label();
		valueDisplayListener = new AtlasValueDisplayListener( label, sourceInfo.currentSourceProperty(), sourceInfo.currentSourceIndexInVisibleSources() );
		this.status.getChildren().add( label );

		addOnEnterOnExit( valueDisplayListener.onEnter(), valueDisplayListener.onExit(), true );

		this.seedSetter = new ARGBStreamSeedSetter( sourceInfo, keyTracker );
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
			if ( keyTracker.areOnlyTheseKeysDown( KeyCode.P ) )
			{
				toggleSourcesTabs();
				event.consume();
			}
		} );

		this.sourceInfo.composites().addListener( ( MapChangeListener< Source< ? >, Composite< ARGBType, ARGBType > > ) change -> baseView().requestRepaint() );

		this.root.addEventHandler( KeyEvent.KEY_PRESSED, EventFX.KEY_PRESSED( "toggle interpolation", e -> toggleInterpolation(), e -> keyTracker.areOnlyTheseKeysDown( KeyCode.I ) ) );

		this.baseView().getState().zoomSpeedProperty().bind( settings.zoomSpeedProperty() );
		this.baseView().getState().translationSpeedProperty().bind( settings.translationSpeedProperty() );
		this.baseView().getState().rotationSpeedProperty().bind( settings.rotationSpeedProperty() );

	}

	public void toggleSourcesTabs()
	{
		if ( this.root.getRight() == null )
		{
			this.root.setRight( this.sourcesAndSettings );
			this.sourceTabsResizer.install();
		}
		else
		{
			this.sourceTabsResizer.remove();
			this.root.setRight( null );
		}
	}

	public Interpolation toggleInterpolation()
	{
		final Interpolation interpolation = this.baseView().getState().toggleInterpolation();
		LOG.debug( "Toggled interpolation to: {}", interpolation );
		return interpolation;
	}

	public void start( final Stage primaryStage ) throws InterruptedException
	{
		this.primaryStage = primaryStage;
		start( primaryStage, "BigCAT" );
	}

	public void start( final Stage primaryStage, final String title ) throws InterruptedException
	{

		final Scene scene = new Scene( this.root, 1280 - 32, 720 - 48 );

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

	private < T, U, V > void addSource(
			final Source< ? > src,
			final Composite< ARGBType, ARGBType > comp,
			final int tMin,
			final int tMax )
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
//		this.baseView().getState().addSource( src );
		this.time.setMin( Math.min( tMin, this.time.getMin() ) );
		this.time.setMax( Math.max( tMax, this.time.getMax() ) );
	}

	public < T, VT > void removeSource( final DataSource< T, VT > spec )
	{
		this.sourceInfo.removeSource( spec );
	}

	public void addLabelSource(
			final DataSource< LabelMultisetType, VolatileLabelMultisetType > spec,
			final FragmentSegmentAssignmentState< ? > assignment,
			final IdService idService,
			final Cache< Long, Interval[] >[] blocksThatContainId,
			final Cache< ShapeKey, Pair< float[], float[] > >[] meshCache )
	{
		final CurrentModeConverter< VolatileLabelMultisetType, HighlightingStreamConverterLabelMultisetType > converter = new CurrentModeConverter<>();
		final SelectedIds selId = new SelectedIds();
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment );
		stream.addListener( () -> baseView().requestRepaint() );
		converter.setConverter( new HighlightingStreamConverterLabelMultisetType( stream ) );

		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();

		addSource( spec, comp, spec.tMin(), spec.tMax() );
		final AtlasSourceState< VolatileLabelMultisetType, LabelMultisetType > state = sourceInfo.makeLabelSourceState(
				spec,
				ToIdConverter.fromLabelMultisetType(),
				( Function< LabelMultisetType, Converter< LabelMultisetType, BoolType > > ) sel -> createBoolConverter( sel, assignment ),
				( FragmentSegmentAssignmentState ) assignment,
				stream,
				selId,
				converter,
				comp );// converter );
		state.idServiceProperty().set( idService );
//		if ( spec instanceof MaskedSource< ?, ?, ? > )
//		{
//			final MaskedSource< LabelMultisetType, VolatileLabelMultisetType, ? extends BooleanType< ? > > mspec =
//					( MaskedSource< LabelMultisetType, VolatileLabelMultisetType, ? extends BooleanType< ? > > ) spec;
//		}

		final LabelMultisetType t = spec.getDataType();
		final Function< LabelMultisetType, String > valueToString = valueToString( t );
		final AffineTransform3D affine = new AffineTransform3D();
		spec.getSourceTransform( 0, 0, affine );
		this.valueDisplayListener.addSource( spec, Optional.of( valueToString ) );

		final SelectedSegments selectedSegments = new SelectedSegments( selId, assignment );
		final FragmentsInSelectedSegments fragmentsInSelection = new FragmentsInSelectedSegments( selectedSegments, assignment );

		final MeshManager meshManager = new MeshManager(
				spec,
				state,
				renderView.meshesGroup(),
				fragmentsInSelection,
				settings.meshSimplificationIterationsProperty(),
				this.generalPurposeExecutorService );

		final MeshInfos meshInfos = new MeshInfos( selectedSegments, assignment, meshManager, spec.getNumMipmapLevels() );
		state.meshManagerProperty().set( meshManager );
		state.meshInfosProperty().set( meshInfos );

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
					selId.addListener( () -> vp.requestRepaint() );
				};
			}
		} );

		if ( meshCache != null && blocksThatContainId != null )
		{
			state.meshesCacheProperty().set( meshCache );
			state.blocklistCacheProperty().set( blocksThatContainId );
		}
		else // TODO get to the scale factors somehow (affine transform might be
				// off-diagonal in case of permutations)
			generateMeshCaches(
					spec,
					state,
					scaleFactorsFromAffineTransforms( spec ),
					( lbl, set ) -> lbl.entrySet().forEach( entry -> set.add( entry.getElement().id() ) ),
					lbl -> ( src, tgt ) -> tgt.set( src.contains( lbl ) ),
					generalPurposeExecutorService );

		sourceInfo.addState( spec, state );

	}

	public < I extends IntegerType< I > & NativeType< I > > void addLabelSource(
			final RandomAccessibleInterval< I > data,
			final double[] resolution,
			final double[] offset,
			final long maxId )
	{

		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );
		addLabelSource( data, transform, maxId, "labels" );
	}

	public < I extends IntegerType< I > & NativeType< I > > void addLabelSource(
			final RandomAccessibleInterval< I > data,
			final long maxId )
	{
		addLabelSource( data, new AffineTransform3D(), maxId, "labels" );
	}

	public < I extends NativeType< I > & IntegerType< I >, V extends AbstractVolatileRealType< I, V > > void addLabelSource(
			final RandomAccessibleInterval< I > data,
			final AffineTransform3D sourceTransform,
			final long maxId,
			final String name )
	{
		final I i = Util.getTypeFromInterval( data );
		final V v = ( V ) VolatileTypeMatcher.getVolatileTypeForType( i );
		if ( v == null )
			throw new RuntimeException( "Was not able to get volatile type for type: " + i.getClass() );
		v.setValid( true );
		final RandomAccessibleInterval< V > convertedData = Converters.convert( data, ( s, t ) -> t.get().set( s ), v );
		final RandomAccessibleInterval< I >[] sources = new RandomAccessibleInterval[] { data };
		final RandomAccessibleInterval< V >[] converted = new RandomAccessibleInterval[] { convertedData };
		final DataSource< I, V > source = new RandomAccessibleIntervalDataSource<>(
				sources,
				converted,
				new AffineTransform3D[] { sourceTransform },
				interpolation -> new NearestNeighborInterpolatorFactory<>(),
				interpolation -> new NearestNeighborInterpolatorFactory<>(),
				name );
		final LocalIdService idService = new LocalIdService();
		idService.invalidate( maxId );
		addLabelSource(
				source,
				new FragmentSegmentAssignmentOnlyLocal(),
				p -> p.get().getIntegerLong(),
				null,
				null,
				null );
	}

	// TODO Is there a better bound for V than AbstractVolatileRealType? V
	// extends Volatile< I > & IntegerType< V > did not work with
	// VolatileUnsignedLongType
	public < I extends IntegerType< I >, V extends AbstractVolatileRealType< I, V > > void addLabelSource(
			final DataSource< I, V > spec,
			final FragmentSegmentAssignmentState< ? > assignment,
			final ToLongFunction< V > toLong,
			final IdService idService,
			final Cache< Long, Interval[] >[] blocksThatContainId,
			final Cache< ShapeKey, Pair< float[], float[] > >[] meshCache )
	{
		final CurrentModeConverter< V, HighlightingStreamConverterIntegerType< V > > converter = new CurrentModeConverter<>();
		final SelectedIds selId = new SelectedIds();
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selId, assignment );
		stream.addListener( () -> baseView().requestRepaint() );
		converter.setConverter( new HighlightingStreamConverterIntegerType<>( stream, toLong ) );

		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();

		addSource( spec, comp, spec.tMin(), spec.tMax() );
		final AtlasSourceState< V, I > state = sourceInfo.makeLabelSourceState(
				spec,
				spec.getDataType() instanceof IntegerType ? ToIdConverter.fromIntegerType() : ToIdConverter.fromRealType(),
				sel -> createBoolConverter( sel, assignment ),
				( FragmentSegmentAssignmentState ) assignment,
				stream,
				selId,
				converter,
				comp );
		state.idServiceProperty().set( idService );
		if ( spec instanceof MaskedSource< ?, ? > )
			state.maskedSourceProperty().set( ( MaskedSource< ?, ? > ) spec );

		final I t = spec.getDataType();
		final Function< I, String > valueToString = valueToString( t );
		final AffineTransform3D affine = new AffineTransform3D();
		spec.getSourceTransform( 0, 0, affine );
		this.valueDisplayListener.addSource( spec, Optional.of( valueToString ) );

		final SelectedSegments selectedSegments = new SelectedSegments( selId, assignment );
		final FragmentsInSelectedSegments fragmentsInSelection = new FragmentsInSelectedSegments( selectedSegments, assignment );

		final MeshManager meshManager = new MeshManager(
				spec,
				state,
				renderView.meshesGroup(),
				fragmentsInSelection,
				settings.meshSimplificationIterationsProperty(),
				this.generalPurposeExecutorService );

		final MeshInfos meshInfos = new MeshInfos( selectedSegments, assignment, meshManager, spec.getNumMipmapLevels() );
		state.meshManagerProperty().set( meshManager );
		state.meshInfosProperty().set( meshInfos );

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
					selId.addListener( () -> vp.requestRepaint() );
				};
			}
		} );

		if ( meshCache != null && blocksThatContainId != null )
		{
			state.meshesCacheProperty().set( meshCache );
			state.blocklistCacheProperty().set( blocksThatContainId );
		}
		else
			generateMeshCaches(
					spec,
					state,
					scaleFactorsFromAffineTransforms( spec ),
					( lbl, set ) -> set.add( lbl.getIntegerLong() ),
					lbl -> ( src, tgt ) -> tgt.set( src.getIntegerLong() == lbl ),
					generalPurposeExecutorService );

//		private static < D extends Type< D >, T extends Type< T > > void generateMeshCaches(
//			final DataSource< D, T > spec,
//			final AtlasSourceState< T, D > state,
//			final double[][] scalingFactors,
//			final BiConsumer< D, TLongHashSet > collectLabels,
//			final LongFunction< Converter< D, BoolType > > getMaskGenerator,
//			final ExecutorService es )

		sourceInfo.addState( spec, state );

	}

	public < T extends RealType< T > > void addRawSource(
			final RandomAccessibleInterval< T > data,
			final double[] resolution,
			final double[] offset,
			final double min,
			final double max )
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );
		System.out.println( "ADDING RAW SOURCE WITH TRANSFORM " + transform );
		addRawSource( data, transform, min, max, Color.WHITE, "raw" );
	}

	public < T extends RealType< T > > void addRawSource(
			final RandomAccessibleInterval< T > data,
			final double min,
			final double max )
	{
		addRawSource( data, new AffineTransform3D(), min, max, Color.WHITE, "raw" );
	}

	public < T extends RealType< T > > void addRawSource(
			final RandomAccessibleInterval< T > data,
			final AffineTransform3D sourceTransform,
			final double min,
			final double max,
			final Color color,
			final String name )
	{
		final RandomAccessibleInterval< T >[] sources = new RandomAccessibleInterval[] { data };
		final DataSource< T, T > source = new RandomAccessibleIntervalDataSource<>(
				sources,
				sources,
				new AffineTransform3D[] { sourceTransform },
				i -> i.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory() : new NearestNeighborInterpolatorFactory(),
				i -> i.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory() : new NearestNeighborInterpolatorFactory(),
				name );
		addRawSource( source, min, max, Colors.toARGBType( color ) );
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

		final AtlasSourceState< U, T > state = sourceInfo.addRawSource( spec, min, max, color, comp );
		final T t = spec.getDataType();
		final Function< T, String > valueToString = valueToString( t );
		this.valueDisplayListener.addSource( spec, Optional.of( valueToString ) );
		final Converter< U, ARGBType > conv = state.converterProperty().get();
		if ( conv instanceof ARGBColorConverter< ? > )
		{
			final ARGBColorConverter< U > colorConv = ( ARGBColorConverter< U > ) conv;
			colorConv.colorProperty().addListener( ( obs, oldv, newv ) -> baseView().requestRepaint() );
			colorConv.minProperty().addListener( ( obs, oldv, newv ) -> baseView().requestRepaint() );
			colorConv.maxProperty().addListener( ( obs, oldv, newv ) -> baseView().requestRepaint() );
			colorConv.alphaProperty().addListener( ( obs, oldv, newv ) -> baseView().requestRepaint() );
		}
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
		else if ( t instanceof Pair< ?, ? > )
		{
			final Pair< ?, ? > p = ( Pair< ?, ? > ) t;
			if ( p.getB() instanceof IntegerType< ? > )
				valueToString = ( Function< T, String > ) ( Function< Pair< ?, IntegerType< ? > >, String > ) valueToStringForPair( p.getA(), ( IntegerType ) p.getB() );
			else
				valueToString = rt -> "Do not understand type!";
		}
		else
			valueToString = rt -> "Do not understand type!";
		return valueToString;
	}

	public static < T, I extends IntegerType< I > > Function< Pair< T, I >, String > valueToStringForPair( final T t, final I i )
	{
		final Function< T, String > valueToStringT = valueToString( t );
		final Function< I, String > valueToStringI = valueToString( i );
		return p -> bdv.labels.labelset.Label.regular( p.getB().getIntegerLong() ) ? valueToStringI.apply( p.getB() ) : valueToStringT.apply( p.getA() );
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

	@SuppressWarnings( "unchecked" )
	public static < T extends RealType< T > > Converter< T, BoolType > createBoolConverter( final T selection, final FragmentSegmentAssignmentState< ? > assignment )
	{
		final boolean isInteger = selection instanceof IntegerType< ? >;
		final long id = isInteger ? ( ( IntegerType< ? > ) selection ).getIntegerLong() : ( long ) selection.getRealDouble();
		final long segmentId = assignment.getSegment( id );
		if ( selection instanceof IntegerType< ? > )
			return ( Converter< T, BoolType > ) ( Converter< IntegerType< ? >, BoolType > ) ( s, t ) -> t.set( assignment.getSegment( s.getIntegerLong() ) == segmentId );
		else
			return ( s, t ) -> t.set( assignment.getSegment( ( long ) s.getRealDouble() ) == segmentId );

	}

	public static < I extends IntegerType< I >, K extends IntegerType< K > > Converter< Pair< I, K >, BoolType > createBoolConverter( final Pair< I, K > selection, final FragmentSegmentAssignmentState< ? > assignment )
	{
		final long paintedLabel = selection.getB().getIntegerLong();
		final long id = bdv.labels.labelset.Label.regular( paintedLabel ) ? paintedLabel : selection.getA().getIntegerLong();
		final long segmentId = assignment.getSegment( id );
		return ( s, t ) -> {
			final long k = s.getB().getIntegerLong();
			t.set( assignment.getSegment( bdv.labels.labelset.Label.regular( k ) ? k : s.getA().getIntegerLong() ) == segmentId );
		};

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

	public static < I extends IntegerType< I > & NativeType< I >, V extends AbstractVolatileRealType< I, V > > MaskedSource< I, V > addCanvas(
			final DataSource< I, V > source,
			final int[] cellSize,
			final String path,
			final Consumer< RandomAccessibleInterval< UnsignedLongType > > mergeCanvasIntoBackground )
	{

		final DiskCachedCellImgOptions cacheOptions = DiskCachedCellImgOptions
				.options()
				.dirtyAccesses( true )
				.cacheDirectory( new File( path ).toPath() )
				.cacheType( CacheType.BOUNDED )
				.cellDimensions( cellSize )
				.deleteCacheDirectoryOnExit( false )
				.maxCacheSize( 100 );

		final I defaultValue = source.getDataType().createVariable();
		defaultValue.setInteger( bdv.labels.labelset.Label.INVALID );

		final I type = source.getDataType();
		type.setInteger( bdv.labels.labelset.Label.OUTSIDE );
		final V vtype = source.getType();
		vtype.setValid( true );
		vtype.get().setInteger( bdv.labels.labelset.Label.OUTSIDE );

		final PickOneAllIntegerTypes< I, UnsignedLongType > pacD = new PickOneAllIntegerTypes<>(
				l -> bdv.labels.labelset.Label.regular( l.getIntegerLong() ),
				( l1, l2 ) -> l2.getIntegerLong() != bdv.labels.labelset.Label.TRANSPARENT && bdv.labels.labelset.Label.regular( l1.getIntegerLong() ),
				type.createVariable() );

		final PickOneAllIntegerTypesVolatile< I, UnsignedLongType, V, VolatileUnsignedLongType > pacT = new PickOneAllIntegerTypesVolatile<>(
				l -> bdv.labels.labelset.Label.regular( l.getIntegerLong() ),
				( l1, l2 ) -> l2.getIntegerLong() != bdv.labels.labelset.Label.TRANSPARENT && bdv.labels.labelset.Label.regular( l1.getIntegerLong() ),
				vtype.createVariable() );

		final MaskedSource< I, V > ms = new MaskedSource<>(
				source,
				cacheOptions,
				level -> String.format( "%s/%d", path, level ),
				pacD,
				pacT,
				type,
				vtype,
				mergeCanvasIntoBackground );
		// opts, mipmapCanvasCacheDirs, pacD, pacT, writeMaskToCanvas,
		// extensionD, extensionT );

//		ms = new MaskedSource<>(
//				null,//source,
//				null,//cacheOptions,
//				null,//path + "/%d",
//				null, // pacD,
//				null, // pacT,
//				null, // writeMaskToCanvas,
//				null,//type,
//				null,//vtype );

		return ms;

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

	public Optional< SelectedIds > getSelectedIds( final Source< ? > source )
	{
		return Optional.ofNullable( sourceInfo.getState( source ).selectedIdsProperty().get() );
	}

	public GlobalTransformManager transformManager()
	{
		return this.baseView().getState().transformManager();
	}

	public SourceInfo sourceInfo()
	{
		return this.sourceInfo;
	}

	public KeyTracker keyTracker()
	{
		return this.keyTracker;
	}

	public Optional< NavigationOnly > getNavigationOnlyMode()
	{
		return getMode( NavigationOnly.class );
	}

	public Optional< Highlights > getHighlightsMode()
	{
		return getMode( Highlights.class );
	}

	public Optional< Merges > getMergesMode()
	{
		return getMode( Merges.class );
	}

	public Optional< PaintMode > getPaintMode()
	{
		return getMode( PaintMode.class );
	}

	public < M extends Mode > Optional< M > getMode( final M mode )
	{
		return this.settings.availableModes().stream().filter( m -> m.equals( mode ) ).map( m -> ( M ) m ).findFirst();
	}

	public < M extends Mode > Optional< M > getMode( final Class< M > mode )
	{
		return this.settings.availableModes().stream().filter( m -> m.getClass().equals( mode ) ).map( m -> ( M ) m ).findFirst();
	}

	public AtlasSettings getSettings()
	{
		return this.settings;
	}

	private static Mode[] defaultModes( final Atlas viewer )
	{
		return new Mode[] {
				new NavigationOnly(),
				new Highlights( viewer.transformManager(), viewer.renderView.meshesGroup(), viewer.sourceInfo(), viewer.keyTracker(), viewer.generalPurposeExecutorService ),
				new Merges( viewer.sourceInfo() ),
				new PaintMode( viewer.baseView().viewerAxes(), viewer.sourceInfo(), viewer.keyTracker(), viewer.transformManager(), () -> viewer.baseView().requestRepaint() ) };
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

		final Cache< HashWrapper< long[] >, long[] >[] uniqueLabelLoaders = CacheUtils.uniqueLabelCaches(
				spec,
				blockSizes,
				collectLabels,
				CacheUtils::toCacheSoftRefLoaderCache );

		final Cache< Long, Interval[] >[] blocksForLabelCache = CacheUtils.blocksForLabelCaches(
				spec,
				uniqueLabelLoaders,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache,
				es );

		final Cache< ShapeKey, Pair< float[], float[] > >[] meshCaches = CacheUtils.meshCacheLoaders(
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

}
