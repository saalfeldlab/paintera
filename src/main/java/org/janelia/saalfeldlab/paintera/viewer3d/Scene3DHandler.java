package org.janelia.saalfeldlab.paintera.viewer3d;

import bdv.util.Affine3DHelpers;
import bdv.viewer.TransformListener;
import javafx.geometry.Point3D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Affine;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.actions.Action;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.actions.DragActionSet;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.ControlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.janelia.saalfeldlab.fx.actions.PainteraActionSetKt.painteraActionSet;

public class Scene3DHandler {

	public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final double SCROLL_MODIFIER = 1.05;
	private static final double SLOW_SCROLL_MODIFIER = SCROLL_MODIFIER - .04;
	private static final double FAST_SCROLL_MODIFIER = SCROLL_MODIFIER + .5;

	private static final double CENTER_X = 0;
	private static final double CENTER_Y = 0;
	private final Viewer3DFX viewer;
	private final Affine initialTransform = new Affine();

	private final Affine affine = new Affine();

	private final static double step = 1.0;// Math.PI / 180;

	private static final Point3D xNormal = new Point3D(1, 0, 0);

	private static final Point3D yNormal = new Point3D(0, 1, 0);
	private final List<TransformListener<Affine>> affineListeners = Collections.synchronizedList(new ArrayList<>());

	public Scene3DHandler(final Viewer3DFX viewer) {

		this.viewer = viewer;
		this.viewer.getSceneGroup().getTransforms().add(affine);

		this.setAffine(initialTransform);

		final var rotateActionSet = new Rotate3DView("rotate 3d");
		ActionSet.installActionSet(viewer, rotateActionSet);

		final var translateXYActionSet = new TranslateXY("translate");
		ActionSet.installActionSet(viewer, translateXYActionSet);

		final var additionalCommands = viewer3DCommands();
		ActionSet.installActionSet(viewer, additionalCommands);

		Paintera.whenPaintable(() -> {
			/* These depend on the keyTracker in the PainteraMainWindow, so cannot be installed until the MainWindow is done initializing. */
			final var zoomActionSet = zoom3D();
			ActionSet.installActionSet(viewer, zoomActionSet);
		});
	}

	public void setInitialTransformToInterval(final Interval interval) {

		initialTransform.setToIdentity();
		initialTransform.prependTranslation(
				-interval.min(0) - interval.dimension(0) / 2.0,
				-interval.min(1) - interval.dimension(1) / 2.0,
				-interval.min(2) - interval.dimension(2) / 2.0);
		final double sf = 1.0 / interval.dimension(0);
		initialTransform.prependScale(sf, sf, sf);
		InvokeOnJavaFXApplicationThread.invoke(() -> this.setAffine(initialTransform));
	}

	private ActionSet viewer3DCommands() {

		return new ActionSet("3D Viewer Commands", actionSet ->
				actionSet.addMouseAction(MouseEvent.MOUSE_CLICKED, mouseAction -> {
					mouseAction.setName("Viewer 3D Context Menu");
					mouseAction.verify(event -> !Paintera.getPaintera().getMouseTracker().isDragging());

					mouseAction.onAction(event ->
							InvokeOnJavaFXApplicationThread.invoke(() -> {
								if (event.getButton() == MouseButton.SECONDARY) {
									viewer.getContextMenu().show(viewer, event.getScreenX(), event.getScreenY());
								} else {
									viewer.getContextMenu().hide();
								}
							}));
				}));
	}

	private ActionSet zoom3D() {

		return painteraActionSet("3D Viewer Zoom", actionSet -> {
			final var normalZoom = new Action<>(ScrollEvent.SCROLL);
			normalZoom.setKeyTracker(actionSet.getKeyTracker());
			normalZoom.setName("Zoom");
			normalZoom.verify(event -> !event.isShiftDown());
			normalZoom.verify(event -> !event.isControlDown());
			normalZoom.onAction(event -> {
				final double scroll = ControlUtils.getBiggestScroll(event);
				if (scroll == 0)
					return;
				double scrollFactor = scroll > 0 ? SCROLL_MODIFIER : 1 / SCROLL_MODIFIER;
				zoom(scrollFactor);
			});
			actionSet.addAction(normalZoom);

			final var slowZoom = new Action<>(ScrollEvent.SCROLL);
			slowZoom.setKeyTracker(actionSet.getKeyTracker());
			slowZoom.setName("Slow Zoom");
			slowZoom.keysDown(KeyCode.SHIFT, KeyCode.CONTROL);
			slowZoom.onAction(event -> {
				final double scroll = ControlUtils.getBiggestScroll(event);
				if (scroll == 0)
					return;
				double scrollFactor = scroll > 0 ? SLOW_SCROLL_MODIFIER : 1 / SLOW_SCROLL_MODIFIER;
				zoom(scrollFactor);
			});
			actionSet.addAction(slowZoom);

			final var fastZoom = new Action<>(ScrollEvent.SCROLL);
			fastZoom.setKeyTracker(actionSet.getKeyTracker());
			fastZoom.setName("Fast Zoom");
			fastZoom.keysDown(KeyCode.SHIFT);
			fastZoom.onAction(event -> {
				final double scroll = ControlUtils.getBiggestScroll(event);
				if (scroll == 0)
					return;
				double scrollFactor = scroll > 0 ? FAST_SCROLL_MODIFIER : 1 / FAST_SCROLL_MODIFIER;
				zoom(scrollFactor);
			});
			actionSet.addAction(fastZoom);
		});
	}

