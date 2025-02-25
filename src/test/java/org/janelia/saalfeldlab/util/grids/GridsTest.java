package org.janelia.saalfeldlab.util.grids;

import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.util.Intervals;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

public class GridsTest {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Test
	public void testMapBoundingBox2D() {

		final double[] min = {-1.0, -1.0};
		final double[] max = {+1.0, +1.0};
		final double[] mappedMin = new double[2];
		final double[] mappedMax = new double[2];

		Grids.mapBoundingBox(min, max, mappedMin, mappedMax, new ScaleAndTranslation(new double[]{1.0, 1.0}, new double[]{0.5, 1.0}));
		assertArrayEquals(new double[]{-0.5, +0.0}, mappedMin, 0.0);
		assertArrayEquals(new double[]{+1.5, +2.0}, mappedMax, 0.0);

		Grids.scaleBoundingBox(min, max, mappedMin, mappedMax, new double[]{2.0, 3.0});
		assertArrayEquals(new double[]{-2.0, -3.0}, mappedMin, 0.0);
		assertArrayEquals(new double[]{+2.0, +3.0}, mappedMax, 0.0);

		Grids.scaleBoundingBox(min, max, mappedMin, mappedMax, new double[]{2.0, 3.0}, new double[]{3.0, 4.0});
		assertArrayEquals(new double[]{2.0 / 3.0 * min[0], 3.0 / 4.0 * min[1]}, mappedMin, 0.0);
		assertArrayEquals(new double[]{2.0 / 3.0 * max[0], 3.0 / 4.0 * max[1]}, mappedMax, 0.0);
	}

	@Test
	public void testGetIntersectingBlocks() {
		// intersects with all
		//
		//  ______ ______ __
		// |   0  |   1  | 2|
		// |------|------|--|
		// |   3  |   4  | 5|
		//  ‾‾‾‾‾‾ ‾‾‾‾‾‾ ‾‾
		{
			final CellGrid grid = new CellGrid(new long[]{7, 8}, new int[]{3, 4});
			final double[] min = new double[]{1.3, 2.1};
			final double[] max = new double[]{7.2, 4.7};
			final long[] indices = Grids.getIntersectingBlocks(min, max, grid);
			Arrays.sort(indices);
			// 3 intervals along first dimensions, 2 dimensions along second dimension
			assertEquals(6, indices.length);
			assertArrayEquals(LongStream.range(0, 6).toArray(), indices);
		}

		// intersects with cells {0, 1, 3, 4}
		//
		//  ______ ______ __
		// |   0  |   1  | 2|
		// |------|------|--|
		// |   3  |   4  | 5|
		//  ‾‾‾‾‾‾ ‾‾‾‾‾‾ ‾‾
		{
			final CellGrid grid = new CellGrid(new long[]{7, 8}, new int[]{3, 4});
			final double[] min = new double[]{1.0, 2.0};
			final double[] max = new double[]{4.7, 5.0};
			final long[] indices = Grids.getIntersectingBlocks(min, max, grid);
			Arrays.sort(indices);
			LOG.debug("Retrieved intersecting indices {}", indices);
			assertEquals(4, indices.length);
			assertArrayEquals(LongStream.of(0, 1, 3, 4).toArray(), indices);
		}
	}

