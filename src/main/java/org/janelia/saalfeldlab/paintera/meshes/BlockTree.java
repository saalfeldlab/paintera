package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.Set;

import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.grids.Grids;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

public class BlockTree
{
	public static final long EMPTY = -1;

	public final class BlockTreeEntry
	{
		public final long index;
		public final int scaleLevel;

		public final long parent;
		public final TLongArrayList children;

		public BlockTreeEntry(final long index, final int scaleLevel, final long parent, final TLongArrayList children)
		{
			this.index = index;
			this.scaleLevel = scaleLevel;
			this.parent = parent;
			this.children = children;
		}

		public Interval interval()
		{
			final CellGrid grid = grids[scaleLevel];
			final long[] cellMin = new long[grid.numDimensions()], cellMax = new long[grid.numDimensions()];
			final int[] cellDims = new int[grid.numDimensions()];
			grid.getCellDimensions(index, cellMin, cellDims);
			Arrays.setAll(cellMax, d -> cellMin[d] + cellDims[d] - 1);
			return new FinalInterval(cellMin, cellMax);
		}

		@Override
		public int hashCode()
		{
			return Long.hashCode(index) + 31 * Integer.hashCode(scaleLevel);
		}

		@Override
		public boolean equals(final Object o)
		{
			if (o instanceof BlockTreeEntry)
			{
				final BlockTreeEntry other = (BlockTreeEntry)o;
				return this.index == other.index && this.scaleLevel == other.scaleLevel;
			}
			return false;
		}
	}

	private final CellGrid[] grids;
	private final TLongObjectHashMap<BlockTreeEntry>[] tree;

	@SuppressWarnings("unchecked")
	public BlockTree(final DataSource<?, ?> source, final Set<HashWrapper<Interval>>[] blocks)
	{
		this.grids = source.getGrids();
		tree = new TLongObjectHashMap[grids.length];
		buildTree(source, blocks);
	}

	public TLongObjectHashMap<BlockTreeEntry>[] getTree()
	{
		return tree;
	}

	public TLongObjectHashMap<BlockTreeEntry> getTreeLevel(final int scaleLevel)
	{
		return tree[scaleLevel];
	}

	public int getNumLevels()
	{
		return tree.length;
	}

	public BlockTreeEntry find(final Interval interval, final int scaleLevel)
	{
		return get(getBlockIndex(interval, scaleLevel), scaleLevel);
	}

	public BlockTreeEntry get(final long index, final int scaleLevel)
	{
		return tree[scaleLevel].get(index);
	}

	public BlockTreeEntry getParent(final BlockTreeEntry entry)
	{
		if (entry.scaleLevel >= getNumLevels() - 1)
			return null;
		return get(entry.parent, entry.scaleLevel + 1);
	}

	private void buildTree(final DataSource<?, ?> source, final Set<HashWrapper<Interval>>[] blocks)
	{
		for (int i = 0; i < tree.length; ++i)
			tree[i] = new TLongObjectHashMap<>();

		if (checkIfBlockSizesAreMultiples())
		{
			// use simple block subdivision algorithm
			TLongLongHashMap lastParents = new TLongLongHashMap();
			for (final HashWrapper<Interval> blockHash : blocks[blocks.length - 1])
				lastParents.put(getBlockIndex(blockHash.getData(), blocks.length - 1), EMPTY);

			for (int scaleLevel = blocks.length - 1; scaleLevel >= 0; --scaleLevel)
			{
				final TLongLongHashMap newParents = new TLongLongHashMap();

				for (final TLongLongIterator blockIt = lastParents.iterator(); blockIt.hasNext();)
				{
					blockIt.advance();
					final long index = blockIt.key();
					final long parent = blockIt.value();

					final TLongArrayList children;
					if (scaleLevel > 0)
					{
						final int nextScaleLevel = scaleLevel - 1;
						final double[] relativeScales = DataSource.getRelativeScales(source, 0, scaleLevel, nextScaleLevel);
						final TLongSet nextBlockIndices = Grids.getRelevantBlocksInTargetGrid(
								new long[] {index},
								grids[scaleLevel],
								grids[nextScaleLevel],
								relativeScales
							);

						if (!nextBlockIndices.isEmpty())
						{
							children = new TLongArrayList();
							for (final TLongIterator it = nextBlockIndices.iterator(); it.hasNext();)
							{
								final long nextBlockIndex = it.next();
								final Interval nextBlock = Grids.getCellInterval(grids[nextScaleLevel], nextBlockIndex);
								if (blocks[nextScaleLevel].contains(HashWrapper.interval(nextBlock)))
								{
									children.add(nextBlockIndex);
									newParents.put(nextBlockIndex, index);
								}
							}
						}
						else
						{
							children = null;
						}
					}
					else
					{
						children = null;
					}

					tree[scaleLevel].put(index, new BlockTreeEntry(index, scaleLevel, parent, children));
				}

				lastParents = newParents;
			}
		}
		else
		{
			// more complicated block subdivision algorithm because blocks may intersect arbitrarily
			throw new UnsupportedOperationException("TODO");
		}
	}

	private long getBlockIndex(final Interval interval, final int scaleLevel)
	{
		final CellGrid grid = grids[scaleLevel];
		final long[] blockPos = new long[grid.numDimensions()];
		grid.getCellPosition(Intervals.minAsLongArray(interval), blockPos);
		return IntervalIndexer.positionToIndex(blockPos, grid.getGridDimensions());
	}

	private boolean checkIfBlockSizesAreMultiples()
	{
		assert grids.length > 0;
		final int[] blockSize = new int[grids[0].numDimensions()];
		grids[0].cellDimensions(blockSize);
		for (final CellGrid grid : grids)
		{
			for (int d = 0; d < blockSize.length; ++d)
			{
				final int largerSize  = Math.max(grid.cellDimension(d), blockSize[d]);
				final int smallerSize = Math.min(grid.cellDimension(d), blockSize[d]);
				if (largerSize % smallerSize != 0)
					return false;
			}
		}
		return true;
	}

}
