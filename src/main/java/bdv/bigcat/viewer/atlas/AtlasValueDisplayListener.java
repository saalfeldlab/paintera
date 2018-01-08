package bdv.bigcat.viewer.atlas;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.viewer.ValueDisplayListener;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.viewer.Source;
import javafx.application.Platform;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

public class AtlasValueDisplayListener
{

	@SuppressWarnings( "rawtypes" )
	private final HashMap< DataSource< ?, ? >, Consumer > handlerMap = new HashMap<>();

	private final HashMap< ViewerPanelFX, ValueDisplayListener > listeners = new HashMap<>();

	private final Label statusBar;

	private final ObservableValue< Source< ? > > currentSource;

	private final ObservableIntegerValue currentSourceIndexInVisibleSources;

	public AtlasValueDisplayListener( final Label statusBar, final ObservableValue< Source< ? > > currentSource, final ObservableIntegerValue currentSourceIndexInVisibleSources )
	{
		super();
		this.statusBar = statusBar;
		this.currentSource = currentSource;
		this.currentSourceIndexInVisibleSources = currentSourceIndexInVisibleSources;
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
				this.listeners.put( t, new ValueDisplayListener( handlerMap, t, currentSource, currentSourceIndexInVisibleSources ) );
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
