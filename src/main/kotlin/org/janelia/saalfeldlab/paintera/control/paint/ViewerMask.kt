package org.janelia.saalfeldlab.paintera.control.paint

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
import net.imglib2.view.Views
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlin.math.sqrt

class ViewerMask private constructor(
	val source: MaskedSource<out RealType<*>, *>,
	internal val viewer: ViewerPanelFX,
	info: MaskInfo,
	invalidate: Invalidate<*>? = null,
	invalidateVolatile: Invalidate<*>? = null,
	shutdown: Runnable? = null,
	private val paintedLabelIsValid: (Long) -> Boolean = { MaskedSource.VALID_LABEL_CHECK.test(it) },
	val paintDepthFactor: Double = 1.0,
	private val maskSize: Interval? = null,
	private val defaultValue: Long = Label.INVALID,
	globalToViewerTransform: AffineTransform3D? = null
) : SourceMask(info) {
	private val sourceIntervalInSource = source.getDataSource(info.time, info.level)

	val sourceToGlobalTransform: AffineTransform3D = source.getSourceTransformForMask(info)


	/**
	 * Transform from global space to either:
	 *  1. the top-left corner of the [ViewerPanelFX] used to create this mask, OR
	 *  2. the passed in initialViewerTransform
	 */
	val initialGlobalToViewerTransform = globalToViewerTransform ?: currentGlobalToViewerTransform

	/**
	 * Transform from global to the current viewer.
	 *  May be different from initial if the viewer has moved/resized, etc.
	 */
	val currentGlobalToViewerTransform: AffineTransform3D get() = AffineTransform3D().also { viewer.state.getViewerTransform(it) }


	val viewerTransform: AffineTransform3D
		get() = paintera.baseView.orthogonalViews().viewerAndTransforms().first { it.viewer() == viewer }.let {
			it.displayTransform.transformCopy.concatenate(it.viewerSpaceToViewerTransform.transformCopy)
		}

	/**
	 * Differs from [initialGlobalToViewerTransform] in that this removes the [viewerTransform]
	 *  which encodes translation and scale information based on the ViewerPanel width/height/screenScale
	 *
	 *  This is equivalent to calling [GlobalTransformManager.transform] at when at the [initialGlobalToViewerTransform]
	 */
	val initialGlobalTransform = initialGlobalToViewerTransform.copy().preConcatenate(viewerTransform.inverse())
	val currentGlobalTransform
		get() = currentGlobalToViewerTransform.preConcatenate(viewerTransform.inverse())

	/**
	 * Transform to the initial Viewer space from the [source] space for the given [MaskInfo] (including mipmap levels)
	 */
	val initialSourceToViewerTransform: AffineTransform3D = sourceToGlobalTransform.copy().preConcatenate(initialGlobalToViewerTransform)
	val currentSourceToViewerTransform: AffineTransform3D get() = sourceToGlobalTransform.copy().preConcatenate(currentGlobalToViewerTransform)

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

	//TODO Caleb: Mask and Source transforms shouldn't change, probably don't need both initial and current
	val currentMaskToSourceTransform: AffineTransform3D
		get() = sourceToGlobalTransform.copy().inverse().concatenate(currentGlobalToMaskTransform.inverse())
	val initialMaskToSourceTransform: AffineTransform3D = sourceToGlobalTransform.copy().inverse().concatenate(initialGlobalToMaskTransform.inverse())

	//TODO Caleb: This tracks zoom only now (translation shouldn't change). May not be needed.
	val initialToCurrentMaskTransform: AffineTransform3D get() = currentGlobalToMaskTransform.copy().concatenate(initialGlobalToMaskTransform.inverse())

	val initialMaskToViewerTransform = initialGlobalToViewerTransform.copy().concatenate(initialGlobalToMaskTransform.inverse())
	val currentMaskToViewerTransform
		get() = currentGlobalToViewerTransform.copy().concatenate(currentGlobalToMaskTransform.inverse())

	private val setMaskOnUpdateProperty = SimpleBooleanProperty(false)
	var setMaskOnUpdate by setMaskOnUpdateProperty.nonnull()


	val viewerImg: WrappedRandomAccessibleInterval<UnsignedLongType>
	val volatileViewerImg: WrappedRandomAccessibleInterval<VolatileUnsignedLongType>

	var viewerImgInSource: RealRandomAccessible<UnsignedLongType>
	var volatileViewerImgInSource: RealRandomAccessible<VolatileUnsignedLongType>


	private val depthScale = let {
		var sqSum = 0.0
		val row = 2
		val transform = initialMaskToSourceTransform.inverse()
		for (col in 0..2) {
			val x = transform.get(row, col)
			sqSum += x * x
		}
 		sqrt(sqSum)
	}
 	val depthScaleTransform get() = Scale3D(1.0, 1.0, paintDepthFactor.times(depthScale))

	/**
	 * The ratio of the initial 0 dimensions scale to the current 0 dimension scale.
	 */
	val xScaleChange get() = (Affine3DHelpers.extractScale(initialGlobalToMaskTransform, 0) / Affine3DHelpers.extractScale(currentGlobalToMaskTransform, 0))

	val initialMaskToGlobalWithDepthTransform: AffineTransform3D = initialGlobalToMaskTransform.inverse().copy().concatenate(depthScaleTransform)
	val initialMaskToSourceWithDepthTransform: AffineTransform3D = initialMaskToSourceTransform.copy().concatenate(depthScaleTransform)
	val currentMaskToSourceWithDepthTransform: AffineTransform3D = currentMaskToSourceTransform.copy().concatenate(depthScaleTransform)
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

		this.shutdown = let {
			val oldShutdown = shutdown
			Runnable {
				oldShutdown?.run()
				cachedCellImg.shutdown()
			}
		}
		return WrappedRandomAccessibleInterval(cachedCellImg) to WrappedRandomAccessibleInterval(volatileRaiWithInvalidate.rai)
	}

	@JvmOverloads
	fun displayPointToMask(displayX: Int, displayY: Int, pointInCurrentDisplay: Boolean = false) = displayPointToMask(Point(displayX, displayY, 0), pointInCurrentDisplay)

	@JvmOverloads
	fun displayPointToMask(displayX: Double, displayY: Double, pointInCurrentDisplay: Boolean = false) = displayPointToMask(RealPoint(displayX, displayY, 0.0), pointInCurrentDisplay)

	@JvmOverloads
	fun displayPointToMask(displayPoint: Point, pointInCurrentDisplay: Boolean = false) = displayPointToMask(displayPoint.positionAsRealPoint(), pointInCurrentDisplay)

	@JvmOverloads
	fun displayPointToMask(displayPoint: RealPoint, pointInCurrentDisplay: Boolean = false): Point {
		val globalToMask = if (pointInCurrentDisplay) currentGlobalToViewerTransform else initialGlobalToViewerTransform
		val globalPoint = displayPoint.also { globalToMask.applyInverse(it, it) }

		val xyScaleOnly = depthScaleTransform.copy().also {
			it.set(it.getScale(0), it.getScale(1), 1.0)
		}

		val pointInMask = RealPoint(globalPoint).also { initialGlobalToMaskTransform.copy().concatenate(xyScaleOnly.inverse()).apply(globalPoint, it) }
		return pointInMask.toPoint()
	}


	class WrappedRandomAccessibleInterval<T>(var wrappedSource: RandomAccessibleInterval<T>, writable: Boolean = true) : RandomAccessibleInterval<T>, View {

		var writableSource: RandomAccessibleInterval<T>? = if (writable) wrappedSource else null
		override fun numDimensions(): Int = wrappedSource.numDimensions()

		override fun randomAccess(): RandomAccess<T> = wrappedSource.randomAccess()

		override fun randomAccess(interval: Interval): RandomAccess<T> = wrappedSource.randomAccess(interval)
		override fun min(d: Int): Long = wrappedSource.min(d)
		override fun max(d: Int): Long = wrappedSource.max(d)
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
			viewerImg.wrappedSource = img
			volatileViewerImg.wrappedSource = volatileImg

			viewerImg.writableSource = writableSourceImages?.first
			volatileViewerImg.writableSource = writableSourceImages?.second
		}
	}

	internal fun pushNewImageLayer(writableSourceImages: Pair<RandomAccessibleInterval<UnsignedLongType>, RandomAccessibleInterval<VolatileUnsignedLongType>>? = newBackingImages()) {
		writableSourceImages?.let { (newImg, newVolatileImg) ->


			val currentRA = viewerImg.wrappedSource.extendValue(Label.INVALID)
			val newRA = newImg.extendValue(Label.INVALID)
			val compositeMask = currentRA.convertWith(newRA, UnsignedLongType(Label.INVALID)) { oldVal, newVal, result ->
				val new = newVal.get()
				if (new != Label.INVALID) {
					result.set(new)
				} else result.set(oldVal)
			}.interval(newImg)

			val currentVolatileRA = volatileViewerImg.wrappedSource.extendValue(VolatileUnsignedLongType(Label.INVALID))
			val newVolatileRA = newVolatileImg.extendValue(VolatileUnsignedLongType(Label.INVALID))
			val compositeVolatileMask = currentVolatileRA.convertWith(newVolatileRA, VolatileUnsignedLongType(Label.INVALID)) { original, overlay, composite ->
				var checkOriginal = false
				if (overlay.isValid) {
					val overlayVal = overlay.get().get()
					if (overlayVal != Label.INVALID) {
						composite.get().set(overlayVal)
						composite.isValid = true
					} else checkOriginal = true
				} else checkOriginal = true
				if (checkOriginal) {
					if (original.isValid) {
						composite.set(original)
						composite.isValid = true
					} else composite.isValid = false
				}
				composite.isValid = true
			}.interval(newVolatileImg)

			val wrappedCompositeMask = WrappedRandomAccessibleInterval(compositeMask)
			wrappedCompositeMask.writableSource = WrappedRandomAccessibleInterval(viewerImg.wrappedSource).apply { writableSource = viewerImg.writableSource }

			val wrappedVolatileCompositeMask = WrappedRandomAccessibleInterval(compositeVolatileMask)
			wrappedVolatileCompositeMask.writableSource = WrappedRandomAccessibleInterval(volatileViewerImg.wrappedSource).apply { writableSource = volatileViewerImg.writableSource }

			updateBackingImages(
				wrappedCompositeMask to wrappedVolatileCompositeMask,
				newImg to newVolatileImg
			)
		}
	}

	internal fun popImageLayer(): Boolean {
		var popped = false
		((viewerImg.wrappedSource as? WrappedRandomAccessibleInterval)?.writableSource as? WrappedRandomAccessibleInterval)?.let { prevImg ->
			viewerImg.wrappedSource = prevImg.wrappedSource
			viewerImg.writableSource = prevImg.writableSource
			popped = true
		}
		((volatileViewerImg.wrappedSource as? WrappedRandomAccessibleInterval)?.writableSource as? WrappedRandomAccessibleInterval)?.let { prevImg ->
			volatileViewerImg.wrappedSource = prevImg.wrappedSource
			volatileViewerImg.writableSource = prevImg.writableSource
		}
		return popped
	}


	override fun <C : IntegerType<C>> applyMaskToCanvas(
		canvas: RandomAccessibleInterval<C>,
		acceptAsPainted: Predicate<Long>
	): Set<Long> {

		val extendedViewerImg = Views.extendBorder(viewerImg)
		val viewerImgInSourceOverCanvas = viewerImgInSource.raster().interval(canvas)

		val sourceToMaskTransform = currentMaskToSourceWithDepthTransform.inverse()
		val sourceToMaskTransformAsArray = sourceToMaskTransform.rowPackedCopy

		val sourceToMaskWithDepthTransform = currentMaskToSourceWithDepthTransform.inverse()
		val sourceToMaskWithDepthTransformAsArray = sourceToMaskWithDepthTransform.rowPackedCopy

		val maxDistInMask = (paintDepthFactor * depthScale) * .5
		val minDistInMask = paintDepthFactor * .5

		val zTransformAtCubeCorner: (DoubleArray, Int?) -> Double = { pos, idx ->
			val x = pos[0] + (idx?.let { CUBE_CORNERS[it][0] } ?: 0.0)
			val y = pos[1] + (idx?.let { CUBE_CORNERS[it][1] } ?: 0.0)
			val z = pos[2] + (idx?.let { CUBE_CORNERS[it][2] } ?: 0.0)
			sourceToMaskTransformAsArray.let { transform ->
				transform[8] * x + transform[9] * y + transform[10] * z + transform[11]
			}
		}


		val painted = AtomicBoolean(false)
		val paintedLabelSet = hashSetOf<Long>()

		fun trackPaintedLabel(painted: Long) {
			synchronized(paintedLabelSet) {
				paintedLabelSet += painted
			}
		}

		val paintCanvas: (IntegerType<*>, Long) -> Unit = { position, id ->
			position.setInteger(id)
			painted.set(true)
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
			val cubeCornerDepths = Array<Double?>(CUBE_CORNERS.size) { null }


			chunk.forEachPixel { canvasBundle, viewerValType ->
				canvasBundle.localize(canvasPosition)
				cubeCornerDepths.fill(null)

				var withinMax = false
				for (idx in cubeCornerDepths.indices) {

					cubeCornerDepths[idx] = zTransformAtCubeCorner(canvasPosition, idx).also {
						withinMax = it.absoluteValue <= maxDistInMask
					}

					if (withinMax)
						break
				}

				if (!withinMax) {
					return@forEachPixel
				}

				val paintVal = viewerValType.get()

				if (acceptAsPainted.test(paintVal) && zTransformAtCubeCorner(canvasPosition, null).absoluteValue < minDistInMask)
                    paintCanvas(canvasBundle.get(), paintVal)
				else {
					/* nearest neighbor interval over source */
					for (idx in 0 until 3) {
						realMinMaskPoint[idx] = canvasPosition[idx] + MIN_CORNER_OFFSET[idx]
						realMaxMaskPoint[idx] = canvasPosition[idx] + MAX_CORNER_OFFSET[idx]
					}

					val realIntervalOverSource = FinalRealInterval(realMinMaskPoint, realMaxMaskPoint, false)

					/* iterate over canvas in viewer */
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
		paintera.baseView.orthogonalViews().requestRepaint(sourceToGlobalTransform.estimateBounds(canvas.extendBy(1.0)))
		return paintedLabelSet
	}

	@JvmOverloads
	fun requestRepaint(intervalOverMask: Interval? = null) {
		intervalOverMask?.let {
			val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(intervalOverMask, initialMaskToSourceWithDepthTransform, .5)
			val globalWithDepth = sourceToGlobalTransform.estimateBounds(sourceInterval)
			paintera.baseView.orthogonalViews().requestRepaint(globalWithDepth)
		} ?: paintera.baseView.orthogonalViews().requestRepaint()
	}

	/**
	 * Returns the screen interval in the ViewerMask space, based on the given width and height.
	 * if [currentScreenInterval] then will be based on the location of (0,0) in the
	 * [currentGlobalToMaskTransform], otherwise it will use [initialGlobalToMaskTransform].
	 *
	 * Results may differ, depending on e.g. zoom since Mask creation ( in SI for example).
	 *
	 * @param width The width of the interval. Defaults to the width of the viewer.
	 * @param height The height of the interval. Defaults to the height of the viewer.
	 * @param currentScreenInterval whether the screen interval should map to the current screen,
	 *  or the in initial screen when the mask was created.
	 *
	 * @return The screen interval in ViewerMask space.
	 */
	@JvmOverloads
	fun getScreenInterval(width: Long = viewer.width.toLong(), height: Long = viewer.height.toLong(), currentScreenInterval: Boolean = false): Interval {
		val (x: Long, y: Long) = displayPointToMask(0, 0, currentScreenInterval)
		return Intervals.createMinSize(x, y, 0, width, height, 1)
	}

	fun getInitialGlobalViewerInterval(width: Double, height: Double): RealInterval {
		val zeroGlobal = doubleArrayOf(0.0, 0.0, 0.0).also { initialGlobalToViewerTransform.applyInverse(it, it) }
		val sizeGlobal = doubleArrayOf(width, height, 1.0).also { initialGlobalToViewerTransform.applyInverse(it, it) }
		return FinalRealInterval(zeroGlobal, sizeGlobal)
	}


	companion object {
		private val CELL_DIMS = intArrayOf(64, 64, 1)

		private val MIN_CORNER_OFFSET = doubleArrayOf(-.5, -.5, -.5)
		private val MAX_CORNER_OFFSET = doubleArrayOf(+.5, +.5, +.5)

		private val CUBE_CORNERS = arrayOf(
			MIN_CORNER_OFFSET,
			MAX_CORNER_OFFSET,

			doubleArrayOf(-.5, -.5, +.5),
			doubleArrayOf(+.5, +.5, -.5),

			doubleArrayOf(-.5, +.5, -.5),
			doubleArrayOf(+.5, -.5, +.5),

			doubleArrayOf(-.5, +.5, +.5),
			doubleArrayOf(+.5, -.5, -.5),
		)


		@JvmStatic
		fun ViewerPanelFX.getGlobalViewerInterval(): RealInterval {
			val zeroGlobal = doubleArrayOf(0.0, 0.0, 0.0).also { displayToGlobalCoordinates(it) }
			val sizeGlobal = doubleArrayOf(width, height, 1.0).also { displayToGlobalCoordinates(it) }
			return FinalRealInterval(zeroGlobal, sizeGlobal)
		}

		@JvmStatic
		@JvmOverloads
		fun MaskedSource<*, *>.createViewerMask(
			maskInfo: MaskInfo,
			viewer: ViewerPanelFX,
			paintDepth: Double = 1.0,
			maskSize: Interval? = null,
			defaultValue: Long = Label.INVALID,
			setMask: Boolean = true,
			initialGlobalToViewerTransform: AffineTransform3D? = null
		): ViewerMask {
			val viewerMask = ViewerMask(this, viewer, maskInfo, paintDepthFactor = paintDepth, maskSize = maskSize, defaultValue = defaultValue, globalToViewerTransform = initialGlobalToViewerTransform)
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
