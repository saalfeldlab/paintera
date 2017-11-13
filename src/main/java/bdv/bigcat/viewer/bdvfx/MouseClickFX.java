package bdv.bigcat.viewer.bdvfx;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

public class MouseClickFX implements InstallAndRemove< Node >
{

	private final EventFX< MouseEvent > onPress;

	private final EventFX< MouseEvent > onRelease;

	private final Consumer< MouseEvent > onPressConsumer;

	private final Consumer< MouseEvent > onReleaseConsumer;

	private final Predicate< MouseEvent >[] eventFilters;

	public MouseClickFX( final String name, final Consumer< MouseEvent > onReleaseConsumer, final Predicate< MouseEvent >... eventFilters )
	{
		this( name, event -> {}, onReleaseConsumer, eventFilters );
	}

	public MouseClickFX( final String name, final Consumer< MouseEvent > onPressConsumer, final Consumer< MouseEvent > onReleaseConsumer, final Predicate< MouseEvent >... eventFilters )
	{
		super();
		this.onPressConsumer = onPressConsumer;
		this.onReleaseConsumer = onReleaseConsumer;
		this.eventFilters = eventFilters;
		this.onPress = EventFX.MOUSE_PRESSED( name, this::press, this.eventFilters );
		this.onRelease = EventFX.MOUSE_RELEASED( name, this::release, event -> true );
	}

	private double startX;

	private double startY;

	private boolean isEvent;

	private final double tolerance = 1.0;

	private boolean testEvent( final MouseEvent event )
	{
		return Arrays.stream( eventFilters ).filter( filter -> filter.test( event ) ).count() > 0;
	}

	private void press( final MouseEvent event )
	{
		if ( testEvent( event ) )
		{
			startX = event.getX();
			startY = event.getY();
			isEvent = true;
			onPressConsumer.accept( event );
		}
	}

	private void release( final MouseEvent event )
	{
		if ( isEvent )
		{
			final double x = event.getX();
			final double y = event.getY();
			final double dX = x - startX;
			final double dY = y - startY;
			if ( dX * dX + dY * dY <= tolerance * tolerance )
				onReleaseConsumer.accept( event );
		}
		isEvent = false;
	}

	@Override
	public void installInto( final Node node )
	{
		onPress.installInto( node );
		onRelease.installInto( node );
	}

	@Override
	public void removeFrom( final Node node )
	{
		onPress.removeFrom( node );
		onRelease.removeFrom( node );
	}

}
