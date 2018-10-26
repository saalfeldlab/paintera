package bdv.fx.viewer;

import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.junit.Assert;
import org.junit.Test;

public class MouseCoordinateTrackerTest {

	@Test
	public void test()
	{
		final Node n = new Region();
		final MouseCoordinateTracker tracker = new MouseCoordinateTracker();
		tracker.installInto(n);

		// initially not inside
		Assert.assertFalse(tracker.getIsInside());

		// move inside
		n.fireEvent(createEvent(MouseEvent.MOUSE_ENTERED, 0, 0));
		Assert.assertTrue(tracker.getIsInside());

		// move position
		n.fireEvent(createEvent(MouseEvent.MOUSE_MOVED, 1, 2));
		Assert.assertEquals(1.0, tracker.getMouseX(), 0.0);
		Assert.assertEquals(2.0, tracker.getMouseY(), 0.0);

		// drag position
		n.fireEvent(createEvent(MouseEvent.MOUSE_DRAGGED, -1, -2));
		Assert.assertEquals(-1.0, tracker.getMouseX(), 0.0);
		Assert.assertEquals(-2.0, tracker.getMouseY(), 0.0);

		// move outside
		n.fireEvent(createEvent(MouseEvent.MOUSE_EXITED, 0, 0));
		Assert.assertFalse(tracker.getIsInside());

		// after removing, state should not change!
		tracker.removeFrom(n);
		final double x = tracker.getMouseX();
		final double y = tracker.getMouseY();
		final boolean isInside = tracker.getIsInside();

		Assert.assertEquals(isInside, tracker.getIsInside());

		// move inside
		n.fireEvent(createEvent(MouseEvent.MOUSE_ENTERED, 0, 0));
		Assert.assertEquals(isInside, tracker.getIsInside());

		// move position
		n.fireEvent(createEvent(MouseEvent.MOUSE_MOVED, 3, 4));
		Assert.assertEquals(x, tracker.getMouseX(), 0.0);
		Assert.assertEquals(y, tracker.getMouseY(), 0.0);

		// drag position
		n.fireEvent(createEvent(MouseEvent.MOUSE_DRAGGED, -3, -4));
		Assert.assertEquals(x, tracker.getMouseX(), 0.0);
		Assert.assertEquals(y, tracker.getMouseY(), 0.0);

		// move outside
		n.fireEvent(createEvent(MouseEvent.MOUSE_EXITED, 0, 0));
		Assert.assertEquals(isInside, tracker.getIsInside());

	}

	private static final MouseEvent createEvent(EventType<MouseEvent> type, double x, double y)
	{
		return new MouseEvent(
				type,
				x,
				y,
				0.0,
				0.0,
				MouseButton.PRIMARY,
				1,
				false,
				false,
				false,
				false,
				true,
				false,
				false,
				false,
				false,
				false,
				null);
	}

}
