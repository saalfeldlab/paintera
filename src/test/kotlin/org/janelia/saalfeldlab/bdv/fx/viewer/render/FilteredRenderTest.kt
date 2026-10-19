package org.janelia.saalfeldlab.bdv.fx.viewer.render

import bdv.cache.CacheControl
import bdv.util.RandomAccessibleIntervalMipmapSource
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.TransformListener
import bdv.viewer.render.AccumulateProjectorARGB
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import mpicbg.spim.data.sequence.FinalVoxelDimensions
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.img.array.ArrayImg
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.basictypeaccess.array.IntArray
import net.imglib2.parallel.TaskExecutors
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.FilteredSourceAndConverter
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilter
import net.imglib2.realtransform.Translation3D
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImageProvider
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.ToIntFunction
import kotlin.math.abs

private typealias Image = ArrayImg<ARGBType, IntArray>

/** a filtered source through the renderer itself: the levels, the frame and the repainted region are the renderer's */
class FilteredRenderTest {

	private class Display(private val size: Int) : TransformAwareRenderTargetGeneric<Image> {

		var image: Image? = null

		override fun setBufferedImageAndTransform(img: Image, transform: AffineTransform3D): Image? = image.also { image = img }
		override fun setBufferedImage(bufferedImage: Image): Image? = image.also { image = bufferedImage }
		override fun getWidth() = size
		override fun getHeight() = size
		override fun addTransformListener(listener: TransformListener<AffineTransform3D>) = Unit
		override fun addTransformListener(listener: TransformListener<AffineTransform3D>, index: Int) = Unit
		override fun removeTransformListener(listener: TransformListener<AffineTransform3D>) = Unit
	}

	private val display = Display(16)

	private val renderer: MultiResolutionRendererGeneric<Image> = CompletableFuture<MultiResolutionRendererGeneric<Image>>().also { created ->
		/* the renderer starts an animation timer */
		Platform.runLater {
			val images = object : MultiResolutionRendererGeneric.ImageGenerator<Image> {
				override fun create(width: Int, height: Int): Image = ArrayImgs.argbs(width.toLong(), height.toLong())
				override fun create(width: Int, height: Int, other: Image): Image = create(width, height)
			}
			created.complete(
				MultiResolutionRendererGeneric(
					display, PainterThreadFx { }, doubleArrayOf(1.0), 30_000_000L, false, TaskExecutors.fixedThreadPool(2), true,
					AccumulateProjectorARGB.factory, CacheControl.Dummy(), Function { it }, images,
					ToIntFunction { it.dimension(0).toInt() }, ToIntFunction { it.dimension(1).toInt() }
				)
			)
		}
	}.get()

	@AfterEach
	fun stopTimer() = renderer.animation.stop()

	/* value = x + 2 y + 10 z */
	private val source: Source<FloatType> = RandomAccessibleIntervalMipmapSource(
		arrayOf(ArrayImgs.floats(32, 32, 8).also { img ->
			val cursor = img.localizingCursor()
			while (cursor.hasNext()) {
				cursor.fwd()
				cursor.get().set(cursor.getIntPosition(0) + 2f * cursor.getIntPosition(1) + 10f * cursor.getIntPosition(2))
			}
		}),
		FloatType(), arrayOf(AffineTransform3D()), FinalVoxelDimensions("px", 1.0, 1.0, 1.0), "ramp", false
	)
	private val converter = ARGBColorConverter.InvertingImp0<FloatType>(0.0, 255.0)
	private val filters = SimpleObjectProperty<List<RenderFilter>>(emptyList())
	private val filtered = FilteredSourceAndConverter(source, converter, filters)

	private val unchanged = RenderFilter { input, _ -> input }
	private val oneUp = RenderFilter { input, _ -> ScreenImageProvider { screenInterval, sliceToScreen -> input.image(screenInterval, sliceToScreen.copy().preConcatenate(Translation3D(0.0, 0.0, 1.0))) } }

	private val frame = FinalInterval(16, 16)

	/* paint [interval] with section [section] on the viewer plane, and return the display's pixels */
	private fun paint(sourceAndConverter: SourceAndConverter<*>, section: Int, interval: Interval = frame): kotlin.IntArray {
		renderer.requestRepaint(interval)
		val viewer = AffineTransform3D().apply { translate(0.0, 0.0, -section.toDouble()) }
		assertTrue(renderer.paint(listOf(sourceAndConverter), 0, viewer) { Interpolation.NLINEAR }.paintedAtFullResolution())
		return display.image!!.update(null).currentStorageArray.copyOf()
	}

	private fun assertSamePixels(expected: kotlin.IntArray, actual: kotlin.IntArray, pixels: Interval = frame) {
		Intervals.positions(pixels).forEach { position ->
			val index = position.getIntPosition(0) + 16 * position.getIntPosition(1)
			/* normalizing through a float can move a color component by one */
			for (component in listOf(ARGBType::alpha, ARGBType::red, ARGBType::green, ARGBType::blue))
				assertTrue(abs(component(expected[index]) - component(actual[index])) <= 1, "pixel $position: ${ARGBType(expected[index])}, ${ARGBType(actual[index])}")
		}
	}

	@Test
	fun `a filtered source renders as the source the filters describe`() {
		val plain = SourceAndConverter(source, converter)
		val sectionThree = paint(plain, 3)
		val sectionFour = paint(plain, 4)
		assertTrue(sectionThree[5 + 16 * 5] != sectionFour[5 + 16 * 5])

		assertSamePixels(sectionThree, paint(filtered, 3))
		filters.set(listOf(unchanged))
		assertSamePixels(sectionThree, paint(filtered, 3))
		filters.set(listOf(oneUp))
		assertSamePixels(sectionFour, paint(filtered, 3))
	}

	@Test
	fun `a partial repaint filters its own part of the frame`() {
		val plain = SourceAndConverter(source, converter)
		val sectionFour = paint(plain, 4)
		val sectionThree = paint(plain, 3)

		filters.set(listOf(oneUp))
		val repainted = FinalInterval(longArrayOf(5, 6), longArrayOf(9, 10))
		val partly = paint(filtered, 3, repainted)
		assertSamePixels(sectionFour, partly, repainted)
		/* past the pixel of padding the renderer adds, the frame is as it was */
		assertSamePixels(sectionThree, partly, FinalInterval(longArrayOf(0, 0), longArrayOf(15, 4)))
		assertSamePixels(sectionThree, partly, FinalInterval(longArrayOf(11, 0), longArrayOf(15, 15)))
	}

	companion object {

		@JvmStatic
		@BeforeAll
		fun startToolkit() {
			try {
				Platform.startup { }
			} catch (_: IllegalStateException) {
				/* already running */
			}
		}
	}
}
