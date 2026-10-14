package org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace

import bdv.cache.SharedQueue
import bdv.util.Affine3DHelpers
import bdv.util.RandomAccessibleIntervalMipmapSource
import bdv.util.volatiles.VolatileViews
import bdv.viewer.Interpolation
import bdv.viewer.Source
import mpicbg.spim.data.sequence.FinalVoxelDimensions
import bdv.viewer.SourceAndConverter
import bdv.viewer.render.MipmapOrdering.Level
import net.imglib2.Dimensions
import net.imglib2.FinalDimensions
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessible
import net.imglib2.type.mask.Masked
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.Invalidate
import net.imglib2.cache.img.CellLoader
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.cache.volatiles.LoadingStrategy
import net.imglib2.converter.Converter
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.parallel.TaskExecutors
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.realtransform.Translation3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.bdv.fx.viewer.render.SliceMipmapFactory
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlur
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Predicate
import kotlin.math.PI

class FilteredProjectorTest {

	private val frame = FinalInterval(8, 8)
	private val executor = TaskExecutors.fixedThreadPool(2)

	/* a sample's value as it is, and its bits as the color, so a rendered pixel reads back exactly */
	private fun <T : RealType<*>> asIs() = Converter<T, ARGBType> { sample, argb -> argb.set(java.lang.Float.floatToRawIntBits(sample.realFloat)) }

	private fun RandomAccessibleInterval<ARGBType>.value(x: Long, y: Long) = java.lang.Float.intBitsToFloat(getAt(x, y).get()).toDouble()

	/* [target] is the part of a [frameSize] frame to render, drawn from [levels] of [source] best first */
	private fun <T : RealType<*>> projectorOf(
		source: Source<T>,
		globalToScreen: AffineTransform3D,
		filters: List<RenderFilter>,
		target: RandomAccessibleInterval<ARGBType>,
		frameSize: Dimensions,
		levels: List<Int>,
		cacheHints: Boolean = true
	): FilteredProjector<T> {
		/* a source's mipmap hints can leave the cache hints out */
		val renderHints = CacheHints(LoadingStrategy.VOLATILE, 0, false).takeIf { cacheHints }
		val prefetchHints = CacheHints(LoadingStrategy.DONTLOAD, 0, false).takeIf { cacheHints }
		val levelsBestFirst = levels.mapIndexed { priority, level -> Level(level, priority, priority, renderHints, prefetchHints) }
		/* the screen the filters work in starts at the target */
		val screenToTarget = AffineTransform3D().apply { translate(-target.min(0).toDouble(), -target.min(1).toDouble(), 0.0) }
		return FilteredProjector.create(SourceAndConverter(source, asIs()), filters, SliceMipmapFactory(!cacheHints), levelsBestFirst, 0, globalToScreen, screenToTarget, Interpolation.NLINEAR, frameSize, target, executor, false)
	}

	private fun <T : RealType<*>> projector(
		source: Source<T>,
		globalToScreen: AffineTransform3D,
		filters: List<RenderFilter>,
		region: Interval = frame,
		levels: List<Int> = listOf(0)
	): Pair<FilteredProjector<T>, RandomAccessibleInterval<ARGBType>> {
		val target = ArrayImgs.argbs(region.dimension(0), region.dimension(1))
		return projectorOf(source, globalToScreen, filters, Views.translate(target, *region.minAsLongArray()), FinalDimensions(8, 8), levels) to target
	}

	private fun <T : RealType<*>> render(source: Source<T>, globalToScreen: AffineTransform3D, vararg filters: RenderFilter, region: Interval = frame): RandomAccessibleInterval<ARGBType> {
		val (projector, target) = projector(source, globalToScreen, filters.toList(), region)
		assertTrue(projector.map(false))
		assertTrue(projector.isValid)
		return target
	}

	/* value = the s0 z index; level 1 holds every other section, so its value is twice its own z index */
	private val s0ToWorld = AffineTransform3D().apply { scale(1.0, 1.0, 4.0) }
	private val zRamp: Source<FloatType> = RandomAccessibleIntervalMipmapSource(
		arrayOf(ramp(8, 8, 20) { _, z -> z.toFloat() }, ramp(4, 4, 10) { _, z -> 2f * z }),
		FloatType(),
		arrayOf(s0ToWorld, AffineTransform3D().apply { scale(2.0, 2.0, 8.0) }),
		FinalVoxelDimensions("px", 1.0, 1.0, 4.0),
		"z ramp",
		false
	)

	/* value = x + 100 z, so a slice read at the wrong x shows */
	private val xRamp: Source<FloatType> = RandomAccessibleIntervalMipmapSource(
		arrayOf(ramp(16, 16, 4) { x, z -> x + 100f * z }), FloatType(), arrayOf(AffineTransform3D()), FinalVoxelDimensions("px", 1.0, 1.0, 1.0), "x ramp", false
	)
	private val shiftedAndZoomed = AffineTransform3D().apply {
		scale(1.5)
		translate(-7.3, -4.1, 0.0)
	}

