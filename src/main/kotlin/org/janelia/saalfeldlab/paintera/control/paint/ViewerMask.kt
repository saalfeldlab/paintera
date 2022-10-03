package org.janelia.saalfeldlab.paintera.control.paint

import bdv.fx.viewer.ViewerPanelFX
import bdv.util.Affine3DHelpers
import javafx.beans.property.SimpleBooleanProperty
import net.imglib2.*
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.realtransform.Scale3D
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.util.LinAlgHelpers
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool.globalToViewerTransform
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.interpolateNearestNeighbor
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.raster
import org.janelia.saalfeldlab.util.toPoint
import java.util.function.Predicate
import kotlin.math.absoluteValue
import kotlin.math.ceil

class ViewerMask private constructor(
    val source: MaskedSource<out RealType<*>, *>,
    val viewer: ViewerPanelFX,
    info: MaskInfo,
    invalidate: Invalidate<*>? = null,
    invalidateVolatile: Invalidate<*>? = null,
    shutdown: Runnable? = null,
    private inline val isPaintedForeground: (UnsignedLongType) -> Boolean = { Label.isForeground(it.get()) },
    val paintDepthFactor: Double = 1.0,
    maskSize: Interval? = null
) : SourceMask(info) {
    private val sourceIntervalInSource = source.getDataSource(info.time, info.level)

    val currentGlobalToViewerTransform: AffineTransform3D get() = AffineTransform3D().also { viewer.state.getViewerTransform(it) }
    val initialGlobalToViewerTransform = currentGlobalToViewerTransform

    val currentSourceToGlobalTransform: AffineTransform3D get() = source.getSourceTransformForMask(info)
    val initialSourceToGlobalTransform: AffineTransform3D = currentSourceToGlobalTransform

    val currentSourceToViewerTransform: AffineTransform3D get() = currentSourceToGlobalTransform.preConcatenate(currentGlobalToViewerTransform)
    val initialSourceToViewerTransform: AffineTransform3D = currentSourceToViewerTransform

    init {
        maskSize?.let { actualSize ->
            val idealMaskSize = Intervals.smallestContainingInterval(initialSourceToViewerTransform.estimateBounds(sourceIntervalInSource))

            val scale = Scale3D(
                actualSize.dimension(0).toDouble() / idealMaskSize.dimension(0),
                actualSize.dimension(1).toDouble() / idealMaskSize.dimension(1),
                1.0
            )

            initialSourceToViewerTransform.preConcatenate(scale)
            initialGlobalToViewerTransform.preConcatenate(scale)
        }
    }

    private val sourceIntervalInCurrentViewer get() = currentSourceToViewerTransform.estimateBounds(sourceIntervalInSource)
    private val sourceIntervalInInitialViewer = initialSourceToViewerTransform.estimateBounds(sourceIntervalInSource)

    val currentGlobalToMaskTransform: AffineTransform3D
        get() = currentGlobalToViewerTransform.copy().also {
            val sourceInCurrentViewer = sourceIntervalInCurrentViewer
            it.translate(-sourceInCurrentViewer.realMin(0), -sourceInCurrentViewer.realMin(1), 0.0)
        }

    val initialGlobalToMaskTransform: AffineTransform3D = initialGlobalToViewerTransform.copy().also {
        it.translate(-sourceIntervalInInitialViewer.realMin(0), -sourceIntervalInInitialViewer.realMin(1), 0.0)
    }

    val currentMaskToSourceTransform: AffineTransform3D get() = currentSourceToGlobalTransform.inverse().concatenate(currentGlobalToMaskTransform.inverse().copy())
    val initialMaskToSourceTransform: AffineTransform3D = initialSourceToGlobalTransform.inverse().concatenate(initialGlobalToMaskTransform.inverse().copy())

    //TODO Caleb: This tracks zoom only now (translation shouldn't change). May not be needed.
    val initialToCurrentMaskTransform: AffineTransform3D get() = currentGlobalToMaskTransform.copy().concatenate(initialGlobalToMaskTransform.inverse())

    private val setMaskOnUpdateProperty = SimpleBooleanProperty(false)
    var setMaskOnUpdate by setMaskOnUpdateProperty.nonnull()


    val viewerImg: RandomAccessibleInterval<UnsignedLongType>

    val volatileViewerImg: RandomAccessibleInterval<VolatileUnsignedLongType>

    var viewerImgInSource: RealRandomAccessible<UnsignedLongType>
    var volatileViewerImgInSource: RealRandomAccessible<VolatileUnsignedLongType>


    private var depthScale = PaintUtils.maximumVoxelDiagonalLengthPerDimension(
        initialSourceToGlobalTransform,
        globalToViewerTransform
    ).maxOrNull()!!
    val depthScaleTransform get() = Scale3D(1.0, 1.0, depthScale * paintDepthFactor)

    /**
     * The ratio of the initial 0 dimensions scale to the current 0 dimension scale.
     */
    val xScaleChange get() = (Affine3DHelpers.extractScale(initialGlobalToMaskTransform, 0) / Affine3DHelpers.extractScale(currentGlobalToMaskTransform, 0))

    val initialMaskToSourceWithDepthTransform: AffineTransform3D = initialMaskToSourceTransform.copy().concatenate(depthScaleTransform)
    val currentMaskToSourceWithDepthTransform: AffineTransform3D get() = currentMaskToSourceTransform.concatenate(depthScaleTransform)
    private val maskSourceInterval
        get() = let {
            val nnExtendedRealSourceInterval = viewerImg.asRealInterval.extendBy(.5)
            currentMaskToSourceWithDepthTransform.estimateBounds(nnExtendedRealSourceInterval)
        }

    override fun getRai(): RandomAccessibleInterval<UnsignedLongType> {
        return viewerImgInSource.raster().interval(maskSourceInterval.smallestContainingInterval)
    }

    override fun getVolatileRai(): RandomAccessibleInterval<VolatileUnsignedLongType> {
        return volatileViewerImgInSource.raster().interval(maskSourceInterval.smallestContainingInterval)
    }

    init {
        this.info = info
        this.invalidate = invalidate
        this.invalidateVolatile = invalidateVolatile
        this.shutdown = shutdown

        val (img, volatileImg) = maskSize?.let { createBackingImages(it) } ?: createBackingImages()
        this.viewerImg = img
        this.volatileViewerImg = volatileImg

        val (sourceImg, volatileSourceImg) = createSourceImages(img, volatileImg)
        this.viewerImgInSource = sourceImg
        this.volatileViewerImgInSource = volatileSourceImg
    }

    fun setViewerMaskOnSource() {
        source.setMask(this, viewerImgInSource, volatileViewerImgInSource, isPaintedForeground)
    }

    fun setSourceMaskOnSource() {
        source.setMask(SourceMask(info, rai, volatileRai, invalidate, invalidateVolatile, shutdown), isPaintedForeground)
    }

    private fun createBackingImages(maskSize: Interval = Intervals.smallestContainingInterval(initialSourceToViewerTransform.estimateBounds(sourceIntervalInSource))): Pair<RandomAccessibleInterval<UnsignedLongType>, RandomAccessibleInterval<VolatileUnsignedLongType>> {

        val width = maskSize.dimension(0)
        val height = maskSize.dimension(1)

        val imgDims = intArrayOf(width.toInt(), height.toInt(), 1)

        val (cachedCellImg, volatileRaiWithInvalidate) = source.createMaskStoreWithVolatile(CELL_DIMS, imgDims)!!

        this.shutdown = Runnable { cachedCellImg.shutdown() }
        return cachedCellImg to volatileRaiWithInvalidate.rai
    }

    fun displayPointToInitialMaskPoint(displayX: Int, displayY: Int) = displayPointToInitialMaskPoint(Point(displayX, displayY, 0))
    fun displayPointToInitialMaskPoint(displayX: Double, displayY: Double) = displayPointToInitialMaskPoint(RealPoint(displayX, displayY, 0.0))
    fun displayPointToInitialMaskPoint(displayPoint: Point) = displayPointToInitialMaskPoint(displayPoint.positionAsRealPoint())
    fun displayPointToInitialMaskPoint(displayPoint: RealPoint): Point {
        val globalPoint = displayPoint.also { viewer.displayToGlobalCoordinates(it) }

        val xyScaleOnly = depthScaleTransform.copy().also {
            it.set(it.getScale(0), it.getScale(1), 1.0)
        }

        val pointInInitialMask = RealPoint(globalPoint).also { initialGlobalToMaskTransform.copy().concatenate(xyScaleOnly.inverse()).apply(globalPoint, it) }
        return pointInInitialMask.toPoint()
    }

    private fun createSourceImages(viewerCellImg: RandomAccessibleInterval<UnsignedLongType>, volatileViewerCellImg: RandomAccessibleInterval<VolatileUnsignedLongType>):
        Pair<RealRandomAccessible<UnsignedLongType>, RealRandomAccessible<VolatileUnsignedLongType>> {

        val realViewerImg = Views.extendValue(viewerCellImg, Label.INVALID).interpolateNearestNeighbor()
        val realVolatileViewerImg = Views.extendValue(volatileViewerCellImg, VolatileUnsignedLongType(Label.INVALID)).interpolateNearestNeighbor()

        val sourceImg = RealViews.affineReal(realViewerImg, initialMaskToSourceWithDepthTransform)
        val volatileSourceImg = RealViews.affineReal(realVolatileViewerImg, initialMaskToSourceWithDepthTransform)

        return sourceImg to volatileSourceImg
    }


    override fun <C : IntegerType<C>> applyMaskToCanvas(
        canvas: RandomAccessibleInterval<C>,
        acceptAsPainted: Predicate<UnsignedLongType>
    ) {
        val paintLabel = info.value

        val extendedViewerImg = Views.extendValue(viewerImg, Label.INVALID)
        val viewerCursorOverCanvasInSource = Views.flatIterable(viewerImgInSource.interval(canvas)).cursor()
        val canvasCursor = Views.flatIterable(canvas).cursor()

        val corners = arrayOf(
            doubleArrayOf(-.5, -.5, -.5),
            doubleArrayOf(+.5, +.5, +.5),

            doubleArrayOf(-.5, -.5, +.5),
            doubleArrayOf(+.5, +.5, -.5),

            doubleArrayOf(-.5, +.5, -.5),
            doubleArrayOf(+.5, -.5, +.5),

            doubleArrayOf(-.5, +.5, +.5),
            doubleArrayOf(+.5, -.5, -.5),
        )

        val sourceToMaskTransform = currentMaskToSourceWithDepthTransform.inverse()


        val sourceToMaskNoTranslation = sourceToMaskTransform.copy().apply { setTranslation(0.0, 0.0, 0.0) }

        val unitXInMask = doubleArrayOf(1.0, 0.0, 0.0).also { sourceToMaskNoTranslation.apply(it, it) }
        val unitYInMask = doubleArrayOf(0.0, 1.0, 0.0).also { sourceToMaskNoTranslation.apply(it, it) }
        val unitZInMask = doubleArrayOf(0.0, 0.0, 1.0).also { sourceToMaskNoTranslation.apply(it, it) }

        val zeros = doubleArrayOf(0.0, 0.0, 0.0).also { sourceToMaskNoTranslation.apply(it, it) }
        val depth = doubleArrayOf(paintDepthFactor, paintDepthFactor, paintDepthFactor).also { sourceToMaskNoTranslation.apply(it, it) }

        val maskToSourceScaleX = Affine3DHelpers.extractScale(sourceToMaskTransform.inverse(), 0)
        val maskToSourceScaleY = Affine3DHelpers.extractScale(sourceToMaskTransform.inverse(), 1)
        val maskToSourceScaleZ = Affine3DHelpers.extractScale(sourceToMaskTransform.inverse(), 2)
        val maskScaleInSource = doubleArrayOf(
            maskToSourceScaleX,
            maskToSourceScaleY,
            maskToSourceScaleZ * paintDepthFactor
        ).also {
            sourceToMaskNoTranslation.inverse().apply(it, it)
        }

        val maxSourceDistInMask = LinAlgHelpers.distance(maskScaleInSource, zeros) / 2
        val minSourceDistInMask = paintDepthFactor * minOf(
            LinAlgHelpers.distance(zeros, unitXInMask),
            LinAlgHelpers.distance(zeros, unitYInMask),
            LinAlgHelpers.distance(zeros, unitZInMask)
        ) / 2

        val sourceToMaskTransformAsArray = sourceToMaskTransform.rowPackedCopy

        val realMinMaskPoint = DoubleArray(3)
        val realMaxMaskPoint = DoubleArray(3)

        val minMaskPoint = LongArray(3)
        val maxMaskPoint = LongArray(3)

        val paintVal = paintLabel.get()
        val canvasPosition = DoubleArray(3)
        val canvasPositionInMask = DoubleArray(3)
        val canvasPositionInMaskAsRealPoint = RealPoint.wrap(canvasPositionInMask)
        while (canvasCursor.hasNext()) {
            canvasCursor.fwd()
            viewerCursorOverCanvasInSource.fwd()
            canvasCursor.localize(canvasPosition)
            sourceToMaskTransform.apply(canvasPosition, canvasPositionInMask)
            val sourceDepthInMask = canvasPositionInMask[2].absoluteValue
            if (sourceDepthInMask > maxSourceDistInMask) {
                continue
            }
            val viewerVal = viewerCursorOverCanvasInSource.get()
            if (acceptAsPainted.test(viewerVal)) {
                if (sourceDepthInMask < minSourceDistInMask) {
                    canvasCursor.get().setInteger(paintVal)
                    continue
                }
                var hasPositive = false
                var hasNegative = false
                for (corner in corners) {
                    /* transform z only */
                    val z = sourceToMaskTransformAsArray.let {
                        it[8] * (canvasPosition[0] + corner[0]) + it[9] * (canvasPosition[1] + corner[1]) + it[10] * (canvasPosition[2] + corner[2]) + it[11]
                    }
                    if (z >= 0) {
                        hasPositive = true
                    } else {
                        hasNegative = true
                    }
                    if (hasPositive && hasNegative) {
                        canvasCursor.get().setInteger(paintVal)
                        break
                    }
                }
            } else {
                /* nearest neighbor interval over source */
                realMinMaskPoint[0] = canvasPosition[0] - .5
                realMinMaskPoint[1] = canvasPosition[1] - .5
                realMinMaskPoint[2] = canvasPosition[2] - .5
                realMaxMaskPoint[0] = canvasPosition[0] + .5
                realMaxMaskPoint[1] = canvasPosition[1] + .5
                realMaxMaskPoint[2] = canvasPosition[2] + .5

                val realIntervalOverSource = FinalRealInterval(realMinMaskPoint, realMaxMaskPoint)

                maskScaleInSource.forEachIndexed { idx, depth ->
                    realMinMaskPoint[idx] -= depth
                    realMaxMaskPoint[idx] += depth
                }

                /* iterate over canvas in viewer */
                val realIntervalOverMask = sourceToMaskTransform.estimateBounds(realIntervalOverSource)
                if (0.0 in realIntervalOverMask.realMin(2)..realIntervalOverMask.realMax(2)) {

                    minMaskPoint[0] = realIntervalOverMask.realMin(0).toLong()
                    minMaskPoint[1] = realIntervalOverMask.realMin(1).toLong()
                    minMaskPoint[2] = 0

                    maxMaskPoint[0] = ceil(realIntervalOverMask.realMax(0)).toLong()
                    maxMaskPoint[1] = ceil(realIntervalOverMask.realMax(1)).toLong()
                    maxMaskPoint[2] = 0

                    val maskInterval = FinalInterval(minMaskPoint, maxMaskPoint)

                    val maskCursor = extendedViewerImg.interval(maskInterval).cursor()
                    while (maskCursor.hasNext()) {
                        val maskId = maskCursor.next().integerLong
                        if (Label.isForeground(maskId)) {
                            maskCursor.localize(canvasPositionInMask)
                            sourceToMaskTransform.inverse().apply(canvasPositionInMask, canvasPositionInMask)
                            if (!Intervals.contains(realIntervalOverSource, canvasPositionInMaskAsRealPoint)) {
                                continue
                            }
                            canvasCursor.get().setInteger(paintVal)
                            break
                        }
                    }
                }
            }
        }
        paintera.baseView.orthogonalViews().requestRepaint(currentSourceToGlobalTransform.estimateBounds(canvas).extendBy(1.0))
    }


    companion object {
        private val CELL_DIMS = intArrayOf(64, 64, 1)

        @JvmStatic
        @JvmOverloads
        fun MaskedSource<*, *>.setNewViewerMask(maskInfo: MaskInfo, viewer: ViewerPanelFX, paintDepth: Double = 1.0, maskSize: Interval? = null): ViewerMask {
            val viewerMask = ViewerMask(this, viewer, maskInfo, paintDepthFactor = paintDepth, maskSize = maskSize)
            viewerMask.setViewerMaskOnSource()
            return viewerMask
        }

        @JvmStatic
        fun ViewerMask.getSourceDataInInitialMaskSpace(): RandomAccessibleInterval<out RealType<*>> {
            val interpolatedDataSource = source.getInterpolatedDataSource(info.time, info.level, null)
            val sourceToInitialMask = initialMaskToSourceTransform.inverse()
            val maskBounds = sourceToInitialMask.estimateBounds(FinalRealInterval(source.getDataSource(info.time, info.level)))
            return RealViews.affineReal(
                interpolatedDataSource,
                sourceToInitialMask
            ).interval(maskBounds)
        }

        @JvmStatic
        fun maskToMaskTransformation(from: ViewerMask, to: ViewerMask): AffineTransform3D {
            return AffineTransform3D().also {
                it.concatenate(to.initialMaskToSourceTransform.inverse())
                it.concatenate(from.initialMaskToSourceTransform)
            }
        }
    }
}
