package org.janelia.saalfeldlab.paintera.control.paint

import net.imglib2.FinalInterval
import net.imglib2.FinalRealInterval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealRandomAccessible
import net.imglib2.img.array.ArrayImgs
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.Scale3D
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.affineReal
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.interpolateNearestNeighbor
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.raster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Predicate
import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * Uniform signature for every kernel variant of the [ViewerMask.applyMaskToCanvas] inner loop, so they can be
 * compared and timed interchangeably. The canvas is fixed to [UnsignedLongType] (the real canvas type) rather than
 * the production generic, since that is all the harness needs.
 */
private fun interface PaintedApply<C : IntegerType<C>> {
    fun applyToSource(
        canvas: RandomAccessibleInterval<UnsignedLongType>,
        viewerImg: RandomAccessibleInterval<UnsignedLongType>,
        viewerImgInSource: RealRandomAccessible<UnsignedLongType>,
        maskToSourceWithDepth: AffineTransform3D,
        paintDepthFactor: Double,
        depthScale: Double,
        acceptAsPainted: Predicate<Long>,
    ): Set<Long>
}

class ViewerMaskApplyRegressionTest {

    private val accept = Predicate<Long> { it != Label.INVALID }

    private val alternativePaintedApply: List<Pair<String, PaintedApply<*>>> = listOf(
        "loop-builder" to loopBuilderApply,
    )

    @Test
    fun alternativesMatchReferenceAcrossTransforms() {
        var anyPainted = false
        for (scenario in scenarios)
            if (regressionTest(scenario, scenario.toString())) anyPainted = true
        assertTrue(anyPainted) { "scenarios painted nothing - the parity test is not exercising the paint path" }
    }

    /**
     * Fuzz the alternatives against the reference under random full-range rotations and independent per-axis
     * (non-isotropic) scales. Seeded for reproducibility; a failure prints the exact rotation/scale/depth to replay.
     */
    @Test
    fun alternativesMatchReferenceUnderRandomTransforms() {
        val random = java.util.Random(20260723L)
        var anyPainted = false
        repeat(24) { trial ->
            val rotX = random.nextDouble() * 2 * Math.PI - Math.PI
            val rotY = random.nextDouble() * 2 * Math.PI - Math.PI
            val rotZ = random.nextDouble() * 2 * Math.PI - Math.PI
            /* independent per-axis scales in [0.5, 4.0] - strongly anisotropic */
            val scale = doubleArrayOf(0.5 + random.nextDouble() * 3.5, 0.5 + random.nextDouble() * 3.5, 0.5 + random.nextDouble() * 3.5)
            val paintDepth = 0.5 + random.nextDouble() * 4.5
            val scenario = buildScenario(rotX, rotY, rotZ, scale, paintDepth, viewerSize = 40, canvasHalf = 32)
            val context = "trial=$trial rot=(%.5f,%.5f,%.5f) scale=%s depth=%.5f".format(rotX, rotY, rotZ, scale.toList(), paintDepth)
            if (regressionTest(scenario, context)) anyPainted = true
        }
        assertTrue(anyPainted) { "random scenarios painted nothing - the fuzz test is not exercising the paint path" }
    }

    /** Run every alternative against the reference on [scenario] and assert voxel-for-voxel + label-set equality. Returns whether anything was painted. */
    private fun regressionTest(scenario: Scenario, context: String): Boolean {
        val canvasRef = scenario.freshCanvas()
        val paintedRef = referenceApply.applyToSource(
            canvasRef,
            scenario.viewerImg,
            scenario.viewerImgInSource,
            scenario.maskToSourceWithDepth,
            scenario.paintDepthFactor,
            scenario.depthScale,
            accept
        )

        for ((name, paintedApply) in alternativePaintedApply) {
            val canvasVariant = scenario.freshCanvas()
            val paintedVariant = paintedApply.applyToSource(
                canvasVariant,
                scenario.viewerImg,
                scenario.viewerImgInSource,
                scenario.maskToSourceWithDepth,
                scenario.paintDepthFactor,
                scenario.depthScale,
                accept
            )

            assertEquals(paintedRef, paintedVariant) { "[$name] painted label set differs ($context)" }

            val refCursor = Views.flatIterable(canvasRef).cursor()
            val variantCursor = Views.flatIterable(canvasVariant).cursor()
            var mismatches = 0
            val firstMismatch = StringBuilder()
            val at = LongArray(3)
            while (refCursor.hasNext()) {
                val a = refCursor.next().get()
                val b = variantCursor.next().get()
                if (a != b) {
                    if (mismatches < 5) {
                        refCursor.localize(at)
                        firstMismatch.append("\n  at ${at.toList()}: ref=$a $name=$b")
                    }
                    mismatches++
                }
            }
            assertEquals(0, mismatches) { "[$name] voxel mismatch ($context, $mismatches voxels)$firstMismatch" }
        }
        return paintedRef.isNotEmpty()
    }