	@Test
	public void snapToGridTest() {
		//
		//  ______ ______ __
		// |   0  |   1  | 2|
		// |------|------|--|
		// |   3  |   4  | 5|
		//  ‾‾‾‾‾‾ ‾‾‾‾‾‾ ‾‾
		final CellGrid grid = new CellGrid(new long[]{7, 8}, new int[]{3, 4});
		// double[] arrays
		{
			final double[] min = new double[]{1.0, 2.0};
			final double[] max = new double[]{4.7, 5.0};
			final Interval interval = Grids.snapToGrid(min, max, grid);
			assertArrayEquals(new long[]{0, 0}, Intervals.minAsLongArray(interval));
			assertArrayEquals(new long[]{5, 7}, Intervals.maxAsLongArray(interval));
		}

		{
			final double[] min = new double[]{0.0, 0.0};
			final double[] max = new double[]{6.0, 7.0};
			final Interval interval = Grids.snapToGrid(min, max, grid);
			assertArrayEquals(new long[]{0, 0}, Intervals.minAsLongArray(interval));
			assertArrayEquals(new long[]{6, 7}, Intervals.maxAsLongArray(interval));
		}

		{
			final double[] min = new double[]{3.0, 3.9};
			final double[] max = new double[]{3.1, 3.9};
			final Interval interval = Grids.snapToGrid(min, max, grid);
			assertArrayEquals(new long[]{3, 0}, Intervals.minAsLongArray(interval));
			assertArrayEquals(new long[]{5, 7}, Intervals.maxAsLongArray(interval));
		}

		// long[] arrays
		{
			final long[] min = new long[]{0, 2};
			final long[] max = new long[]{4, 5};
			final Interval interval = Grids.snapToGrid(min, max, grid);
			assertArrayEquals(new long[]{0, 0}, Intervals.minAsLongArray(interval));
			assertArrayEquals(new long[]{5, 7}, Intervals.maxAsLongArray(interval));
		}

		{
			final long[] min = new long[]{0, 0};
			final long[] max = new long[]{6, 7};
			final Interval interval = Grids.snapToGrid(min, max, grid);
			assertArrayEquals(new long[]{0, 0}, Intervals.minAsLongArray(interval));
			assertArrayEquals(new long[]{6, 7}, Intervals.maxAsLongArray(interval));
		}

		{
			final long[] min = new long[]{2, 3};
			final long[] max = new long[]{2, 4};
			final Interval interval = Grids.snapToGrid(min, max, grid);
			assertArrayEquals(new long[]{0, 0}, Intervals.minAsLongArray(interval));
			assertArrayEquals(new long[]{2, 7}, Intervals.maxAsLongArray(interval));
		}

		{
			final long[] min = new long[]{1, 5};
			final long[] max = new long[]{1, 6};
			final Interval interval = Grids.snapToGrid(min, max, grid);
			assertArrayEquals(new long[]{0, 4}, Intervals.minAsLongArray(interval));
			assertArrayEquals(new long[]{2, 7}, Intervals.maxAsLongArray(interval));
		}
	}

	@Test
	public void testGetRelevantBlocksInTargetGrid() {
		// source
		//
		//  ______ ______ __
		// |   0  |   1  | 2|
		// |------|------|--|
		// |   3  |   4  | 5|
		//  ‾‾‾‾‾‾ ‾‾‾‾‾‾ ‾‾

		// target
		//  ____ ____
		// |  0 |  1 |
		// |----|----|
		// |  2 |  3 |
		//  ‾‾‾‾ ‾‾‾‾

		final double[] scaleSourceToWorld = {2.0, 3.0};
		final double[] scaleTargetToWorld = {3.0, 6.0};

		// img size in world coordinates: {14, 24}
		// source grid block size in world coordinates: {6, 12}
		// target grid block size in world coorinates: {9, 18}
		final CellGrid sourceGrid = new CellGrid(new long[]{7, 8}, new int[]{3, 4});
		final CellGrid targetGrid = new CellGrid(new long[]{5, 4}, new int[]{3, 3});
		{
			long[] sourceBlocks = LongStream.range(0, 6).toArray();
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					scaleSourceToWorld,
					scaleTargetToWorld).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(LongStream.range(0, 4).toArray(), targetBlocks);
		}

