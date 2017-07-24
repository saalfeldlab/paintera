package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.dockfx.DockPane;

import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.viewer.AtlasFocusHandler.OnEnterOnExit;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class Atlas
{

	private final BaseView view;

	private final HashSet< Source< ? > > sources;

	private final Label status;

	private final AtlasFocusHandler focusHandler = new AtlasFocusHandler();

	private final AtlasValueDisplayListener valueDisplayListener;

	private final HashMap< DatasetSpec< ?, ? >, Source< ? > > specs = new HashMap<>();

	public Atlas()
	{
		super();
		this.view = new BaseView( focusHandler.onEnter(), focusHandler.onExit() );
		this.sources = new HashSet<>();
		this.status = new Label( "STATUS BAR!" );
		this.view.setBottom( status );
		valueDisplayListener = new AtlasValueDisplayListener( status );
//		final AtlasMouseCoordinatePrinter mcp = new AtlasMouseCoordinatePrinter( this.status );
//		addOnEnterOnExit( mcp.onEnter(), mcp.onExit(), true );
		addOnEnterOnExit( valueDisplayListener.onEnter(), valueDisplayListener.onExit(), true );
	}

	public void start( final Stage primaryStage ) throws Exception
	{

		final Scene scene = view.createScene( 800, 600 );

		primaryStage.setTitle( "ATLAS" );
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
		DockPane.initializeDefaultUserAgentStylesheet();

	}

	public void addOnEnterOnExit( final Consumer< ViewerPanel > onEnter, final Consumer< ViewerPanel > onExit, final boolean onExitRemovable )
	{
		this.addOnEnterOnExit( new OnEnterOnExit( onEnter, onExit ), onExitRemovable );
	}

	public void addOnEnterOnExit( final OnEnterOnExit onEnterOnExit, final boolean onExitRemovable )
	{
		this.focusHandler.add( onEnterOnExit, onExitRemovable );
	}

	public < T, VT extends Volatile< T > > void addSource( final DatasetSpec< T, VT > spec )
	{
		final T t = spec.getSource().getType();
		final Composite< ARGBType, ARGBType > comp = t instanceof ARGBType ? new ARGBCompositeAlphaYCbCr() : new CompositeCopy<>();
		final Source< T > source = spec.getSource();
		final SourceAndConverter< VT > vsource = new SourceAndConverter<>( spec.getVolatileSource(), spec.getVolatileConverter() );
		final SourceAndConverter< ? > src = new SourceAndConverter<>( source, spec.getConverter(), vsource );
		view.addSource( src, comp );
		this.specs.put( spec, source );

		System.out.println( t.getClass().getName() + " " + ( t instanceof VolatileARGBType ) );
		final Function< T, String > valueToString;
		if ( t instanceof ARGBType )
			valueToString = ( Function< T, String > ) Object::toString;
		else if ( t instanceof IntegerType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%d", ( ( IntegerType< ? > ) rt ).getIntegerLong() );
		else if ( t instanceof RealType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%.3f", ( ( RealType< ? > ) rt ).getRealDouble() );
		else
			valueToString = rt -> "Do not understand type!";
		this.valueDisplayListener.addSource( source, source.getInterpolatedSource( 0, 0, Interpolation.NLINEAR ).realRandomAccess(), Optional.of( valueToString ) );
	}

	// this needs to be rewritten to addDataset( DatasetSpec spec );
	public < T > void addSource( final SourceAndConverter< T > source )
	{
		final T t = source.getSpimSource().getType();
		final Composite comp = t instanceof ARGBType ? new ARGBCompositeAlphaYCbCr() : new CompositeCopy<>();
		view.addSource( source, comp );
		this.sources.add( source.getSpimSource() );

		final Function< T, String > valueToString;
		System.out.println( t.getClass().getName() + " " + ( t instanceof VolatileARGBType ) );
		if ( t instanceof VolatileARGBType )
			valueToString = ( Function< T, String > ) ( Function< VolatileARGBType, String > ) rt -> rt.get().toString();
		else if ( t instanceof IntegerType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%d", ( ( IntegerType< ? > ) rt ).getIntegerLong() );
		else if ( t instanceof RealType< ? > )
			valueToString = ( Function< T, String > ) rt -> String.format( "%.3f", ( ( RealType< ? > ) rt ).getRealDouble() );
		else
			valueToString = rt -> "Do not understand type!";
		this.valueDisplayListener.addSource( source.getSpimSource(), source.getSpimSource().getInterpolatedSource( 0, 0, Interpolation.NLINEAR ).realRandomAccess(), Optional.of( valueToString ) );
	}

	public BaseView baseView()
	{
		return this.view;
	}

}
