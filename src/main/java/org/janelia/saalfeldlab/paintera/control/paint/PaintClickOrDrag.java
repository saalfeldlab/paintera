package org.janelia.saalfeldlab.paintera.control.paint;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.exception.PainteraException;
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class PaintClickOrDrag implements InstallAndRemove<Node> {

  private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static class IllegalIdForPainting extends PainteraException {

	private final Long id;

	public IllegalIdForPainting(Long id) {

	  super("Cannot paint this id: " + id);
	  this.id = id;
	}

	public Long getId() {

	  return id;
	}
  }

  private static final class Position {

	public double x;

	public double y;

	public void update(final double x, final double y) {

	  this.x = x;
	  this.y = y;
	}

	public void update(final MouseEvent e) {

	  this.update(e.getX(), e.getY());
	}

	@Override
	public String toString() {

	  return String.format("<Position: (%f, %f)>", x, y);
	}
  }

  private static final Predicate<UnsignedLongType> FOREGROUND_CHECK = t -> Label.isForeground(t.get());

  private final PainteraBaseView paintera;

  private final ViewerPanelFX viewer;

  private final Supplier<Long> paintId;

  private final DoubleSupplier brushRadius;

  private final DoubleSupplier brushDepth;

  private final Predicate<MouseEvent> check;

  private final EventHandler<MouseEvent> onPress;

  private final EventHandler<MouseEvent> onDragOrMove;

  private final EventHandler<MouseEvent> onRelease;

  private boolean isPainting = false;

  private Mask<UnsignedLongType> mask = null;

  private MaskedSource<?, ?> paintIntoThis = null;

  private long fillLabel = 0;

  private Interval interval = null;

  private final AffineTransform3D labelToGlobalTransform = new AffineTransform3D();

  private final AffineTransform3D labelToViewerTransform = new AffineTransform3D();

  private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

  private final Position position = new Position();

  public PaintClickOrDrag(
		  final PainteraBaseView paintera,
		  final ViewerPanelFX viewer,
		  final Supplier<Long> paintId,
		  final DoubleSupplier brushRadius,
		  final DoubleSupplier brushDepth,
		  final Predicate<MouseEvent> check) {

	this.paintera = paintera;
	this.viewer = viewer;
	this.paintId = paintId;
	this.brushRadius = brushRadius;
	this.brushDepth = brushDepth;
	this.check = check;

	this.onPress = event -> {

	  LOG.debug("Entering on click event handler: {}", event);

	  synchronized (PaintClickOrDrag.this) {
		if (getIsPainting()) {
		  LOG.debug("Already painting -- will not start new paint.");
		  return;
		}
		if (!check.test(event)) {
		  LOG.debug("Event did not pass check -- will not start new paint.");
		  return;
		}

		event.consume();

		try {
		  final Source<?> currentSource = paintera.sourceInfo().currentSourceProperty().get();
		  if (!(currentSource instanceof MaskedSource<?, ?>))
			return;
		  final MaskedSource<?, ?> source = (MaskedSource<?, ?>)currentSource;
		  final ViewerState state = viewer.getState();
		  final AffineTransform3D screenScaleTransform = new AffineTransform3D();
		  viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
		  final AffineTransform3D viewerTransform = new AffineTransform3D();
		  final int level;
		  synchronized (state) {
			state.getViewerTransform(viewerTransform);
			level = state.getBestMipMapLevel(screenScaleTransform, currentSource);
		  }
		  source.getSourceTransform(0, level, labelToGlobalTransform);
		  this.labelToViewerTransform.set(viewerTransform.copy().concatenate(labelToGlobalTransform));
		  this.globalToViewerTransform.set(viewerTransform);
		  final Long id = paintId.get();
		  if (id == null)
			throw new IllegalIdForPainting(id);
		  this.mask = source.generateMask(new MaskInfo<>(0, level, new UnsignedLongType(id)), FOREGROUND_CHECK);
		  this.isPainting = true;
		  this.fillLabel = 1;
		  this.interval = null;
		  this.paintIntoThis = source;
		  position.update(event);
		  paint(position.x, position.y);
		}
		// TODO should this be more specific? I think that we should never enter a painting state
		// TODO when an exception occurs
		catch (final Exception e) {
		  InvokeOnJavaFXApplicationThread.invoke(() ->
				  Exceptions.exceptionAlert(Paintera.Constants.NAME, "Unable to paint.", e).show());
		  release();
		}
	  }

	};

	this.onDragOrMove = event -> {

	  synchronized (PaintClickOrDrag.this) {
		if (!getIsPainting()) {
		  LOG.trace("Not currently painting -- will not paint");
		  return;
		}

		event.consume();

		try {
		  double x = event.getX();
		  double y = event.getY();
		  if (x != this.position.x || y != this.position.y) {
			final double[] initialPos = new double[]{position.x, position.y};

			LOG.debug("Drag: paint at screen=({},{}) / start={}", x, y, position);

			final double[] dragPos = new double[]{x, y};

			LinAlgHelpers.subtract(dragPos, initialPos, dragPos);

			final double draggedDistance = LinAlgHelpers.length(dragPos);
			LinAlgHelpers.normalize(dragPos);

			LOG.debug("Number of paintings triggered {}", draggedDistance + 1);

			final long t0 = System.currentTimeMillis();
			for (int i = 0; i < draggedDistance; ++i) {
			  paint(initialPos[0], initialPos[1]);
			  LinAlgHelpers.add(initialPos, dragPos, initialPos);
			}
			paint(x, y);
			final long t1 = System.currentTimeMillis();
			LOG.debug(
					"Painting {} times with radius {} took a total of {}ms",
					draggedDistance + 1,
					brushRadius.getAsDouble(),
					t1 - t0
			);
		  }
		} finally {
		  this.position.update(event);
		}
	  }
	};

	this.onRelease = event -> {
	  synchronized (PaintClickOrDrag.this) {
		try {
		  if (!getIsPainting()) {
			LOG.debug("Not currently painting -- will not do anything");
			return;
		  }

		  if (this.paintIntoThis == null) {
			LOG.debug("No current source available -- will not do anything");
			return;
		  }

		  try {
			this.paintIntoThis.applyMask(this.mask, this.interval, FOREGROUND_CHECK);
		  } catch (final Exception e) {
			InvokeOnJavaFXApplicationThread.invoke(() ->
					Exceptions.exceptionAlert(Paintera.Constants.NAME, "Exception when trying to submit mask.", e).show());
		  }
		}
		// always release
		finally {
		  release();
		}
	  }
	};
  }

  @Override
  public void installInto(final Node node) {

	node.addEventHandler(MouseEvent.MOUSE_PRESSED, onPress);
	node.addEventHandler(MouseEvent.MOUSE_DRAGGED, onDragOrMove);
	node.addEventHandler(MouseEvent.MOUSE_MOVED, onDragOrMove);
	node.addEventHandler(MouseEvent.MOUSE_RELEASED, onRelease);
  }

  @Override
  public void removeFrom(final Node node) {

	node.removeEventHandler(MouseEvent.MOUSE_PRESSED, onPress);
	node.removeEventHandler(MouseEvent.MOUSE_DRAGGED, onDragOrMove);
	node.removeEventHandler(MouseEvent.MOUSE_MOVED, onDragOrMove);
	node.removeEventHandler(MouseEvent.MOUSE_RELEASED, onRelease);
  }

  public EventHandler<MouseEvent> singleEventHandler() {

	return event -> {
	  final EventType<? extends MouseEvent> eventType = event.getEventType();
	  if (MouseEvent.MOUSE_PRESSED.equals(eventType)) {
		LOG.debug("Single event handler: Is pressed");
		onPress.handle(event);
	  } else if (MouseEvent.MOUSE_DRAGGED.equals(eventType) || MouseEvent.MOUSE_MOVED.equals(eventType))
		onDragOrMove.handle(event);
	  else if (MouseEvent.MOUSE_RELEASED.equals(eventType))
		onRelease.handle(event);
	};
  }

  private synchronized boolean getIsPainting() {

	return this.isPainting;
  }

  private synchronized RandomAccessibleInterval<UnsignedLongType> getMaskIfIsPaintingOrNull() {

	return getIsPainting() && this.mask != null ? this.mask.mask : null;
  }

  private synchronized void paint(final double viewerX, final double viewerY) {

	LOG.debug("At {} {}", viewerX, viewerY);

	if (!this.isPainting) {
	  LOG.debug("Not currently activated for painting, returning without action");
	  return;
	}

	final RandomAccessibleInterval<UnsignedLongType> mask = getMaskIfIsPaintingOrNull();
	if (mask == null) {
	  LOG.debug("Current mask is null, returning without action");
	  return;
	}
	final int orthoAxis =
			this.paintera.orthogonalViews().topLeft().viewer() == viewer ? 2 : this.paintera.orthogonalViews().topRight().viewer() == viewer ? 0 : 1;
	final double radius = brushRadius.getAsDouble();
	final Interval trackedInterval = Paint2D.paint(
			Views.extendValue(mask, new UnsignedLongType(Label.INVALID)),
			this.fillLabel,
			orthoAxis,
			viewerX,
			viewerY,
			radius,
			brushDepth.getAsDouble(),
			labelToViewerTransform,
			globalToViewerTransform,
			labelToGlobalTransform);
	this.interval = this.interval == null
			? trackedInterval
			: Intervals.union(trackedInterval, this.interval);
	++this.fillLabel;

	final RealInterval trackedIntervalInGlobalSpace = IntervalHelpers.extendAndTransformBoundingBox(trackedInterval, labelToGlobalTransform, 0.5);
	this.paintera.orthogonalViews().requestRepaint(trackedIntervalInGlobalSpace);

  }

  private void release() {

	this.mask = null;
	this.isPainting = false;
	this.interval = null;
	this.paintIntoThis = null;
  }

}
