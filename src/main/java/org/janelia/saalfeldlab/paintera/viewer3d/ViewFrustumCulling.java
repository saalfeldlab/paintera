package org.janelia.saalfeldlab.paintera.viewer3d;

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

		for (int i = 0; i < planes.length; ++i) {
			final double distance = dot(planes[i], sphereCenter);
			if (distance < -sphereRadius)
				return false;
		}
		return true;
	}

	public boolean isInside(final Vec3d point) {
		for (int i = 0; i < planes.length; ++i)
			if (dot(planes[i], point) < 0.0)
				return false;
		return true;
	}

	public double distanceFromCamera(final RealInterval block) {
		final double[] blockCorner = new double[3];
		double minPositiveDistance = Double.POSITIVE_INFINITY;
		boolean negative = false;
		final IntervalIterator cornerIterator = new IntervalIterator(new int[]{2, 2, 2});
		while (cornerIterator.hasNext()) {
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

	private static Vec4d createPlane(final RealPoint p1, final RealPoint p2, final RealPoint p3) {
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

	private static double dot(final Vec4d plane, final Vec3d vector) {
		return
				plane.x * vector.x +
						plane.y * vector.y +
						plane.z * vector.z +
						plane.w;
	}


	/**
	 * A 3-dimensional, double-precision, floating-point vector.
	 */
	static class Vec3d {
		/**
		 * The x coordinate.
		 */
		public double x;

		/**
		 * The y coordinate.
		 */
		public double y;

		/**
		 * The z coordinate.
		 */
		public double z;

		public Vec3d() {
		}

		public Vec3d(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public Vec3d(Vec3d v) {
			set(v);
		}

		public void set(Vec3d v) {
			this.x = v.x;
			this.y = v.y;
			this.z = v.z;
		}

		public void set(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		/**
		 * Multiplies this vector by the specified scalar value.
		 *
		 * @param scale the scalar value
		 */
		public void mul(double scale) {
			x *= scale;
			y *= scale;
			z *= scale;
		}

		/**
		 * Sets the value of this vector to the difference
		 * of vectors t1 and t2 (this = t1 - t2).
		 *
		 * @param t1 the first vector
		 * @param t2 the second vector
		 */
		public void sub(Vec3d t1, Vec3d t2) {
			this.x = t1.x - t2.x;
			this.y = t1.y - t2.y;
			this.z = t1.z - t2.z;
		}

		/**
		 * Sets the value of this vector to the difference of
		 * itself and vector t1 (this = this - t1) .
		 *
		 * @param t1 the other vector
		 */
		public void sub(Vec3d t1) {
			this.x -= t1.x;
			this.y -= t1.y;
			this.z -= t1.z;
		}

		/**
		 * Sets the value of this vector to the sum
		 * of vectors t1 and t2 (this = t1 + t2).
		 *
		 * @param t1 the first vector
		 * @param t2 the second vector
		 */
		public void add(Vec3d t1, Vec3d t2) {
			this.x = t1.x + t2.x;
			this.y = t1.y + t2.y;
			this.z = t1.z + t2.z;
		}

		/**
		 * Sets the value of this vector to the sum of
		 * itself and vector t1 (this = this + t1) .
		 *
		 * @param t1 the other vector
		 */
		public void add(Vec3d t1) {
			this.x += t1.x;
			this.y += t1.y;
			this.z += t1.z;
		}

		/**
		 * Returns the length of this vector.
		 *
		 * @return the length of this vector
		 */
		public double length() {
			return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
		}

		/**
		 * Normalize this vector.
		 */
		public void normalize() {
			double norm = 1.0 / length();
			this.x = this.x * norm;
			this.y = this.y * norm;
			this.z = this.z * norm;
		}

		/**
		 * Sets this vector to be the vector cross product of vectors v1 and v2.
		 *
		 * @param v1 the first vector
		 * @param v2 the second vector
		 */
		public void cross(Vec3d v1, Vec3d v2) {
			double tmpX;
			double tmpY;

			tmpX = v1.y * v2.z - v1.z * v2.y;
			tmpY = v2.x * v1.z - v2.z * v1.x;
			this.z = v1.x * v2.y - v1.y * v2.x;
			this.x = tmpX;
			this.y = tmpY;
		}

		/**
		 * Computes the dot product of this vector and vector v1.
		 *
		 * @param v1 the other vector
		 * @return the dot product of this vector and v1
		 */
		public double dot(Vec3d v1) {
			return this.x * v1.x + this.y * v1.y + this.z * v1.z;
		}

		/**
		 * Returns the hashcode for this <code>Vec3f</code>.
		 *
		 * @return a hash code for this <code>Vec3f</code>.
		 */
		@Override
		public int hashCode() {
			long bits = 7L;
			bits = 31L * bits + Double.doubleToLongBits(x);
			bits = 31L * bits + Double.doubleToLongBits(y);
			bits = 31L * bits + Double.doubleToLongBits(z);
			return (int) (bits ^ (bits >> 32));
		}

		/**
		 * Determines whether or not two 3D points or vectors are equal.
		 * Two instances of <code>Vec3d</code> are equal if the values of their
		 * <code>x</code>, <code>y</code> and <code>z</code> member fields,
		 * representing their position in the coordinate space, are the same.
		 *
		 * @param obj an object to be compared with this <code>Vec3d</code>
		 * @return <code>true</code> if the object to be compared is
		 * an instance of <code>Vec3d</code> and has
		 * the same values; <code>false</code> otherwise.
		 */
		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj instanceof Vec3d) {
				Vec3d v = (Vec3d) obj;
				return (x == v.x) && (y == v.y) && (z == v.z);
			}
			return false;
		}

		/**
		 * Returns a <code>String</code> that represents the value
		 * of this <code>Vec3f</code>.
		 *
		 * @return a string representation of this <code>Vec3f</code>.
		 */
		@Override
		public String toString() {
			return "Vec3d[" + x + ", " + y + ", " + z + "]";
		}
	}

	/**
	 * A 4-dimensional, double-precision, floating-point vector.
	 *
	 */
	static class Vec4d {
		/**
		 * The x coordinate.
		 */
		public double x;

		/**
		 * The y coordinate.
		 */
		public double y;

		/**
		 * The z coordinate.
		 */
		public double z;

		/**
		 * The w coordinate.
		 */
		public double w;

		public Vec4d() { }

		public Vec4d(Vec4d v) {
			this.x = v.x;
			this.y = v.y;
			this.z = v.z;
			this.w = v.w;
		}

		public Vec4d(double x, double y, double z, double w) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.w = w;
		}

		public void set(Vec4d v) {
			this.x = v.x;
			this.y = v.y;
			this.z = v.z;
			this.w = v.w;
		}
	}
}
