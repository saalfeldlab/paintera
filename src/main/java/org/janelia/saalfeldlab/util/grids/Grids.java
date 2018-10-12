package org.janelia.saalfeldlab.util.grids;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.stream.DoubleStream;

public class Grids {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void linearIndexToCellPositionMinMax(
			final CellGrid grid,
			final long linearIndex,
			final long[] gridPosition,
			final long[] min,
			final long[] max
	)
	{
		grid.getCellGridPositionFlat(linearIndex, gridPosition);
		Arrays.setAll(min, d -> gridPosition[d] * grid.cellDimension(d));
		Arrays.setAll(max, d -> Math.min(min[d] + grid.cellDimension(d), grid.imgDimension(d)) - 1);
	}

	/**
	 *
	 * @param sourceBlocks linear index representation of blocks in source grid space
	 * @param sourceGrid grid of source
	 * @param targetGrid grid of target
	 * @param relativeScale relative scale from source to target coordinates
	 * @return Linear index representation for all blocks in {@code targetGrid} that intersect with {@code sourceBlocks}
	 * in {@code sourcegrid}
	 */
	public static TLongSet getRelevantBlocksInTargetGrid(
			final long[] sourceBlocks,
			final CellGrid sourceGrid,
			final CellGrid targetGrid,
			final double[] relativeScale)
	{
		return getRelevantBlocksInTargetGrid(
				sourceBlocks,
				sourceGrid,
				targetGrid,
				DoubleStream.generate(() -> 1.0).limit(relativeScale.length).toArray(),
				relativeScale
		);
	}


	/**
	 *
	 * @param sourceBlocks linear index representation of blocks in source grid space
	 * @param sourceGrid grid of source
	 * @param targetGrid grid of target
	 * @param scaleSourceToWorld scale source coordinate to world
	 * @param scaleTargetToWorld scale target coordinate to world
	 * @return Linear index representation for all blocks in {@code targetGrid} that intersect with {@code sourceBlocks}
	 * in {@code sourcegrid}
	 */
	public static TLongSet getRelevantBlocksInTargetGrid(
			final long[] sourceBlocks,
			final CellGrid sourceGrid,
			final CellGrid targetGrid,
			final double[] scaleSourceToWorld,
			final double[] scaleTargetToWorld)
	{

		assert DoubleStream.of(scaleSourceToWorld).filter(d -> d <= 0).count() == 0;
		assert DoubleStream.of(scaleTargetToWorld).filter(d -> d <= 0).count() == 0;

		final long[]   blockPos     = new long[sourceGrid.numDimensions()];
		final int[]    blockSize    = new int[sourceGrid.numDimensions()];
		final double[] blockMin     = new double[sourceGrid.numDimensions()];
		final double[] blockMax     = new double[sourceGrid.numDimensions()];
		sourceGrid.cellDimensions(blockSize);

		final TLongSet targetBlocks = new TLongHashSet();
		for (final long blockId : sourceBlocks)
		{
			sourceGrid.getCellGridPositionFlat(blockId, blockPos);
			Arrays.setAll(blockMin, d -> blockPos[d] * blockSize[d]);
			Arrays.setAll(blockMax, d -> Math.min(blockMin[d] + blockSize[d], sourceGrid.imgDimension(d)) - 1);
			scaleBoundingBox(blockMin, blockMax, blockMin, blockMax, scaleSourceToWorld, scaleTargetToWorld);
			targetBlocks.addAll(getIntersectingBlocks(blockMin, blockMax, targetGrid));
		}

		return targetBlocks;
	}

	/**
	 * Get all blocks/cells of a {@link CellGrid} that the real interval defined by {@code min} and {@code max}
	 * intersects with, represented as linear indices.
	 * @param min top-left corner of interval
	 * @param max bottom-right corner of interval
	 * @param cellGrid defines grid (block size/cell size)
	 * @return linear indices of all cells/blocks that intersect with interval defined by {@code min}, {@code max}.
	 */
	public static long[] getIntersectingBlocks(
			final double[] min,
			final double[] max,
			final CellGrid cellGrid)
	{
		final Interval relevantInterval = snapToGrid(min, max, cellGrid);
		final int[] blockSize = new int[cellGrid.numDimensions()];
		cellGrid.cellDimensions(blockSize);
		TLongArrayList blockIndices = new TLongArrayList();
		net.imglib2.algorithm.util.Grids.forEachOffset(
				Intervals.minAsLongArray(relevantInterval),
				Intervals.maxAsLongArray(relevantInterval),
				blockSize,
				blockOffset -> blockIndices.add(posToIndex(cellGrid, blockOffset)));
		return blockIndices.toArray();
	}

	/**
	 *
	 * Snap {@code min}, {@code max} to {@code cellGrid}, i.e. increase/decrease min/max such that
	 * min/max are integer multiples of block size/cell size. The snapped interval is restricted
	 * to the interval defined by {@code cellGrid}.
	 *
	 * @param min top-left corner of interval
	 * @param max bottom-right corner of interval
	 * @param cellGrid defines the block size/cell size to which to snap
	 * @return new interval that is aligned with block size/cell size of {@code cellGrid}
	 */
	public static Interval snapToGrid(
			final double[] min,
			final double[] max,
			final CellGrid cellGrid
	)
	{
		return snapToGrid(new FinalRealInterval(min, max), cellGrid);
	}

	/**
	 *
	 * Snap real {@code interval} to {@code cellGrid}, i.e. increase/decrease min/max such that
	 * min/max are integer multiples of block size/cell size. The snapped interval is restricted
	 * to the interval defined by {@code cellGrid}.
	 *
	 * @param interval to be snapped to grid
	 * @param cellGrid defines the block size/cell size to which to snap
	 * @return new interval that is aligned with block size/cell size of {@code cellGrid}
	 */
	public static Interval snapToGrid(
			final RealInterval interval,
			final CellGrid cellGrid
			)
	{
		return snapToGrid(Intervals.smallestContainingInterval(interval), cellGrid);
	}

