package org.janelia.saalfeldlab.paintera.viewer3d;

import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point3D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;
import net.imglib2.Interval;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.actions.DragActionSet;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.control.ControlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Scene3DHandler {

  public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Viewer3DFX viewer;

  private static final double CENTER_X = 0;

  private static final double CENTER_Y = 0;

  private final Affine initialTransform = new Affine();

  private final Affine affine = new Affine();

  private final static double step = 1.0;// Math.PI / 180;

  private static final Point3D xNormal = new Point3D(1, 0, 0);

  private static final Point3D yNormal = new Point3D(0, 1, 0);

  private final List<TransformListener<Affine>> affineListeners = Collections.synchronizedList(new ArrayList<>());

  public Scene3DHandler(final Viewer3DFX viewer) {

	this.viewer = viewer;
	this.viewer.sceneGroup().getTransforms().add(affine);

	this.setAffine(initialTransform);
	addCommands();

	final var rotateActionSet = new Rotate3DView("rotate 3d");
	ActionSet.installActionSet(viewer, rotateActionSet);

	final var translateXYActionSet = new TranslateXY("translate");
	ActionSet.installActionSet(viewer, translateXYActionSet);
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

  private void addCommands() {

	viewer.addEventHandler(ScrollEvent.SCROLL, event -> {

	  final double scroll = ControlUtils.getBiggestScroll(event);
	  if (scroll == 0) {
		event.consume();
		return;
	  }

	  double scrollFactor = scroll > 0 ? 1.05 : 1 / 1.05;

	  if (event.isShiftDown()) {
		if (event.isControlDown()) {
		  scrollFactor = scroll > 0 ? 1.01 : 1 / 1.01;
		} else {
		  scrollFactor = scroll > 0 ? 2.05 : 1 / 2.05;
		}
	  }

	  final Affine target = affine.clone();
	  target.prependScale(scrollFactor, scrollFactor, scrollFactor);
	  InvokeOnJavaFXApplicationThread.invoke(() -> this.setAffine(target));

	  event.consume();
	});

	viewer.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
	  if (event.getCode().equals(KeyCode.Z) && event.isShiftDown()) {
		InvokeOnJavaFXApplicationThread.invoke(() -> this.setAffine(initialTransform));
		event.consume();
	  }
	});

	viewer.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
	  if (event.getCode().equals(KeyCode.P) && event.isControlDown()) {
		InvokeOnJavaFXApplicationThread.invoke(this::saveAsPng);
		event.consume();
	  }
	});
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
	  /* don't update XY*/
	  setUpdateXY(false);
	  onDrag(this::drag);
	}

	private void drag(MouseEvent event) {

	  synchronized (affine) {
		LOG.trace("drag - translate");
		final double dX = event.getX() - getStartX();
		final double dY = event.getY() - getStartY();

		LOG.trace("dx " + dX + " dy: " + dY);

		final Affine target = affine.clone();
		target.prependTranslation(2 * dX / viewer.getHeight(), 2 * dY / viewer.getHeight());

		InvokeOnJavaFXApplicationThread.invoke(() -> setAffine(target));

		setStartX(getStartX() + dX);
		setStartY(getStartY() + dY);
	  }
	}
  }

  private class Rotate3DView extends DragActionSet {

	private double baseSpeed = 1.0;
	private double factor = 1.0;

	private double speed = baseSpeed * factor;

	private final static double SLOW_FACTOR = 0.1;

	private final static double NORMAL_FACTOR = 1;

	private final static double FAST_FACTOR = 2;

	private final Affine affineDragStart = new Affine();

	public Rotate3DView(String name) {

	  super(name);
	  LOG.trace(name);
	  verify(MouseEvent::isPrimaryButtonDown);
	  setUpdateXY(false);
	  onDragDetected(this::dragDetected);
	  onDrag(this::drag);
	}

	private void dragDetected(MouseEvent event) {

	  factor = NORMAL_FACTOR;

	  if (event.isShiftDown()) {
		if (event.isControlDown()) {
		  factor = SLOW_FACTOR;
		} else {
		  factor = FAST_FACTOR;
		}
	  }

	  speed = baseSpeed * factor;

	  synchronized (affine) {
		affineDragStart.setToTransform(affine);
	  }
	}

	private void drag(MouseEvent event) {

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
  }

  private void saveAsPng() {

	final WritableImage image = viewer.scene().snapshot(new SnapshotParameters(), null);

	final FileChooser fileChooser = new FileChooser();
	fileChooser.setTitle("Save 3d snapshot ");
	final SimpleObjectProperty<Optional<File>> fileProperty = new SimpleObjectProperty<>(Optional.empty());
	try {
	  InvokeOnJavaFXApplicationThread.invokeAndWait(() -> fileProperty.set(
					  Optional.ofNullable(
							  fileChooser.showSaveDialog(viewer.root().sceneProperty().get().getWindow())
					  )
			  )
	  );
	} catch (final InterruptedException e) {
	  e.printStackTrace();
	}
	if (fileProperty.get().isPresent()) {
	  File file = fileProperty.get().get();
	  if (!file.getName().endsWith(".png")) {
		// TODO: now, it is overwritten if there is a file with the same
		// name and extension
		file = new File(file.getAbsolutePath() + ".png");
	  }

	  try {
		ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
	  } catch (final IOException e) {
		// TODO: handle exception here
	  }
	}
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
