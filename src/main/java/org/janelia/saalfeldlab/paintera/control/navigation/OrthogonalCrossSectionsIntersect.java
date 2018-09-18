package org.janelia.saalfeldlab.paintera.control.navigation;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrthogonalCrossSectionsIntersect
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void getCenter(final AffineTransform3D transform, final double[] center)
	{
		Arrays.fill(center, 0);
		transform.applyInverse(center, center);
	}

	public static void centerAt(
			final AffineTransform3D transform,
			final double x,
			final double y,
			final double z)
	{
		final double[] center = new double[] {x, y, z};
		LOG.debug("Centering at {}", center);
		transform.apply(center, center);
		transform.set(transform.get(0, 3) - center[0], 0, 3);
		transform.set(transform.get(1, 3) - center[1], 1, 3);
		transform.set(transform.get(2, 3) - center[2], 2, 3);
	}

}
