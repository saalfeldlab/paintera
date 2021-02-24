package org.janelia.saalfeldlab.paintera.viewer3d;

import com.sun.javafx.geom.Vec3d;
import com.sun.javafx.geom.Vec4d;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

@SuppressWarnings("restriction")
public class Geom3DUtils {

  /**
   * Returns a 4D vector [a,b,c,d] that defines a 3D plane in the following way: ax+by+cz+d=0.
   * <p>
   * The normal vector of the plane is calculated by taking the cross-product of the two vectors within the plane
   * formed between 3 points that are passed as parameters: P1->P2 and P1->P3.
   *
   * @param p1
   * @param p2
   * @param p3
   * @return
   */
  public static Vec4d createPlane(final RealPoint p1, final RealPoint p2, final RealPoint p3) {
	// p1 -> p2
	final Vec3d p12 = new Vec3d(
			p2.getDoublePosition(0) - p1.getDoublePosition(0),
			p2.getDoublePosition(1) - p1.getDoublePosition(1),
			p2.getDoublePosition(2) - p1.getDoublePosition(2));

	// p1 -> p3
	final Vec3d p13 = new Vec3d(
			p3.getDoublePosition(0) - p1.getDoublePosition(0),
			p3.getDoublePosition(1) - p1.getDoublePosition(1),
			p3.getDoublePosition(2) - p1.getDoublePosition(2));

	final Vec3d normal = new Vec3d();
	normal.cross(p12, p13);
	normal.normalize();

	// plug the coordinates of any given point in the plane into the plane equation to find the last component of the equation
	final double d = -normal.dot(new Vec3d(p1.getDoublePosition(0), p1.getDoublePosition(1), p1.getDoublePosition(2)));

	return new Vec4d(normal.x, normal.y, normal.z, d);
  }

  /**
   * Returns distance from the given plane to a point.
   *
   * @param plane
   * @param vector
   * @return
   */
  public static double planeToPointDistance(final Vec4d plane, final Vec3d vector) {

	return
			plane.x * vector.x +
					plane.y * vector.y +
					plane.z * vector.z +
					plane.w;
  }

  /**
   * Returns distance from the camera to a point.
   * <p>
   * Note that for performance reasons the {@code point} parameter will be modified by this method!
   *
   * @param cameraToWorldTransform
   * @param point
   * @return
   */
  public static double distanceFromCamera(final AffineTransform3D cameraToWorldTransform, final RealPoint point) {

	cameraToWorldTransform.applyInverse(point, point);
	return length(point);
  }

  /**
   * Returns the Z plane coordinate in camera space where the point is located.
   * <p>
   * Note that for performance reasons the {@code point} parameter will be modified by this method!
   *
   * @param cameraToWorldTransform
   * @param point
   * @return
   */
  public static double cameraZPlane(final AffineTransform3D cameraToWorldTransform, final RealPoint point) {

	cameraToWorldTransform.applyInverse(point, point);
	return point.getDoublePosition(2);
  }

  /**
   * Returns the length of the vector between (0,0,0) and the given point.
   *
   * @param point
   * @return
   */
  public static double length(final RealPoint point) {

	double sqLen = 0;
	for (int d = 0; d < point.numDimensions(); ++d) {
	  sqLen += point.getDoublePosition(d) * point.getDoublePosition(d);
	}
	return Math.sqrt(sqLen);
  }
}
