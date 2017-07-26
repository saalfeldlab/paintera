package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import javafx.application.Platform;
import javafx.scene.control.Label;

public class AtlasValueDisplayListener
{

	private final HashMap< Source< ? >, Source< ? > > dataSourceMap = new HashMap<>();

	private final HashMap< Source< ? >, Consumer > handlerMap = new HashMap<>();

	private final HashMap< ViewerPanel, ValueDisplayListener > listeners = new HashMap<>();

	private final Label statusBar;

	public AtlasValueDisplayListener( final Label statusBar )
	{
		super();
		this.statusBar = statusBar;
	}

	public < VT, T > void addSource( final Source< VT > source, final Source< T > dataSource, final Optional< Function< T, String > > valueToString )
	{
		final Function< T, String > actualValueToString = valueToString.orElseGet( () -> Object::toString );
		this.dataSourceMap.put( source, dataSource );
		final Consumer< T > handler = t -> {
			Platform.runLater( () -> statusBar.setText( actualValueToString.apply( t ) ) );
		};
		this.handlerMap.put( source, handler );
	}

	public Consumer< ViewerPanel > onEnter()
	{
		return t -> {
			if ( !this.listeners.containsKey( t ) )
				this.listeners.put( t, new ValueDisplayListener( dataSourceMap, handlerMap, t ) );
			t.getDisplay().addMouseMotionListener( this.listeners.get( t ) );
			t.addTransformListener( this.listeners.get( t ) );
		};
	}

	public Consumer< ViewerPanel > onExit()
	{
		return t -> {
			t.getDisplay().removeMouseMotionListener( this.listeners.get( t ) );
			t.removeTransformListener( this.listeners.get( t ) );
			if ( statusBar != null )
				statusBar.setText( "" );
		};
	}

}
