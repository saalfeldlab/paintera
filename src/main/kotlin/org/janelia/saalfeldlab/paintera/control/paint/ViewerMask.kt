package org.janelia.saalfeldlab.paintera.control.paint

import bdv.fx.viewer.ViewerPanelFX
import bdv.util.Affine3DHelpers
import javafx.beans.property.SimpleBooleanProperty
import net.imglib2.*
import net.imglib2.cache.Invalidate
import net.imglib2.loops.LoopBuilder
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
import net.imglib2.view.BundleView
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
import org.janelia.saalfeldlab.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import kotlin.math.ceil

class ViewerMask private constructor(
    val source: MaskedSource<out RealType<*>, *>,
    val viewer: ViewerPanelFX,
    info: MaskInfo,
    invalidate: Invalidate<*>? = null,
    invalidateVolatile: Invalidate<*>? = null,
    shutdown: Runnable? = null,
    private inline val paintedLabelIsValid: (UnsignedLongType) -> Boolean = { MaskedSource.VALID_LABEL_CHECK.test(it) },
    val paintDepthFactor: Double? = 1.0,
    private val maskSize: Interval? = null,
    private val defaultValue: Long = Label.INVALID
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

    val currentMaskToSourceTransform: AffineTransform3D
        get() = currentSourceToGlobalTransform.inverse().concatenate(currentGlobalToMaskTransform.inverse().copy())
    val initialMaskToSourceTransform: AffineTransform3D = initialSourceToGlobalTransform.inverse().copy().concatenate(initialGlobalToMaskTransform.inverse().copy())

    //TODO Caleb: This tracks zoom only now (translation shouldn't change). May not be needed.
    val initialToCurrentMaskTransform: AffineTransform3D get() = currentGlobalToMaskTransform.copy().concatenate(initialGlobalToMaskTransform.inverse())

    private val setMaskOnUpdateProperty = SimpleBooleanProperty(false)
    var setMaskOnUpdate by setMaskOnUpdateProperty.nonnull()


    val viewerImg: WrappedRandomAccessibleInterval<UnsignedLongType>
    val volatileViewerImg: WrappedRandomAccessibleInterval<VolatileUnsignedLongType>

    var viewerImgInSource: RealRandomAccessible<UnsignedLongType>
    var volatileViewerImgInSource: RealRandomAccessible<VolatileUnsignedLongType>


    private var depthScale = PaintUtils.maximumVoxelDiagonalLengthPerDimension(
        initialSourceToGlobalTransform,
        globalToViewerTransform
    ).maxOrNull()!!
    val depthScaleTransform get() = Scale3D(1.0, 1.0, paintDepthFactor?.times(depthScale) ?: 1.0)

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

        val (img, volatileImg) = maskSize?.let { createBackingImages(it, defaultValue) }
            ?: createBackingImages(defaultValue = defaultValue)
        this.viewerImg = img
        this.volatileViewerImg = volatileImg

        val (sourceImg, volatileSourceImg) = createSourceImages(img, volatileImg)
        this.viewerImgInSource = sourceImg
        this.volatileViewerImgInSource = volatileSourceImg
    }

    fun setViewerMaskOnSource() {
        source.setMask(this, viewerImgInSource, volatileViewerImgInSource, paintedLabelIsValid)
    }

    fun setSourceMaskOnSource() {
        source.setMask(SourceMask(info, rai, volatileRai, invalidate, invalidateVolatile, shutdown), paintedLabelIsValid)
    }

    private fun createBackingImages(
        maskSize: Interval = Intervals.smallestContainingInterval(initialSourceToViewerTransform.estimateBounds(sourceIntervalInSource)),
        defaultValue: Long
    ): Pair<WrappedRandomAccessibleInterval<UnsignedLongType>, WrappedRandomAccessibleInterval<VolatileUnsignedLongType>> {

        val width = maskSize.dimension(0)
        val height = maskSize.dimension(1)

        val imgDims = intArrayOf(width.toInt(), height.toInt(), 1)

        val (cachedCellImg, volatileRaiWithInvalidate) = source.createMaskStoreWithVolatile(CELL_DIMS, imgDims, defaultValue)!!

        this.shutdown = Runnable { cachedCellImg.shutdown() }
        return WrappedRandomAccessibleInterval(cachedCellImg) to WrappedRandomAccessibleInterval(volatileRaiWithInvalidate.rai)
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


    class WrappedRandomAccessibleInterval<T>(var source: RandomAccessibleInterval<T>, writable: Boolean = true) : RandomAccessibleInterval<T>, View {

        var writableSource: RandomAccessibleInterval<T>? = if (writable) source else null
        override fun numDimensions(): Int = source.numDimensions()

        override fun randomAccess(): RandomAccess<T> = source.randomAccess()

        override fun randomAccess(interval: Interval): RandomAccess<T> = source.randomAccess(interval)
        override fun min(d: Int): Long = source.min(d)
        override fun max(d: Int): Long = source.max(d)
    }

    private fun createSourceImages(
        viewerCellImg: RandomAccessibleInterval<UnsignedLongType>,
        volatileViewerCellImg: RandomAccessibleInterval<VolatileUnsignedLongType>
    ):
        Pair<RealRandomAccessible<UnsignedLongType>, RealRandomAccessible<VolatileUnsignedLongType>> {

        val realViewerImg = viewerCellImg.extendValue(Label.INVALID).interpolateNearestNeighbor()
        val realVolatileViewerImg = volatileViewerCellImg.extendValue(VolatileUnsignedLongType(Label.INVALID)).interpolateNearestNeighbor()

        val sourceImg = realViewerImg.affineReal(initialMaskToSourceWithDepthTransform)
        val volatileSourceImg = realVolatileViewerImg.affineReal(initialMaskToSourceWithDepthTransform)

        return sourceImg to volatileSourceImg
    }

    internal fun newBackingImages() = (maskSize?.let { createBackingImages(it, defaultValue) }
        ?: createBackingImages(defaultValue = defaultValue))

    internal fun updateBackingImages(
        newSourceImages: Pair<RandomAccessibleInterval<UnsignedLongType>, RandomAccessibleInterval<VolatileUnsignedLongType>>? = null,
        writableSourceImages: Pair<RandomAccessibleInterval<UnsignedLongType>?, RandomAccessibleInterval<VolatileUnsignedLongType>?>? = newSourceImages
    ) {
        (newSourceImages ?: newBackingImages()).let { (img, volatileImg) ->
            viewerImg.source = (img as? WrappedRandomAccessibleInterval)?.source ?: img
            volatileViewerImg.source = (volatileImg as? WrappedRandomAccessibleInterval)?.source ?: volatileImg

            viewerImg.writableSource = (writableSourceImages?.first as? WrappedRandomAccessibleInterval)?.source ?: writableSourceImages?.first
            volatileViewerImg.writableSource = (writableSourceImages?.second as? WrappedRandomAccessibleInterval)?.source ?: writableSourceImages?.second
        }
    }


    override fun <C : IntegerType<C>> applyMaskToCanvas(
        canvas: RandomAccessibleInterval<C>,
        acceptAsPainted: Predicate<UnsignedLongType>
    ): Set<Long> {

        val extendedViewerImg = Views.extendValue(viewerImg, Label.INVALID)
        val viewerImgInSourceOverCanvas = viewerImgInSource.raster().interval(canvas)

        val sourceToMaskTransform = currentMaskToSourceWithDepthTransform.inverse()


        val sourceToMaskNoTranslation = sourceToMaskTransform.copy().apply { setTranslation(0.0, 0.0, 0.0) }

        val unitXInMask = doubleArrayOf(1.0, 0.0, 0.0).also { sourceToMaskNoTranslation.apply(it, it) }
        val unitYInMask = doubleArrayOf(0.0, 1.0, 0.0).also { sourceToMaskNoTranslation.apply(it, it) }
        val unitZInMask = doubleArrayOf(0.0, 0.0, 1.0).also { sourceToMaskNoTranslation.apply(it, it) }

        val zeros = doubleArrayOf(0.0, 0.0, 0.0).also { sourceToMaskNoTranslation.apply(it, it) }

        val maskToSourceScaleX = Affine3DHelpers.extractScale(sourceToMaskTransform.inverse(), 0)
        val maskToSourceScaleY = Affine3DHelpers.extractScale(sourceToMaskTransform.inverse(), 1)
        val maskToSourceScaleZ = Affine3DHelpers.extractScale(sourceToMaskTransform.inverse(), 2)
        val maskScaleInSource = doubleArrayOf(
            maskToSourceScaleX,
            maskToSourceScaleY,
            maskToSourceScaleZ * (paintDepthFactor ?: 1.0)
        ).also {
            sourceToMaskNoTranslation.inverse().apply(it, it)
        }

        val maxSourceDistInMask = LinAlgHelpers.distance(maskScaleInSource, zeros) / 2
        val minSourceDistInMask = (paintDepthFactor ?: 1.0) * minOf(
            LinAlgHelpers.distance(zeros, unitXInMask),
            LinAlgHelpers.distance(zeros, unitYInMask),
            LinAlgHelpers.distance(zeros, unitZInMask)
        ) / 2

        val sourceToMaskTransformAsArray = sourceToMaskTransform.rowPackedCopy
        val painted = AtomicBoolean(false)
        val paintedLabelSet = hashSetOf<Long>()
        fun trackPaintedLabel(painted: Long) {
            synchronized(paintedLabelSet) {
                paintedLabelSet += painted
            }
        }

        LoopBuilder.setImages(
            BundleView(canvas).interval(canvas),
            viewerImgInSourceOverCanvas
        )
            .multiThreaded()
            .forEachChunk { chunk ->
                val realMinMaskPoint = DoubleArray(3)
                val realMaxMaskPoint = DoubleArray(3)

                val minMaskPoint = LongArray(3)
                val maxMaskPoint = LongArray(3)

                val canvasPosition = DoubleArray(3)
                val canvasPositionInMask = DoubleArray(3)
                val canvasPositionInMaskAsRealPoint = RealPoint.wrap(canvasPositionInMask)

                chunk.forEachPixel { canvasBundle, viewerVal ->
                    canvasBundle.localize(canvasPosition)
                    val sourceDepthInMask = sourceToMaskTransformAsArray.let {
                        it[8] * canvasPosition[0] + it[9] * canvasPosition[1] + it[10] * canvasPosition[2] + it[11]
                    }

                    sourceToMaskTransform.apply(canvasPosition, canvasPositionInMask)
                    if (sourceDepthInMask <= maxSourceDistInMask) {
                        if (acceptAsPainted.test(viewerVal)) {
                            val paintVal = viewerVal.get()
                            if (sourceDepthInMask < minSourceDistInMask) {
                                canvasBundle.get().setInteger(paintVal)
                            } else {
                                var hasPositive = false
                                var hasNegative = false
                                for (corner in CUBE_CORNERS) {
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
                                        canvasBundle.get().setInteger(paintVal)
                                        painted.set(true)
                                        trackPaintedLabel(paintVal)
                                        break
                                    }
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
                                    val maskLabel = maskCursor.next()
                                    val maskId = maskLabel.integerLong
                                    if (acceptAsPainted.test(maskLabel)) {
                                        maskCursor.localize(canvasPositionInMask)
                                        sourceToMaskTransform.inverse().apply(canvasPositionInMask, canvasPositionInMask)
                                        /* just renaming for clarity. */
                                        @Suppress("UnnecessaryVariable") val maskPointInSource = canvasPositionInMaskAsRealPoint
                                        if (!Intervals.contains(realIntervalOverSource, maskPointInSource)) {
                                            continue
                                        }
                                        canvasBundle.get().setInteger(maskId)
                                        painted.set(true)
                                        trackPaintedLabel(maskId)
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
        paintera.baseView.orthogonalViews().requestRepaint(currentSourceToGlobalTransform.estimateBounds(canvas.extendBy(1.0)))
        return paintedLabelSet
    }

    fun requestRepaint(intervalOverMask: Interval? = null) {
        intervalOverMask?.let {
            val globalInterval = initialGlobalToMaskTransform.inverse().estimateBounds(it)
            paintera.baseView.orthogonalViews().requestRepaint(globalInterval)
        } ?: paintera.baseView.orthogonalViews().requestRepaint()
    }

    fun getScreenInterval(width: Long = viewer.width.toLong(), height: Long = viewer.height.toLong()): Interval {
        val (x: Long, y: Long) = displayPointToInitialMaskPoint(0, 0)
        return Intervals.createMinSize(x, y, 0, width, height, 1)
    }

    fun maskOverScreenInterval(): RandomAccessibleInterval<UnsignedLongType> = viewerImg.interval(getScreenInterval())


    companion object {
        private val CELL_DIMS = intArrayOf(64, 64, 1)

        private val CUBE_CORNERS = arrayOf(
            doubleArrayOf(-.5, -.5, -.5),
            doubleArrayOf(+.5, +.5, +.5),

            doubleArrayOf(-.5, -.5, +.5),
            doubleArrayOf(+.5, +.5, -.5),

            doubleArrayOf(-.5, +.5, -.5),
            doubleArrayOf(+.5, -.5, +.5),

            doubleArrayOf(-.5, +.5, +.5),
            doubleArrayOf(+.5, -.5, -.5),
        )

        @JvmStatic
        @JvmOverloads
        fun MaskedSource<*, *>.createViewerMask(maskInfo: MaskInfo, viewer: ViewerPanelFX, paintDepth: Double? = 1.0, maskSize: Interval? = null, defaultValue: Long = Label.INVALID, setMask: Boolean = true): ViewerMask {
            val viewerMask = ViewerMask(this, viewer, maskInfo, paintDepthFactor = paintDepth, maskSize = maskSize, defaultValue = defaultValue)
            if (setMask) {
                viewerMask.setViewerMaskOnSource()
            }
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
            ).raster().interval(maskBounds)
        }

        @JvmStatic
        fun maskToMaskTransformation(from: ViewerMask, to: ViewerMask): AffineTransform3D {
            return AffineTransform3D().also {
                it.concatenate(to.initialGlobalToMaskTransform)
                it.concatenate(from.initialGlobalToMaskTransform.inverse())
            }
        }
    }
}
