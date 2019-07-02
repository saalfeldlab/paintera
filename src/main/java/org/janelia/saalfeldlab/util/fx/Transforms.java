package org.janelia.saalfeldlab.util.fx;

import javafx.scene.transform.Affine;
import javafx.scene.transform.MatrixType;
import javafx.scene.transform.Transform;
import net.imglib2.realtransform.AffineTransform3D;

public class Transforms
{
	public static AffineTransform3D fromTransformFX(final Transform transform)
	{
		final AffineTransform3D ret = new AffineTransform3D();
		ret.set(transform.toArray(MatrixType.MT_3D_3x4));
		return ret;
	}

	public static Affine toTransformFX(final AffineTransform3D transform)
	{
		return new Affine(transform.getRowPackedCopy(), MatrixType.MT_3D_3x4, 0);
	}
}
