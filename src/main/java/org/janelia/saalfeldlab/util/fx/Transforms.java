package org.janelia.saalfeldlab.util.fx;

import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import net.imglib2.realtransform.AffineTransform3D;

public class Transforms
{
	public static AffineTransform3D fromTransformFX(final Transform transform)
	{
		final AffineTransform3D ret = new AffineTransform3D();
		ret.set(
				transform.getMxx(), transform.getMxy(), transform.getMxz(), transform.getTx(),
				transform.getMyx(), transform.getMyy(), transform.getMyz(), transform.getTy(),
				transform.getMzx(), transform.getMzy(), transform.getMzz(), transform.getTz()
			);
		return ret;
	}

	public static Affine toTransformFX(final AffineTransform3D transform)
	{
		return new Affine(
				transform.get(0, 0), transform.get(0, 1), transform.get(0, 2), transform.get(0, 3),
				transform.get(1, 0), transform.get(1, 1), transform.get(1, 2), transform.get(1, 3),
				transform.get(2, 0), transform.get(2, 1), transform.get(2, 2), transform.get(2, 3)
			);
	};
}
