package org.janelia.saalfeldlab.paintera.data.mask.persist;

import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.view.Views;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class PersistCanvasBackToRandomAccessibleIntervalTest {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Test
	public void testPersist() {

		final ArrayImg<UnsignedLongType, LongArray> labels = ArrayImgs.unsignedLongs(3);
		final ArrayImg<UnsignedLongType, LongArray> groundTruth = ArrayImgs.unsignedLongs(new long[]{2, 0, 1}, 3);
		final PersistCanvasBackToRandomAccessibleInterval persist = new PersistCanvasBackToRandomAccessibleInterval(labels);
		final CellGrid grid = new CellGrid(new long[] {3}, new int[] {2});

		final CellLoader<UnsignedLongType> loader =
				img -> LoopBuilder.setImages(img, Views.interval(groundTruth, img)).forEachPixel(Type::set);
		final Cache<Long, Cell<LongArray>> cache = new SoftRefLoaderCache<Long, Cell<LongArray>>()
				.withLoader(LoadedCellCacheLoader.get(grid, loader, new UnsignedLongType(), AccessFlags.setOf()));

		final CachedCellImg<UnsignedLongType, LongArray> canvas = new CachedCellImg<>(
				grid,
				new UnsignedLongType(),
				cache,
				new LongArray(1));

		persist.persistCanvas(canvas, new long[] {0, 1});

		LOG.debug("Labels array:       {}", labels.update(null).getCurrentStorageArray());
		LOG.debug("Ground truth array: {}", groundTruth.update(null).getCurrentStorageArray());

		Assert.assertArrayEquals(
				groundTruth.update(null).getCurrentStorageArray(),
				labels.update(null).getCurrentStorageArray());



	}

}
