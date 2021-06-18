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
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.function.LongPredicate;

public class LabelSourceStateIdSelectorHandler {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final LongPredicate FOREGROUND_CHECK = Label::isForeground;

  private final DataSource<? extends IntegerType<?>, ?> source;

  private final IdService idService;

  private final SelectedIds selectedIds;

  private final FragmentSegmentAssignment assignment;

  private final LockedSegments lockedSegments;

  private final HashMap<ViewerPanelFX, EventHandler<Event>> handlers = new HashMap<>();

  public LabelSourceStateIdSelectorHandler(
		  final DataSource<? extends IntegerType<?>, ?> source,
		  final IdService idService,
		  final SelectedIds selectedIds,
		  final FragmentSegmentAssignment assignment,
		  final LockedSegments lockedSegments) {

	this.source = source;
	this.idService = idService;
	this.selectedIds = selectedIds;
	this.assignment = assignment;
	this.lockedSegments = lockedSegments;
  }

  public EventHandler<Event> viewerHandler(
		  final PainteraBaseView paintera,
		  final KeyAndMouseBindings labelSourceStateBindings,
		  final KeyTracker keyTracker,
		  final String bindingKeySelectAll,
		  final String bindingKeySelectAllInCurrentView,
		  final String bindingKeyLockSegment,
		  final String bindingKeyNextId) {

	return event -> {
	  final EventTarget target = event.getTarget();
	  if (!(target instanceof Node))
		return;
	  Node node = (Node)target;
	  LOG.trace("Handling event {} in target {}", event, target);
	  // kind of hacky way to accomplish this:
	  while (node != null) {
		if (node instanceof ViewerPanelFX) {
		  handlers.computeIfAbsent((ViewerPanelFX)node, k -> this.makeHandler(
				  paintera,
				  labelSourceStateBindings,
				  keyTracker,
				  k,
				  bindingKeySelectAll,
				  bindingKeySelectAllInCurrentView,
				  bindingKeyLockSegment,
				  bindingKeyNextId)).handle(event);
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
		  final ViewerPanelFX vp,
		  final String bindingKeySelectAll,
		  final String bindingKeySelectAllInCurrentView,
		  final String bindingKeyLockSegment,
		  final String bindingKeyNextId) {

	final IdSelector selector = new IdSelector(source, selectedIds, vp, FOREGROUND_CHECK);
	final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
	// TODO event handlers should probably not be on ANY/RELEASED but on PRESSED
	handler.addEventHandler(MouseEvent.ANY, selector.selectFragmentWithMaximumCount(
			"toggle single id",
			event -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Toggle) && event.isPrimaryButtonDown() && keyTracker.noKeysActive())
			.getHandler());
	handler.addEventHandler(MouseEvent.ANY, selector.appendFragmentWithMaximumCount(
			"append id",
			event -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Append) &&
					((event.isSecondaryButtonDown() && keyTracker.noKeysActive()) ||
							(event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL))))
			.getHandler());

	final NamedKeyCombination.CombinationMap keyBindings = labelSourceStateBindings.getKeyCombinations();

	handler.addOnKeyPressed(EventFX.KEY_PRESSED(
			bindingKeySelectAll,
			e -> selector.selectAll(),
			e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.SelectAll) && keyBindings.get(bindingKeySelectAll).getPrimaryCombination()
					.match(e)));
	handler.addOnKeyPressed(EventFX.KEY_PRESSED(
			bindingKeySelectAllInCurrentView,
			e -> selector.selectAllInCurrentView(vp),
			e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.SelectAll) && keyBindings.get(bindingKeySelectAllInCurrentView)
					.getPrimaryCombination().match(e)));
	handler.addOnKeyPressed(EventFX.KEY_PRESSED(
			bindingKeyLockSegment,
			e -> selector.toggleLock(assignment, lockedSegments),
			e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.Lock) && keyBindings.get(bindingKeyLockSegment).getPrimaryCombination()
					.match(e)));

	final SelectNextId nextId = new SelectNextId(idService, selectedIds);
	handler.addOnKeyPressed(EventFX.KEY_PRESSED(
			bindingKeyNextId,
			e -> nextId.getNextId(),
			e -> paintera.allowedActionsProperty().get().isAllowed(LabelActionType.CreateNew) && keyBindings.get(bindingKeyNextId).getPrimaryCombination()
					.match(e)));
	return handler;
  }
}
