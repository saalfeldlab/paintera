package org.janelia.saalfeldlab.paintera.meshes;

import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustumCulling;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.sun.javafx.geom.Vec3d;

import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;

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

	@Test
	public void testIsInside()
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

		final AffineTransform3D eyeToSourceTransform = new AffineTransform3D();
		eyeToSourceTransform
			.preConcatenate(frustumCamera.eyeToWorldTransform())
			.preConcatenate(sourceToWorldTransform.inverse());
		final ViewFrustumCulling frustumCullingWithTransform = new ViewFrustumCulling(frustumCamera, eyeToSourceTransform);
		Assert.assertTrue(frustumCullingWithTransform.isInside(new Vec3d(-80, 200, 70)));
	}

	@Test
	public void testIntersects()
	{
		final AffineTransform3D eyeToSourceTransform = new AffineTransform3D();
		eyeToSourceTransform
			.preConcatenate(frustumCamera.eyeToWorldTransform())
			.preConcatenate(sourceToWorldTransform.inverse());

		final ViewFrustumCulling frustumCullingWithTransform = new ViewFrustumCulling(frustumCamera, eyeToSourceTransform);

		Assert.assertTrue(frustumCullingWithTransform.intersects(Intervals.createMinSize(0, 0, 0, 64, 64, 64)));
		Assert.assertFalse(frustumCullingWithTransform.intersects(Intervals.createMinSize(128, 0, 0, 64, 64, 64)));
	}
}

