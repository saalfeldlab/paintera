package org.janelia.saalfeldlab.fx.ortho;

import net.imglib2.realtransform.AffineTransform3D;

public enum ViewerAxis
{
	X, Y, Z;

	public static AffineTransform3D globalToViewer(final ViewerAxis axis)
	{

		final AffineTransform3D tf = new AffineTransform3D();
		switch (axis)
		{
			case Z:
				break;
			case Y:
				tf.rotate(0, -Math.PI / 2);
				break;
			case X:
				tf.rotate(1, Math.PI / 2);
				break;
		}
		return tf;
	}
};
