package bdv.bigcat.viewer.atlas;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.viewer.ValueDisplayListener;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

public class AtlasValueDisplayListener
{

	private final HashMap< DataSource< ?, ? >, Consumer > handlerMap = new HashMap<>();

	private final HashMap< ViewerPanelFX, ValueDisplayListener > listeners = new HashMap<>();

	private final Label statusBar;

	public AtlasValueDisplayListener( final Label statusBar )
	{
		super();
		this.statusBar = statusBar;
	}

	public < D, T > void addSource( final DataSource< D, T > dataSource, final Optional< Function< D, String > > valueToString )
	{
		final Function< D, String > actualValueToString = valueToString.orElseGet( () -> Object::toString );
		final Consumer< D > handler = t -> {
			Platform.runLater( () -> statusBar.setText( actualValueToString.apply( t ) ) );
		};
		this.handlerMap.put( dataSource, handler );
	}

	public Consumer< ViewerPanelFX > onEnter()
	{
		return t -> {
			if ( !this.listeners.containsKey( t ) )
				this.listeners.put( t, new ValueDisplayListener( handlerMap, t ) );
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
