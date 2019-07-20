package org.janelia.saalfeldlab.paintera.viewer3d;

import java.util.Arrays;

import javafx.scene.PerspectiveCamera;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.util.Util;

public class ViewFrustum
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

		public ViewFrustumPlane[] toArray()
		{
			return new ViewFrustumPlane[] {nearPlane, farPlane};
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

	private final ViewFrustumPlanes nearFarPlanes;
	private final double[] screenSize;
	private final double[] tanHalfFov;

	public ViewFrustum(final PerspectiveCamera camera, final double[] screenSize)
	{
		this.screenSize = screenSize.clone();
		this.tanHalfFov = new double[2];

		final double widthToHeightRatio = screenSize[0] / screenSize[1];
		final double halfFovMainDimension = Math.toRadians(camera.getFieldOfView() / 2);
		if (camera.isVerticalFieldOfView())
		{
			tanHalfFov[1] = Math.tan(halfFovMainDimension);
			tanHalfFov[0] = tanHalfFov[1] * widthToHeightRatio;
		}
		else
		{
			tanHalfFov[0] = Math.tan(halfFovMainDimension);
			tanHalfFov[1] = tanHalfFov[0] / widthToHeightRatio;
		}

		final ViewFrustumPlane[] newNearFarPlanes = new ViewFrustumPlane[2];
		final double[] clipValues = {camera.getNearClip(), camera.getFarClip()};
		for (int i = 0; i < 2; ++i)
		{
			final double clipVal = clipValues[i];
			final RealInterval viewPlane2D = viewPlaneAtGivenDistance(clipVal);
			newNearFarPlanes[i] = new ViewFrustumPlane();
			newNearFarPlanes[i].minMin.setPosition(new double[] {viewPlane2D.realMin(0), viewPlane2D.realMin(1), clipVal});
			newNearFarPlanes[i].minMax.setPosition(new double[] {viewPlane2D.realMin(0), viewPlane2D.realMax(1), clipVal});
			newNearFarPlanes[i].maxMin.setPosition(new double[] {viewPlane2D.realMax(0), viewPlane2D.realMin(1), clipVal});
			newNearFarPlanes[i].maxMax.setPosition(new double[] {viewPlane2D.realMax(0), viewPlane2D.realMax(1), clipVal});
		}

		nearFarPlanes = new ViewFrustumPlanes(
				newNearFarPlanes[0],
				newNearFarPlanes[1]
			);
	}

	public ViewFrustumPlanes getNearFarPlanes()
	{
		return nearFarPlanes;
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

	public double screenSizeToViewPlaneRatio(final double z)
	{
		if (Util.isApproxEqual(z, 0, 1e-7) || z < 0)
			return Double.POSITIVE_INFINITY;
		else if (!Double.isFinite(z))
			return 0.0;

		final double viewPlaneWidth = tanHalfFov[0] * z * 2;
		return screenSize[0] / viewPlaneWidth;
	}
}
