package org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace

import bdv.util.Affine3DHelpers
import bdv.util.MipmapTransforms
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.render.MipmapOrdering.Level
import bdv.viewer.render.VolatileProjector
import com.sun.javafx.image.PixelUtils
import net.imglib2.Dimensions
import net.imglib2.Interval
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccess
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealRandomAccessible
import net.imglib2.Volatile
import net.imglib2.cache.iotiming.CacheIoTiming
import net.imglib2.converter.Converter
import net.imglib2.img.array.ArrayImgs
import net.imglib2.loops.LoopBuilder
import net.imglib2.parallel.Parallelization
import net.imglib2.parallel.TaskExecutor
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.Translation3D
import net.imglib2.type.mask.Masked
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.type.operators.SetZero
import net.imglib2.util.Intervals
import net.imglib2.util.StopWatch
import net.imglib2.view.composite.CompositeIntervalView
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.bdv.fx.viewer.project.VolatileHierarchyProjector
import org.janelia.saalfeldlab.bdv.fx.viewer.render.SliceMipmapFactory
import org.janelia.saalfeldlab.util.addDimension
import org.janelia.saalfeldlab.util.convert
import org.janelia.saalfeldlab.util.hyperSlice
import org.janelia.saalfeldlab.util.translate
import java.util.concurrent.CancellationException
import java.util.function.BiConsumer

/**
 * Renders a source through its screen-space filters. Each slice the filters ask for is projected from the best loaded
 * mipmap level per pixel, and the last filter's slice on the viewer plane is colored into [target], which is the part of
 * the [view] that is rendered.
 */
