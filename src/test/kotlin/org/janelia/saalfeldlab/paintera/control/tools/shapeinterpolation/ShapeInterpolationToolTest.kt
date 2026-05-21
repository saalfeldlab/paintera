package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool.Companion.RequestDirection.NEXT
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool.Companion.RequestDirection.PREV
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool.Companion.RequestDistance.FAR
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool.Companion.RequestDistance.NEAR
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool.Companion.bestEagerRequest
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool.Companion.eagerRequestDepths
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class ShapeInterpolationToolTest {

    @Test
    fun `eager depth with no slices`() {
        val noExistingSlices = emptyList<Double>()
        val actual = eagerRequestDepths(noExistingSlices)
        assertNull(actual)
    }

    @Test
    fun `eager depths with 1 slice`() {
        val testData = mapOf(
            listOf(-20.0) to doubleArrayOf(-40.0, 0.0),
            listOf(0.0) to doubleArrayOf(-20.0, 20.0),
            listOf(20.0) to doubleArrayOf(0.0, 40.0),
        )
        for ((existingSlices, expected) in testData) {
            val actual = eagerRequestDepths(existingSlices)!!.toDoubleArray()
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `eager depths with 2 slices`() {
        val testData = mapOf(
            listOf(-20.0, 0.0) to doubleArrayOf(-40.0, -10.0, 20.0),
            listOf(-10.0, 10.0) to doubleArrayOf(-30.0, 0.0, 30.0),
            listOf(20.0, 40.0) to doubleArrayOf(0.0, 30.0, 60.0),
        )
        for ((existingSlices, expected) in testData) {
            val actual = eagerRequestDepths(existingSlices)!!.toDoubleArray()
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `eager depths with 3 slices`() {
        val testData = mapOf(
            /* uniform: bisects at 5, 20; endpoints extend by first/last gap of 10, 20 */
            listOf(0.0, 10.0, 30.0) to doubleArrayOf(-10.0, 5.0, 20.0, 50.0),
            /* non-uniform: endpoints use the adjacent gap (5 and 95), not fallbackDistance */
            listOf(0.0, 5.0, 100.0) to doubleArrayOf(-5.0, 2.5, 52.5, 195.0),
        )
        for ((existingSlices, expected) in testData) {
            val actual = eagerRequestDepths(existingSlices)!!.toDoubleArray()
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `eager depths sorts unsorted input`() {
        val sorted = eagerRequestDepths(listOf(0.0, 10.0, 30.0))!!.toDoubleArray()
        val unsorted = eagerRequestDepths(listOf(30.0, 0.0, 10.0))!!.toDoubleArray()
        assertArrayEquals(sorted, unsorted)
    }

    @Test
    fun `eager depths respects custom fallbackDistance for single slice`() {
        /* fallbackDistance only applies in the single-slice case; multi-slice
         * cases derive endpoint extensions from adjacent gaps */
        val actual = eagerRequestDepths(listOf(10.0), fallbackDistance = 5.0)!!.toDoubleArray()
        assertArrayEquals(doubleArrayOf(5.0, 15.0), actual)
    }

    @Test
    fun `best eager request returns currentDepth when no slices exist`() {
        /* with no slices there are no eager depths, so currentDepth passes through
         * regardless of distance or direction */
        val empty = emptyList<Double>()
        assertEquals(7.5, bestEagerRequest(empty, 7.5, FAR, PREV))
        assertEquals(7.5, bestEagerRequest(empty, 7.5, FAR, NEXT))
        assertEquals(7.5, bestEagerRequest(empty, 7.5, NEAR, PREV))
        assertEquals(7.5, bestEagerRequest(empty, 7.5, NEAR, NEXT))
    }

    @Test
    fun `best FAR eager request ignores currentDepth`() {
        /* sliceDepths [0, 10, 30] -> eagerDepths [-10, 5, 20, 50] */
        val depths = listOf(0.0, 10.0, 30.0)
        assertEquals(-10.0, bestEagerRequest(depths, 5.0, FAR, PREV))
        assertEquals(50.0, bestEagerRequest(depths, 5.0, FAR, NEXT))
        /* currentDepth far outside the slice range must not change FAR's choice */
        assertEquals(-10.0, bestEagerRequest(depths, -1000.0, FAR, PREV))
        assertEquals(50.0, bestEagerRequest(depths, 1000.0, FAR, NEXT))
    }

    @Test
    fun `best eager request at slice`() {
        /* sliceDepths [0, 10, 30] -> eagerDepths [-10, 5, 20, 50] */
        val depths = listOf(0.0, 10.0, 30.0)
        /* at the interior slice the adjacent bisects are on either side */
        assertEquals(5.0, bestEagerRequest(depths, 10.0, NEAR, PREV))
        assertEquals(20.0, bestEagerRequest(depths, 10.0, NEAR, NEXT))
        /* at edge slices the direction must cross into the boundary extension */
        assertEquals(-10.0, bestEagerRequest(depths, 0.0, NEAR, PREV))
        assertEquals(50.0, bestEagerRequest(depths, 30.0, NEAR, NEXT))
        /* single-slice on-slice still respects direction (PREV/NEXT are not collapsed) */
        val single = listOf(0.0)
        assertEquals(-20.0, bestEagerRequest(single, 0.0, NEAR, PREV))
        assertEquals(20.0, bestEagerRequest(single, 0.0, NEAR, NEXT))
    }

    @Test
    fun `best eager request between slices`() {
        /* sliceDepths [0, 10, 30] -> eagerDepths [-10, 5, 20, 50]
         * NEAR between slices is direction-agnostic; picks the eager depth in the same gap */
        val depths = listOf(0.0, 10.0, 30.0)
        assertEquals(5.0, bestEagerRequest(depths, 3.0, NEAR, PREV))
        assertEquals(5.0, bestEagerRequest(depths, 7.0, NEAR, NEXT))
        assertEquals(20.0, bestEagerRequest(depths, 15.0, NEAR, PREV))
        /* outside the slice range falls into the bookend gap and returns the boundary extension */
        assertEquals(50.0, bestEagerRequest(depths, 1000.0, NEAR, NEXT))
        assertEquals(-10.0, bestEagerRequest(depths, -1000.0, NEAR, PREV))
    }

    @Test
    fun `best eager request 1 slice`() {
        /* 1-slice not-on-slice: returns the eager depth on currentDepth's side regardless of direction */
        val single = listOf(0.0)
        assertEquals(20.0, bestEagerRequest(single, 5.0, NEAR, PREV))
        assertEquals(-20.0, bestEagerRequest(single, -5.0, NEAR, NEXT))
    }

    @Test
    fun `best eager slice currentDepth beyond existing slices`() {
        /* sliceDepths [0, 10, 30] -> eagerDepths [-10, 5, 20, 50]
         * When currentDepth lies outside the slice range, NEAR falls into the bookend gap
         * and is direction-agnostic: the boundary extension on currentDepth's side wins,
         * even when the requested direction would point back across the slice range */
        val depths = listOf(0.0, 10.0, 30.0)
        /* past the last slice -> 50 for both directions */
        assertEquals(50.0, bestEagerRequest(depths, 1000.0, NEAR, NEXT))
        assertEquals(50.0, bestEagerRequest(depths, 1000.0, NEAR, PREV))
        /* before the first slice -> -10 for both directions */
        assertEquals(-10.0, bestEagerRequest(depths, -1000.0, NEAR, NEXT))
        assertEquals(-10.0, bestEagerRequest(depths, -1000.0, NEAR, PREV))
    }
}