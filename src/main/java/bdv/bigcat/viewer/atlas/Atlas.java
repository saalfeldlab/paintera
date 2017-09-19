package bdv.bigcat.viewer.atlas;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import bdv.bigcat.composite.ARGBCompositeAlphaAdd;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.ClearingCompositeProjector.ClearingCompositeProjectorFactory;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.BaseView;
import bdv.bigcat.viewer.BaseViewState;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.ToIdConverter.FromLabelMultisetType;
import bdv.bigcat.viewer.ViewerActor;
import bdv.bigcat.viewer.atlas.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.atlas.Specs.SourceState;
import bdv.bigcat.viewer.atlas.converter.NaNMaskedRealARGBConverter;
import bdv.bigcat.viewer.atlas.converter.VolatileARGBIdentiyWithAlpha;
import bdv.bigcat.viewer.atlas.data.ConvertedSource;
import bdv.bigcat.viewer.atlas.data.DatasetSpec;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetSourceSpec;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetSourceSpec.HighlightingStreamConverter;
import bdv.bigcat.viewer.atlas.data.LabelSpec;
import bdv.bigcat.viewer.atlas.mode.Highlights;
import bdv.bigcat.viewer.atlas.mode.Merges;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.atlas.mode.ModeUtil;
import bdv.bigcat.viewer.atlas.mode.NavigationOnly;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileRealType;

public class Atlas
{

	private final BaseView view;

	private final HBox status = new HBox();

	private final AtlasFocusHandler focusHandler = new AtlasFocusHandler();

	private final AtlasValueDisplayListener valueDisplayListener;

	private final Specs specs = new Specs();

	private final HashMap< Source< ? >, SelectedIds > selectedIds = new HashMap<>();

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > assignments = new HashMap<>();

	private final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > composites = new HashMap<>();

	private final ViewerOptions viewerOptions;

	private final Mode[] modes = { new NavigationOnly(), new Highlights( selectedIds ), new Merges( selectedIds, assignments ) };

	private final SourcesTab sourcesTab = new SourcesTab( specs );

	public Atlas( final Interval interval )
	{
		this( ViewerOptions.options(), interval );
	}