	private fun ramp(width: Long, height: Long, depth: Long, value: (x: Int, z: Int) -> Float) = ArrayImgs.floats(width, height, depth).also { img ->
		val cursor = img.localizingCursor()
		while (cursor.hasNext()) {
			cursor.fwd()
			cursor.get().set(value(cursor.getIntPosition(0), cursor.getIntPosition(2)))
		}
	}

	/* [viewer] with s0 section [section] of the z ramp on the slice */
	private fun atSection(viewer: AffineTransform3D, section: Int) = viewer.copy().apply { translate(0.0, 0.0, -viewer.get(2, 2) * 4.0 * section) }

	private val zoomedIn = AffineTransform3D().apply { scale(2.0) }

	private val oneUp = RenderFilter { input, _ -> ScreenImageProvider { screenInterval, sliceToScreen -> input.image(screenInterval, sliceToScreen.oneUp()) } }

	private fun AffineTransform3D.oneUp() = copy().preConcatenate(Translation3D(0.0, 0.0, 1.0))

	/* a slice's value is its own z */
	private val zTag = RenderFilter { _, _ -> volume { _, _, z -> z } }

	@Test
	fun `without filters it renders the source`() {
		assertEquals(5.0, render(zRamp, atSection(zoomedIn, 5)).value(1, 1), 1e-6)
	}

	@Test
	fun `levels without cache hints render, prefetching included`() {
		val target = ArrayImgs.argbs(frame.dimension(0), frame.dimension(1))
		val projector = projectorOf(zRamp, atSection(zoomedIn, 5), emptyList(), target, FinalDimensions(8, 8), listOf(0), cacheHints = false)
		assertTrue(projector.map(false))
		assertEquals(5.0, target.value(1, 1), 1e-6)
	}

	@Test
	fun `one z unit is one voxel of the displayed level, whatever the zoom or rotation`() {
		/* zoomed in, s0 is displayed: one unit is one section */
		assertEquals(6.0, render(zRamp, atSection(zoomedIn, 5), oneUp).value(1, 1), 1e-6)

		/* zoomed out so level 1 is displayed: one unit is two sections, even when rendering level 0 */
		val zoomedOut = AffineTransform3D().apply { scale(0.5) }
		assertEquals(7.0, render(zRamp, atSection(zoomedOut, 5), oneUp).value(1, 1), 1e-6)

		/* looking along x, one unit is one x voxel; the ramp is constant along x, so the value is the slice's */
		val alongX = AffineTransform3D().apply {
			rotate(1, PI / 2)
			scale(2.0)
			translate(-2.0 * 4.0, 0.0, -2.0 * 4.0)
		}
		assertEquals(render(zRamp, alongX).value(1, 1), render(zRamp, alongX, oneUp).value(1, 1), 1e-6)
	}

	@Test
	fun `filters compose in order, each reading the one before it`() {
		assertEquals(7.0, render(zRamp, atSection(zoomedIn, 5), oneUp, oneUp).value(1, 1), 1e-6)
		assertEquals(2.0, render(zRamp, atSection(zoomedIn, 5), zTag, oneUp, oneUp).value(1, 1), 1e-6)
	}

	@Test
	fun `a slice off the viewer plane keeps the view's x, y placement`() {
		val rendered = render(xRamp, shiftedAndZoomed, oneUp)
		for (x in 2L..5L)
			assertEquals((x + 7.3) / 1.5 + 100, rendered.value(x, 3), 1e-4, "pixel $x")
	}

	@Test
	fun `a partial repaint and a filter's wider request are placed in the frame`() {
		/* each pixel takes the value of its left neighbor, which for the first column is outside the region */
		val fromLeft = RenderFilter { input, _ ->
			ScreenImageProvider { screenInterval, sliceToScreen ->
				val wider = FinalInterval(longArrayOf(screenInterval.min(0) - 1, screenInterval.min(1)), longArrayOf(screenInterval.max(0), screenInterval.max(1)))
				val slice = input.image(wider, sliceToScreen)
				ScreenImage(image(screenInterval) { x, y -> slice.value(x - 1, y) }, image(screenInterval) { x, y -> slice.covered(x - 1, y) }, slice.complete)
			}
		}
		val region = FinalInterval(longArrayOf(3, 2), longArrayOf(6, 5))
		val rendered = render(xRamp, shiftedAndZoomed, fromLeft, region = region)
		for (x in 0L..3L)
			assertEquals((x + 3 - 1 + 7.3) / 1.5, rendered.value(x, 1), 1e-4, "pixel $x of the region")
	}