class FilteredProjector<T>(
	private val perLevelScreenImageProvider: PerLevelScreenImageProvider<Masked<T>>,
	private val converter: Converter<in T, ARGBType>,
	private val filters: List<RenderFilter>,
	private val view: ScreenView,
	private val target: RandomAccessibleInterval<ARGBType>,
	private val taskExecutor: TaskExecutor,
	private val preMultiply: Boolean
) : VolatileProjector {

	fun interface PerLevelScreenImageProvider<S> {

		/**
		 * @param screenInterval the part of the projector's target to be at zero min
		 * @param sliceToScreen where each pixel of the slice samples the screen, in render target pixels; the identity is the current viewer slice
		 * @return the screen image of every mipmap level to render, best first
		 */
		fun at(screenInterval: Interval, sliceToScreen: AffineTransform3D): List<RandomAccessible<S>>
	}

	private fun filtered() = filters.fold(ScreenImageProvider(::screenImage)) { input, filter -> filter.apply(input, view) }

	@kotlin.concurrent.Volatile
	private var canceled = false

	@kotlin.concurrent.Volatile
	private var valid = false

	@kotlin.concurrent.Volatile
	private var projecting: VolatileProjector? = null

	private var lastFrameRenderNanoTime = -1L

	override fun map(clearUntouchedTargetPixels: Boolean): Boolean {
		canceled = false
		valid = false
		val stopWatch = StopWatch.createAndStart()
		val ioStatistics = CacheIoTiming.getIoStatistics()
		val ioNanosAtStart = ioStatistics.cumulativeIoNanoTime
		var complete = false
		try {
			Parallelization.runWithExecutor(taskExecutor) {
				val slice = filtered().image(target)
				color(slice)
				complete = slice.complete
			}
		} catch (_: CancellationException) {
			return false
		}
		lastFrameRenderNanoTime = stopWatch.nanoTime() - (ioStatistics.cumulativeIoNanoTime - ioNanosAtStart) / taskExecutor.parallelism
		valid = complete
		return !canceled
	}

	override fun cancel() {
		canceled = true
		projecting?.cancel()
	}

	override fun getLastFrameRenderNanoTime() = lastFrameRenderNanoTime

	override fun isValid() = valid

	private fun screenImage(screenInterval: Interval, sliceToScreen: AffineTransform3D): ScreenImage {
		val onTarget = Intervals.translate(screenInterval, -target.min(0), -target.min(1))
		val samples = perLevelScreenImageProvider.at(onTarget, view.toScreenPixels(sliceToScreen)).map { level ->
			level.convert({ VolatileMasked<T>() }) { masked, sample -> sample.masked = masked }
		}

		/* the img and the mask */
		val imgAndMask = ArrayImgs.floats(screenInterval.dimension(0), screenInterval.dimension(1), 2)
		val write = Converter<VolatileMasked<T>, RealCompositeSetZero<FloatType>> { sample, pixel ->
			val masked = sample.masked!!
			val value = (masked.value() as RealType<*>).realFloat
			pixel.get(0).set(if (value.isNaN()) 0f else value)
			pixel.get(1).set(if (value.isNaN()) 0f else masked.mask().toFloat())
		}

		val pixels = CompositeIntervalView(imgAndMask) { RealCompositeSetZero(it, 2) }
		val projector = VolatileHierarchyProjector(samples, write, pixels.addDimension(0, 0), taskExecutor)
		projecting = projector
		if (canceled || !projector.map(false))
			throw CancellationException()
		val layer = { index: Long -> imgAndMask.hyperSlice(2, index).translate(screenInterval.min(0), screenInterval.min(1)) }
		return ScreenImage(layer(0), layer(1), projector.isValid)
	}

	@Suppress("UNCHECKED_CAST")
	private fun color(screenImage: ScreenImage) {
		/* the converter reads its sample as a real number only, so a float colors as the sample would */
		val toARGB = converter as Converter<FloatType, ARGBType>
		LoopBuilder.setImages(screenImage.rasterImg, target).multiThreaded().forEachPixel(BiConsumer { pixel, argb ->
			toARGB.convert(pixel, argb)
			if (preMultiply)
				argb.set(PixelUtils.NonPretoPre(argb.get()))
		})
	}

	private class VolatileMasked<T> : Volatile<Masked<T>?>(null) {

		var masked: Masked<T>? = null

		override fun get() = masked

		override fun isValid() = (masked!!.value() as? Volatile<*>)?.isValid ?: true
	}

	private class RealCompositeSetZero<T>(access: RandomAccess<T>, length: Int) : RealComposite<T>(access, length), SetZero where T : RealType<T>, T : SetZero {

		override fun setZero() {
			for (i in 0 until length)
				get(i.toLong()).setZero()
		}
	}

	companion object {

		/** voxels of the level the viewer displays, to screen pixels */
		@JvmStatic
		fun bestLevelToScreen(source: Source<*>, timepoint: Int, globalToScreen: AffineTransform3D): AffineTransform3D {
			val bestLevel = MipmapTransforms.getBestMipMapLevel(globalToScreen, source, timepoint)
			val bestLevelToGlobal = AffineTransform3D().also { source.getSourceTransform(timepoint, bestLevel, it) }
			return globalToScreen.copy().concatenate(bestLevelToGlobal)
		}

		/**
		 * [sourceAndConverter] drawn through [filters] into [target], the part of the [frame] to render at [screenScaleTransform]
		 *
		 * @param levels the mipmap levels to project each slice from
		 */
		@JvmStatic
		@JvmName("create")
		@Suppress("UNCHECKED_CAST")
		internal fun <T> create(
			sourceAndConverter: SourceAndConverter<T>,
			filters: List<RenderFilter>,
			sliceMipmapFactory: SliceMipmapFactory,
			levels: List<Level>,
			timepoint: Int,
			globalToViewer: AffineTransform3D,
			screenScaleTransform: AffineTransform3D,
			interpolation: Interpolation,
			frame: Dimensions,
			target: RandomAccessibleInterval<ARGBType>,
			taskExecutor: TaskExecutor,
			preMultiply: Boolean
		): FilteredProjector<T> {
			val source = sourceAndConverter.spimSource
			val globalToScreen = globalToViewer.copy()
			globalToScreen.preConcatenate(screenScaleTransform)
			val bestLevelToScreen = bestLevelToScreen(source, timepoint, globalToScreen)
			val view = ScreenView.of(frame, bestLevelToScreen, Affine3DHelpers.extractScale(globalToScreen, 0))
			val slices = PerLevelScreenImageProvider<Masked<T>> { screenInterval, sliceToScreen ->
				val screenToSlice = sliceToScreen.inverse()
				val viewerToSlice = screenScaleTransform.copy()
				viewerToSlice.preConcatenate(screenToSlice)
				val screenToZeroMinSlice = Translation3D(-screenInterval.min(0).toDouble(), -screenInterval.min(1).toDouble(), 0.0)
				viewerToSlice.preConcatenate(screenToZeroMinSlice)
				sliceMipmapFactory.create(source, timepoint, globalToViewer, viewerToSlice, levels, screenInterval, interpolation) { level ->
					source.getInterpolatedMaskedSource(timepoint, level, interpolation) as RealRandomAccessible<Masked<T>>
				}
			}
			return FilteredProjector(slices, sourceAndConverter.converter, filters, view, target, taskExecutor, preMultiply)
		}
	}
}
