package bdv.fx.viewer;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import org.janelia.saalfeldlab.fx.event.InstallAndRemove;

/**
 * Event filter that tracks both the x and y positions of the mouse inside a node as well whether or not the mouse is inside that node.
 */
public class MouseCoordinateTracker implements InstallAndRemove<Node> {



	private final SimpleDoubleProperty mouseX = new SimpleDoubleProperty();

	private final SimpleDoubleProperty mouseY = new SimpleDoubleProperty();

	private final SimpleBooleanProperty isInside = new SimpleBooleanProperty(false);

	private final EventHandler<MouseEvent> movedTracker = event -> setPosition(event.getX(), event.getY());

	private final EventHandler<MouseEvent> draggedTracker = event -> setPosition(event.getX(), event.getY());

	private final EventHandler<MouseEvent> enteredTracker = event -> isInside.set(true);

	private final EventHandler<MouseEvent> exitedTracker = event -> isInside.set(false);

	@Override
	public void installInto(Node node) {
		node.addEventFilter(MouseEvent.MOUSE_MOVED, movedTracker);
		node.addEventFilter(MouseEvent.MOUSE_DRAGGED, draggedTracker);
		node.addEventFilter(MouseEvent.MOUSE_ENTERED, enteredTracker);
		node.addEventFilter(MouseEvent.MOUSE_EXITED, exitedTracker);
	}

	@Override
	public void removeFrom(Node node) {
		node.removeEventFilter(MouseEvent.MOUSE_MOVED, movedTracker);
		node.removeEventFilter(MouseEvent.MOUSE_DRAGGED, draggedTracker);
		node.removeEventFilter(MouseEvent.MOUSE_ENTERED, enteredTracker);
		node.removeEventFilter(MouseEvent.MOUSE_EXITED, exitedTracker);
	}

	/**
	 *
	 * @return read-only property that is {@code true} if the mouse is inside the {@link Node} it was installed into,
	 * {@code false} otherwise
	 */
	public ReadOnlyBooleanProperty isInsideProperty()
	{
		return this.isInside;
	}

	/**
	 *
	 * @return read-only property that is tracks the x coordinate of the mouse relative to the node this was installed into.
	 */
	public ReadOnlyDoubleProperty mouseXProperty()
	{
		return this.mouseX;
	}


	/**
	 *
	 * @return read-only property that is tracks the y coordinate of the mouse relative to the node this was installed into.
	 */
	public ReadOnlyDoubleProperty mouseYProperty()
	{
		return this.mouseY;
	}

	/**
	 *
	 * @return {@code true} if the mouse is inside the {@link Node} it was installed into,
	 * {@code false} otherwise
	 */
	public synchronized boolean getIsInside()
	{
		return this.isInside.get();
	}

	/**
	 *
	 * @return x coordinate of the mouse relative to the node this was installed into.
	 */
	public synchronized double getMouseX()
	{
		return this.mouseX.doubleValue();
	}

	/**
	 *
	 * @return y coordinate of the mouse relative to the node this was installed into.
	 */
	public synchronized double getMouseY()
	{
		return this.mouseY.doubleValue();
	}

	private synchronized void setPosition(final double x, final double y)
	{
		mouseX.set(x);
		mouseY.set(y);
	}
}