	/**
	 *
	 * Snap {@code min}, {@code max} to {@code cellGrid}, i.e. increase/decrease min/max such that
	 * min/max are integer multiples of block size/cell size. The snapped interval is restricted
	 * to the interval defined by {@code cellGrid}.
	 *
	 * @param min top-left corner of interval
	 * @param max bottom-right corner of interval
	 * @param cellGrid defines the block size/cell size to which to snap
	 * @return new interval that is aligned with block size/cell size of {@code cellGrid}
	 */
	public static Interval snapToGrid(
			long[] min,
			long[] max,
			final CellGrid cellGrid)
	{
		return snapToGrid(new FinalInterval(min, max), cellGrid);
	}

	/**
	 *
	 * Snap {@code interval} to {@code cellGrid}, i.e. increase/decrease min/max such that
	 * min/max are integer multiples of block size/cell size. The snapped interval is restricted
	 * to the interval defined by {@code cellGrid}.
	 *
	 * @param interval to be snapped to grid
	 * @param cellGrid defines the block size/cell size to which to snap
	 * @return new interval that is aligned with block size/cell size of {@code cellGrid}
	 */
	public static Interval snapToGrid(
			final Interval interval,
			final CellGrid cellGrid)
	{
		assert interval.numDimensions() == cellGrid.numDimensions();
		final Interval intersectedInterval = Intervals.intersect(
				interval,
				new FinalInterval(cellGrid.getImgDimensions()));
		final int nDim = cellGrid.numDimensions();
		final long[] snappedMin = new long[nDim];
		final long[] snappedMax = new long[nDim];
		for (int d = 0; d < nDim; ++d)
		{
			final long min = intersectedInterval.min(d);
			final long max = intersectedInterval.max(d);
			final int blockSize = cellGrid.cellDimension(d);
			snappedMin[d] = Math.max(snapDown(min, blockSize), 0);
			snappedMax[d] = Math.min(snapUp(max, blockSize), cellGrid.imgDimension(d) - 1);
//			snappedMin[d] = Math.max(min - (min % blockSize), 0);
//			snappedMax[d] = Math.min(max - (max % blockSize), cellGrid.imgDimension(d)) - 1;
		}
		return new FinalInterval(snappedMin, snappedMax);
	}

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to {@code relativeScale}.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param relativeScale relativeScale from source to target
	 */
	public static void scaleBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final double[] relativeScale
			)
	{
		final ScaleAndTranslation tf = new ScaleAndTranslation(relativeScale, new double[relativeScale.length]);
		mapBoundingBox(min, max, mappedMin, mappedMax, tf);
	}

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to scaling to world space.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param sourceToWorld transform from source to world coordinates
	 * @param targetToWorld transform from target to world coordinates
	 */
	public static void scaleBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final double[] sourceToWorld,
			final double[] targetToWorld
	)
	{
		mapBoundingBox(
				min,
				max,
				mappedMin,
				mappedMax,
				new ScaleAndTranslation(sourceToWorld, new double[sourceToWorld.length]),
				new ScaleAndTranslation(targetToWorld, new double[targetToWorld.length]));
	}

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to {@code tf}.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param tf transform from source to target
	 */
	public static void mapBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final ScaleAndTranslation tf)
	{
		assert min.length == tf.numSourceDimensions();
		assert max.length == tf.numSourceDimensions();
		assert mappedMin.length == tf.numTargetDimensions();
		assert mappedMax.length == tf.numTargetDimensions();

		tf.apply(min, mappedMin);
		tf.apply(max, mappedMax);
		for (int d = 0; d < mappedMin.length; ++d)
		{
			double tmp = mappedMax[d];
			if (mappedMin[d] > tmp)
			{
				mappedMax[d] = mappedMin[d];
				mappedMin[d] = tmp;
			}
		}
	}

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to transforms to world coordinates.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param sourceToWorld transform from source to world coordinates
	 * @param targetToWorld transform from target to world coordinates
	 */
	public static void mapBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final ScaleAndTranslation sourceToWorld,
			final ScaleAndTranslation targetToWorld)
	{
		assert sourceToWorld.numSourceDimensions() == targetToWorld.numSourceDimensions();
		assert sourceToWorld.numTargetDimensions() == targetToWorld.numTargetDimensions();
		assert min.length == sourceToWorld.numSourceDimensions();
		assert max.length == sourceToWorld.numSourceDimensions();
		assert mappedMin.length == targetToWorld.numTargetDimensions();
		assert mappedMax.length == targetToWorld.numTargetDimensions();
		mapBoundingBox(min, max, mappedMin, mappedMax, sourceToWorld.preConcatenate(targetToWorld.inverse()));
	}

	private static long[] getCellPos(
			final CellGrid cellGrid,
			final long[] pos,
			final long[] cellPos
	)
	{
		cellGrid.getCellPosition(pos, cellPos);
		return cellPos;
	}

	private static long posToIndex(
			final CellGrid cellGrid,
			final long[] position
	)
	{
		final long index = IntervalIndexer.positionToIndex(
				getCellPos(cellGrid, position, new long[position.length]),
				cellGrid.getGridDimensions());
		LOG.debug("Index for position {} ind grid {}: {}", position, cellGrid, index);
		return index;
	}

	private static long snapDown(long pos, int blockSize)
	{
		return (pos / blockSize) * blockSize;
	}

	private static long snapUp(long pos, int blockSize)
	{
		return (pos / blockSize) * blockSize + blockSize - 1;
	}


}
