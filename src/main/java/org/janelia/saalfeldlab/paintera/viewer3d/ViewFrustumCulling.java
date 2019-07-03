package org.janelia.saalfeldlab.paintera.viewer3d;

import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum.ViewFrustumPlane;

import com.sun.javafx.geom.Vec3d;
import com.sun.javafx.geom.Vec4d;

import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;

@SuppressWarnings("restriction")
public class ViewFrustumCulling
{
	private final ViewFrustum viewFrustumCamera;
	private final AffineTransform3D transform;
	private final Vec4d[] planes;
	private final RealPoint[] points;

	/**
	 * For culling in the camera coordinate space.
	 *
	 * @param viewFrustumCamera
	 * 			view frustum in the camera space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 */
	public ViewFrustumCulling(final ViewFrustum viewFrustumCamera)
	{
		this(viewFrustumCamera, new AffineTransform3D());
	}

	/**
	 * For culling in the target coordinate space defined by the {@code transform} that maps the camera space into the target space.
	 *
	 * @param viewFrustumCamera
	 * 			view frustum in the camera space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 * @param transform
	 * 			transform that maps points from the camera space into the target space
	 */
	public ViewFrustumCulling(final ViewFrustum viewFrustumCamera, final AffineTransform3D transform)
	{
		this.viewFrustumCamera = viewFrustumCamera;
		this.transform = transform;

		final ViewFrustumPlane[] cameraNearFarPlanes = {
			viewFrustumCamera.nearFarPlanesProperty().get().nearPlane,
			viewFrustumCamera.nearFarPlanesProperty().get().farPlane
		};
		final ViewFrustumPlane[] targetNearFarPlanes = new ViewFrustumPlane[2];
		for (int i = 0; i < 2; ++i)
		{
			targetNearFarPlanes[i] = new ViewFrustumPlane();
			transform.apply(cameraNearFarPlanes[i].minMin, targetNearFarPlanes[i].minMin);
			transform.apply(cameraNearFarPlanes[i].minMax, targetNearFarPlanes[i].minMax);
			transform.apply(cameraNearFarPlanes[i].maxMin, targetNearFarPlanes[i].maxMin);
			transform.apply(cameraNearFarPlanes[i].maxMax, targetNearFarPlanes[i].maxMax);
		}

		this.points = new RealPoint[] {
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
		this.planes[planeIndex++] = createPlane(targetNearFarPlanes[0].maxMax, targetNearFarPlanes[0].minMax, targetNearFarPlanes[0].maxMin); // near plane
		this.planes[planeIndex++] = createPlane(targetNearFarPlanes[1].maxMin, targetNearFarPlanes[1].minMin, targetNearFarPlanes[1].maxMax); // far plane

		// side planes
		this.planes[planeIndex++] = createPlane(targetNearFarPlanes[1].minMin, targetNearFarPlanes[0].minMin, targetNearFarPlanes[1].minMax);
		this.planes[planeIndex++] = createPlane(targetNearFarPlanes[0].maxMin, targetNearFarPlanes[0].minMin, targetNearFarPlanes[1].maxMin);
		this.planes[planeIndex++] = createPlane(targetNearFarPlanes[0].maxMin, targetNearFarPlanes[1].maxMin, targetNearFarPlanes[0].maxMax);
		this.planes[planeIndex++] = createPlane(targetNearFarPlanes[0].maxMax, targetNearFarPlanes[1].maxMax, targetNearFarPlanes[0].minMax);
	}

	/**
	 * @return view frustum in the camera space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 */
	public ViewFrustum getViewFrustumCamera()
	{
		return viewFrustumCamera;
	}

	/**
	 * @return transform that maps points from the camera space into the target space
	 */
	public AffineTransform3D getTransform()
	{
		return transform;
	}

	/**
	 * @return six planes that define the view frustum in the target coordinate space
	 */
	public Vec4d[] getPlanes()
	{
		return planes;
	}

	/**
	 * @return eight points that define the view frustum in the target coordinate space
	 */
	public RealPoint[] getPoints()
	{
		return points;
	}

	/**
	 * Detect if a given block intersects with this view frustum.
	 *
	 * @param block
	 * @return
	 */
	public boolean intersects(final RealInterval block)
	{
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
			final double distance = dot(planes[i], sphereCenter);
			if (distance < -sphereRadius)
				return false;
		}
		return true;
	}

	public boolean isInside(final Vec3d point)
	{
		for (int i = 0; i < planes.length; ++i)
			if (dot(planes[i], point) < 0.0)
				return false;
		return true;
	}

	public double distanceFromCamera(final RealInterval block)
	{
		final double[] blockCorner = new double[3];
		double minPositiveDistance = Double.POSITIVE_INFINITY;
		boolean negative = false;
		final IntervalIterator cornerIterator = new IntervalIterator(new int[] {2, 2, 2});
		while (cornerIterator.hasNext())
		{
			cornerIterator.fwd();
			for (int d = 0; d < block.numDimensions(); ++d)
				blockCorner[d] = cornerIterator.getIntPosition(d) == 0 ? block.realMin(d) : block.realMax(d);
			transform.applyInverse(blockCorner, blockCorner);
			final double distanceFromCamera = blockCorner[2];

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

	private static Vec4d createPlane(final RealPoint p1, final RealPoint p2, final RealPoint p3)
	{
		// find normal vector of the plane by taking the cross-product of the two vectors within the plane formed between 3 points

		// p1 -> p2
		final Vec3d p12 = new Vec3d(
				p2.getDoublePosition(0) - p1.getDoublePosition(0),
				p2.getDoublePosition(1) - p1.getDoublePosition(1),
				p2.getDoublePosition(2) - p1.getDoublePosition(2)
			);

		// p1 -> p3
		final Vec3d p13 = new Vec3d(
				p3.getDoublePosition(0) - p1.getDoublePosition(0),
				p3.getDoublePosition(1) - p1.getDoublePosition(1),
				p3.getDoublePosition(2) - p1.getDoublePosition(2)
			);

		final Vec3d normal = new Vec3d();
		normal.cross(p12, p13);
		normal.normalize();

		// plug the coordinates of any given point in the plane into the plane equation to find the last component of the equation
		final double d = -normal.dot(new Vec3d(p1.getDoublePosition(0), p1.getDoublePosition(1), p1.getDoublePosition(2)));

		return new Vec4d(normal.x, normal.y, normal.z, d);
	}

	private static double dot(final Vec4d plane, final Vec3d vector)
	{
		return
				plane.x * vector.x +
				plane.y * vector.y +
				plane.z * vector.z +
				plane.w;
	}
}
