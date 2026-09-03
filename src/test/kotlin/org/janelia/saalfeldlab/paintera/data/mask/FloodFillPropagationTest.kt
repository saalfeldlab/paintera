package org.janelia.saalfeldlab.paintera.data.mask

import bdv.cache.SharedQueue
import gnu.trove.map.TLongObjectMap
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.Invalidate
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.img.cell.CellImgFactory
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test paint propagation via FloodFill3D from
 * `applyMask` -> `propagateMask` -> `downsampleBlocks`.
 */
class FloodFillPropagationTest {

    private val blockSize = intArrayOf(16, 16, 16)
    private val numLevels = 5
    private val level0Size = 128L
    private val label = 7L

    /* dimensions per level: 128, 64, 32, 16, 8 */
    private fun dimensionsAt(level: Int) = LongArray(3) { level0Size shr level }

    private fun noopInvalidate() = object : Invalidate<Long> {
        override fun invalidate(key: Long) {}
        override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<Long>) {}
        override fun invalidateAll(parallelismThreshold: Long) {}
    }

    private fun noopPersistCanvas() = object : PersistCanvas {
        private val progress = SimpleDoubleProperty(0.0)
        override fun getProgressProperty(): ReadOnlyDoubleProperty = progress
        override fun persistCanvas(canvas: CachedCellImg<UnsignedLongType, *>, blockIds: LongArray): MutableList<TLongObjectMap<PersistCanvas.BlockDiff>> =
            mutableListOf()
    }

    private fun maskedSource(): MaskedSource<UnsignedLongType, VolatileUnsignedLongType> {
        val dataFactory = CellImgFactory(UnsignedLongType(), *blockSize)
        val volatileFactory = CellImgFactory(VolatileUnsignedLongType(), *blockSize)

        val data = Array<RandomAccessibleInterval<UnsignedLongType>>(numLevels) { level ->
            dataFactory.create(*dimensionsAt(level))
        }
        val vData = Array<RandomAccessibleInterval<VolatileUnsignedLongType>>(numLevels) { level ->
            volatileFactory.create(*dimensionsAt(level))
        }
        val transforms = Array(numLevels) { level ->
            val scale = (1 shl level).toDouble()
            AffineTransform3D().also {
                it.set(
                    scale, 0.0, 0.0, 0.0,
                    0.0, scale, 0.0, 0.0,
                    0.0, 0.0, scale, 0.0
                )
            }
        }

        val source = RandomAccessibleIntervalDataSource(
            data,
            vData,
            { transforms },
            noopInvalidate(),
            { NearestNeighborInterpolatorFactory() },
            { NearestNeighborInterpolatorFactory() },
            "floodfill-3d-propagation-test"
        )

        return Masks.fromIntegerType(
            source,
            SharedQueue(1),
            noopPersistCanvas(),
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        )
    }

    /**
     * The blocks a flood fill through a diagonal structure would touch: one block per step along the diagonal, so
     * the painted volume is a small fraction of a bounding box that spans everything.
     */
    private fun diagonalBlocks(): List<LongArray> {
        val blocksPerAxis = (level0Size / blockSize[0]).toInt()
        return (0 until blocksPerAxis).map { i -> longArrayOf(i.toLong(), i.toLong(), i.toLong()) }
    }

    private fun paintDiagonal(mask: SourceMask): Interval {
        val access = mask.rai.randomAccess()
        val position = LongArray(3)
        for (block in diagonalBlocks()) {
            for (z in 0 until blockSize[2]) {
                for (y in 0 until blockSize[1]) {
                    for (x in 0 until blockSize[0]) {
                        position[0] = block[0] * blockSize[0] + x
                        position[1] = block[1] * blockSize[1] + y
                        position[2] = block[2] * blockSize[2] + z
                        access.setPositionAndGet(*position).set(label)
                    }
                }
            }
        }
        return FinalInterval(longArrayOf(0, 0, 0), LongArray(3) { level0Size - 1 })
    }

    private fun awaitIdle(source: MaskedSource<*, *>, timeoutSeconds: Long = 900) {
        val latch = CountDownLatch(1)
        val busy = source.isBusyProperty()
        val subscription = busy.subscribe { isBusy ->
            if (!isBusy)
                latch.countDown()
        }
        try {
            assertTrue(latch.await(timeoutSeconds, TimeUnit.SECONDS), "propagation did not finish within ${timeoutSeconds}s")
        } finally {
            subscription.unsubscribe()
        }
    }

    /** the label must land in every lower level exactly where the diagonal was painted, and nowhere else */
    private fun assertDownsampledCorrectly(source: MaskedSource<*, *>) {
        for (level in 1 until numLevels) {
            val canvas = source.getReadOnlyDataCanvas(0, level)
            val scale = 1 shl level
            val access = canvas.randomAccess()
            val position = LongArray(3)
            var painted = 0L
            var stray = 0L

            for (block in diagonalBlocks()) {
                /* the painted block's footprint at this level */
                for (z in 0 until blockSize[2] / scale) {
                    for (y in 0 until blockSize[1] / scale) {
                        for (x in 0 until blockSize[0] / scale) {
                            position[0] = (block[0] * blockSize[0]) / scale + x
                            position[1] = (block[1] * blockSize[1]) / scale + y
                            position[2] = (block[2] * blockSize[2]) / scale + z
                            assertEquals(label, access.setPositionAndGet(*position).get(), "s$level at ${position.contentToString()}")
                            painted++
                        }
                    }
                }
            }

            /* nothing outside the diagonal may have been written */
            val paintedAtLevel = HashSet<Long>()
            for (block in diagonalBlocks()) {
                for (z in 0 until blockSize[2] / scale)
                    for (y in 0 until blockSize[1] / scale)
                        for (x in 0 until blockSize[0] / scale) {
                            val px = (block[0] * blockSize[0]) / scale + x
                            val py = (block[1] * blockSize[1]) / scale + y
                            val pz = (block[2] * blockSize[2]) / scale + z
                            paintedAtLevel.add(px + (level0Size shr level) * (py + (level0Size shr level) * pz))
                        }
            }
            val dim = level0Size shr level
            for (z in 0 until dim) {
                for (y in 0 until dim) {
                    for (x in 0 until dim) {
                        val flat = x + dim * (y + dim * z)
                        if (paintedAtLevel.contains(flat))
                            continue
                        val value = access.setPositionAndGet(x, y, z).get()
                        if (value != Label.INVALID)
                            stray++
                    }
                }
            }
            assertEquals(0L, stray, "s$level must not write outside the painted diagonal")
            assertTrue(painted > 0, "s$level should have painted voxels")
        }
    }

    @Test
    fun `flood fill shaped propagation downsamples correctly`() {
        val source = maskedSource()
        val mask = source.generateMask(MaskInfo(0, 0), MaskedSource.VALID_LABEL_CHECK)
        val bbox = paintDiagonal(mask)

        source.applyMask(mask, bbox, MaskedSource.VALID_LABEL_CHECK)
        awaitIdle(source)

        assertDownsampledCorrectly(source)
    }

    @Test
    @Ignore
    fun `benchmark flood fill shaped propagation`() {
        val paintedVoxels = diagonalBlocks().size.toLong() * blockSize[0] * blockSize[1] * blockSize[2]
        val bboxVoxels = level0Size * level0Size * level0Size
        val repetitions = 5
        val timings = ArrayList<Long>(repetitions)

        repeat(repetitions) {
            val source = maskedSource()
            val mask = source.generateMask(MaskInfo(0, 0), MaskedSource.VALID_LABEL_CHECK)
            val bbox = paintDiagonal(mask)

            val start = System.nanoTime()
            source.applyMask(mask, bbox, MaskedSource.VALID_LABEL_CHECK)
            awaitIdle(source)
            timings.add((System.nanoTime() - start) / 1_000_000)

            assertDownsampledCorrectly(source)
        }

        println("=== flood fill propagation benchmark ===")
        println("  levels             : $numLevels, block ${blockSize.contentToString()}, s0 ${dimensionsAt(0).contentToString()}")
        println("  painted voxels     : $paintedVoxels")
        println("  bounding box       : $bboxVoxels  (${bboxVoxels / paintedVoxels}x the painted volume)")
        println("  applyMask+propagate: min ${timings.min()} ms, median ${timings.sorted()[repetitions / 2]} ms, all $timings")
    }
}
