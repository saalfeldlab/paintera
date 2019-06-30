package org.janelia.saalfeldlab.paintera.viewer3d;

import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum.ViewFrustumPlane;

import com.sun.javafx.geom.Vec3d;
import com.sun.javafx.geom.Vec4d;

import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

@SuppressWarnings("restriction")
public class ViewFrustumTransformed
{
	private final ViewFrustum viewFrustumCamera;
	private final AffineTransform3D transform;
	private final Vec4d[] planes;
	private final RealPoint[] points;

	/**
	 * Calculate planes the define the view frustum in the transformed coordinate space
	 * from the local view frustum (in the camera coordinate space) and the transform from the camera space into the target space.
	 *
	 * @param viewFrustumCamera
	 * 			view frustum in the local (camera) space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 * @param transform
	 * 			transform that maps points from the local camera space into the target space
	 */
	public ViewFrustumTransformed(final ViewFrustum viewFrustumCamera, final AffineTransform3D transform)
	{
		this.viewFrustumCamera = viewFrustumCamera;
		this.transform = transform;

		final ViewFrustumPlane[] cameraNearFarPlanes = viewFrustumCamera.nearFarPlanesProperty().get();
		final ViewFrustumPlane[] worldNearFarPlanes = new ViewFrustumPlane[2];
		for (int i = 0; i < 2; ++i)
		{
			worldNearFarPlanes[i] = new ViewFrustumPlane();
			transform.apply(cameraNearFarPlanes[i].minMin, worldNearFarPlanes[i].minMin);
			transform.apply(cameraNearFarPlanes[i].minMax, worldNearFarPlanes[i].minMax);
			transform.apply(cameraNearFarPlanes[i].maxMin, worldNearFarPlanes[i].maxMin);
			transform.apply(cameraNearFarPlanes[i].maxMax, worldNearFarPlanes[i].maxMax);
		}

		this.points = new RealPoint[] {
				worldNearFarPlanes[0].minMin,
				worldNearFarPlanes[0].minMax,
				worldNearFarPlanes[0].maxMin,
				worldNearFarPlanes[0].maxMax,
				worldNearFarPlanes[1].minMin,
				worldNearFarPlanes[1].minMax,
				worldNearFarPlanes[1].maxMin,
				worldNearFarPlanes[1].maxMax
		};

		this.planes = new Vec4d[6];
		this.planes[0] = createPlane(worldNearFarPlanes[0].minMin, worldNearFarPlanes[0].minMax, worldNearFarPlanes[0].maxMin); // near plane
		this.planes[1] = createPlane(worldNearFarPlanes[1].minMin, worldNearFarPlanes[1].minMax, worldNearFarPlanes[1].maxMin); // far plane

		// side planes
		this.planes[2] = createPlane(worldNearFarPlanes[0].minMin, worldNearFarPlanes[0].minMax, worldNearFarPlanes[1].minMin);
		this.planes[3] = createPlane(worldNearFarPlanes[0].minMin, worldNearFarPlanes[0].maxMin, worldNearFarPlanes[1].minMin);
		this.planes[4] = createPlane(worldNearFarPlanes[0].maxMin, worldNearFarPlanes[0].maxMax, worldNearFarPlanes[1].maxMin);
		this.planes[5] = createPlane(worldNearFarPlanes[0].minMax, worldNearFarPlanes[0].maxMax, worldNearFarPlanes[1].minMax);
	}

	/**
	 * @return view frustum in the local (camera) space, where the camera is placed at (0,0,0) and is looking towards positive Z axis.
	 */
	public ViewFrustum getViewFrustumCamera()
	{
		return viewFrustumCamera;
	}

	/**
	 * @return transform that maps points from the local camera space into the world space
	 */
	public AffineTransform3D getTransform()
	{
		return transform;
	}

	/**
	 * @return six planes that define the view frustum in the world coordinate space
	 */
	public Vec4d[] getPlanes()
	{
		return planes;
	}

