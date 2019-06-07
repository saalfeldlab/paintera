package org.janelia.saalfeldlab.paintera.state;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.ControlUtils;
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType;
import org.janelia.saalfeldlab.paintera.control.paint.Fill2DOverlay;
import org.janelia.saalfeldlab.paintera.control.paint.FillOverlay;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill;
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D;
import org.janelia.saalfeldlab.paintera.control.paint.PaintActions2D;
import org.janelia.saalfeldlab.paintera.control.paint.PaintClickOrDrag;
import org.janelia.saalfeldlab.paintera.control.paint.RestrictPainting;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Source;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import net.imglib2.type.label.Label;

public class LabelSourceStatePaintHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SelectedIds selectedIds;

	private final HashMap<ViewerPanelFX, EventHandler<Event>> handlers = new HashMap<>();

	private final HashMap<ViewerPanelFX, PaintActions2D> painters = new HashMap<>();

	private final SimpleDoubleProperty brushRadius = new SimpleDoubleProperty(5.0);

	private final SimpleDoubleProperty brushRadiusIncrement = new SimpleDoubleProperty(1.0);

	private final SimpleDoubleProperty brushDepth = new SimpleDoubleProperty(1.0);

	public LabelSourceStatePaintHandler(final SelectedIds selectedIds) {
		this.selectedIds = selectedIds;
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

	public EventHandler<Event> viewerFilter(final PainteraBaseView paintera, final KeyTracker keyTracker) {
		return event -> {
			final EventTarget target = event.getTarget();
			if (MouseEvent.MOUSE_EXITED.equals(event.getEventType()) && target instanceof ViewerPanelFX)
				Optional.ofNullable(painters.get(target)).ifPresent(p -> p.setBrushOverlayVisible(false));
		};
	}

	private EventHandler<Event> makeHandler(final PainteraBaseView paintera, final KeyTracker keyTracker, final ViewerPanelFX t)
	{

		LOG.debug("Making handler with PainterBaseView {} key Tracker {} and ViewerPanelFX {}", paintera, keyTracker, t);
		final SourceInfo sourceInfo = paintera.sourceInfo();


		final DelegateEventHandlers.AnyHandler handler = DelegateEventHandlers.handleAny();
		//			if ( this.paintableViews.contains( this.viewerAxes.get( t ) ) )
		// TODO For now, only request repaint viewer that was painted into.
		// TODO In the future, transform painted interval appropriately and
		// TODO update all viewers
		final PaintActions2D paint2D = new PaintActions2D(
				t,
				sourceInfo,
				paintera.manager(),
				t::requestRepaint,
				paintera.getPaintQueue());
		paint2D.brushRadiusProperty().bindBidirectional(this.brushRadius);
		paint2D.brushRadiusIncrementProperty().bindBidirectional(this.brushRadiusIncrement);
		paint2D.brushDepthProperty().bindBidirectional(this.brushDepth);
		final ObjectProperty<Source<?>> currentSource = sourceInfo.currentSourceProperty();

		final Supplier<Long> paintSelection = () -> {

			final long lastSelection = selectedIds.getLastSelection();
			LOG.debug("Last selection is {}", lastSelection);
			return Label.regular(lastSelection) ? lastSelection : null;
		};

		painters.put(t, paint2D);

		final FloodFill fill = new FloodFill(t, sourceInfo, t::requestRepaint);
		final FloodFill2D fill2D = new FloodFill2D(t, sourceInfo, t::requestRepaint);
		fill2D.fillDepthProperty().bindBidirectional(this.brushDepth);
		final Fill2DOverlay fill2DOverlay = new Fill2DOverlay(t);
		fill2DOverlay.brushDepthProperty().bindBidirectional(this.brushDepth);
		final FillOverlay fillOverlay = new FillOverlay(t);

		final RestrictPainting restrictor = new RestrictPainting(t, sourceInfo, t::requestRepaint);

		// brush
		handler.addEventHandler(KeyEvent.KEY_PRESSED, EventFX.KEY_PRESSED(
				"show brush overlay",
				event -> {LOG.trace("Showing brush overlay!"); paint2D.showBrushOverlay();},
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Paint) && keyTracker.areKeysDown(KeyCode.SPACE)));

		handler.addEventHandler(KeyEvent.KEY_RELEASED, EventFX.KEY_RELEASED(
				"hide brush overlay",
				event -> {LOG.trace("Hiding brush overlay!"); paint2D.hideBrushOverlay();},
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Paint) && event.getCode().equals(KeyCode.SPACE) && !keyTracker.areKeysDown(KeyCode.SPACE)));

		handler.addOnScroll(EventFX.SCROLL(
				"change brush size",
				event -> paint2D.changeBrushRadius(event.getDeltaY()),
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.SetBrush) && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE)));

		handler.addOnScroll(EventFX.SCROLL(
				"change brush depth",
				event -> paint2D.changeBrushDepth(-ControlUtils.getBiggestScroll(event)),
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.SetBrush) &&
					(keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE, KeyCode.SHIFT) ||
					keyTracker.areOnlyTheseKeysDown(KeyCode.F) ||
					keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT,KeyCode.F))));

		handler.addOnKeyPressed(EventFX.KEY_PRESSED("show fill 2D overlay", event -> {
			fill2DOverlay.setVisible(true);
			fillOverlay.setVisible(false);
		}, event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Fill) && keyTracker.areOnlyTheseKeysDown(KeyCode.F)));

		handler.addOnKeyReleased(EventFX.KEY_RELEASED(
				"show fill 2D overlay",
				event -> fill2DOverlay.setVisible(false),
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Fill) && event.getCode().equals(KeyCode.F) && keyTracker.noKeysActive()));

		handler.addOnKeyPressed(EventFX.KEY_PRESSED("show fill overlay", event -> {
			fillOverlay.setVisible(true);
			fill2DOverlay.setVisible(false);
		}, event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Fill) && keyTracker.areOnlyTheseKeysDown(KeyCode.F, KeyCode.SHIFT)));

		handler.addOnKeyReleased(EventFX.KEY_RELEASED(
				"show fill overlay",
				event -> fillOverlay.setVisible(false),
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Fill) &&
					((event.getCode().equals(KeyCode.F) && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT)) ||
					(event.getCode().equals(KeyCode.SHIFT) && keyTracker.areOnlyTheseKeysDown(KeyCode.F))
			)));

		// paint
		final PaintClickOrDrag paintDrag = new PaintClickOrDrag(
				sourceInfo,
				t,
				paintSelection,
				brushRadius::get,
				brushDepth::get,
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Paint) && event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE));
		handler.addEventHandler(MouseEvent.ANY, paintDrag.singleEventHandler());

		// erase
		final PaintClickOrDrag eraseDrag = new PaintClickOrDrag(
				sourceInfo,
				t,
				() -> Label.TRANSPARENT,
				brushRadius::get,
				brushDepth::get,
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Erase) && event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE));
		handler.addEventHandler(MouseEvent.ANY, eraseDrag.singleEventHandler());

		// background
		final PaintClickOrDrag backgroundDrag = new PaintClickOrDrag(
				sourceInfo,
				t,
				() -> Label.BACKGROUND,
				brushRadius::get,
				brushDepth::get,
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Background) && event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE, KeyCode.SHIFT));
		handler.addEventHandler(MouseEvent.ANY, backgroundDrag.singleEventHandler());

		// advanced paint stuff
		handler.addOnMousePressed((EventFX.MOUSE_PRESSED(
				"fill",
				event -> fill.fillAt(event.getX(), event.getY(), paintSelection::get),
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Fill) && event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(
						KeyCode.SHIFT,
						KeyCode.F))));

		handler.addOnMousePressed(EventFX.MOUSE_PRESSED(
				"fill 2D",
				event -> fill2D.fillAt(event.getX(), event.getY(), paintSelection::get),
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Fill) && event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(KeyCode.F)));

		handler.addOnMousePressed(EventFX.MOUSE_PRESSED(
				"restrict",
				event -> restrictor.restrictTo(event.getX(), event.getY()),
				event -> paintera.allowedActionsProperty().get().isAllowed(PaintActionType.Restrict) && event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown(
						KeyCode.SHIFT,
						KeyCode.R)));

		return handler;

	}

	public SelectedIds selectedIdsFromState(final SourceState<?, ?> state)
	{
		return state instanceof HasSelectedIds
				? ((HasSelectedIds) state).selectedIds()
				: null;
	}

}