    companion object {

        private val scenarios: List<Scenario> = buildList {
            /* axis-aligned control */
            add(buildScenario(0.0, 0.0, 0.0, doubleArrayOf(1.0, 1.0, 1.0), 1.0, viewerSize = 48, canvasHalf = 40))
            /* pure single-axis rotations */
            add(buildScenario(0.6, 0.0, 0.0, doubleArrayOf(1.0, 1.0, 1.0), 1.0, viewerSize = 48, canvasHalf = 40))
            add(buildScenario(0.0, 0.6, 0.0, doubleArrayOf(1.0, 1.0, 1.0), 2.0, viewerSize = 48, canvasHalf = 44))
            add(buildScenario(0.0, 0.0, 0.6, doubleArrayOf(1.0, 1.0, 1.0), 1.0, viewerSize = 48, canvasHalf = 40))
            /* fully oblique, anisotropic scale, various paint depths  */
            add(buildScenario(0.5, 0.35, 0.2, doubleArrayOf(2.0, 2.0, 1.0), 1.0, viewerSize = 48, canvasHalf = 56))
            add(buildScenario(0.5, 0.35, 0.2, doubleArrayOf(2.0, 2.0, 1.0), 3.0, viewerSize = 48, canvasHalf = 56))
            add(buildScenario(0.9, 0.75, 0.6, doubleArrayOf(1.5, 3.0, 1.0), 4.0, viewerSize = 48, canvasHalf = 60))
            add(buildScenario(-0.4, 1.1, -0.7, doubleArrayOf(2.5, 1.0, 1.0), 2.0, viewerSize = 48, canvasHalf = 60))
            /* explicitly non-isotropic: all three axes distinct, in-plane anisotropy, and extreme ratios */
            add(buildScenario(0.5, 0.35, 0.2, doubleArrayOf(3.0, 1.0, 2.0), 2.0, viewerSize = 48, canvasHalf = 60))
            add(buildScenario(0.7, -0.5, 1.2, doubleArrayOf(0.5, 4.0, 2.5), 3.0, viewerSize = 48, canvasHalf = 64))
            add(buildScenario(1.3, 0.2, -0.9, doubleArrayOf(4.0, 0.6, 1.0), 1.5, viewerSize = 48, canvasHalf = 64))
        }

        private data class Scenario(
            val label: String,
            val viewerImg: RandomAccessibleInterval<UnsignedLongType>,
            val viewerImgInSource: RealRandomAccessible<UnsignedLongType>,
            val maskToSourceWithDepth: AffineTransform3D,
            val paintDepthFactor: Double,
            val depthScale: Double,
            val canvasMin: LongArray,
            val canvasDims: LongArray
        ) {
            /* a fresh source-space canvas at the scenario coords, filled with INVALID like a real uncommitted canvas */
            fun freshCanvas(): RandomAccessibleInterval<UnsignedLongType> {
                val array = ArrayImgs.unsignedLongs(*canvasDims)
                array.forEach { it.set(Label.INVALID) }
                return Views.translate(array, *canvasMin)
            }

            override fun toString() = label
        }

        /**
         * Build the inputs exactly as [ViewerMask] does: `depthScale` is the norm of the z-row of source->mask, the
         * `maskToSourceWithDepth` transform folds in `Scale3D(1, 1, paintDepth * depthScale)`, and the source projection
         * of the viewer image is `viewerImg.extendValue(INVALID).interpolateNearestNeighbor().affineReal(that)`.
         */
        private fun buildScenario(
            rotX: Double, rotY: Double, rotZ: Double,
            scale: DoubleArray,
            paintDepthFactor: Double,
            viewerSize: Int,
            canvasHalf: Long
        ): Scenario {
            /* a painted rectangle in the center of the viewer plane, INVALID border, so both the center-accept and the
             * boundary (footprint) paths fire */
            val viewerImg = ArrayImgs.unsignedLongs(viewerSize.toLong(), viewerSize.toLong(), 1L)
            val paintedLabel = 7L
            val lo = viewerSize / 6
            val hi = viewerSize - viewerSize / 6
            val cursor = viewerImg.localizingCursor()
            val position = IntArray(3)
            while (cursor.hasNext()) {
                cursor.fwd()
                cursor.localize(position)
                cursor.get().set(if (position[0] in lo until hi && position[1] in lo until hi) paintedLabel else Label.INVALID)
            }

            /* mask to source, no depth, anisotropic scale, rotate about all three axes, translation */
            val maskToSource = AffineTransform3D().also {
                it.concatenate(Scale3D(scale[0], scale[1], scale[2]))
                it.rotate(0, rotX)
                it.rotate(1, rotY)
                it.rotate(2, rotZ)
                it.translate(3.5, -2.25, 1.75)
            }

            val sourceToMask = maskToSource.inverse()
            val depthScale = sqrt((0..2).sumOf { col -> sourceToMask.get(2, col) * sourceToMask.get(2, col) })

            val maskToSourceWithDepth = maskToSource.copy().concatenate(Scale3D(1.0, 1.0, paintDepthFactor * depthScale))

            val viewerImgInSource = viewerImg.extendValue(Label.INVALID).interpolateNearestNeighbor().affineReal(maskToSourceWithDepth)

            /* a bounded source-space canvas centered on where the plane center lands, so the oblique slab crosses it */
            val planeCenterMask = doubleArrayOf(viewerSize / 2.0, viewerSize / 2.0, 0.0)
            val planeCenterSource = DoubleArray(3).also { maskToSourceWithDepth.apply(planeCenterMask, it) }
            val canvasMin = LongArray(3) { Math.round(planeCenterSource[it]) - canvasHalf }
            val canvasDims = LongArray(3) { 2 * canvasHalf }

            val label = "rot(%.2f,%.2f,%.2f) scale%s depth%.1f canvas%d".format(rotX, rotY, rotZ, scale.toList(), paintDepthFactor, 2 * canvasHalf)
            return Scenario(label, viewerImg, viewerImgInSource, maskToSourceWithDepth, paintDepthFactor, depthScale, canvasMin, canvasDims)
        }
    }
}

