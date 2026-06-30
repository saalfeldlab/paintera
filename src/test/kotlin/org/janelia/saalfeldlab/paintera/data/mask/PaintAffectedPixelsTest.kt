package org.janelia.saalfeldlab.paintera.data.mask

import gnu.trove.set.hash.TLongHashSet
import net.imglib2.FinalInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.cell.CellGrid
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.function.Predicate
import kotlin.test.assertEquals

class PaintAffectedPixelsTest {

    private val dimensions = longArrayOf(128, 128, 1)
    private val cellSize = intArrayOf(8, 8, 1)
    private val grid = CellGrid(dimensions, cellSize)
    private val numBlocks = grid.gridDimensions.fold(1L) { acc, d -> acc * d }.toInt()
    private val paintedInterval = FinalInterval(LongArray(dimensions.size) { 0L }, LongArray(dimensions.size) { dimensions[it] - 1 })

    /* every relevant block in the grid */
    private fun allBlocks() = TLongHashSet().apply { for (block in 0 until numBlocks) add(block.toLong()) }

    /* a mask whose rai paints a single label everywhere; only getRai() is read by applyMaskToCanvas */
    private fun paintedMask(label: Long): SourceMask {
        val maskRai = ArrayImgs.unsignedLongs(*dimensions).apply { forEach { it.set(label) } }
        return SourceMask(MaskInfo(0, 0), maskRai, null, null, null, null)
    }

    /**
     * One label-to-block mapping across all blocks maximizes contention on a single result set - the case that corrupted
     * trove's internal arrays under the old shared-mutation update. Repeated to make the race reliably surface.
     * The reduction must be deterministic: hotLabel maps to every block.
     */
    @Test
    fun `concurrent paint over one hot label maps it to every block`() {
        val label = 7L
        val mask = paintedMask(label)
        val acceptAsPainted = Predicate<Long> { it == label }
        val pool = Executors.newFixedThreadPool(16)
        try {
            repeat(100) { iteration ->
                /* fresh canvas each run; applyMaskToCanvas writes into it */
                val canvas = ArrayImgs.unsignedLongs(*dimensions)
                val result = MaskedSource.paintAffectedPixels(
                    allBlocks(),
                    mask,
                    canvas,
                    grid,
                    paintedInterval,
                    acceptAsPainted,
                    pool
                )

                assertEquals(setOf(label), result.keys, "only the single label we wrote should be present (iteration: $iteration)")
                val blocks = result[label]!!
                assertEquals(numBlocks, blocks.size(), "single label must map to every block (iteration: $iteration)")
                for (block in 0 until numBlocks)
                    assert(blocks.contains(block.toLong())) { "missing block $block (iteration: $iteration)" }
            }
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `label-to-block mapping update`() {
        val labelA = 3L
        val labelB = 9L
        val splitX = 60L  // inside block column 7 (x in 56..63)

        val maskRai = ArrayImgs.unsignedLongs(*dimensions)
        val cursor = maskRai.localizingCursor()
        while (cursor.hasNext()) {
            val type = cursor.next()
            val label = labelA.takeIf {cursor.getLongPosition(0) < splitX  } ?: labelB
            type.set(label)
        }
        val mask = SourceMask(MaskInfo(0, 0), maskRai, null, null, null, null)
        val acceptAsPainted = Predicate<Long> { it == labelA || it == labelB }

        val pool = Executors.newFixedThreadPool(16)
        val result = try {
            val canvas = ArrayImgs.unsignedLongs(*dimensions)
            MaskedSource.paintAffectedPixels(allBlocks(), mask, canvas, grid, paintedInterval, acceptAsPainted, pool)
        } finally {
            pool.shutdown()
        }

        /* build the expected mapping independently: block column 0..6 -> A, 8..15 -> B, column 7 -> both */
        val gridX = grid.gridDimensions[0].toInt()
        val expectedA = TLongHashSet()
        val expectedB = TLongHashSet()
        for (block in 0 until numBlocks) {
            val blockColumnX = (block % gridX)
            val xCellMin = blockColumnX * cellSize[0]
            val xCellMax = xCellMin + cellSize[0] - 1
            if (xCellMin < splitX) expectedA.add(block.toLong())
            if (xCellMax >= splitX) expectedB.add(block.toLong())
        }

        assertEquals(setOf(labelA, labelB), result.keys)
        assertEquals(expectedA, result[labelA], "labelA blocks")
        assertEquals(expectedB, result[labelB], "labelB blocks")
    }
}
