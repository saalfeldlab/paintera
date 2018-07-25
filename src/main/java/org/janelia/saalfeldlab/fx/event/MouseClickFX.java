package org.janelia.saalfeldlab.fx.event;

import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

public class MouseClickFX implements InstallAndRemove<Node>
{

	private final EventFX<MouseEvent> onPress;

	private final EventFX<MouseEvent> onRelease;

	private final Consumer<MouseEvent> onPressConsumer;

	private final Consumer<MouseEvent> onReleaseConsumer;

	private final Predicate<MouseEvent> eventFilter;

	public MouseClickFX(final String name, final Consumer<MouseEvent> onReleaseConsumer, final Predicate<MouseEvent>
			eventFilter)
	{
		this(name, event -> {
		}, onReleaseConsumer, eventFilter);
	}

	public MouseClickFX(final String name, final Consumer<MouseEvent> onPressConsumer, final Consumer<MouseEvent>
			onReleaseConsumer, final Predicate<MouseEvent> eventFilters)
	{
		super();
		this.onPressConsumer = onPressConsumer;
		this.onReleaseConsumer = onReleaseConsumer;
		this.eventFilter = eventFilters;
		this.onPress = EventFX.MOUSE_PRESSED(name, this::press, this.eventFilter);
		this.onRelease = EventFX.MOUSE_RELEASED(name, this::release, event -> isEvent);
	}

	private double startX;

	private double startY;

	private boolean isEvent;

	private final double tolerance = 1.0;

	private boolean testEvent(final MouseEvent event)
	{
		return eventFilter.test(event);
	}

	private void press(final MouseEvent event)
	{
		if (testEvent(event))
		{
			startX = event.getX();
			startY = event.getY();
			isEvent = true;
			onPressConsumer.accept(event);
		}
	}

	private void release(final MouseEvent event)
	{
		final double x  = event.getX();
		final double y  = event.getY();
		final double dX = x - startX;
		final double dY = y - startY;
		if (dX * dX + dY * dY <= tolerance * tolerance)
		{
			onReleaseConsumer.accept(event);
		}
		isEvent = false;
	}

	@Override
	public void installInto(final Node node)
	{
		onPress.installInto(node);
		onRelease.installInto(node);
	}

	@Override
	public void removeFrom(final Node node)
	{
		onPress.removeFrom(node);
		onRelease.removeFrom(node);
	}

}
