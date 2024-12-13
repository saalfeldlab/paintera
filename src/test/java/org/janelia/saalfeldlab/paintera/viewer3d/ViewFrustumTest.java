package org.janelia.saalfeldlab.paintera.viewer3d;

import com.sun.javafx.geom.Vec3d;
import javafx.scene.PerspectiveCamera;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("restriction")
public class ViewFrustumTest {

	private PerspectiveCamera camera;
	private ViewFrustum frustumCamera;

	private AffineTransform3D cameraTransform;
	private AffineTransform3D sceneTransform;
	private AffineTransform3D sourceToWorldTransform;

	@BeforeEach
	public void setUp() {

		camera = new PerspectiveCamera(true);
		camera.setNearClip(0.1);
		camera.setFarClip(10.0);
		camera.setFieldOfView(45);
		camera.setVerticalFieldOfView(true);

		frustumCamera = new ViewFrustum(camera, new double[]{800, 600});

		cameraTransform = new AffineTransform3D();
		cameraTransform.setTranslation(0, 0, -1);

		sceneTransform = new AffineTransform3D();
		sceneTransform.set(
				-1.9735242914056459E-4, -1.0436920839427981E-4, -2.061953312972022E-4, 3.0306137875177632,
				-1.2649862727035413E-4, -1.7813723813362014E-4, 2.11240737752298E-4, 0.956379113095983,
				-1.9341029860978865E-4, 2.2300587509429097E-4, 7.223755022420857E-5, -1.1240682338705246
		);

		sourceToWorldTransform = new AffineTransform3D();
		sourceToWorldTransform.set(
				64.0, 0.0, 0.0, 3674.0,
				0.0, 64.0, 0.0, 3674.0,
				0.0, 0.0, 80.0, 1540.0
		);
	}

	@Test
	public void testIsInside() {

		final ViewFrustumCulling frustumCulling = new ViewFrustumCulling(frustumCamera);
		assertFalse(frustumCulling.isInside(new Vec3d(0, 0, 0)));
		assertTrue(frustumCulling.isInside(new Vec3d(0, 0, 1)));
		assertTrue(frustumCulling.isInside(new Vec3d(0, 0, 5)));
		assertTrue(frustumCulling.isInside(new Vec3d(0, 0, 9.9)));
		assertTrue(frustumCulling.isInside(new Vec3d(-4.8, 3.6, 8.7)));
		assertFalse(frustumCulling.isInside(new Vec3d(-4.2, 3.7, 8.7)));
		assertFalse(frustumCulling.isInside(new Vec3d(100, -20, 15)));
		assertFalse(frustumCulling.isInside(new Vec3d(-2, 5, -0.5)));
		assertFalse(frustumCulling.isInside(new Vec3d(0, 0, 10.5)));
		assertFalse(frustumCulling.isInside(new Vec3d(-2, 1, 0.5)));

		final AffineTransform3D eyeToSourceTransform = new AffineTransform3D();
		eyeToSourceTransform
				.preConcatenate(cameraTransform)
				.preConcatenate(sceneTransform.inverse())
				.preConcatenate(sourceToWorldTransform.inverse());

		final ViewFrustumCulling frustumCullingWithTransform = new ViewFrustumCulling(frustumCamera, eyeToSourceTransform);
		assertTrue(frustumCullingWithTransform.isInside(new Vec3d(-80, 200, 70)));
	}

	@Test
	public void testIntersects() {

		final AffineTransform3D eyeToSourceTransform = new AffineTransform3D();
		eyeToSourceTransform
				.preConcatenate(cameraTransform)
				.preConcatenate(sceneTransform.inverse())
				.preConcatenate(sourceToWorldTransform.inverse());

		final ViewFrustumCulling frustumCullingWithTransform = new ViewFrustumCulling(frustumCamera, eyeToSourceTransform);

		assertTrue(frustumCullingWithTransform.intersects(Intervals.createMinSize(0, 0, 0, 64, 64, 64)));
		assertFalse(frustumCullingWithTransform.intersects(Intervals.createMinSize(128, 0, 0, 64, 64, 64)));
	}
}

