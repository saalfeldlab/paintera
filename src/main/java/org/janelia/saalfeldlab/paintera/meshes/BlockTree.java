package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.Set;

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

	public static final class BlockTreeEntry
	{
		public final long index;
		public final int scaleLevel;

		public final long parent;
//		public final TLongArrayList children;

		public CellGrid grid;

		public BlockTreeEntry(final long index, final int scaleLevel, final long parent, /*final TLongArrayList children,*/ final CellGrid grid)
		{
			this.index = index;
			this.scaleLevel = scaleLevel;
			this.parent = parent;
//			this.children = children;
			this.grid = grid;
		}

		public Interval interval()
		{
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

		@Override
		public String toString()
		{
			final Interval interval = interval();
			return String.format("index=%d, scale=%d, min=%s, max=%s, parent=%d", index, scaleLevel, Arrays.toString(Intervals.minAsLongArray(interval)), Arrays.toString(Intervals.maxAsLongArray(interval)), parent);
		}
	}

	private final CellGrid[] grids;
	private final TLongObjectHashMap<BlockTreeEntry>[] tree;

	@SuppressWarnings("unchecked")
	public BlockTree(
			final Set<HashWrapper<Interval>>[] blocks,
			final long[][] dimensions,
			final int[][] rendererBlockSizes,
			final double[][] scales)
	{
		if (!checkIfBlockSizesAreMultiples(rendererBlockSizes))
			throw new RuntimeException("Expected the block sizes to be multiples");

		this.grids = new CellGrid[dimensions.length];
		Arrays.setAll(this.grids, i -> new CellGrid(dimensions[i], rendererBlockSizes[i]));

		tree = new TLongObjectHashMap[grids.length];
		buildTree(blocks, scales);
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

	private void buildTree(final Set<HashWrapper<Interval>>[] blocks, final double[][] scales)
	{
		for (int i = 0; i < tree.length; ++i)
			tree[i] = new TLongObjectHashMap<>();

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
					final int currentScaleLevel = scaleLevel;
					final int nextScaleLevel = scaleLevel - 1;

					final double[] relativeScale = new double[scales[scaleLevel].length];
					Arrays.setAll(relativeScale, d -> scales[nextScaleLevel][d] / scales[currentScaleLevel][d]);

					final TLongSet nextBlockIndices = Grids.getRelevantBlocksInTargetGrid(
							new long[] {index},
							grids[scaleLevel],
							grids[nextScaleLevel],
							relativeScale
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

				tree[scaleLevel].put(index, new BlockTreeEntry(index, scaleLevel, parent, /*children,*/ grids[scaleLevel]));
			}

			lastParents = newParents;
		}
	}

	private long getBlockIndex(final Interval interval, final int scaleLevel)
	{
		final CellGrid grid = grids[scaleLevel];
		final long[] blockPos = new long[grid.numDimensions()];
		grid.getCellPosition(Intervals.minAsLongArray(interval), blockPos);
		return IntervalIndexer.positionToIndex(blockPos, grid.getGridDimensions());
	}

	private static boolean checkIfBlockSizesAreMultiples(final int[][] blockSizes)
	{
		for (int i = 1; i < blockSizes.length; ++i)
			for (int d = 0; d < blockSizes[i].length; ++d)
				if (blockSizes[i][d] % blockSizes[i - 1][d] != 0)
					return false;
		return true;
	}
}
