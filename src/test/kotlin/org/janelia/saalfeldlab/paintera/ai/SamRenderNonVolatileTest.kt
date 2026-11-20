package org.janelia.saalfeldlab.paintera.ai

import bdv.cache.SharedQueue
import bdv.util.volatiles.VolatileViews
import javafx.application.Platform
import kotlinx.coroutines.runBlocking
import net.imglib2.cache.Invalidate
import net.imglib2.cache.img.CellLoader
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.type.volatiles.VolatileDoubleType
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.paintera.PainteraTestApp
import org.janelia.saalfeldlab.paintera.PainteraTestApplication
import org.janelia.saalfeldlab.paintera.PainteraTestApplicationExtension
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.renderState
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.MinimalSourceState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.function.Predicate
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

private fun volatileDouble(value: Double) = VolatileDoubleType().apply {
	get().set(value)
	isValid = true
}

private const val CELL_LOAD_MILLIS = 150L
private const val COLOR_TOLERANCE = 8

/* a gradient, away from the converter's black and white ends */
private fun gradient(x: Double, y: Double) = 16.0 + x + y

/** Renders through the SAM path ([ImageRenderer]) with a slow-loading source; every pixel must be loaded, not the volatile placeholder */
@ExtendWith(PainteraTestApplicationExtension::class)
class SamRenderNonVolatileTest {

	private val noOpInvalidate = object : Invalidate<Long> {
		override fun invalidate(key: Long) = Unit
		override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<Long>) = Unit
		override fun invalidateAll(parallelismThreshold: Long) = Unit
	}

	@Test
	fun `SAM render of a slow raw source is fully loaded through the non-volatile primary`(@PainteraTestApp app: PainteraTestApplication) {
		val slowGradientLoader = CellLoader<DoubleType> { cell ->
			Thread.sleep(CELL_LOAD_MILLIS)
			val cursor = cell.localizingCursor()
			while (cursor.hasNext()) {
				cursor.fwd()
				cursor.get().set(gradient(cursor.getDoublePosition(0), cursor.getDoublePosition(1)))
			}
		}
		val rai = ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options().cellDimensions(16, 16, 16).volatileAccesses(true))
			.create(longArrayOf(64, 64, 64), DoubleType(), slowGradientLoader)
		val volatileRai = VolatileViews.wrapAsVolatile<DoubleType, VolatileDoubleType>(rai, SharedQueue(2))
		val dataSource = RandomAccessibleIntervalDataSource<DoubleType, VolatileDoubleType>(
			rai, volatileRai, { AffineTransform3D() }, noOpInvalidate,
			{ NearestNeighborInterpolatorFactory() }, { NearestNeighborInterpolatorFactory() }, "source"
		)
		assertFalse(volatileRai.randomAccess().setPositionAndGet(0, 0, 0).isValid, "the volatile source has not loaded the slow cell yet")
		val converter = ARGBColorConverter.InvertingImp0<VolatileDoubleType>(0.0, 128.0)
		val state = MinimalSourceState<DoubleType, VolatileDoubleType, _, _>(dataSource, converter, CompositeCopy(), dataSource.name)

		val added = CountDownLatch(1)
		Platform.runLater {
			paintera.baseView.addState(state)
			added.countDown()
		}
		added.await()

		val size = 32L
		val renderState = paintera.baseView.orthogonalViews().topLeft.viewer().renderState(
			globalToViewerTransform = AffineTransform3D(),
			size = size to size,
			excludeActiveSource = false
		)
		val primary = renderState.sources.single().spimSource
		assertNotSame<Any>(dataSource, primary)
		assertIs<DoubleType>(primary.getSource(0, 0).getType(), "primary is over the non-volatile type")

		/* the volatile render goes first, while the cells are still loading */
		val viewer = paintera.baseView.orthogonalViews().topLeft.viewer()
		val volatileRenderState = RenderUnitState(AffineTransform3D(), viewer.state.timepoint, viewer.state.sources.toList(), size, size)
		val volatileImage = runBlocking { ImageRenderer.renderBufferedImage(volatileRenderState, doubleArrayOf(1.0), useVolatileIfAvailable = true) }
		val blockingImage = runBlocking { ImageRenderer.renderBufferedImage(renderState, doubleArrayOf(1.0)) }

		val outputDir = File("target/test_tmp/sam_render").also { it.mkdirs() }
		assertTrue(ImageIO.write(volatileImage.onBlack(), "jpg", File(outputDir, "volatile_render.jpg")))
		assertTrue(ImageIO.write(blockingImage.onBlack(), "jpg", File(outputDir, "blocking_render.jpg")))

		/* a loaded pixel is within a half-pixel offset of the gradient, a volatile placeholder is far from it */
		val blockingMismatches = gradientMismatches(blockingImage, converter, size.toInt())
		assertTrue(blockingMismatches.isEmpty(), "blocking pixels not the loaded gradient: ${blockingMismatches.take(10)} of ${blockingMismatches.size}")
		assertTrue(gradientMismatches(volatileImage, converter, size.toInt()).isNotEmpty(), "the volatile render of the slow source is only partly loaded")

		val corner = blockingImage.getRGB(2, 2) and 0xFF
		val farCorner = blockingImage.getRGB(size.toInt() - 3, size.toInt() - 3) and 0xFF
		assertTrue(abs(corner - farCorner) > 20, "the image is a gradient, not one flat value")
	}

	/* jpg has no alpha; unloaded (transparent) pixels show as black */
	private fun BufferedImage.onBlack() = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { opaque ->
		opaque.createGraphics().apply {
			drawImage(this@onBlack, 0, 0, null)
			dispose()
		}
	}

	private fun gradientMismatches(image: BufferedImage, converter: ARGBColorConverter<VolatileDoubleType>, size: Int): List<String> {
		val mismatches = mutableListOf<String>()
		val expected = ARGBType()
		for (y in 2 until size - 2) {
			for (x in 2 until size - 2) {
				converter.convert(volatileDouble(gradient(x.toDouble(), y.toDouble())), expected)
				val actualRgb = image.getRGB(x, y)
				val maxChannelError = listOf(16, 8, 0).maxOf { shift -> abs(((expected.get() shr shift) and 0xFF) - ((actualRgb shr shift) and 0xFF)) }
				if (maxChannelError > COLOR_TOLERANCE)
					mismatches += "($x, $y) error $maxChannelError"
			}
		}
		return mismatches
	}
}
