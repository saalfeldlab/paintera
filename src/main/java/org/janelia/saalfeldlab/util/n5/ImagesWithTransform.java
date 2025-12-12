package org.janelia.saalfeldlab.util.n5;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Invalidate;
import net.imglib2.realtransform.AffineTransform3D;

public record ImagesWithTransform<D, T>(
		RandomAccessibleInterval<D> data,
		RandomAccessibleInterval<T> vdata,
		AffineTransform3D transform,
		Invalidate<Long> invalidateData,
		Invalidate<Long> invalidateVData
) {
}