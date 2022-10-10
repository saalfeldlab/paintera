package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

//TOOD Caleb: Consider refactoring to use existing `Rotate` logic
public class KeyRotate {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public enum Axis {
	X(0),
	Y(1),
	Z(2);

	private final int axis;

	Axis(final int axis) {

	  this.axis = axis;
	}
  }

  private final ObjectExpression<Axis> axis;

  private final DoubleExpression step;

  private final AffineTransformWithListeners displayTransformUpdater;

  private final AffineTransformWithListeners globalToViewerTransformUpdater;

  private final AffineTransform3D globalTransform = new AffineTransform3D();
  private final Consumer<AffineTransform3D> submit;

  private final GlobalTransformManager manager;

  private final TranslationController.TransformTracker globalTransformTracker;
  private final TranslationController.TransformTracker globalToViewerTransformTracker;

  private final TranslationController.TransformTracker displayTransformTracker;

  private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

  private final AffineTransform3D displayTransform = new AffineTransform3D();

  public KeyRotate(
	  final ObjectExpression<Axis> axis,
	  final DoubleExpression step,
	  final AffineTransformWithListeners displayTransformUpdater,
	  final AffineTransformWithListeners globalToViewerTransformUpdater,
	  final GlobalTransformManager globalTransformManager,
	  final Consumer<AffineTransform3D> submit) {

	super();
	this.axis = axis;
	this.step = step;
	this.displayTransformUpdater = displayTransformUpdater;
	this.globalToViewerTransformUpdater = globalToViewerTransformUpdater;
	this.globalTransformTracker = new TranslationController.TransformTracker(globalTransform, globalTransformManager);
	this.globalToViewerTransformTracker = new TranslationController.TransformTracker(globalToViewerTransform, globalTransformManager);
	this.displayTransformTracker = new TranslationController.TransformTracker(displayTransform, globalTransformManager);
	this.submit = submit;
	this.manager = globalTransformManager;
	listenOnTransformChanges();
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

  public void rotate(final double x, final double y) {

	final AffineTransform3D concatenated = displayTransform.copy()
		.concatenate(globalToViewerTransform)
		.concatenate(globalTransform);

	concatenated.set(concatenated.get(0, 3) - x, 0, 3);
	concatenated.set(concatenated.get(1, 3) - y, 1, 3);
	LOG.debug("Rotating {} around axis={} by angle={}", concatenated, axis, step);
	concatenated.rotate(axis.get().axis, step.get());
	concatenated.set(concatenated.get(0, 3) + x, 0, 3);
	concatenated.set(concatenated.get(1, 3) + y, 1, 3);

	submit.accept(
		displayTransform.copy()
			.concatenate(globalToViewerTransform)
			.inverse()
			.concatenate(concatenated)
	);

  }

}
