package org.janelia.saalfeldlab.util.fx;

import javafx.scene.transform.Affine;
import javafx.scene.transform.MatrixType;
import net.imglib2.realtransform.AffineTransform3D;

public class Transforms
{
	public static AffineTransform3D fromAffineFX(final Affine affine)
	{
		final AffineTransform3D ret = new AffineTransform3D();
		ret.set(affine.toArray(MatrixType.MT_3D_3x4));
		return ret;
	}

	public static Affine toAffineFX(final AffineTransform3D affine)
	{
		return new Affine(affine.getRowPackedCopy(), MatrixType.MT_3D_3x4, 0);
	}
}
