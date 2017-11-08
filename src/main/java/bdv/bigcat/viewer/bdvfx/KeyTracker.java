package bdv.bigcat.viewer.bdvfx;

import java.util.Arrays;
import java.util.HashSet;

import bdv.bigcat.viewer.panel.OnWindowInitListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class KeyTracker implements InstallAndRemove< Scene >
{

	private final HashSet< KeyCode > activeKeys = new HashSet<>();

	private final ActivateKey activate = new ActivateKey();

	private final DeactivateKey deactivate = new DeactivateKey();

	private final OnFocusChanged onFocusChanged = new OnFocusChanged();

	@Override
	public void installInto( final Scene scene )
	{
		scene.addEventFilter( KeyEvent.KEY_RELEASED, deactivate );
		scene.addEventFilter( KeyEvent.KEY_PRESSED, activate );
		scene.windowProperty().addListener( new OnWindowInitListener( window -> window.focusedProperty().addListener( onFocusChanged ) ) );
	}

	@Override
	public void removeFrom( final Scene scene )
	{
		scene.removeEventFilter( KeyEvent.KEY_PRESSED, activate );
		scene.removeEventFilter( KeyEvent.KEY_RELEASED, deactivate );
		if ( scene.getWindow() != null )
			scene.getWindow().focusedProperty().removeListener( onFocusChanged );
	}

	private final class ActivateKey implements EventHandler< KeyEvent >
	{
		@Override
		public void handle( final KeyEvent event )
		{
			synchronized ( activeKeys )
			{
				activeKeys.add( event.getCode() );
			}
		}
	}

	private final class DeactivateKey implements EventHandler< KeyEvent >
	{
		@Override
		public void handle( final KeyEvent event )
		{
			synchronized ( activeKeys )
			{
				activeKeys.remove( event.getCode() );
			}
		}
	}

	private final class OnFocusChanged implements ChangeListener< Boolean >
	{

		@Override
		public void changed( final ObservableValue< ? extends Boolean > observable, final Boolean oldValue, final Boolean newValue )
		{
			if ( !newValue )
				synchronized ( activeKeys )
				{
					activeKeys.clear();
				}
		}

	}

	public boolean areOnlyTheseKeysDown( final KeyCode... codes )
	{
		final HashSet< KeyCode > codesHashSet = new HashSet<>( Arrays.asList( codes ) );
		synchronized ( activeKeys )
		{
			return codesHashSet.equals( activeKeys );
		}
	}

	public boolean areKeysDown( final KeyCode... codes )
	{
		synchronized ( activeKeys )
		{
			return activeKeys.containsAll( Arrays.asList( codes ) );
		}
	}

	public int activeKeyCount()
	{
		synchronized ( activeKeys )
		{
			return activeKeys.size();
		}
	}

	public boolean noKeysActive()
	{
		return activeKeyCount() == 0;
	}

}
