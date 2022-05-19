package org.janelia.saalfeldlab.paintera.control.paint

import bdv.fx.viewer.ViewerPanelFX
import bdv.util.Affine3DHelpers
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import net.imglib2.*
import net.imglib2.cache.Invalidate
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.realtransform.Scale3D
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.raster

class ViewerMask private constructor(
    val source: MaskedSource<out RealType<*>, *>,
    val viewer: ViewerPanelFX,
    info: MaskInfo,
    invalidate: Invalidate<*>? = null,
    invalidateVolatile: Invalidate<*>? = null,
    shutdown: Runnable? = null,
    inline val isPaintedForeground: (UnsignedLongType) -> Boolean = { Label.isForeground(it.get()) },
    val paintDepth: Double,
) : SourceMask() {

    val curMipMapLevel: Int
        get() {
            val screenScaleTransform = AffineTransform3D().also { viewer.renderUnit.getScreenScaleTransform(0, it) }
            return viewer.state.getBestMipMapLevel(screenScaleTransform, source)
        }

    val initialGlobalToViewerTransform = AffineTransform3D().also { viewer.state.getViewerTransform(it) }
    val initialSourceToGlobalTransform = source.getSourceTransformForMask(info)!!
    val initialSourceToViewerTransform: AffineTransform3D = AffineTransform3D().apply {
        set(initialGlobalToViewerTransform.copy().concatenate(initialSourceToGlobalTransform))
    }
    private val depthScale = Affine3DHelpers.extractScale(initialSourceToViewerTransform, paintera.activeOrthoAxis)

    val currentSourceToGlobalTransform: AffineTransform3D get() = AffineTransform3D().also { source.getSourceTransform(info.time, curMipMapLevel, it) }
    val currentSourceToViewerTransform: AffineTransform3D get() = currentGlobalToViewerTransform.copy().concatenate(initialSourceToGlobalTransform)
    val currentGlobalToViewerTransform: AffineTransform3D get() = AffineTransform3D().also { viewer.state.getViewerTransform(it) }

    val initialToCurrentViewerTransform: AffineTransform3D get() = currentGlobalToViewerTransform.copy().concatenate(initialGlobalToViewerTransform.inverse())
    val initialToCurrentSourceTransform: AffineTransform3D get() = currentSourceToGlobalTransform.inverse().copy().concatenate(initialSourceToGlobalTransform)

    private val paintToViewerScaleTransform = Scale3D(1.0, 1.0, paintDepth * depthScale)
    private val sourceIntervalInSource = source.getDataSource(info.time, info.level)

    private val sourceIntervalInInitialViewer = currentSourceToViewerTransform.estimateBounds(sourceIntervalInSource)

    val viewerImg: RealRandomAccessibleRealInterval<UnsignedLongType>
    val volatileViewerImg: RealRandomAccessibleRealInterval<VolatileUnsignedLongType>
    val viewerRai: RandomAccessibleInterval<UnsignedLongType>

    var viewerImgInSource: RealRandomAccessibleRealInterval<UnsignedLongType>
    var volatileViewerImgInSource: RealRandomAccessibleRealInterval<VolatileUnsignedLongType>

    private val setMaskOnUpdateProperty = SimpleBooleanProperty(false)
    var setMaskOnUpdate by setMaskOnUpdateProperty.nonnull()

    val viewerTransformListener: InvalidationListener = InvalidationListener {

        updateTranslation()
        if (setMaskOnUpdate) {
            source.resetMasks(false)
            setViewerMaskOnSource()
        }
    }


    init {
        this.info = info
        this.invalidate = invalidate
        this.invalidateVolatile = invalidateVolatile
        this.shutdown = shutdown
        this.initialSourceToViewerTransform.set(currentSourceToViewerTransform)


        val (sourceAlignedViewerImg, sourceAlignedVolatileViewerImg) = createBackingImages()

        this.viewerImg = sourceAlignedViewerImg
        this.volatileViewerImg = sourceAlignedVolatileViewerImg
        this.viewerRai = viewerImg.raster().interval(viewerImg.smallestContainingInterval)

        val (sourceImg, volatileSourceImg) = getSourceImages(sourceAlignedViewerImg, sourceAlignedVolatileViewerImg)
        this.viewerImgInSource = sourceImg
        this.volatileViewerImgInSource = volatileSourceImg
        this.raiOverSource = viewerImgInSource.interval(source.getSource(info.time, info.level))
        this.volatileRaiOverSource = volatileViewerImg.interval(source.getSource(info.time, info.level))

    }

    fun disable() {
        viewer.state.removeListener(viewerTransformListener)
    }

    fun enable() {
        viewer.state.addListener(viewerTransformListener)
    }

    private fun updateTranslation() {

        val (sourceImg, volatileSourceImg) = getSourceImages(viewerImg, volatileViewerImg)

        this.viewerImgInSource = sourceImg
        this.volatileViewerImgInSource = volatileSourceImg

        val curMipMap = curMipMapLevel
        this.raiOverSource = Views.interval(Views.raster(viewerImgInSource), source.getSource(info.time, curMipMap))
        this.volatileRaiOverSource = Views.interval(Views.raster(volatileViewerImg), source.getSource(info.time, curMipMap))
    }

    fun setViewerMaskOnSource() {
        source.setMask(info, viewerImgInSource, volatileViewerImgInSource, invalidate, invalidateVolatile, shutdown, isPaintedForeground)
    }

    fun setSourceMaskOnSource() {
        source.setMask(this, isPaintedForeground)
    }

    fun currentToInitialPoint(viewerX: Double, viewerY: Double): RealPoint {
        return RealPoint(viewerX, viewerY, 0.0).also {
            initialToCurrentViewerTransform.applyInverse(it, it)
        }
    }

    private fun createBackingImages(): Pair<RealRandomAccessibleRealInterval<UnsignedLongType>, RealRandomAccessibleRealInterval<VolatileUnsignedLongType>> {

        val viewerInterval = Intervals.smallestContainingInterval(currentSourceToViewerTransform.estimateBounds(sourceIntervalInSource))

        val width = viewerInterval.dimension(0)
        val height = viewerInterval.dimension(1)

        val imgDims = intArrayOf(width.toInt(), height.toInt(), 1)
        val dataToVolatilePair = source.createMaskStoreWithVolatile(CELL_DIMS, imgDims)!!

        this.shutdown = Runnable { dataToVolatilePair.key.shutdown() }

        return offsetImagesToSourceBounds(dataToVolatilePair.key, dataToVolatilePair.value.rai)
    }

    private fun offsetImagesToSourceBounds(
        viewerCellImg: RandomAccessibleInterval<UnsignedLongType>,
        volatileViewerCellImg: RandomAccessibleInterval<VolatileUnsignedLongType>
    ): Pair<RealRandomAccessibleRealInterval<UnsignedLongType>, RealRandomAccessibleRealInterval<VolatileUnsignedLongType>> {

        val sourceBoundsOffset = AffineTransform3D().apply {
            translate(
                sourceIntervalInInitialViewer.realMin(0),
                sourceIntervalInInitialViewer.realMin(1),
                0.0
            )
        }

        val offsetMin = viewerCellImg.minAsLongArray()
            .mapIndexed() { idx, minVal -> minVal.toDouble() + sourceBoundsOffset.get(idx, 3) }
            .toDoubleArray()
        val offsetMax = viewerCellImg.maxAsLongArray()
            .mapIndexed() { idx, maxVal -> maxVal.toDouble() + sourceBoundsOffset.get(idx, 3) }
            .toDoubleArray()

        val offsetRealInterval = FinalRealInterval(offsetMin, offsetMax)

        val realOffsetViewerImg = RealViews.affine(Views.interpolate(Views.extendValue(viewerCellImg, Label.INVALID), NearestNeighborInterpolatorFactory()), sourceBoundsOffset)
        val manualTranslateRealInverseImg = FinalRealRandomAccessibleRealInterval(
            realOffsetViewerImg,
            offsetRealInterval
        )

        val realOffsetVolatileViewerImg = RealViews.affine(Views.interpolate(Views.extendValue(volatileViewerCellImg, VolatileUnsignedLongType(Label.INVALID)), NearestNeighborInterpolatorFactory()), sourceBoundsOffset)
        val manualTranslateRealInverseVolatileImg = FinalRealRandomAccessibleRealInterval(
            realOffsetVolatileViewerImg,
            offsetRealInterval
        )

        return manualTranslateRealInverseImg to manualTranslateRealInverseVolatileImg
    }

    private fun getSourceImages(viewerCellImg: RealRandomAccessibleRealInterval<UnsignedLongType>, volatileViewerCellImg: RealRandomAccessibleRealInterval<VolatileUnsignedLongType>):
        Pair<RealRandomAccessibleRealInterval<UnsignedLongType>, RealRandomAccessibleRealInterval<VolatileUnsignedLongType>> {

        val depthScaledViewerImg = RealViews.affineReal(viewerCellImg, paintToViewerScaleTransform)
        val depthScaledVolatileViewerImg = RealViews.affineReal(volatileViewerCellImg, paintToViewerScaleTransform)

        val initialViewerToCurrentSource = getInitialViewerToCurrentSourceTransform()

        val sourceBounds = initialViewerToCurrentSource.estimateBounds(viewerCellImg)

        val sourceImg = RealViews.affineReal(depthScaledViewerImg, initialViewerToCurrentSource)
        val sourceRai = FinalRealRandomAccessibleRealInterval(sourceImg, sourceBounds)

        val volatileSourceImg = RealViews.affineReal(depthScaledVolatileViewerImg, initialViewerToCurrentSource)
        val volatileSourceImgRai = FinalRealRandomAccessibleRealInterval(volatileSourceImg, sourceBounds)

        return sourceRai to volatileSourceImgRai
    }

    private fun getInitialViewerToCurrentSourceTransform(): AffineTransform3D {
        return currentSourceToGlobalTransform.inverse().copy().concatenate(initialGlobalToViewerTransform.inverse())
    }

    companion object {
        private val CELL_DIMS = intArrayOf(64, 64, 1)

        @JvmStatic
        fun MaskedSource<*, *>.setNewViewerMask(maskInfo: MaskInfo, viewer: ViewerPanelFX, brushDepth: Double): ViewerMask {
            val viewerMask = ViewerMask(this, viewer, maskInfo, paintDepth = brushDepth)
            viewerMask.setViewerMaskOnSource()
            viewerMask.enable()
            return viewerMask
        }

        @JvmStatic
        fun ViewerMask.getSourceDataInInitialViewerSpace(): RandomAccessibleInterval<out RealType<*>> {
            val interpolatedDataSource = source.getInterpolatedDataSource(info.time, info.level, null)
            val viewerBounds = initialSourceToViewerTransform.estimateBounds(FinalRealInterval(source.getDataSource(info.time, info.level)))
            return RealViews.affineReal(
                interpolatedDataSource,
                initialSourceToViewerTransform
            ).interval(viewerBounds)
        }

        @JvmStatic
        fun ViewerMask.getSourceDataInCurrentViewerSpace(): RandomAccessibleInterval<out RealType<*>> {
            val interpolatedDataSource = source.getInterpolatedDataSource(info.time, info.level, null)
            val viewerBounds = currentSourceToViewerTransform.estimateBounds(FinalRealInterval(source.getDataSource(info.time, info.level)))
            return RealViews.affineReal(
                interpolatedDataSource,
                currentSourceToViewerTransform
            ).interval(viewerBounds)
        }

        @JvmStatic
        fun maskToMaskTransformation(from: ViewerMask, to: ViewerMask): AffineTransform3D {
            return to.initialSourceToViewerTransform.copy().concatenate(
                to.initialSourceToGlobalTransform.inverse().copy().concatenate(
                    from.initialSourceToGlobalTransform.copy().concatenate(
                        from.initialSourceToViewerTransform.inverse()
                    )
                )
            )
        }
    }
}
