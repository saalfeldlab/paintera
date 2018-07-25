package org.janelia.saalfeldlab.paintera.control.navigation;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveRotation
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	//		This only works when we assume that affine can be
	//		https://stackoverflow.com/questions/10546320/remove-rotation-from-a-4x4-homogeneous-transformation-matrix
	//		scaling S is symmetric
	//		tr is transpose
	//		| x2 |   | R11*SX R12*SY R13*SZ TX | | x1 |
	//		| y2 | = | R21*SX R22*SY R23*SZ TY | | y1 |
	//		| z2 |   | R31*SX R32*SY R33*SZ TZ | | z1 |
	//		| 1  |   | 0      0      0      1  | |  1 |
	//		tr(A)*A = tr(R*S)*(R*S) = tr(S)*tr(R)*R*S = tr(S)*S == S * S
	//		S = sqrt( tr(A) * A )
	//		( tr(A)*A )_ij = sum_k( tr(A)_ik * A_kj ) = sum_k( A_ki * A_kj )
	//		( tr(A)*A )_ii = sum_k( (A_ki)^2 )

	private final AffineTransform3D viewerTransform;

	private final AffineTransform3D globalTransform;

	private final Consumer<AffineTransform3D> submitTransform;

	private final Object lock;

	public RemoveRotation(
			final AffineTransform3D viewerTransform,
			final AffineTransform3D globalTransform,
			final Consumer<AffineTransform3D> submitTransform,
			final Object lock)
	{
		super();
		this.viewerTransform = viewerTransform;
		this.globalTransform = globalTransform;
		this.submitTransform = submitTransform;
		this.lock = lock;
	}

	public void removeRotationCenteredAt(final double x, final double y)
	{

		final double[] mouseLocation = new double[3];

		final RealPoint p = RealPoint.wrap(mouseLocation);
		p.setPosition(x, 0);
		p.setPosition(y, 1);
		p.setPosition(0, 2);

		final AffineTransform3D global = new AffineTransform3D();

		synchronized (lock)
		{
			global.set(globalTransform);
			viewerTransform.applyInverse(mouseLocation, mouseLocation);
		}
		final double[] inOriginalSpace = mouseLocation.clone();
		global.apply(mouseLocation, mouseLocation);

		final AffineTransform3D affine = new AffineTransform3D();
		for (int i = 0; i < affine.numDimensions(); ++i)
		{
			double val = 0.0;
			for (int k = 0; k < affine.numDimensions(); ++k)
			{
				final double entry = global.get(k, i);
				val += entry * entry;
			}
			val = Math.sqrt(val);
			affine.set(val, i, i);
			affine.set(mouseLocation[i] - inOriginalSpace[i] * val, i, 3);
		}
		LOG.warn("Updating transform to {}", affine);
		submitTransform.accept(affine);
	}

}
