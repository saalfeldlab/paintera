package org.janelia.saalfeldlab.paintera.data.mask.persist;

import gnu.trove.map.TLongObjectMap;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PersistCanvasBackToRandomAccessibleInterval implements PersistCanvas {

	private final RandomAccessibleInterval<? extends IntegerType<?>> labels;

	public PersistCanvasBackToRandomAccessibleInterval(final RandomAccessibleInterval<? extends IntegerType<?>> labels) {
		this.labels = labels;
	}

	@Override
	public List<TLongObjectMap<BlockDiff>> persistCanvas(final CachedCellImg<UnsignedLongType, ?> canvas, long[] blockIds) {
		final CellGrid grid = canvas.getCellGrid();
		final long[] pos = Intervals.minAsLongArray(canvas);
		final long[] min = Intervals.minAsLongArray(canvas);
		final long[] max = Intervals.maxAsLongArray(canvas);
		final int[] size = Intervals.minAsIntArray(canvas);
		for (final long blockId : blockIds) {
			grid.getCellGridPositionFlat(blockId, pos);
			grid.getCellDimensions(pos, min, size);
			Arrays.setAll(max, d -> min[d] + size[d] - 1);
			LoopBuilder
					.setImages(Views.interval(labels, min, max), Views.interval(canvas, min, max))
					.forEachPixel((t, s) -> t.setInteger(s.getIntegerLong()));
		}
		return new ArrayList<>();
	}

	@Override
	public void updateLabelBlockLookup(List<TLongObjectMap<BlockDiff>> blockDiffs) {
		// do nothing
	}

	@Override
	public boolean supportsLabelBlockLookupUpdate() {
		return false;
	}
}
