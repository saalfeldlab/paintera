package org.janelia.saalfeldlab.paintera.state;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.IdSelector;
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.paint.SelectNextId;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import net.imglib2.type.numeric.IntegerType;

public class LabelSourceStateIdSelectorHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<? extends IntegerType<?>, ?> source;

	private final SelectedIds selectedIds;

	private final FragmentSegmentAssignment assignment;

	private final LockedSegments lockedSegments;

	private final HashMap<ViewerPanelFX, EventHandler<Event>> handlers = new HashMap<>();

	public LabelSourceStateIdSelectorHandler(
			final DataSource<? extends IntegerType<?>, ?> source,
			final SelectedIds selectedIds,
			final FragmentSegmentAssignment assignment,
			final LockedSegments lockedSegments) {
		this.source = source;
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

	private EventHandler<Event> makeHandler(final PainteraBaseView paintera, final KeyTracker keyTracker, final ViewerPanelFX vp) {
		final IdSelector selector = new IdSelector(source, selectedIds, vp);
		final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
		// TODO event handlers should probably not be on ANY/RELEASED but on PRESSED
		handler.addEventHandler(MouseEvent.ANY, selector.selectFragmentWithMaximumCount(
				"toggle single id",
				event -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Toggle) && event.isPrimaryButtonDown() && keyTracker.noKeysActive()).handler());
		handler.addEventHandler(MouseEvent.ANY, selector.appendFragmentWithMaximumCount(
				"append id",
				event -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Append) &&
						((event.isSecondaryButtonDown() && keyTracker.noKeysActive()) ||
						(event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL))))
			.handler());
		handler.addOnKeyPressed(EventFX.KEY_PRESSED(
				"lock segment",
				e -> selector.toggleLock(selectedIds, assignment, lockedSegments),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Lock) && keyTracker.areOnlyTheseKeysDown(KeyCode.L)));

		final SourceInfo sourceInfo = paintera.sourceInfo();
		final SelectNextId nextId = new SelectNextId(sourceInfo);
		handler.addOnKeyPressed(EventFX.KEY_PRESSED(
				"next id",
				event -> nextId.getNextId(),
				event -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.CreateNew) && keyTracker.areOnlyTheseKeysDown(KeyCode.N)));
		return handler;
	}
}
