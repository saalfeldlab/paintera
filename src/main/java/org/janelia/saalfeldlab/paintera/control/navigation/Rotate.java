package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.beans.binding.DoubleExpression;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;

import java.util.function.Consumer;

public class Rotate {

  private static final double ROTATION_STEP = Math.PI / 180;

  private final DoubleExpression speed;

  private final AffineTransform3D globalTransform = new AffineTransform3D();

  private final AffineTransformWithListeners displayTransformUpdater;

  private final AffineTransformWithListeners globalToViewerTransformUpdater;

  private final Consumer<AffineTransform3D> submitTransform;

  private final GlobalTransformManager manager;

  private final AffineTransform3D affineDragStart = new AffineTransform3D();

  private final TranslationController.TransformTracker globalTransformTracker;

  private final TranslationController.TransformTracker globalToViewerTransformTracker;

  private final TranslationController.TransformTracker displayTransformTracker;

  private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

  private final AffineTransform3D displayTransform = new AffineTransform3D();

  public Rotate(
	  final DoubleExpression speed,
	  final AffineTransformWithListeners displayTransformUpdater,
	  final AffineTransformWithListeners globalToViewerTransformUpdater,
	  final GlobalTransformManager globalTransformManager,
	  final Consumer<AffineTransform3D> submitTransform) {

	super();
	this.speed = speed;


	this.globalTransformTracker = new TranslationController.TransformTracker(globalTransform, globalTransformManager);
	this.globalToViewerTransformTracker = new TranslationController.TransformTracker(globalToViewerTransform, globalTransformManager);
	this.displayTransformTracker = new TranslationController.TransformTracker(displayTransform, globalTransformManager);

	this.displayTransformUpdater = displayTransformUpdater;
	this.globalToViewerTransformUpdater = globalToViewerTransformUpdater;
	this.submitTransform = submitTransform;
	this.manager = globalTransformManager;
	listenOnTransformChanges();
  }

  public void initialize() {

	synchronized (manager) {
	  affineDragStart.set(globalTransform);
	}
  }

  public void listenOnTransformChanges() {

	this.manager.addListener(this.globalTransformTracker);
	this.displayTransformUpdater.addListener(this.displayTransformTracker);
	this.globalToViewerTransformUpdater.addListener(this.globalToViewerTransformTracker);
  }

  public void stopListeningOnTransformChanges() {

	this.manager.removeListener(this.globalTransformTracker);
	this.displayTransformUpdater.removeListener(this.displayTransformTracker);
	this.globalToViewerTransformUpdater.removeListener(this.globalToViewerTransformTracker);
  }

  public void rotate(final double x, final double y, final double startX, final double startY) {

	final AffineTransform3D affine = new AffineTransform3D();
	synchronized (manager) {
	  final double v = ROTATION_STEP * this.speed.get();
	  affine.set(affineDragStart);
	  final double[] point = new double[]{x, y, 0};
	  final double[] origin = new double[]{startX, startY, 0};

	  displayTransform.applyInverse(point, point);
	  displayTransform.applyInverse(origin, origin);

	  final double[] delta = new double[]{point[0] - origin[0], point[1] - origin[1], 0};
	  // TODO do scaling separately. need to swap .get( 0, 0 ) and
	  // .get( 1, 1 ) ?
	  final double[] rotation = new double[]{
			  delta[1] * v * displayTransform.get(0, 0),
			  -delta[0] * v * displayTransform.get(1, 1),
			  0};

	  globalToViewerTransform.applyInverse(origin, origin);
	  globalToViewerTransform.applyInverse(rotation, rotation);

	  // center shift
	  for (int d = 0; d < origin.length; ++d) {
		affine.set(affine.get(d, 3) - origin[d], d, 3);
	  }

	  for (int d = 0; d < rotation.length; ++d) {
		affine.rotate(d, rotation[d]);
	  }

	  // center un-shift
	  for (int d = 0; d < origin.length; ++d) {
		affine.set(affine.get(d, 3) + origin[d], d, 3);
	  }

	  submitTransform.accept(affine);
	}
  }
}
