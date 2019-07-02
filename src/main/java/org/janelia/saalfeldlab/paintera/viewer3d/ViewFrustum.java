package org.janelia.saalfeldlab.paintera.viewer3d;

import java.util.Arrays;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.util.fx.Transforms;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Transform;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

public class ViewFrustum extends ObservableWithListenersList
{
	public static final class ViewFrustumPlanes
	{
		public final ViewFrustumPlane nearPlane;
		public final ViewFrustumPlane farPlane;

		public ViewFrustumPlanes(final ViewFrustumPlane nearPlane, final ViewFrustumPlane farPlane)
		{
			this.nearPlane = nearPlane;
			this.farPlane = farPlane;
		}
	}

	public static final class ViewFrustumPlane
	{
		public final RealPoint minMin = new RealPoint(3);
		public final RealPoint minMax = new RealPoint(3);
		public final RealPoint maxMin = new RealPoint(3);
		public final RealPoint maxMax = new RealPoint(3);

		public RealPoint[] toArray()
		{
			return new RealPoint[] {
				minMin,
				minMax,
				maxMin,
				maxMax
			};
		}

		@Override
		public String toString()
		{
			return "{min=" + Arrays.toString(new long[] {
					Math.round(minMin.getDoublePosition(0)),
					Math.round(minMin.getDoublePosition(1)),
					Math.round(minMin.getDoublePosition(2))
				})
			+ ", max=" + Arrays.toString(new long[] {
					Math.round(maxMax.getDoublePosition(0)),
					Math.round(maxMax.getDoublePosition(1)),
					Math.round(maxMax.getDoublePosition(2))
				});
		}
	}

	private final PerspectiveCamera camera;
	private final Transform cameraTransform;
	private final Supplier<Transform> sceneTransformSupplier;

	private final ObjectProperty<ViewFrustumPlanes> nearFarPlanes = new SimpleObjectProperty<>();

	private final double[] screenSize = new double[2];
	private final double[] tanHalfFov = new double[2];

	public ViewFrustum(final PerspectiveCamera camera, final Transform cameraTransform, final Supplier<Transform> sceneTransformSupplier)
	{
		this.camera = camera;
		this.cameraTransform = cameraTransform;
		this.sceneTransformSupplier = sceneTransformSupplier;
	}

	public ReadOnlyObjectProperty<ViewFrustumPlanes> nearFarPlanesProperty()
	{
		return nearFarPlanes;
	}

	public PerspectiveCamera getCamera()
	{
		return camera;
	}

	public double[] getScreenSize()
	{
		return screenSize;
	}

	public AffineTransform3D eyeToWorldTransform()
	{
		final AffineTransform3D eyeToWorld = new AffineTransform3D();
		return eyeToWorld
			.preConcatenate(Transforms.fromTransformFX(cameraTransform))
			.preConcatenate(Transforms.fromTransformFX(sceneTransformSupplier.get()).inverse());
	}

	public RealInterval viewPlaneAtGivenDistance(final double z)
	{
		final double[] min = new double[2], max = new double[2];
		for (int d = 0; d < 2; ++d)
		{
			final double halfLen = tanHalfFov[d] * z;
			min[d] = -halfLen;
			max[d] = +halfLen;
		}
		return new FinalRealInterval(min, max);
	}