	@Test
	fun `coverage is 0 past the data and where a sample is NaN`() {
		val withNaN = ramp(8, 8, 4) { x, _ -> if (x == 5) Float.NaN else 1f }
		val source: Source<FloatType> = RandomAccessibleIntervalMipmapSource(arrayOf(withNaN), FloatType(), arrayOf(AffineTransform3D()), FinalVoxelDimensions("px", 1.0, 1.0, 1.0), "nan", false)
		var seen: ScreenImage? = null
		val capture = RenderFilter { input, _ -> ScreenImageProvider { screenInterval, sliceToScreen -> input.image(screenInterval, sliceToScreen).also { seen = it } } }
		/* frame column 3 is the NaN column, and the last two are past the data */
		render(source, AffineTransform3D().apply { translate(-2.0, 0.0, -1.0) }, capture)
		val slice = seen!!
		assertEquals(1.0, slice.covered(0, 4), 1e-6)
		assertEquals(1.0, slice.value(0, 4), 1e-6)
		assertEquals(0.0, slice.covered(3, 4), 1e-6, "the NaN column")
		assertEquals(0.0, slice.value(3, 4), 1e-6)
		assertEquals(0.0, slice.covered(7, 4), 1e-6, "past the data")
	}

	/* value = z of the finest level, in cells that load in the background, as paintera's sources are */
	private val queue = SharedQueue(2, 2)

	private fun loadingLevel(level: Int): Pair<RandomAccessibleInterval<UnsignedByteType>, RandomAccessibleInterval<VolatileUnsignedByteType>> {
		val size = 64L shr level
		val loader = CellLoader<UnsignedByteType> { cell ->
			Thread.sleep(2)
			val cursor = cell.localizingCursor()
			while (cursor.hasNext()) {
				cursor.fwd()
				cursor.get().set((cursor.getLongPosition(2) shl level).toInt())
			}
		}
		val data = ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options().cellDimensions(16, 16, 16)).create(longArrayOf(size, size, size), UnsignedByteType(), loader)
		return data to VolatileViews.wrapAsVolatile<UnsignedByteType, VolatileUnsignedByteType>(data, queue, CacheHints(LoadingStrategy.VOLATILE, 0, false))
	}

	@Test
	fun `a source that loads in the background converges on the filtered image`() {
		val levels = listOf(loadingLevel(0), loadingLevel(1))
		val loading: Source<VolatileUnsignedByteType> = RandomAccessibleIntervalDataSource(
			arrayOf(levels[0].first, levels[1].first),
			arrayOf(levels[0].second, levels[1].second),
			{ arrayOf(AffineTransform3D(), AffineTransform3D().apply { scale(2.0) }) },
			object : Invalidate<Long> {
				override fun invalidate(key: Long) = Unit
				override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<Long>) = Unit
				override fun invalidateAll(parallelismThreshold: Long) = Unit
			},
			{ interpolation -> if (interpolation == Interpolation.NLINEAR) NLinearInterpolatorFactory() else NearestNeighborInterpolatorFactory() },
			{ interpolation -> if (interpolation == Interpolation.NLINEAR) NLinearInterpolatorFactory() else NearestNeighborInterpolatorFactory() },
			UnsignedByteType(),
			VolatileUnsignedByteType(),
			"ramp"
		)
		val region = FinalInterval(40, 40)
		val globalToScreen = AffineTransform3D().apply { translate(0.0, 0.0, -32.0) }
		val target = ArrayImgs.argbs(40, 40)
		val filters = listOf<RenderFilter>(DepthBlur(2, 1.0, DepthBlur.Blend.MEAN))

		/* a new projector each pass, as the viewer makes while it refines */
		var passes = 0
		var valid = false
		while (!valid && passes++ < 200) {
			val projector = projectorOf(loading, globalToScreen, filters, Views.translate(target, *region.minAsLongArray()), FinalDimensions(40, 40), listOf(0, 1))
			assertTrue(projector.map(false))
			valid = projector.isValid
			if (!valid)
				Thread.sleep(10)
		}
		assertTrue(valid, "did not converge")
		assertTrue(passes > 1, "the first pass cannot have had every cell")
		Intervals.positions(region).forEach { assertEquals(32.0, target.value(it.getLongPosition(0), it.getLongPosition(1)), 1e-4) }
	}

	@Test
	fun `a canceled pass reports it`() {
		val (projector, _) = projector(zRamp, atSection(zoomedIn, 5), listOf(RenderFilter { input, _ ->
			ScreenImageProvider { screenInterval, sliceToScreen -> input.image(screenInterval, sliceToScreen).also { projectorToCancel!!.cancel() }.let { input.image(screenInterval, sliceToScreen.oneUp()) } }
		}))
		projectorToCancel = projector
		assertFalse(projector.map(false))
		assertFalse(projector.isValid)
	}

	@Test
	fun `a filter that throws fails the pass`() {
		val failure = IllegalStateException("filter failed")
		val (projector, _) = projector(zRamp, atSection(zoomedIn, 5), listOf(RenderFilter { _, _ -> ScreenImageProvider { _, _ -> throw failure } }))
		assertSame(failure, assertThrows(IllegalStateException::class.java) { projector.map(false) })
		assertFalse(projector.isValid)
	}

	private var projectorToCancel: FilteredProjector<*>? = null
}
