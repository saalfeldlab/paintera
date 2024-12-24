/**
 * License: GPL
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License 2 as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.net.imglib2.util;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.AbstractConvertedRandomAccess;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.util.Intervals;

import java.util.stream.IntStream;

/**
 * A {@link RandomAccessible} that tracks all blocks that have been accessed by
 * random accesses.
 *
 * @author Philipp Hanslovsky
 */
public class AccessedBlocksRandomAccessible<T> extends AbstractWrappedInterval<RandomAccessibleInterval<T>>
		implements RandomAccessibleInterval<T> {

	private final TLongSet visitedBlocks;

	private final int[] blockSize;

	private final long[] blockGridDimensions;

	public AccessedBlocksRandomAccessible(final RandomAccessibleInterval<T> source, final CellGrid grid) {

		this(
				source,
				grid.getCellDimensions(),
				grid.getGridDimensions()
		);
	}

	public AccessedBlocksRandomAccessible(final RandomAccessibleInterval<T> source, final int[] blockSize, final
	long[] blockGridDimensions) {

		super(source);
		this.visitedBlocks = new TLongHashSet();
		this.blockSize = blockSize;
		this.blockGridDimensions = blockGridDimensions;
	}

	public void clear() {

		synchronized (visitedBlocks) {
			visitedBlocks.clear();
		}
	}

	protected void addBlockId(final long id) {

		synchronized (visitedBlocks) {
			visitedBlocks.add(id);
		}
	}

	public long[] listBlocks() {
		synchronized (visitedBlocks) {
			return visitedBlocks.toArray();
		}
	}

	public CellGrid getGrid() {

		return new CellGrid(Intervals.dimensionsAsLongArray(getSource()), blockSize);
	}

	@Override
	public RandomAccess<T> randomAccess() {

		return new TrackingRandomAccess(getSource().randomAccess());
	}

	@Override
	public RandomAccess<T> randomAccess(final Interval interval) {

		return new TrackingRandomAccess(getSource().randomAccess(interval));
	}

	public class TrackingRandomAccess extends AbstractConvertedRandomAccess<T, T> {

		private final long[] blockGridPosition;

		public TrackingRandomAccess(final RandomAccess<T> source) {

			super(source);
			this.blockGridPosition = new long[source.numDimensions()];
		}

		@Override
		public T get() {

			/* Calculate the blockId from the current grid position and dimension.
			 * NOTE: Previously we used IntervalIndexer.positionToIndex, but this was SLOW, since it required us to pass in the `blockGridPosition`
			 * 	as an array. Arrays.setAll was taking an obnoxious about of time. */

			final int maxDim = blockGridDimensions.length - 1;
			long blockId = (source.getLongPosition(maxDim) / blockSize[maxDim]);
			for (int d = maxDim - 1; d >= 0; --d)
				blockId = blockId * blockGridDimensions[d] + (source.getLongPosition(d) / blockSize[d]);
			addBlockId(blockId);
			return source.get();
		}

		@Override
		public AbstractConvertedRandomAccess<T, T> copy() {

			return new TrackingRandomAccess(source.copy());
		}

	}
}