	public void update(final double width, final double height)
	{
		this.screenSize[0] = width;
		this.screenSize[1] = height;

		final double widthToHeightRatio = width / height;
		final double halfFovMainDimension = Math.toRadians(camera.getFieldOfView() / 2);
		if (camera.isVerticalFieldOfView())
		{
			tanHalfFov[0] = Math.tan(halfFovMainDimension * widthToHeightRatio);
			tanHalfFov[1] = Math.tan(halfFovMainDimension);
		}
		else
		{
			tanHalfFov[0] = Math.tan(halfFovMainDimension);
			tanHalfFov[1] = Math.tan(halfFovMainDimension / widthToHeightRatio);
		}

		final ViewFrustumPlane[] newNearFarPlanes = new ViewFrustumPlane[2];
		final double[] clipValues = {camera.getNearClip(), camera.getFarClip()};
		for (int i = 0; i < 2; ++i)
		{
			final double clipVal = clipValues[i];
			final double[] halfLen = {tanHalfFov[0] * clipVal, tanHalfFov[1] * clipVal};
			newNearFarPlanes[i] = new ViewFrustumPlane();
			newNearFarPlanes[i].minMin.setPosition(new double[] {-halfLen[0], -halfLen[1], clipVal});
			newNearFarPlanes[i].minMax.setPosition(new double[] {-halfLen[0], +halfLen[1], clipVal});
			newNearFarPlanes[i].maxMin.setPosition(new double[] {+halfLen[0], -halfLen[1], clipVal});
			newNearFarPlanes[i].maxMax.setPosition(new double[] {+halfLen[0], +halfLen[1], clipVal});
		}

		nearFarPlanes.set(new ViewFrustumPlanes(
				newNearFarPlanes[0],
				newNearFarPlanes[1]
			));

		stateChanged();


//		final RealInterval[] newFrustumNearFarPlanes = new RealInterval[2];
//		for (int i = 0; i < 2; ++i)
//		{
//			final double clipVal = clipValues[i];
//			final double[] clipMin = new double[3], clipMax = new double[3];
//			for (int d = 0; d < 2; ++d)
//			{
//				final double halfLen = tanHalfFov[d] * clipVal;
//				clipMin[d] = -halfLen;
//				clipMax[d] = +halfLen;
//			}
//			clipMin[2] = clipMax[2] = clipVal;
//			newFrustumNearFarPlanes[i] = new FinalRealInterval(clipMin, clipMax);
//		}
//
//		if (!Intervals.isEmpty(newFrustumNearFarPlanes[0]) && !Intervals.isEmpty(newFrustumNearFarPlanes[1]))
//		{
//			frustumNearFarPlanes.set(newFrustumNearFarPlanes);
//		}
//		else
//		{
//			frustumNearFarPlanes.set(null);
//		}

//		System.out.println("FOV angle: " + camera.getFieldOfView());
//		System.out.println("size of near clipping plane: " + Arrays.toString(Intervals.dimensionsAsLongArray(Intervals.smallestContainingInterval(frustumNearFarPlanes[0]))));
//		System.out.println("size of far  clipping plane: " + Arrays.toString(Intervals.dimensionsAsLongArray(Intervals.smallestContainingInterval(frustumNearFarPlanes[1]))));

		/*final Affine sceneAffine = sceneHandler.getAffine();

		final TriangleMesh frustum = new TriangleMesh();
		frustum.getTexCoords().setAll(0, 0);

//		final float[] vertexBuffer = new float[3 * 4 * 2];
		final float[] vertexBuffer = new float[3 * 5];
		System.out.println("Frustum pts:");
		int bufferPos = 0;
		for (int i = 0; i < 2; ++i)
		{
			if (i == 0)
			{
				try
				{
					final Point3D ptCamera = sceneHandler.getAffine() cameraTranslation.transform(new Point3D(0, 0, 0));
					final Point3D ptWorld = sceneAffine.inverseTransform(ptCamera);
					System.out.println("   top: " + Arrays.toString(new double[] {ptWorld.getX(), ptWorld.getY(), ptWorld.getZ()}));
					for (final double coord : new double[] {ptWorld.getX(), ptWorld.getY(), ptWorld.getZ()})
						vertexBuffer[bufferPos++] = (float) coord;
				}
				catch (final NonInvertibleTransformException e)
				{
					e.printStackTrace();
					return;
				}
				continue;
			}

			final double zPos = clipValues[i];
			final RealInterval xyPlane = frustumNearFarPlanes[i];
			final IntervalIterator cornerIterator = new IntervalIterator(new int[] {2, 2});
			while (cornerIterator.hasNext())
			{
				cornerIterator.fwd();
				final Point3D ptCamera = cameraTranslation.transform(new Point3D(
						cornerIterator.getIntPosition(0) == 0 ? xyPlane.realMin(0) : xyPlane.realMax(0),
						cornerIterator.getIntPosition(1) == 0 ? xyPlane.realMin(1) : xyPlane.realMax(1),
						zPos
					));
				try
				{
					final Point3D ptWorld = sceneAffine.inverseTransform(ptCamera);
					System.out.println("   #1: " + Arrays.toString(new double[] {ptWorld.getX(), ptWorld.getY(), ptWorld.getZ()}));
					for (final double coord : new double[] {ptWorld.getX(), ptWorld.getY(), ptWorld.getZ()})
						vertexBuffer[bufferPos++] = (float) coord;
				}
				catch (final NonInvertibleTransformException e)
				{
					e.printStackTrace();
					return;
				}
			}
		}
		frustum.getPoints().setAll(vertexBuffer);

		final int[] faces = {
				0, 2, 1,
				0, 4, 2,
				0, 3, 4,
				0, 1, 4,
				1, 2, 3,
				2, 3, 4
			};
		final int[] facesBuffer = new int[faces.length * 2];
		for (int i = 0; i < faces.length; ++i)
			facesBuffer[i * 2] = faces[i];

		frustum.getFaces().setAll(facesBuffer);

		final MeshView frustumMeshView = new MeshView(frustum);
		final PhongMaterial material = new PhongMaterial();
		material.setDiffuseColor(new Color(0.9, 0.9, 0.9, 0.5));
		frustumMeshView.setCullFace(CullFace.NONE);
		frustumMeshView.setDrawMode(DrawMode.FILL);
		frustumMeshView.setMaterial(material);
		frustumGroup.getChildren().setAll(frustumMeshView);*/
	}
}
