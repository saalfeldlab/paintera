package org.janelia.saalfeldlab.util

import kotlinx.coroutines.runBlocking
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.cell.CellImgFactory
import net.imglib2.type.numeric.integer.LongType
import net.imglib2.util.Intervals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.LongAdder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdaptiveScaleBlocksTest {

	/* 10 x 6 at s0 in 4 x 4 blocks, 5 x 3 at s1 in 2 x 2 blocks, 3 x 2 at s2 in 2 x 2 blocks */
	private val levels = listOf(
		level(0, longArrayOf(10, 6), intArrayOf(4, 4), longArrayOf(1, 1)),
		level(1, longArrayOf(5, 3), intArrayOf(2, 2), longArrayOf(2, 2)),
		level(2, longArrayOf(3, 2), intArrayOf(2, 2), longArrayOf(4, 4)),
	)

	private val s0Extent: Interval = FinalInterval(levels[0].image)

	private val adaptiveBlocks = AdaptiveScaleBlocks(levels, s0Extent)

	private fun blocksFor(s0Interval: Interval) = adaptiveBlocks.blocksFor(s0Interval)

	private fun level(index: Int, dimensions: LongArray, blockSize: IntArray, factors: LongArray) =
		MultiscaleLevel.of(index, CellImgFactory(LongType(), *blockSize).create(*dimensions), factors)

	private fun interval(minX: Long, minY: Long, maxX: Long, maxY: Long): Interval = FinalInterval(longArrayOf(minX, minY), longArrayOf(maxX, maxY))

	private fun assertTiles(region: Interval) {
		val clipped = Intervals.intersect(region, s0Extent)
		val covered = HashMap<List<Long>, Int>()
		blocksFor(region).forEach { block ->
			assertTrue(Intervals.contains(clipped, block.firstLevelInterval))
			val cursor = net.imglib2.view.Views.flatIterable(net.imglib2.view.Views.interval(ArrayImgs.longs(*s0Extent.dimensionsAsLongArray()), block.firstLevelInterval)).localizingCursor()
			while (cursor.hasNext()) {
				cursor.fwd()
				covered.merge(cursor.positionAsLongArray().toList(), 1, Int::plus)
			}
		}
		assertEquals(Intervals.numElements(clipped), covered.size.toLong())
		assertTrue(covered.values.all { it == 1 })
	}

	@Test
	fun `a level that does not cover s0 is rejected`() {
		assertFailsWith<IllegalArgumentException> {
			val shortLevels = listOf(
				level(0, longArrayOf(10, 6), intArrayOf(4, 4), longArrayOf(1, 1)),
				level(1, longArrayOf(4, 3), intArrayOf(2, 2), longArrayOf(2, 2)),
			)
			AdaptiveScaleBlocks(shortLevels, s0Extent)
		}
	}

	@Test
	fun `the whole image is one block of the coarsest level`() {
		val blocks = blocksFor(s0Extent).toList()
		assertEquals(2, blocks.size)
		assertTrue(blocks.all { it.level.index == 2 && !it.isPartial })
		assertTiles(s0Extent)
	}

	@Test
	fun `the pieces of a region tile it exactly once`() {
		assertTiles(interval(0, 0, 9, 5))
		assertTiles(interval(3, 1, 8, 4))
		assertTiles(interval(5, 5, 5, 5))
		assertTiles(interval(-3, -3, 20, 20))
	}

	@Test
	fun `a region inside one s0 block is read from s0`() {
		val blocks = blocksFor(interval(1, 1, 2, 2)).toList()
		assertEquals(1, blocks.size)
		assertEquals(0, blocks[0].level.index)
		assertTrue(blocks[0].isPartial)
		assertEquals(4L, blocks[0].firstLevelSize)
	}

	@Test
	fun `collected blocks keep their own grid position`() {
		val blocks = blocksFor(interval(3, 1, 8, 4)).toList()
		assertTrue(blocks.size > 1)
		assertEquals(blocks.size, blocks.map { it.level.index to it.gridPosition.toList() }.distinct().size)
	}

	@Test
	fun `an empty region has no pieces`() {
		assertEquals(0, blocksFor(interval(20, 20, 30, 30)).count())
	}

	@Test
	fun `for each block sums the s0 sizes to the region size`() = runBlocking {
		val region = interval(3, 1, 8, 4)
		val total = LongAdder()
		adaptiveBlocks.forEachBlock(region) { block -> total.add(block.firstLevelSize) }
		assertEquals(Intervals.numElements(region), total.sum())
	}
}
