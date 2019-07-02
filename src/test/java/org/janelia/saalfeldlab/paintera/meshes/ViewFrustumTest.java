package org.janelia.saalfeldlab.paintera.meshes;

import java.util.ArrayList;
import java.util.List;

import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustumCulling;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.sun.javafx.geom.Vec3d;
import com.sun.javafx.geom.Vec4d;

import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import net.imglib2.FinalInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;

@SuppressWarnings("restriction")
public class ViewFrustumTest
{
	private PerspectiveCamera camera;
	private Transform cameraTransform;
	private Transform sceneTransform;
	private ViewFrustum frustumCamera;
	private AffineTransform3D sourceToWorldTransform;

	@Before
	public void setUp()
	{
		camera = new PerspectiveCamera(true);
		camera.setNearClip(0.1);
		camera.setFarClip(10.0);
		camera.setFieldOfView(45);
		camera.setVerticalFieldOfView(true);

		cameraTransform = new Translate(0, 0, -1);
		sceneTransform = new Affine(
				-1.9735242914056459E-4, -1.0436920839427981E-4, -2.061953312972022E-4, 3.0306137875177632,
				-1.2649862727035413E-4, -1.7813723813362014E-4, 2.11240737752298E-4, 0.956379113095983,
				-1.9341029860978865E-4, 2.2300587509429097E-4, 7.223755022420857E-5, -1.1240682338705246
			);

		frustumCamera = new ViewFrustum(camera, cameraTransform, () -> sceneTransform);
		frustumCamera.update(800, 600);

		sourceToWorldTransform = new AffineTransform3D();
		sourceToWorldTransform.set(
				64.0, 0.0, 0.0, 3674.0,
				0.0, 64.0, 0.0, 3674.0,
				0.0, 0.0, 80.0, 1540.0
			);
	}

//	@Test
	public void testIsInsideInCameraSpace()
	{
		final ViewFrustumCulling frustumCulling = new ViewFrustumCulling(frustumCamera);
		Assert.assertFalse(frustumCulling.isInside(new Vec3d(0, 0, 0)));
		Assert.assertTrue(frustumCulling.isInside(new Vec3d(0, 0, 1)));
		Assert.assertTrue(frustumCulling.isInside(new Vec3d(0, 0, 5)));
		Assert.assertTrue(frustumCulling.isInside(new Vec3d(0, 0, 9.9)));
		Assert.assertTrue(frustumCulling.isInside(new Vec3d(-5.0, 3.6, 8.7)));
		Assert.assertFalse(frustumCulling.isInside(new Vec3d(-4.2, 3.7, 8.7)));
		Assert.assertFalse(frustumCulling.isInside(new Vec3d(100, -20, 15)));
		Assert.assertFalse(frustumCulling.isInside(new Vec3d(-2, 5, -0.5)));
		Assert.assertFalse(frustumCulling.isInside(new Vec3d(0, 0, 10.5)));
		Assert.assertFalse(frustumCulling.isInside(new Vec3d(-2, 1, 0.5)));

		System.out.println("Camera view frustum:");
		for (int i = 0; i < frustumCulling.getPlanes().length; ++i)
			System.out.println("  plane " + i + ": " + toString(frustumCulling.getPlanes()[i]));

		System.out.println("Middle point in frustum: " + frustumCulling.middlePoint());
	}

	@Test
	public void testIsInsideInSourceSpace()
	{
		final AffineTransform3D eyeToSourceTransform = new AffineTransform3D();
		eyeToSourceTransform
			.preConcatenate(frustumCamera.eyeToWorldTransform())
			.preConcatenate(sourceToWorldTransform.inverse());

		final ViewFrustumCulling frustumCulling = new ViewFrustumCulling(frustumCamera, eyeToSourceTransform);

		System.out.println("Transformed view frustum:");
		for (int i = 0; i < frustumCulling.getPlanes().length; ++i)
			System.out.println("  plane " + i + ": " + toString(frustumCulling.getPlanes()[i]));

		final RealPoint middlePoint = frustumCulling.middlePoint();
		System.out.println("Middle point in frustum: " + middlePoint);
		Assert.assertTrue(frustumCulling.isInside(new Vec3d(
				middlePoint.getDoublePosition(0),
				middlePoint.getDoublePosition(1),
				middlePoint.getDoublePosition(2)
			)));

		Assert.assertTrue(frustumCulling.isInside(new Vec3d(-80, 200, 70)));


		final FinalInterval block = new FinalInterval(64, 64, 64);


		final IntervalIterator cornerIterator = new IntervalIterator(new int[] {2, 2, 2});
		System.out.println(System.lineSeparator() + "Intersects with block: " + frustumCulling.intersects(block));

		final List<RealPoint> blockEyeCorners = new ArrayList<>();
		while (cornerIterator.hasNext())
		{
			cornerIterator.fwd();
			final RealPoint blockCorner = new RealPoint(3);
			for (int d = 0; d < cornerIterator.numDimensions(); ++d)
				blockCorner.setPosition(cornerIterator.getIntPosition(d) == 0 ? block.realMin(d) : block.realMax(d), d);

			System.out.println("  block corner " + blockCorner + ": " + (frustumCulling.isInside(vec3d(blockCorner)) ? "inside" : "outside"));

			eyeToSourceTransform.applyInverse(blockCorner, blockCorner);
			blockEyeCorners.add(blockCorner);
		}

		final ViewFrustumCulling frustumCullingEye = new ViewFrustumCulling(frustumCamera);
		System.out.println(System.lineSeparator() + "Block transformed into the eye space:");
		for (int i = 0; i < blockEyeCorners.size(); ++i)
			System.out.println("  [" + (frustumCullingEye.isInside(vec3d(blockEyeCorners.get(i))) ? "in!" : "out") + "]  point " + i + ": " + blockEyeCorners.get(i));

	}

	private static String toString(final Vec4d vec4)
	{
		return vec4.x + ", " + vec4.y + ", " + vec4.z + ", " + vec4.w;
	}

	private static Vec3d vec3d(final RealPoint point)
	{
		return new Vec3d(
				point.getDoublePosition(0),
				point.getDoublePosition(1),
				point.getDoublePosition(2)
			);
	}
}
