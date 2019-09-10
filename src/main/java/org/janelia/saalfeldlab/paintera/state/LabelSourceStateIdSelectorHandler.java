package org.janelia.saalfeldlab.paintera.state;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.NamedKeyCombination;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings;
import org.janelia.saalfeldlab.paintera.control.IdSelector;
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.paint.SelectNextId;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.function.LongPredicate;

public class LabelSourceStateIdSelectorHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final LongPredicate FOREGROUND_CHECK = id -> Label.isForeground(id);

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

	public EventHandler<Event> viewerHandler(
			final PainteraBaseView paintera,
			final KeyAndMouseBindings labelSourceStateBindings,
			final KeyTracker keyTracker) {
		return event -> {
			final EventTarget target = event.getTarget();
			if (!(target instanceof Node))
				return;
			Node node = (Node) target;
			LOG.trace("Handling event {} in target {}", event, target);
			// kind of hacky way to accomplish this:
			while (node != null) {
				if (node instanceof ViewerPanelFX) {
					handlers.computeIfAbsent((ViewerPanelFX) node, k -> this.makeHandler(paintera, labelSourceStateBindings, keyTracker, k)).handle(event);
					return;
				}
				node = node.getParent();
			}
		};
	}

	private EventHandler<Event> makeHandler(
			final PainteraBaseView paintera,
			final KeyAndMouseBindings labelSourceStateBindings,
			final KeyTracker keyTracker,
			final ViewerPanelFX vp) {
		final IdSelector selector = new IdSelector(source, selectedIds, vp, FOREGROUND_CHECK);
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

		final NamedKeyCombination.CombinationMap keyBindings = labelSourceStateBindings.getKeyCombinations();

		handler.addOnKeyPressed(EventFX.KEY_PRESSED(
				LabelSourceState.BindingKeys.SELECT_ALL,
				e -> selector.selectAll(),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.SelectAll) && keyBindings.get(LabelSourceState.BindingKeys.SELECT_ALL).getPrimaryCombination().match(e)));
		handler.addOnKeyPressed(EventFX.KEY_PRESSED(
				LabelSourceState.BindingKeys.SELECT_ALL_IN_CURRENT_VIEW,
				e -> selector.selectAllInCurrentView(vp),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.SelectAll) && keyBindings.get(LabelSourceState.BindingKeys.SELECT_ALL_IN_CURRENT_VIEW).getPrimaryCombination().match(e)));
		handler.addOnKeyPressed(EventFX.KEY_PRESSED(
				LabelSourceState.BindingKeys.LOCK_SEGEMENT,
				e -> selector.toggleLock(assignment, lockedSegments),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Lock) && keyBindings.get(LabelSourceState.BindingKeys.LOCK_SEGEMENT).getPrimaryCombination().match(e)));

		final SourceInfo sourceInfo = paintera.sourceInfo();
		final SelectNextId nextId = new SelectNextId(sourceInfo);
		handler.addOnKeyPressed(EventFX.KEY_PRESSED(
				LabelSourceState.BindingKeys.NEXT_ID,
				e -> nextId.getNextId(),
				e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.CreateNew) && keyBindings.get(LabelSourceState.BindingKeys.NEXT_ID).getPrimaryCombination().match(e)));
		return handler;
	}
}