	private void zoom(double scrollFactor) {

		final Affine target = affine.clone();
		target.prependScale(scrollFactor, scrollFactor, scrollFactor);
		InvokeOnJavaFXApplicationThread.invoke(() -> this.setAffine(target));
	}

	private class TranslateXY extends DragActionSet {

		public TranslateXY(String name) {

			super(name);
			/* only on right click */
			verify(MouseEvent::isSecondaryButtonDown);
			/* trigger as filters */
			getDragDetectedAction().setFilter(true);
			getDragAction().setFilter(true);
			getDragReleaseAction().setFilter(true);
			/* each drag is relative to previous */
			setRelative(true);
			onDrag(this::drag);
		}

		private void drag(MouseEvent event) {

			synchronized (affine) {
				LOG.trace("drag - translate");
				final Affine target = affine.clone();
				final double dX = event.getX() - getStartX();
				final double dY = event.getY() - getStartY();
				LOG.trace("dx " + dX + " dy: " + dY);

				target.prependTranslation(2 * dX / viewer.getHeight(), 2 * dY / viewer.getHeight());

				LOG.trace("target: {}", target);
				InvokeOnJavaFXApplicationThread.invoke(() -> setAffine(target));
			}
		}
	}

	private class Rotate3DView extends DragActionSet {

		private double baseSpeed = 0.25;
		private double factor = 1.0;

		private double speed = baseSpeed * factor;

		private final static double SLOW_FACTOR = 0.5;

		private final static double NORMAL_FACTOR = 1;

		private final static double FAST_FACTOR = 2.0;

		private final Affine affineDragStart = new Affine();

		public Rotate3DView(String name) {

			super(name);
			LOG.trace(name);
			verify(MouseEvent::isPrimaryButtonDown);
			setRelative(false);
			onDragDetected(this::dragDetected);
			onDrag(this::drag);
		}

		private void dragDetected(MouseEvent event) {

			updateSpeed(event);

			synchronized (affine) {
				affineDragStart.setToTransform(affine);
			}
		}

		private void drag(MouseEvent event) {

			updateSpeed(event);

			synchronized (affine) {
				LOG.trace("drag - rotate");
				final Affine target = new Affine(affineDragStart);
				final double dX = event.getX() - getStartX();
				final double dY = event.getY() - getStartY();
				final double v = step * this.speed;
				LOG.trace("dx: {} dy: {}", dX, dY);

				target.prependRotation(v * dY, CENTER_X, CENTER_Y, 0, xNormal);
				target.prependRotation(v * -dX, CENTER_X, CENTER_Y, 0, yNormal);

				LOG.trace("target: {}", target);
				InvokeOnJavaFXApplicationThread.invoke(() -> setAffine(target));
			}
		}

		//TODO Caleb: Use same speed control mechanism as NavigationControlMode uses (to be toggleable)
		private void updateSpeed(MouseEvent event) {
			factor = NORMAL_FACTOR;

			if (event.isControlDown()) {
				factor = SLOW_FACTOR;
			} else if (event.isShiftDown()) {
				factor = FAST_FACTOR;
			}

			speed = baseSpeed * factor;
		}
	}

	public void resetAffine() {

		setAffine(initialTransform);
	}

	public void centerAffine() {

		final Affine centerScaledAffine = initialTransform.clone();

		final double mxx = affine.getMxx();
		final double myy = affine.getMyy();
		final double mzz = affine.getMzz();

		final double sx = mxx / initialTransform.getMxx();
		final double sy = myy / initialTransform.getMyy();
		final double sz = mzz / initialTransform.getMzz();

		final AffineTransform3D affineTransform = new AffineTransform3D();
		affineTransform.set(
				affine.getMxx(), affine.getMxy(), affine.getMxz(), affine.getTx(),
				affine.getMyx(), affine.getMyy(), affine.getMyz(), affine.getTy(),
				affine.getMzx(), affine.getMzy(), affine.getMzz(), affine.getTz()
		);

		final AffineTransform3D initialAffineTransform = new AffineTransform3D();
		initialAffineTransform.set(
				initialTransform.getMxx(), initialTransform.getMxy(), initialTransform.getMxz(), initialTransform.getTx(),
				initialTransform.getMyx(), initialTransform.getMyy(), initialTransform.getMyz(), initialTransform.getTy(),
				initialTransform.getMzx(), initialTransform.getMzy(), initialTransform.getMzz(), initialTransform.getTz()
		);

		final double curScale = Affine3DHelpers.extractScale(affineTransform, 0);

		final double initScale = Affine3DHelpers.extractScale(initialAffineTransform, 0);

		final double scaleFactor = curScale / initScale;
		centerScaledAffine.prependScale(
				scaleFactor,
				scaleFactor,
				scaleFactor
		);

		setAffine(centerScaledAffine);

	}

	public void getAffine(final Affine target) {

		target.setToTransform(affine);
	}

	public void setAffine(final Affine affine) {

		this.affine.setToTransform(affine);
		this.affineListeners.forEach(l -> l.transformChanged(affine));
	}

	public void addAffineListener(final TransformListener<Affine> listener) {

		this.affineListeners.add(listener);
		listener.transformChanged(this.affine.clone());
	}

}
