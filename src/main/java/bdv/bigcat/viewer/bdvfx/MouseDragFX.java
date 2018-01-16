package bdv.bigcat.viewer.bdvfx;

import java.util.Arrays;
import java.util.function.Predicate;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

public abstract class MouseDragFX implements InstallAndRemove< Node >
{
	protected double startX = 0;

	protected double startY = 0;

	private final SimpleBooleanProperty isDragging = new SimpleBooleanProperty();

	private final DragDetect detect = new DragDetect();

	private final Drag drag = new Drag();

	private final DragRelease release = new DragRelease();

	private final String name;

	private final Predicate< MouseEvent >[] eventFilter;

	protected final Object transformLock;

	protected final boolean consume;

	public MouseDragFX( final String name, final Predicate< MouseEvent >[] eventFilter, final Object transformLock )
	{
		this( name, eventFilter, false, transformLock );
	}

	public MouseDragFX( final String name, final Predicate< MouseEvent >[] eventFilter, final boolean consume, final Object transformLock )
	{
		super();
		this.name = name;
		this.eventFilter = eventFilter;
		this.transformLock = transformLock;
		this.consume = consume;
	}

	public abstract void initDrag( MouseEvent event );

	public abstract void drag( MouseEvent event );

	public void endDrag( final MouseEvent event )
	{}

	public String name()
	{
		return name;
	}

	@Override
	public void installInto( final Node node )
	{
		node.addEventHandler( MouseEvent.DRAG_DETECTED, detect );
		node.addEventHandler( MouseEvent.MOUSE_DRAGGED, drag );
		node.addEventHandler( MouseEvent.MOUSE_RELEASED, release );
	}

	@Override
	public void removeFrom( final Node node )
	{
		node.removeEventHandler( MouseEvent.DRAG_DETECTED, detect );
		node.removeEventHandler( MouseEvent.MOUSE_DRAGGED, drag );
		node.removeEventHandler( MouseEvent.MOUSE_RELEASED, release );
	}

	public void installIntoAsFilter( final Node node )
	{
		node.addEventFilter( MouseEvent.DRAG_DETECTED, detect );
		node.addEventFilter( MouseEvent.MOUSE_DRAGGED, drag );
		node.addEventFilter( MouseEvent.MOUSE_RELEASED, release );
	}

	public void removeFromAsFilter( final Node node )
	{
		node.removeEventFilter( MouseEvent.DRAG_DETECTED, detect );
		node.removeEventFilter( MouseEvent.MOUSE_DRAGGED, drag );
		node.removeEventFilter( MouseEvent.MOUSE_RELEASED, release );
	}

	private class DragDetect implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			if ( Arrays.stream( eventFilter ).filter( filter -> filter.test( event ) ).count() > 0 )
			{
				startX = event.getX();
				startY = event.getY();
				isDragging.set( true );
				initDrag( event );
			}
		}
	}

	private class Drag implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			if ( isDragging.get() )
				drag( event );
		}
	}

	private class DragRelease implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			final boolean wasDragging = isDragging.get();
			isDragging.set( false );
			if ( wasDragging )
				endDrag( event );
		}

	}

	public ObservableBooleanValue isDraggingProperty()
	{
		return this.isDragging;
	}

	public void abortDrag()
	{
		this.isDragging.set( false );
	}

}
