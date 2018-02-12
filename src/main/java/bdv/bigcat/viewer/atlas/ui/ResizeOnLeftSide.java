package bdv.bigcat.viewer.atlas.ui;

import java.util.Optional;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;

import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

public class ResizeOnLeftSide
{

	private final Node node;

	private final DoubleProperty width;

	private final DoublePredicate isWithinMarginOfBorder;

	private final BooleanProperty isCurrentlyWithinMarginOfBorder = new SimpleBooleanProperty( false );

	private final MouseMoved mouseMoved = new MouseMoved();

	private final MouseDragFX mouseDragged;

	private final double minWidth;

	private final double maxWidth;

	public ResizeOnLeftSide(
			final Node node,
			final DoubleProperty width,
			final DoublePredicate isWithinMarginOfBorder )
	{
		this( node, width, 50, 500, isWithinMarginOfBorder );
	}

	public ResizeOnLeftSide(
			final Node node,
			final DoubleProperty width,
			final double minWidth,
			final double maxWidth,
			final DoublePredicate isWithinMarginOfBorder )
	{
		super();
		this.node = node;
		this.width = width;
		this.minWidth = minWidth;
		this.maxWidth = maxWidth;
		this.isWithinMarginOfBorder = isWithinMarginOfBorder;

		this.mouseDragged = new MouseDragFX( "resize", new Predicate[] { event -> isCurrentlyWithinMarginOfBorder.get() }, true, this )
		{

			@Override
			public void initDrag( final MouseEvent event )
			{
				node.getScene().setCursor( Cursor.W_RESIZE );
			}

			@Override
			public void drag( final MouseEvent event )
			{
				final Bounds bounds = node.localToScene( node.getBoundsInLocal() );
				final double dx = event.getSceneX() - bounds.getMinX();
				width.set( Math.min( Math.max( width.get() - dx, ResizeOnLeftSide.this.minWidth ), ResizeOnLeftSide.this.maxWidth ) );
			}

			@Override
			public void endDrag( final MouseEvent event )
			{
				node.getScene().setCursor( Cursor.DEFAULT );
			}
		};

		isCurrentlyWithinMarginOfBorder.addListener( ( obs, oldv, newv ) -> {
			if ( !mouseDragged.isDraggingProperty().get() )
				Optional.ofNullable( node.getScene() ).ifPresent( s -> s.setCursor( newv ? Cursor.W_RESIZE : Cursor.DEFAULT ) );
		} );
	}

	public void install()
	{
		node.getParent().addEventFilter( MouseEvent.MOUSE_MOVED, mouseMoved );
		this.mouseDragged.installIntoAsFilter( node.getParent() );
	}

	public void remove()
	{
		node.getParent().removeEventFilter( MouseEvent.MOUSE_MOVED, mouseMoved );
		this.mouseDragged.removeFromAsFilter( node.getParent() );
		this.isCurrentlyWithinMarginOfBorder.set( false );
		this.mouseDragged.abortDrag();
	}

	private class MouseMoved implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			final Bounds bounds = node.getBoundsInParent();
			final double y = event.getY();
			isCurrentlyWithinMarginOfBorder.set( y >= bounds.getMinY() && y <= bounds.getMaxY() && isWithinMarginOfBorder.test( event.getX() - bounds.getMinX() ) );
		}
	}

}
