package org.janelia.saalfeldlab.util.n5;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;

public class ImagesWithTransform<D, T> {
	public final RandomAccessibleInterval<D> data;

	public final RandomAccessibleInterval<T> vdata;

	public final AffineTransform3D transform;

	public ImagesWithTransform(
			final RandomAccessibleInterval<D> data,
			final RandomAccessibleInterval<T> vdata,
			final AffineTransform3D transform) {
		this.data = data;
		this.vdata = vdata;
		this.transform = transform;
	}
}
