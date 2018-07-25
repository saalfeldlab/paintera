package org.janelia.saalfeldlab.paintera.control.navigation;

import java.lang.invoke.MethodHandles;
import java.util.function.DoubleSupplier;

import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranslateAlongNormal
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final AffineTransform3D global = new AffineTransform3D();

	private final DoubleSupplier translationSpeed;

	private final GlobalTransformManager manager;

	private final AffineTransform3D worldToSharedViewerSpace;

	private final Object lock;

	public TranslateAlongNormal(
			final DoubleSupplier translationSpeed,
			final GlobalTransformManager manager,
			final AffineTransform3D worldToSharedViewerSpace,
			final Object lock)
	{
		this.translationSpeed = translationSpeed;
		this.manager = manager;
		this.worldToSharedViewerSpace = worldToSharedViewerSpace;
		this.lock = lock;

		manager.addListener(global::set);
	}

	public void scroll(final double step)
	{
		synchronized (lock)
		{
			final double[]          delta                  = {0, 0, Math.signum(step) * translationSpeed.getAsDouble
					()};
			final AffineTransform3D affine                 = global.copy();
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
