package org.janelia.saalfeldlab.util.n5;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Invalidate;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * A single scale level of a source with an associated source-to-global transform.
 * <p>
 * When the backing dataset is not already 3D, {@code data}/{@code vdata} are in-place, writable {@code SpatialMapping.to3D}
 * views. Higher dimensions are sliced at a fixed position, missing spatial dimensions embedded as singletons.
 *
 * @param data            XYZ 3D image
 * @param vdata           volatile XYZ 3D image for asynchronous rendering
 * @param transform       maps the 3D source coordinates to global space
 * @param invalidateData  cache-invalidation handle for {@code data}'s backing cells, keyed by cell index
 * @param invalidateVData cache-invalidation handle for {@code vdata}'s backing cells, keyed by cell index
 * @param grid            the 3D cell grid of the source
 */
public record ImagesWithTransform<D, V>(
		RandomAccessibleInterval<D> data,
		RandomAccessibleInterval<V> vdata,
		AffineTransform3D transform,
		Invalidate<Long> invalidateData,
		Invalidate<Long> invalidateVData,
		CellGrid grid
) {
}
