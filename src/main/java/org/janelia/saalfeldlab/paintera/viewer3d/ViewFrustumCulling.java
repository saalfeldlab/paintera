package org.janelia.saalfeldlab.paintera.viewer3d;

import java.util.Arrays;

import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum.ViewFrustumPlane;

import com.sun.javafx.geom.Vec3d;
import com.sun.javafx.geom.Vec4d;

import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Util;

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

	public RealPoint middlePoint()
	{
		final RealPoint[][] cameraPlanePoints = {
			viewFrustumCamera.nearFarPlanesProperty().get().nearPlane.toArray(),
			viewFrustumCamera.nearFarPlanesProperty().get().farPlane.toArray()
		};
		final Double[] clipValues = {null, null};
		for (int i = 0; i < clipValues.length; ++i)
		{
			for (int j = 0; j < cameraPlanePoints[i].length; ++j)
			{
				if (clipValues[i] == null)
					clipValues[i] = cameraPlanePoints[i][j].getDoublePosition(2);
				else if (!Util.isApproxEqual(clipValues[i], cameraPlanePoints[i][j].getDoublePosition(2), 1e-9))
					throw new IllegalArgumentException();
			}
		}
		final RealPoint middlePoint = new RealPoint(0, 0, (clipValues[0] + clipValues[1]) / 2);
		transform.apply(middlePoint, middlePoint);
		return middlePoint;
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





		// check if the block is outside or inside/intersects with the frustum
//		final Vec3d vector = new Vec3d();
//		for (int i = 0; i < planes.length; i++)
//	    {
//	        boolean outside = true;
//	        final IntervalIterator cornerIterator = new IntervalIterator(new int[] {2, 2, 2});
//	        while (cornerIterator.hasNext())
//	        {
//	        	cornerIterator.fwd();
//	        	vector.set(
//	        			cornerIterator.getIntPosition(0) == 0 ? block.realMin(0) : block.realMax(0),
//    					cornerIterator.getIntPosition(1) == 0 ? block.realMin(1) : block.realMax(1),
//						cornerIterator.getIntPosition(2) == 0 ? block.realMin(2) : block.realMax(2)
//        			);
//	        	if (dot(planes[i], vector) < 0.0)
//	        	{
//	        		outside = false;
//	        		break;
//	        	}
//	        }
//	        if (outside)
//	        	return false;
//	    }

	    // check if the frustum is outside or inside the box to reduce chance of false positives for large objects
//	    for (int d = 0; d < 3; ++d)
//	    {
//	    	for (int corner = 0; corner < 2; ++corner)
//	    	{
//	    		int out = 0;
//	    		for (int i = 0; i < 8; i++)
//	    		{
//	    			if (corner == 0)
//	    				out += points[i].getDoublePosition(d) < block.realMin(d) ? 1 : 0;
//	    			else
//	    				out += points[i].getDoublePosition(d) > block.realMax(d) ? 1 : 0;
//	    		}
//	    		if (out == 8)
//	    			return false;
//	    	}
//	    }

//		return true;







		// ---------------------------
		// FIXME: this is a simple implementation but it would fail for cases when block and frustum intersect but their corners are not contained inside each other
		// ---------------------------

		// check if any of the block corners is inside the frustum
//		final Vec3d blockCorner = new Vec3d();
//        final IntervalIterator blockCornerIterator = new IntervalIterator(new int[] {2, 2, 2});
//        while (blockCornerIterator.hasNext())
//        {
//        	blockCornerIterator.fwd();
//        	blockCorner.set(
//        			blockCornerIterator.getIntPosition(0) == 0 ? block.realMin(0) : block.realMax(0),
//        			blockCornerIterator.getIntPosition(1) == 0 ? block.realMin(1) : block.realMax(1),
//        			blockCornerIterator.getIntPosition(2) == 0 ? block.realMin(2) : block.realMax(2)
//    			);
//        	if (isInside(blockCorner))
//        		return true;
//        }
//
//        // check if any of the frustum corners are inside the block
//        for (final RealPoint frustumCorner : points)
//        	if (Intervals.contains(block, frustumCorner))
//        		return true;
//
//		return false;
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

	public double[] sourcePixelSize(final double z)
	{
		final double[] sourcePixelSize = new double[2];
		final RealInterval viewPlane = viewFrustumCamera.viewPlaneAtGivenDistance(z);
		final double[] screenSize = viewFrustumCamera.getScreenSize(), viewPlaneToScreenScaling = new double[2];
		Arrays.setAll(viewPlaneToScreenScaling, d -> (viewPlane.realMax(d) - viewPlane.realMin(d)) / screenSize[d]);

		final double[] zero = {viewPlane.realMin(0), viewPlane.realMin(1), z}, tzero = new double[3];
		final double[] one = new double[3], tone = new double[3];
		final double[] diff = new double[3];
		transform.apply(zero, tzero);
		for (int i = 0; i < 2; ++i)
		{
			one[2] = z;
			for (int d = 0; d < 2; ++d)
				one[d] = d == i ? zero[d] + viewPlaneToScreenScaling[d] : zero[d];
			transform.apply(one, tone);
			LinAlgHelpers.subtract(tone, tzero, diff);
			sourcePixelSize[i] = LinAlgHelpers.length(diff);
		}
		return sourcePixelSize;
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
