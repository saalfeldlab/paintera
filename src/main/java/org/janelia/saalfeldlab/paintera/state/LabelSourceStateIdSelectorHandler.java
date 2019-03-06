package org.janelia.saalfeldlab.paintera.state;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.IdSelector;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;

public class LabelSourceStateIdSelectorHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SelectedIds selectedIds;

	private final FragmentSegmentAssignment assignment;

	private final LockedSegments lockedSegments;

	private final HashMap<ViewerPanelFX, EventHandler<Event>> handlers = new HashMap<>();

	public LabelSourceStateIdSelectorHandler(
			final SelectedIds selectedIds,
			final FragmentSegmentAssignment assignment,
			final LockedSegments lockedSegments) {
		this.selectedIds = selectedIds;
		this.assignment = assignment;
		this.lockedSegments = lockedSegments;
	}

	public EventHandler<Event> viewerHandler(final PainteraBaseView paintera, final KeyTracker keyTracker) {
		return event -> {
			final EventTarget target = event.getTarget();
			if (!(target instanceof Node))
				return;
			Node node = (Node) target;
			LOG.trace("Handling event {} in target {}", event, target);
			// kind of hacky way to accomplish this:
			while (node != null) {
				if (node instanceof ViewerPanelFX) {
					handlers.computeIfAbsent((ViewerPanelFX) node, k -> this.makeHandler(paintera, keyTracker, k)).handle(event);
					return;
				}
				node = node.getParent();
			}
		};
	}

	private EventHandler<Event> makeHandler(PainteraBaseView paintera, KeyTracker keyTracker, ViewerPanelFX vp) {
		final IdSelector selector = new IdSelector(vp, paintera.sourceInfo());
		final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
		handler.addMouseHandler(selector.selectFragmentWithMaximumCount(
				"toggle single id",
				event -> event.isPrimaryButtonDown() && keyTracker.noKeysActive()).handler());
		handler.addMouseHandler(selector.appendFragmentWithMaximumCount(
				"append id",
				event -> event.isSecondaryButtonDown() && keyTracker.noKeysActive()).handler());
		handler.addKeyHandler(EventFX.KEY_PRESSED(
				"lock segment",
				e -> selector.toggleLock(selectedIds, assignment, lockedSegments),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.L)));
		return handler;
	}
}
