package bdv.bigcat.viewer.atlas;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.viewer.ValueDisplayListener;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.viewer.Source;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

public class AtlasValueDisplayListener
{

	private final HashMap< Source< ? >, Source< ? > > dataSourceMap = new HashMap<>();

	private final HashMap< Source< ? >, Consumer > handlerMap = new HashMap<>();

	private final HashMap< ViewerPanelFX, ValueDisplayListener > listeners = new HashMap<>();

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

	public Consumer< ViewerPanelFX > onEnter()
	{
		return t -> {
			if ( !this.listeners.containsKey( t ) )
				this.listeners.put( t, new ValueDisplayListener( dataSourceMap, handlerMap, t ) );
			t.getDisplay().addEventHandler( MouseEvent.MOUSE_MOVED, this.listeners.get( t ) );
			t.addTransformListener( this.listeners.get( t ) );
		};
	}

	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
			t.getDisplay().removeEventHandler( MouseEvent.MOUSE_MOVED, this.listeners.get( t ) );
			t.removeTransformListener( this.listeners.get( t ) );
			if ( statusBar != null )
				statusBar.setText( "" );
		};
	}

}