		// test each source block:
		{
			long[] sourceBlocks = {0};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					scaleSourceToWorld,
					scaleTargetToWorld).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0}, targetBlocks);
		}

		{
			long[] sourceBlocks = {1};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					scaleSourceToWorld,
					scaleTargetToWorld).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0, 1}, targetBlocks);
		}

		{
			long[] sourceBlocks = {2};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					scaleSourceToWorld,
					scaleTargetToWorld).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{1}, targetBlocks);
		}

		{
			long[] sourceBlocks = {3};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					scaleSourceToWorld,
					scaleTargetToWorld).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0, 2}, targetBlocks);
		}

		{
			long[] sourceBlocks = {4};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					scaleSourceToWorld,
					scaleTargetToWorld).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0, 1, 2, 3}, targetBlocks);
		}

		{
			long[] sourceBlocks = {5};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					scaleSourceToWorld,
					scaleTargetToWorld).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{1, 3}, targetBlocks);
		}
	}

	@Test
	public void testGetRelevantBlocksInTargetGridRelativeScales() {
		// source
		//
		//  ______ ______ __
		// |   0  |   1  | 2|
		// |------|------|--|
		// |   3  |   4  | 5|
		//  ‾‾‾‾‾‾ ‾‾‾‾‾‾ ‾‾

		// target
		//  ____ ____
		// |  0 |  1 |
		// |----|----|
		// |  2 |  3 |
		//  ‾‾‾‾ ‾‾‾‾

		final double[] scaleSourceToWorld = {2.0, 3.0};
		final double[] scaleTargetToWorld = {3.0, 6.0};
		final double[] relativeScale = IntStream
				.range(0, 2)
				.mapToDouble(i -> scaleTargetToWorld[i] / scaleSourceToWorld[i])
				.toArray();

		// img size in world coordinates: {14, 24}
		// source grid block size in world coordinates: {6, 12}
		// target grid block size in world coorinates: {9, 18}
		final CellGrid sourceGrid = new CellGrid(new long[]{7, 8}, new int[]{3, 4});
		final CellGrid targetGrid = new CellGrid(new long[]{5, 4}, new int[]{3, 3});
		{
			long[] sourceBlocks = LongStream.range(0, 6).toArray();
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					relativeScale).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(LongStream.range(0, 4).toArray(), targetBlocks);
		}

		// test each source block:
		{
			long[] sourceBlocks = {0};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					relativeScale).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0}, targetBlocks);
		}

		{
			long[] sourceBlocks = {1};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					relativeScale).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0, 1}, targetBlocks);
		}

		{
			long[] sourceBlocks = {2};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					relativeScale).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{1}, targetBlocks);
		}

		{
			long[] sourceBlocks = {3};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					relativeScale).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0, 2}, targetBlocks);
		}

		{
			long[] sourceBlocks = {4};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					relativeScale).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{0, 1, 2, 3}, targetBlocks);
		}

		{
			long[] sourceBlocks = {5};
			long[] targetBlocks = Grids.getRelevantBlocksInTargetGrid(
					sourceBlocks,
					sourceGrid,
					targetGrid,
					relativeScale).toArray();
			Arrays.sort(targetBlocks);
			assertArrayEquals(new long[]{1, 3}, targetBlocks);
		}
	}

	@Test
	public void testLinearIndexToCellPositionMinMax() {
		// intersects with all
		//
		//  ______ ______ __
		// |   0  |   1  | 2|
		// |------|------|--|
		// |   3  |   4  | 5|
		//  ‾‾‾‾‾‾ ‾‾‾‾‾‾ ‾‾
		final CellGrid grid = new CellGrid(new long[]{7, 8}, new int[]{3, 4});
		final long[] min = new long[2];
		final long[] max = new long[2];
		final long[] pos = new long[2];

		Grids.linearIndexToCellPositionMinMax(grid, 0, pos, min, max);
		assertArrayEquals(new long[]{0, 0}, pos);
		assertArrayEquals(new long[]{0, 0}, min);
		assertArrayEquals(new long[]{2, 3}, max);

		Grids.linearIndexToCellPositionMinMax(grid, 1, pos, min, max);
		assertArrayEquals(new long[]{1, 0}, pos);
		assertArrayEquals(new long[]{3, 0}, min);
		assertArrayEquals(new long[]{5, 3}, max);

		Grids.linearIndexToCellPositionMinMax(grid, 2, pos, min, max);
		assertArrayEquals(new long[]{2, 0}, pos);
		assertArrayEquals(new long[]{6, 0}, min);
		assertArrayEquals(new long[]{6, 3}, max);

		Grids.linearIndexToCellPositionMinMax(grid, 3, pos, min, max);
		assertArrayEquals(new long[]{0, 1}, pos);
		assertArrayEquals(new long[]{0, 4}, min);
		assertArrayEquals(new long[]{2, 7}, max);

		Grids.linearIndexToCellPositionMinMax(grid, 4, pos, min, max);
		assertArrayEquals(new long[]{1, 1}, pos);
		assertArrayEquals(new long[]{3, 4}, min);
		assertArrayEquals(new long[]{5, 7}, max);

		Grids.linearIndexToCellPositionMinMax(grid, 5, pos, min, max);
		assertArrayEquals(new long[]{2, 1}, pos);
		assertArrayEquals(new long[]{6, 4}, min);
		assertArrayEquals(new long[]{6, 7}, max);
	}

}
