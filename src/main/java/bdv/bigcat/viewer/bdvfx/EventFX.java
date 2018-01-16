package bdv.bigcat.viewer.bdvfx;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

public abstract class EventFX< E extends Event > implements EventHandler< E >, InstallAndRemove< Node >
{
	private final String name;

	private final EventType< E > eventType;

	private final Predicate< E >[] eventFilter;

//	private final boolean consume;

	public EventFX( final String name, final EventType< E > eventType, final Predicate< E >[] eventFilter )
	{
		super();
		this.name = name;
		this.eventType = eventType;
		this.eventFilter = eventFilter;
	}

	public abstract void actOn( E event );

	public String name()
	{
		return name;
	}

	@Override
	public void installInto( final Node node )
	{
		node.addEventHandler( eventType, this );
	}

	@Override
	public void removeFrom( final Node node )
	{
		node.removeEventHandler( eventType, this );
	}

	@Override
	public void handle( final E e )
	{
		if ( Arrays.stream( eventFilter ).filter( filter -> filter.test( e ) ).count() > 0 )
			actOn( e );
	}

	@SafeVarargs
	public static EventFX< KeyEvent > KEY_PRESSED( final String name, final Consumer< KeyEvent > eventHandler, final Predicate< KeyEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, KeyEvent.KEY_PRESSED, eventHandler, eventFilter );
	}

	public static EventFX< KeyEvent > KEY_RELEASED( final String name, final Consumer< KeyEvent > eventHandler, final Predicate< KeyEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, KeyEvent.KEY_RELEASED, eventHandler, eventFilter );
	}

	public static EventFX< KeyEvent > KEY_TYPED( final String name, final Consumer< KeyEvent > eventHandler, final Predicate< KeyEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, KeyEvent.KEY_TYPED, eventHandler, eventFilter );
	}

	public static EventFX< MouseEvent > MOUSE_CLICKED( final String name, final Consumer< MouseEvent > eventHandler, final Predicate< MouseEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, MouseEvent.MOUSE_CLICKED, eventHandler, eventFilter );
	}

	public static EventFX< MouseEvent > MOUSE_PRESSED( final String name, final Consumer< MouseEvent > eventHandler, final Predicate< MouseEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, MouseEvent.MOUSE_PRESSED, eventHandler, eventFilter );
	}

	public static EventFX< MouseEvent > MOUSE_RELEASED( final String name, final Consumer< MouseEvent > eventHandler, final Predicate< MouseEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, MouseEvent.MOUSE_RELEASED, eventHandler, eventFilter );
	}

	public static EventFX< MouseEvent > MOUSE_DRAGGED( final String name, final Consumer< MouseEvent > eventHandler, final Predicate< MouseEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, MouseEvent.MOUSE_DRAGGED, eventHandler, eventFilter );
	}

	public static EventFX< ScrollEvent > SCROLL( final String name, final Consumer< ScrollEvent > eventHandler, final Predicate< ScrollEvent >... eventFilter )
	{
		return new EventFXWithConsumer<>( name, ScrollEvent.SCROLL, eventHandler, eventFilter );
	}

	public static class EventFXWithConsumer< E extends Event > extends EventFX< E >
	{

		private final Consumer< E > eventHandler;

		public EventFXWithConsumer( final String name, final EventType< E > eventType, final Consumer< E > eventHandler, final Predicate< E >... eventFilter )
		{
			super( name, eventType, eventFilter );
			this.eventHandler = eventHandler;
		}

		@Override
		public void actOn( final E event )
		{
			eventHandler.accept( event );
		}

	}

}