	/**
	 * @return eight points that define the view frustum in the world coordinate space
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
		// check if the block is outside or inside/intersects with the frustum
		final Vec3d vector = new Vec3d();
		for (int i = 0; i < planes.length; i++)
	    {
	        int out = 0;
	        final IntervalIterator cornerIterator = new IntervalIterator(new int[] {2, 2, 2});
	        while (cornerIterator.hasNext())
	        {
	        	cornerIterator.fwd();
	        	vector.set(
	        			cornerIterator.getIntPosition(0) == 0 ? block.realMin(0) : block.realMax(0),
    					cornerIterator.getIntPosition(1) == 0 ? block.realMin(1) : block.realMax(1),
						cornerIterator.getIntPosition(2) == 0 ? block.realMin(2) : block.realMax(2)
        			);
	        	out += dot(planes[i], vector) < 0.0 ? 1 : 0;
	        }
	        if (out == 8)
	        	return false;
	    }

	    // check if the frustum is outside or inside the box to reduce chance of false positives for large objects
//		final RealPoint[] frustumPoints = viewFrustumWorld.getPoints();
//	    for (int d = 0; d < 3; ++d)
//	    {
//	    	for (int corner = 0; corner < 2; ++corner)
//	    	{
//	    		int out = 0;
//	    		for (int i = 0; i < 8; i++)
//	    		{
//	    			if (corner == 0)
//	    				out += frustumPoints[i].getDoublePosition(d) < block.realMin(d) ? 1 : 0;
//	    			else
//	    				out += frustumPoints[i].getDoublePosition(d) > block.realMax(d) ? 1 : 0;
//	    		}
//	    		if (out == 8)
//	    			return false;
//	    	}
//	    }

		return true;
	}

	public double distance(final RealInterval block)
	{
//		final double[] cameraTransformedPosition = new double[3], blockCorner = new double[3];
//		transform.apply(cameraTransformedPosition, cameraTransformedPosition);
//		final IntervalIterator cornerIterator = new IntervalIterator(new int[] {2, 2, 2});
//		double minDistance = Double.POSITIVE_INFINITY;
//		while (cornerIterator.hasNext())
//		{
//			cornerIterator.fwd();
//			for (int d = 0; d < block.numDimensions(); ++d)
//				blockCorner[d ] = cornerIterator.getIntPosition(d) == 0 ? block.realMin(d) : block.realMax(d);
//			minDistance = Math.min(LinAlgHelpers.distance(blockCorner, cameraTransformedPosition), minDistance);
//		}
//		return minDistance;
		
		final double[] blockCorner = new double[3];
		final IntervalIterator cornerIterator = new IntervalIterator(new int[] {2, 2, 2});
		double minDistance = Double.POSITIVE_INFINITY;
		boolean positive = false, negative = false;
		while (cornerIterator.hasNext())
		{
			cornerIterator.fwd();
			for (int d = 0; d < block.numDimensions(); ++d)
				blockCorner[d] = cornerIterator.getIntPosition(d) == 0 ? block.realMin(d) : block.realMax(d);
			transform.applyInverse(blockCorner, blockCorner);
			final double zDistance = blockCorner[2];
			
			if (zDistance < 0)
				negative = true;
			else
				positive = true;
				
			if (Math.abs(zDistance) < Math.abs(minDistance))
				minDistance = zDistance;
		}
		
		if (positive && negative)
			return 0.0; // camera is inside the object
		
		return minDistance;
	}

	private static Vec4d createPlane(final RealPoint p1, final RealPoint p2, final RealPoint p3)
	{
		// find normal vector of the plane by taking the cross-product of the two vectors within the plane formed between 3 points
		final Vec3d p12 = new Vec3d(
				p2.getDoublePosition(0) - p1.getDoublePosition(0),
				p2.getDoublePosition(1) - p1.getDoublePosition(1),
				p2.getDoublePosition(2) - p1.getDoublePosition(2)
			);
		final Vec3d p13 = new Vec3d(
				p3.getDoublePosition(0) - p1.getDoublePosition(0),
				p3.getDoublePosition(1) - p1.getDoublePosition(1),
				p3.getDoublePosition(2) - p1.getDoublePosition(2)
			);

		final Vec3d normal = new Vec3d();
		normal.cross(p12, p13);
		normal.normalize();

		// plug the coordinates of any given point in the plane into the plane equation to find the last component of the equation
		final double d = normal.dot(new Vec3d(p1.getDoublePosition(0), p1.getDoublePosition(1), p1.getDoublePosition(2)));

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
