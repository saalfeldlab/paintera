package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import net.imglib2.Localizable
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgFactory
import net.imglib2.position.FunctionRandomAccessible
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.util.translate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PaddedCellTest {

    /* unique position encoding reveals wrong placements */
    private fun expectedPositionEncoding(loc: Localizable) =
        1 * loc.getLongPosition(0) +
                1_000L * loc.getLongPosition(1) +
                1_000_000L * loc.getLongPosition(2)

    private val testImg: RandomAccessible<UnsignedLongType> = FunctionRandomAccessible(
        3,
        { pos, type -> type.set(expectedPositionEncoding(pos)) },
        ::UnsignedLongType
    )

    /* a cell positioned away from the origin, to reveal translation bugs */
    private fun cellAt(min: LongArray, max: LongArray): RandomAccessibleInterval<UnsignedLongType> {
        val dims = LongArray(min.size) { max[it] - min[it] + 1 }
        return ArrayImgFactory(UnsignedLongType()).create(*dims).translate(*min)
    }

    /* a mutable image over [min, max] prefilled with the testImg, to verify padding writes don't escape back to it */
    private fun testImage(min: LongArray, max: LongArray): RandomAccessibleInterval<UnsignedLongType> {
        val image = cellAt(min, max)
        val cursor = Views.flatIterable(image).localizingCursor()
        while (cursor.hasNext()) {
            val type = cursor.next()
            type.set(expectedPositionEncoding(cursor))
        }
        return image
    }

    /* every position in the padded interval must read back the testImg value at that position */
    private fun assertMirrorsSource(padded: PaddedCell<UnsignedLongType>) {
        /* setPosition path: probe each coordinate directly */
        val access = padded.randomAccess()
        val pos = LongArray(3)
        (padded.min(2)..padded.max(2)).forEach { z ->
            (padded.min(1)..padded.max(1)).forEach { y ->
                (padded.min(0)..padded.max(0)).forEach { x ->
                    pos[0] = x
                    pos[1] = y
                    pos[2] = z
                    access.setPosition(pos)
                    assertEquals(expectedPositionEncoding(access), access.get().get(), "setPosition at ${pos.toList()}")
                }
            }
        }

        /* fwd/move path: flatIterable traverses via the random-access fast path across slab boundaries */
        val actual = Views.flatIterable(padded).cursor()
        val expected = Views.flatIterable(Views.interval(testImg, padded)).cursor()
        while (actual.hasNext()) {
            val expectedValue = expected.next().get()
            val actualValue = actual.next().get()
            assertEquals(expectedValue, actualValue, "flat traversal mismatch")
        }
    }

    @Test
    fun `padded cell mirrors source across cell and padding`() {
        val cell = cellAt(longArrayOf(10, 10, 10), longArrayOf(20, 18, 16))
        val padded = PaddedCell.createAndFill(cell, longArrayOf(3, 4, 2), testImg, UnsignedLongType())
        assertMirrorsSource(padded)
    }

    @Test
    fun `padding of zero in a dimension still mirrors source`() {
        val cell = cellAt(longArrayOf(5, 7, 9), longArrayOf(14, 13, 15))
        val padded = PaddedCell.createAndFill(cell, longArrayOf(0, 3, 2), testImg, UnsignedLongType())
        assertMirrorsSource(padded)
    }

    @Test
    fun `padding corners read from the correct slab`() {
        val cellMin = longArrayOf(10, 10, 10)
        val cellMax = longArrayOf(20, 18, 16)
        val cell = cellAt(cellMin, cellMax)
        val padded = PaddedCell.createAndFill(cell, longArrayOf(3, 4, 2), testImg, UnsignedLongType())
        val access = padded.randomAccess()
        /* padded interval corners and faces; each must equal the testImg at that exact position */
        val testCases = listOf(
            longArrayOf(15, 14, 13),                                    // cell interior
            longArrayOf(cellMin[0] - 3, 14, 13),                        // before-x face
            longArrayOf(15, cellMax[1] + 4, 13),                        // after-y face
            longArrayOf(cellMin[0] - 3, cellMin[1] - 4, cellMin[2] - 2),// all-before corner
            longArrayOf(cellMax[0] + 3, cellMax[1] + 4, cellMax[2] + 2) // all-after corner
        )
        for (testCase in testCases) {
            access.setPosition(testCase)
            assertEquals(expectedPositionEncoding(access), access.get().get(), "corner ${testCase.toList()}")
        }
    }

    @Test
    fun `writes through the cell region update the backing cell`() {
        val cellMin = longArrayOf(10, 10, 10)
        val cellMax = longArrayOf(20, 18, 16)
        val cell = cellAt(cellMin, cellMax)
        val padded = PaddedCell.create(cell, longArrayOf(3, 4, 2), UnsignedLongType())

        val cellPosition = longArrayOf(15, 14, 13)
        val paddedAccess = padded.randomAccess().apply { setPosition(cellPosition) }
        paddedAccess.get().set(777L)

        /* the underlying cell sees the write  */
        val cellAccess = cell.randomAccess().apply { setPosition(cellPosition) }
        assertEquals(777L, cellAccess.get().get(), "cell write must reach the backing cell")

        /* and it reads back through the padded cell */
        paddedAccess.setPosition(cellPosition)
        assertEquals(777L, paddedAccess.get().get(), "cell write must be visible through the padded cell")
    }

    @Test
    fun `writes into the padding are scratch space and do not update the backing source`() {
        val cellMin = longArrayOf(10, 10, 10)
        val cellMax = longArrayOf(20, 18, 16)
        val padding = longArrayOf(3, 4, 2)
        val paddedMin = LongArray(3) { cellMin[it] - padding[it] }
        val paddedMax = LongArray(3) { cellMax[it] + padding[it] }

        val cell = cellAt(cellMin, cellMax)
        val source = testImage(paddedMin, paddedMax)
        val padded = PaddedCell.createAndFill(cell, padding, source, UnsignedLongType())

        /* a position in the padding (before the cell in x), still inside the padded interval */
        val paddingPosition = longArrayOf(cellMin[0] - 1, 14, 13)
        val access = padded.randomAccess().apply { setPosition(paddingPosition) }
        assertEquals(expectedPositionEncoding(access), access.get().get(), "padding starts as a copy of the source")

        /* the scratch slab is writable and reads back the new value */
        access.get().set(999L)
        access.setPosition(paddingPosition)
        assertEquals(999L, access.get().get(), "padding scratch must be writable")

        /* but the write must not escape to the source it was copied from (would corrupt a neighboring cell) */
        val sourceAccess = source.randomAccess().apply { setPosition(paddingPosition) }
        assertEquals(expectedPositionEncoding(sourceAccess), sourceAccess.get().get(), "padding write must not escape to the source")
    }
}