	public Atlas( final ViewerOptions viewerOptions, final Interval interval )
	{
		super();
		this.viewerOptions = viewerOptions.accumulateProjectorFactory( new ClearingCompositeProjectorFactory<>( composites, new ARGBType() ) );
		this.view = new BaseView( focusHandler.onEnter(), focusHandler.onExit(), new BaseViewState( this.viewerOptions ) );
		this.view.setBottom( status );
//		this.view.setInfoNode( this.view.globalSourcesInfoNode() );
		this.view.setInfoNode( new Label( "" ) );
		this.view.setRight( sourcesTab );
		this.view.heightProperty().addListener( ( ChangeListener< Number > ) ( observable, old, newVal ) -> {
			this.sourcesTab.prefHeightProperty().set( newVal.doubleValue() );
		} );
//		this.sourcesTab.listen( this.specs );

		this.view.addCurrentSourceListener( ( observable, oldValue, newValue ) -> {
			final Optional< SourceState< ?, ?, ? > > state = specs.getState( newValue );
			if ( state.isPresent() )
				specs.selectedSourceProperty().setValue( Optional.of( state.get().spec() ) );
		} );

		for ( final Mode mode : modes )
			addOnEnterOnExit( mode.onEnter(), mode.onExit(), true );

		final ComboBox< Mode > modeSelector = ModeUtil.comboBox( modes );
		modeSelector.setPromptText( "Mode" );
		this.status.getChildren().add( modeSelector );
		modeSelector.getSelectionModel().select( modes[ 2 ] );

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
		this.specs.addVisibilityChangedListener( () -> {
			final List< SourceState< ?, ?, ? > > states = this.specs.sourceStates();

			System.out.println( "LALELU " + states );
		} );

		this.specs.addListChangeListener( ( ListChangeListener< Specs.SourceState< ?, ?, ? > > ) c -> {
			while ( c.next() )
				if ( c.wasRemoved() )
					for ( final SourceState< ?, ?, ? > removed : c.getRemoved() )
					{
						final Source< ? > source = removed.source();
						this.selectedIds.remove( source );
						this.composites.remove( source );
						this.baseView().removeSource( source );
					}
				else if ( c.wasAdded() )
					this.baseView().addSources( c.getAddedSubList().stream().map( Specs.SourceState::sourceAndConverter ).collect( Collectors.toList() ) );
		} );

//		this.specs.addListener( () -> {
//			this.baseView().removeAllSources();
//			final List< SourceAndConverter< ? > > sacs = this.specs.getSourceAndConverters();
//			this.baseView().addSource( sacs.get( 0 ) );
//		} );

		this.baseView().addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( !event.isConsumed() && event.isAltDown() && event.getCode().equals( KeyCode.S ) )
			{
				toggleSourcesTable();
				event.consume();
			}
		} );
	}

	public void toggleSourcesTable()
	{
		this.baseView().setRight( this.baseView().getRight() == null ? this.sourcesTab : null );
	}

	public void start( final Stage primaryStage )
	{
		start( primaryStage, "ATLAS" );
	}

	public void start( final Stage primaryStage, final String title )
	{

		final Scene scene = view.createScene( 800, 600 );

		primaryStage.setTitle( title );
		primaryStage.setScene( scene );
		primaryStage.sizeToScene();

		view.makeDefaultLayout();

		primaryStage.show();

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

	public void addOnEnterOnExit( final Consumer< ViewerPanel > onEnter, final Consumer< ViewerPanel > onExit, final boolean onExitRemovable )
	{
		this.addOnEnterOnExit( new OnEnterOnExit( onEnter, onExit ), onExitRemovable );
	}

	public void addOnEnterOnExit( final OnEnterOnExit onEnterOnExit, final boolean onExitRemovable )
	{
		this.focusHandler.add( onEnterOnExit, onExitRemovable );
	}

	private < T, U, V > void addSource( final SourceAndConverter< T > src, final Composite< ARGBType, ARGBType > comp, final DatasetSpec< U, V > spec )
	{
		this.composites.put( src.getSpimSource(), comp );
		this.specs.addSpec( spec, src.getSpimSource(), src.getConverter() );
	}

	public < T, VT > void removeSource( final DatasetSpec< T, VT > spec )
	{
		final List< SourceAndConverter< ? > > sacs = this.specs.getSourceAndConverters( spec );
		this.specs.removeSpec( spec );
		sacs.stream().map( SourceAndConverter::getSpimSource ).forEach( this.composites::remove );
	}

	public void addLabelSource( final LabelSpec< LabelMultisetType, VolatileLabelMultisetType > spec )
	{
		final Source< VolatileLabelMultisetType > originalVSource = spec.getViewerSource();
		final FragmentSegmentAssignmentState< ? > assignment = spec.getAssignment();
		final SelectedIds selIds = new SelectedIds();
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selIds, assignment );
		final HighlightingStreamConverter streamConverter = new HDF5LabelMultisetSourceSpec.HighlightingStreamConverter( stream );
		final Converter< VolatileLabelMultisetType, VolatileARGBType > converter = ( s, t ) -> {
			final boolean isValid = s.isValid();
			t.setValid( isValid );
			if ( isValid )
				streamConverter.convert( s, t.get() );
		};

		final VolatileARGBIdentiyWithAlpha identity = new VolatileARGBIdentiyWithAlpha( 255 );
//		final Converter< VolatileARGBType, ARGBType > identity = ( source, target ) -> {
//			target.set( source.get() );
//		};

		final Source< VolatileARGBType > vsource = new ConvertedSource<>( originalVSource, new VolatileARGBType( 0 ), converter, originalVSource.getName() );
		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();
		final SourceAndConverter< VolatileARGBType > src = new SourceAndConverter<>( vsource, identity );
		addSource( src, comp, spec );

		final Source< LabelMultisetType > source = spec.getSource();
		final LabelMultisetType t = source.getType();
		final Function< LabelMultisetType, String > valueToString = valueToString( t );
		final AffineTransform3D affine = new AffineTransform3D();
		source.getSourceTransform( 0, 0, affine );
		this.valueDisplayListener.addSource( vsource, source, Optional.of( valueToString ) );

		this.assignments.put( vsource, assignment );
		view.addActor( new ViewerActor()
		{

			@Override
			public Consumer< ViewerPanel > onRemove()
			{
				return vp -> {};
			}

			@Override
			public Consumer< ViewerPanel > onAdd()
			{
				return vp -> assignment.addListener( () -> vp.requestRepaint() );
			}
		} );

		final FromLabelMultisetType toIdConverter = ToIdConverter.fromLabelMultisetType();
		final SelectedIds selectedIds = selIds;
		this.selectedIds.put( vsource, selectedIds );
		for ( final Mode mode : this.modes )
			if ( mode instanceof Highlights )
				( ( Highlights ) mode ).addSource( vsource, source, toIdConverter );
			else if ( mode instanceof Merges )
				( ( Merges ) mode ).addSource( vsource, source, toIdConverter );

		view.addActor( new ViewerActor()
		{

			@Override
			public Consumer< ViewerPanel > onRemove()
			{
				return vp -> {};
			}

			@Override
			public Consumer< ViewerPanel > onAdd()
			{
				return vp -> {
					System.out.println( "VP? " + vp + " " + selectedIds );
					selectedIds.addListener( () -> vp.requestRepaint() );
				};
			}
		} );

	}

	public < T extends RealType< T >, U extends RealType< U > > void addRawSource( final DatasetSpec< T, ? extends Volatile< U > > spec, final double min, final double max )
	{
		final Source< VolatileRealType< DoubleType > > vsource = ConvertedSource.volatileRealTypeAsDoubleType( spec.getViewerSource() );
		final Composite< ARGBType, ARGBType > comp = new ARGBCompositeAlphaAdd();
		final NaNMaskedRealARGBConverter< VolatileRealType< DoubleType > > conv = new NaNMaskedRealARGBConverter<>( min, max );
		final SourceAndConverter< ? > src = new SourceAndConverter<>( vsource, conv );
		addSource( src, comp, spec );
//		view.addSource( src, comp );
//		this.specs.put( spec, vsource );
//		this.composites.put( vsource, comp );

		final Source< T > source = spec.getSource();
		final T t = source.getType();
		final Function< T, String > valueToString = valueToString( t );
		final AffineTransform3D affine = new AffineTransform3D();
		source.getSourceTransform( 0, 0, affine );
		this.valueDisplayListener.addSource( vsource, source, Optional.of( valueToString ) );
	}

	public BaseView baseView()
	{
		return this.view;
	}

	protected Node createInfo()
	{
		final TableView< ? > table = new TableView<>();
		table.setEditable( true );
		table.getColumns().addAll( new TableColumn<>( "Property" ), new TableColumn<>( "Value" ) );

		final TextField tf = new TextField( "some text" );

		final TabPane infoPane = new TabPane();

		final VBox jfxStuff = new VBox( 1 );
		jfxStuff.getChildren().addAll( tf, table );
		infoPane.getTabs().add( new Tab( "jfx stuff", jfxStuff ) );
		infoPane.getTabs().add( new Tab( "dataset info", new Label( "random floats" ) ) );
		return infoPane;

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

}
