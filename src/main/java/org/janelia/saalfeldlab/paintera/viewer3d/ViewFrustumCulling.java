package org.janelia.saalfeldlab.paintera.viewer3d;

import com.sun.javafx.geom.Vec4d;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum.ViewFrustumPlane;

import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;

@SuppressWarnings("restriction")
public class ViewFrustumCulling {
	private final ViewFrustum viewFrustumCamera;
	private final AffineTransform3D transform;
	private final Vec4d[] planes;
	private final RealPoint[] points;

	/**
	 * For culling in the camera coordinate space.
	 *
	 * @param viewFrustumCamera view frustum in the camera space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 */
	public ViewFrustumCulling(final ViewFrustum viewFrustumCamera) {
		this(viewFrustumCamera, new AffineTransform3D());
	}

	/**
	 * For culling in the target coordinate space defined by the {@code transform} that maps the camera space into the target space.
	 *
	 * @param viewFrustumCamera view frustum in the camera space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 * @param transform         transform that maps points from the camera space into the target space
	 */
	public ViewFrustumCulling(final ViewFrustum viewFrustumCamera, final AffineTransform3D transform) {
		this.viewFrustumCamera = viewFrustumCamera;
		this.transform = transform;

		final ViewFrustumPlane[] cameraNearFarPlanes = viewFrustumCamera.getNearFarPlanes().toArray();
		final ViewFrustumPlane[] targetNearFarPlanes = new ViewFrustumPlane[2];
		for (int i = 0; i < 2; ++i) {
			targetNearFarPlanes[i] = new ViewFrustumPlane();
			transform.apply(cameraNearFarPlanes[i].minMin, targetNearFarPlanes[i].minMin);
			transform.apply(cameraNearFarPlanes[i].minMax, targetNearFarPlanes[i].minMax);
			transform.apply(cameraNearFarPlanes[i].maxMin, targetNearFarPlanes[i].maxMin);
			transform.apply(cameraNearFarPlanes[i].maxMax, targetNearFarPlanes[i].maxMax);
		}

		this.points = new RealPoint[]{
				targetNearFarPlanes[0].minMin,
				targetNearFarPlanes[0].minMax,
				targetNearFarPlanes[0].maxMin,
				targetNearFarPlanes[0].maxMax,
				targetNearFarPlanes[1].minMin,
				targetNearFarPlanes[1].minMax,
				targetNearFarPlanes[1].maxMin,
				targetNearFarPlanes[1].maxMax
		};

		this.planes = new Vec4d[6];
		int planeIndex = 0;

		// near plane
		this.planes[planeIndex++] = Geom3DUtils.createPlane(targetNearFarPlanes[0].maxMax, targetNearFarPlanes[0].minMax, targetNearFarPlanes[0].maxMin);

		// far plane
		this.planes[planeIndex++] = Geom3DUtils.createPlane(targetNearFarPlanes[1].maxMin, targetNearFarPlanes[1].minMin, targetNearFarPlanes[1].maxMax);

		// side planes
		this.planes[planeIndex++] = Geom3DUtils.createPlane(targetNearFarPlanes[1].minMin, targetNearFarPlanes[0].minMin, targetNearFarPlanes[1].minMax);
		this.planes[planeIndex++] = Geom3DUtils.createPlane(targetNearFarPlanes[0].maxMin, targetNearFarPlanes[0].minMin, targetNearFarPlanes[1].maxMin);
		this.planes[planeIndex++] = Geom3DUtils.createPlane(targetNearFarPlanes[0].maxMin, targetNearFarPlanes[1].maxMin, targetNearFarPlanes[0].maxMax);
		this.planes[planeIndex++] = Geom3DUtils.createPlane(targetNearFarPlanes[0].maxMax, targetNearFarPlanes[1].maxMax, targetNearFarPlanes[0].minMax);
	}

	/**
	 * @return view frustum in the camera space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 */
	public ViewFrustum getViewFrustumCamera() {
		return viewFrustumCamera;
	}

	/**
	 * @return transform that maps points from the camera space into the target space
	 */
	public AffineTransform3D getTransform() {
		return transform;
	}

	/**
	 * @return six planes that define the view frustum in the target coordinate space
	 */
	public Vec4d[] getPlanes() {
		return planes;
	}

	/**
	 * @return eight points that define the view frustum in the target coordinate space
	 */
	public RealPoint[] getPoints() {
		return points;
	}

	/**
	 * Detect if a given block intersects with this view frustum.
	 *
	 * @param block
	 * @return
	 */
	public boolean intersects(final RealInterval block) {
		// sphere-based test
		final double[] sphereCenterPos = {
				(block.realMin(0) + block.realMax(0)) / 2,
				(block.realMin(1) + block.realMax(1)) / 2,
				(block.realMin(2) + block.realMax(2)) / 2
		};
		final double sphereRadius = LinAlgHelpers.distance(sphereCenterPos, Intervals.minAsDoubleArray(block));
		final Vec3d sphereCenter = new Vec3d(sphereCenterPos[0], sphereCenterPos[1], sphereCenterPos[2]);

		for (int i = 0; i < planes.length; ++i)
		{
			final double distance = Geom3DUtils.planeToPointDistance(planes[i], sphereCenter);
			if (distance < -sphereRadius)
				return false;
		}
		return true;
	}

	public boolean isInside(final Vec3d point) {
		for (int i = 0; i < planes.length; ++i)
			if (Geom3DUtils.planeToPointDistance(planes[i], point) < 0.0)
				return false;
		return true;
	}

	public double distanceFromCamera(final RealInterval block)
	{
		final RealPoint blockCorner = new RealPoint(3);
		double minPositiveDistance = Double.POSITIVE_INFINITY;
		boolean negative = false;
		final IntervalIterator cornerIterator = new IntervalIterator(new int[]{2, 2, 2});
		while (cornerIterator.hasNext()) {
			cornerIterator.fwd();
			for (int d = 0; d < block.numDimensions(); ++d)
				blockCorner.setPosition(cornerIterator.getIntPosition(d) == 0 ? block.realMin(d) : block.realMax(d), d);

			final double distanceFromCamera = Geom3DUtils.cameraZPlane(transform, blockCorner);
			if (distanceFromCamera >= 0)
				minPositiveDistance = Math.min(distanceFromCamera, minPositiveDistance);
			else
				negative = true;
		}

		if (!Double.isFinite(minPositiveDistance))
			return Double.POSITIVE_INFINITY;
		else if (negative)
			return 0.0;
		else
			return minPositiveDistance;
	}
}
