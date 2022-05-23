package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.beans.binding.DoubleExpression;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class TranslateAlongNormal {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final AffineTransform3D global = new AffineTransform3D();

  private final DoubleExpression translationSpeed;

  private final GlobalTransformManager manager;

  private final AffineTransform3D worldToSharedViewerSpace;

  public TranslateAlongNormal(
		  final DoubleExpression translationSpeed,
		  final GlobalTransformManager manager,
		  final AffineTransform3D worldToSharedViewerSpace) {

	this.translationSpeed = translationSpeed;
	this.manager = manager;
	this.worldToSharedViewerSpace = worldToSharedViewerSpace;

	manager.addListener(global::set);
  }

  public void translate(final double step) {

	translate(step, 1.0);

  }

  public void translate(final double step, final double speedModifier) {

	synchronized (manager) {
	  if (step == 0) {
		return;
	  }
	  final double[] delta = {0, 0, Math.signum(step) * translationSpeed.multiply(speedModifier).doubleValue()};
	  final AffineTransform3D affine = global.copy();
	  final AffineTransform3D rotationAndScalingOnly = worldToSharedViewerSpace.copy();
	  rotationAndScalingOnly.setTranslation(0, 0, 0);
	  rotationAndScalingOnly.applyInverse(delta, delta);
	  LOG.debug("Translating by delta={}", delta);
	  affine.set(affine.get(0, 3) + delta[0], 0, 3);
	  affine.set(affine.get(1, 3) + delta[1], 1, 3);
	  affine.set(affine.get(2, 3) + delta[2], 2, 3);
	  manager.setTransform(affine);
	}
  }
}