private val MIN_CORNER_OFFSET = doubleArrayOf(-.5, -.5, -.5)
private val MAX_CORNER_OFFSET = doubleArrayOf(+.5, +.5, +.5)

private val referenceApply: PaintedApply<UnsignedLongType> =
    PaintedApply { canvas, viewerImg, viewerImgInSource, maskToSourceWithDepth, paintDepthFactor, depthScale, acceptAsPainted ->
        ViewerMask.applyMaskToCanvas(canvas, viewerImg, viewerImgInSource, maskToSourceWithDepth, paintDepthFactor, depthScale, acceptAsPainted)
    }


/**
 * The straightforward [LoopBuilder] kernel the optimized [ViewerMask.applyMaskToCanvas] replaced, kept as an
 * independent implementation to cross-check the row-range loop against. Its slab gate tracks the production one.
 */
private val loopBuilderApply: PaintedApply<UnsignedLongType> =
    PaintedApply { canvas, viewerImg, viewerImgInSource, maskToSourceWithDepth, paintDepthFactor, depthScale, acceptAsPainted ->

        val extendedViewerImg = Views.extendBorder(viewerImg)
        val viewerImgInSourceOverCanvas = viewerImgInSource.raster().interval(canvas)

        val sourceToMaskTransform = maskToSourceWithDepth.inverse()
        val sourceToMaskTransformAsArray = sourceToMaskTransform.rowPackedCopy

        val sourceToMaskWithDepthTransform = maskToSourceWithDepth.inverse()

        val minDistInMask = paintDepthFactor * .5
        val zLimit = minDistInMask + 0.5 * (8..10).sumOf { sourceToMaskTransformAsArray[it].absoluteValue }

        val zTransformAtCenter: (DoubleArray) -> Double = { pos ->
            sourceToMaskTransformAsArray.let { transform ->
                transform[8] * pos[0] + transform[9] * pos[1] + transform[10] * pos[2] + transform[11]
            }
        }

        val paintedLabelSet = hashSetOf<Long>()
        fun trackPaintedLabel(painted: Long) {
            synchronized(paintedLabelSet) { paintedLabelSet += painted }
        }

        val paintCanvas: (IntegerType<*>, Long) -> Unit = { position, id ->
            position.setInteger(id)
            trackPaintedLabel(id)
        }

        LoopBuilder.setImages(
            BundleView(canvas).interval(canvas),
            viewerImgInSourceOverCanvas
        ).multiThreaded().forEachChunk { chunk ->
            val realMinMaskPoint = DoubleArray(3)
            val realMaxMaskPoint = DoubleArray(3)
            val minMaskPoint = LongArray(3)
            val maxMaskPoint = LongArray(3)
            val canvasPosition = DoubleArray(3)
            val canvasMinPositionInMask = DoubleArray(3)
            val canvasMaxPositionInMask = DoubleArray(3)

            chunk.forEachPixel { canvasBundle, viewerValType ->
                canvasBundle.localize(canvasPosition)

                val centerZ = zTransformAtCenter(canvasPosition)
                if (centerZ.absoluteValue > zLimit) {
                    return@forEachPixel
                }

                val paintVal = viewerValType.get()

                if (acceptAsPainted.test(paintVal) && centerZ.absoluteValue < minDistInMask)
                    paintCanvas(canvasBundle.get(), paintVal)
                else {
                    for (idx in 0 until 3) {
                        realMinMaskPoint[idx] = canvasPosition[idx] + MIN_CORNER_OFFSET[idx]
                        realMaxMaskPoint[idx] = canvasPosition[idx] + MAX_CORNER_OFFSET[idx]
                    }

                    val realIntervalOverSource = FinalRealInterval(realMinMaskPoint, realMaxMaskPoint, false)

                    val realIntervalOverMask = sourceToMaskWithDepthTransform.estimateBounds(realIntervalOverSource).smallestContainingInterval
                    if (0 !in realIntervalOverMask.min(2)..realIntervalOverMask.max(2)) {
                        return@forEachPixel
                    }

                    minMaskPoint[0] = realIntervalOverMask.min(0)
                    minMaskPoint[1] = realIntervalOverMask.min(1)
                    minMaskPoint[2] = 0
                    maxMaskPoint[0] = realIntervalOverMask.max(0)
                    maxMaskPoint[1] = realIntervalOverMask.max(1)
                    maxMaskPoint[2] = 0

                    val maskInterval = FinalInterval(minMaskPoint, maxMaskPoint)

                    val maskCursor = extendedViewerImg.interval(maskInterval).cursor()
                    while (maskCursor.hasNext()) {
                        val maskId = maskCursor.next().get()
                        if (acceptAsPainted.test(maskId)) {
                            for (idx in 0 until 2) {
                                canvasMinPositionInMask[idx] = maskCursor.getDoublePosition(idx) + MIN_CORNER_OFFSET[idx]
                                canvasMaxPositionInMask[idx] = maskCursor.getDoublePosition(idx) + MAX_CORNER_OFFSET[idx]
                            }

                            val maskPixelInterval = FinalRealInterval(canvasMinPositionInMask, canvasMaxPositionInMask, false)
                            val canvasInterval = sourceToMaskWithDepthTransform.inverse().estimateBounds(maskPixelInterval)
                            if (!Intervals.isEmpty(Intervals.intersect(realIntervalOverSource, canvasInterval))) {
                                paintCanvas(canvasBundle.get(), maskId)
                                return@forEachPixel
                            }
                        }
                    }
                }
            }
        }
        paintedLabelSet
    }
