package bdv.fx.viewer;

import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.janelia.saalfeldlab.bdv.fx.viewer.MouseCoordinateTracker;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MouseCoordinateTrackerTest {

	@Test
	public void test() {

		final Node node = new Region();
		final MouseCoordinateTracker tracker = new MouseCoordinateTracker();
		ActionSet.installActionSet(node, tracker.getActions());

		// initially not inside
		assertFalse(tracker.getIsInside());

		// move inside
		node.fireEvent(createEvent(MouseEvent.MOUSE_ENTERED, 0, 0));
		assertTrue(tracker.getIsInside());

		// move position
		node.fireEvent(createEvent(MouseEvent.MOUSE_MOVED, 1, 2));
		assertEquals(1.0, tracker.getMouseX(), 0.0);
		assertEquals(2.0, tracker.getMouseY(), 0.0);

		// drag position
		node.fireEvent(createEvent(MouseEvent.MOUSE_DRAGGED, -1, -2));
		assertEquals(-1.0, tracker.getMouseX(), 0.0);
		assertEquals(-2.0, tracker.getMouseY(), 0.0);

		// move outside
		node.fireEvent(createEvent(MouseEvent.MOUSE_EXITED, 0, 0));
		assertFalse(tracker.getIsInside());

		// after removing, state should not change!
		ActionSet.removeActionSet(node, tracker.getActions());
		final double x = tracker.getMouseX();
		final double y = tracker.getMouseY();
		final boolean isInside = tracker.getIsInside();

		assertEquals(isInside, tracker.getIsInside());

		// move inside
		node.fireEvent(createEvent(MouseEvent.MOUSE_ENTERED, 0, 0));
		assertEquals(isInside, tracker.getIsInside());

		// move position
		node.fireEvent(createEvent(MouseEvent.MOUSE_MOVED, 3, 4));
		assertEquals(x, tracker.getMouseX(), 0.0);
		assertEquals(y, tracker.getMouseY(), 0.0);

		// drag position
		node.fireEvent(createEvent(MouseEvent.MOUSE_DRAGGED, -3, -4));
		assertEquals(x, tracker.getMouseX(), 0.0);
		assertEquals(y, tracker.getMouseY(), 0.0);

		// move outside
		node.fireEvent(createEvent(MouseEvent.MOUSE_EXITED, 0, 0));
		assertEquals(isInside, tracker.getIsInside());

	}

	private static MouseEvent createEvent(EventType<MouseEvent> type, double x, double y) {

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
