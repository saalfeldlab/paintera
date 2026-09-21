package org.janelia.saalfeldlab.paintera.data.mask

import bdv.cache.SharedQueue
import bdv.viewer.Interpolation
import net.imglib2.FinalInterval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.CellLoader
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.cache.img.SingleCellArrayImg
import net.imglib2.converter.Converters
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import gnu.trove.map.TLongObjectMap
import javafx.beans.property.SimpleDoubleProperty
import org.janelia.saalfeldlab.paintera.control.actions.paint.replaceLabelMask
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.label.RaiBackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataType
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.janelia.saalfeldlab.util.intersectOrNull
import org.janelia.saalfeldlab.util.pad
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.absolutePathString

/**
 * A virtual crop narrows what is painted and committed, not where the canvas lives: the canvas spans the dataset,
 * paint reaches every voxel of the crop, and a block that straddles the crop edge keeps its outside voxels. Over every
 * 3D label layout, sharded zarr3 included
 */
class VirtualCropWriteTest {

	companion object {
		@JvmStatic
		@BeforeAll
		fun registerLookupAdapter() = TestData.registerLabelBlockLookupAdapter()

		private val labelCases = TestData.allCases.filter {
			it.dataType == DataType.UINT64 && it.numDimensions == 3 && it.metadata == TestData.Metadata.NONE
		}

		/* 100³ with 50³ blocks; sharded zarr3 has 10³ chunks inside them */
		@JvmStatic
		val cropCases = labelCases.filter { it.scalePyramid == TestData.ScalePyramid.Single }

		/* s0 plus one level downsampled by 2 */
		@JvmStatic
		val multiscaleCropCases = labelCases.filter { it.scalePyramid == TestData.ScalePyramid.Multi }
	}

	private val queue = SharedQueue(1)
	private val executor = Executors.newFixedThreadPool(2)

	private val background = 1L
	private val paintedLabel = 5L

	/* [33, 76]: block 0 (0..49) straddles the low edge, block 1 (50..99) the high edge, and neither edge is chunk aligned */
	private val crop = FinalInterval(longArrayOf(33, 33, 33), longArrayOf(76, 76, 76))

	private class Fixture(val dimensions: LongArray, val masked: MaskedSource<UnsignedLongType, VolatileUnsignedLongType>, val commit: () -> RandomAccessibleInterval<UnsignedLongType>)

	private fun croppedMaskedSource(testCase: TestCase, tmp: Path): Fixture {
		val writer = TestData.newWriter(testCase, tmp)
		val dimensions = TestData.defaultDimensions(testCase)
		val dataset = TestData.createRaw(writer, testCase, "label", dimensions)
		val s0 = if (testCase.scalePyramid == TestData.ScalePyramid.Multi) "$dataset/s0" else dataset
		val filled = ArrayImgs.unsignedLongs(*dimensions).also { img -> img.forEach { it.set(background) } }
		N5Utils.saveBlock(filled, writer, s0, writer.getDatasetAttributes(s0))

		val metadataState = createMetadataState(N5ContainerState(writer), dataset)!!.also {
			it.isLabel = true
			it.xyzView.setCropInterval(crop)
		}
		val dataSource = N5DataSource<UnsignedLongType, VolatileUnsignedLongType>(metadataState, dataset, queue, 0)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		@Suppress("UNCHECKED_CAST")
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
				as MaskedSource<UnsignedLongType, VolatileUnsignedLongType>
		return Fixture(dimensions, masked) {
			CommitCanvasN5(metadataState).persistCanvas(masked.canvas, masked.affectedBlocks)
			N5Utils.open(writer, s0)
		}
	}

	/* a non-tracking mask over the whole dataset, like the brush's ViewerMask before it is bounded */
	private fun invalidFilledStore(dimensions: LongArray) = DiskCachedCellImgFactory(
		UnsignedLongType(),
		DiskCachedCellImgOptions.options().cellDimensions(50, 50, 50)
	).create(dimensions, CellLoader { img: SingleCellArrayImg<UnsignedLongType, *> -> img.forEach { it.set(Label.INVALID) } })

	private fun Fixture.paint(vararg regions: FinalInterval, level: Int = 0) {
		val store = invalidFilledStore(masked.getGrid(level).imgDimensions)
		@Suppress("UNCHECKED_CAST")
		val volatileView = Converters.convert(
			store as RandomAccessibleInterval<UnsignedLongType>,
			{ source, target -> target.get().set(source); target.isValid = true },
			VolatileUnsignedLongType()
		)
		val mask = SourceMask(MaskInfo(0, level), store, volatileView, null, null, null)
		masked.setMask(mask, MaskedSource.VALID_LABEL_CHECK)
		regions.forEach { region -> Views.interval(store, region).forEach { it.set(paintedLabel) } }
		masked.applyMask(mask, Intervals.union(regions[0], regions.last()), MaskedSource.VALID_LABEL_CHECK)
		var waited = 0
		while (masked.isMaskInUseBinding.get() && waited < 20_000) {
			Thread.sleep(20); waited += 20
		}
	}

	@Suppress("UNCHECKED_CAST")
	private val MaskedSource<UnsignedLongType, *>.canvas: CachedCellImg<UnsignedLongType, *>
		get() {
			val field = MaskedSource::class.java.getDeclaredField("dataCanvases").apply { isAccessible = true }
			return (field.get(this) as Array<Any?>)[0] as CachedCellImg<UnsignedLongType, *>
		}

	private val Fixture.canvas
		get() = masked.canvas

	private fun RandomAccessibleInterval<UnsignedLongType>.at(x: Long, y: Long, z: Long) = randomAccess().setPositionAndGet(x, y, z).get()

	/* label blocks are intersected with the crop one by one, then padded; never merged or kept whole for touching it */
	@Test
	fun `label blocks intersect the crop then pad`() {
		val block0 = FinalInterval(longArrayOf(0, 0, 0), longArrayOf(49, 49, 49))
		val block1 = FinalInterval(longArrayOf(50, 50, 50), longArrayOf(99, 99, 99))
		val outside = FinalInterval(longArrayOf(0, 0, 50), longArrayOf(29, 29, 99))

		val intersected = listOf(block0, block1, outside).mapNotNull { it.intersectOrNull(crop) }
		assertEquals(2, intersected.size, "the block off the crop is dropped, the others stay separate")
		assertTrue(intersected.any { Intervals.equals(it, FinalInterval(longArrayOf(33, 33, 33), longArrayOf(49, 49, 49))) }, "block 0 keeps its inside part: $intersected")
		assertTrue(intersected.any { Intervals.equals(it, FinalInterval(longArrayOf(50, 50, 50), longArrayOf(76, 76, 76))) }, "block 1 keeps its inside part: $intersected")

		val padded = listOf(block0, block1).mapNotNull { it.intersectOrNull(crop)?.pad(5, 5, 5) }
		assertTrue(padded.any { Intervals.equals(it, FinalInterval(longArrayOf(28, 28, 28), longArrayOf(54, 54, 54))) }, "the inside part grows by the padding on every side: $padded")
		assertTrue(padded.any { Intervals.equals(it, FinalInterval(longArrayOf(45, 45, 45), longArrayOf(81, 81, 81))) }, "the inside part grows by the padding on every side: $padded")
	}

	@ParameterizedTest
	@FieldSource("cropCases")
	fun `the replace label mask is positioned like the canvas`(testCase: TestCase, @TempDir tmp: Path) {
		val fixture = croppedMaskedSource(testCase, tmp)
		val replaced = fixture.masked.replaceLabelMask(paintedLabel, level = 0, background)
		assertArrayEquals(fixture.dimensions, Intervals.dimensionsAsLongArray(replaced), "the mask is the dataset's size for $testCase")
		assertEquals(paintedLabel, replaced.at(72, 72, 72), "the background past crop.min from the origin is replaced for $testCase")
	}

	/* an in-memory label source; the backend applies the crop itself, with no metadata state behind it */
	@Test
	fun `a cropped RandomAccessibleInterval backend presents the crop over a dataset-sized canvas`(@TempDir tmp: Path) {
		val dimensions = longArrayOf(100, 100, 100)
		val filled = ArrayImgs.unsignedLongs(*dimensions).also { img -> img.forEach { it.set(background) } }
		val backend = RaiBackendLabel<UnsignedLongType, VolatileUnsignedLongType>("rai", filled, doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(0.0, 0.0, 0.0), paintedLabel).apply {
			xyzView.setCropInterval(crop)
		}
		val dataSource = backend.createSource(queue, 0, "rai")
		val noPersist = object : PersistCanvas {
			override fun persistCanvas(canvas: CachedCellImg<UnsignedLongType, *>, blockIds: LongArray) = emptyList<TLongObjectMap<PersistCanvas.BlockDiff>>()
			override fun getProgressProperty() = SimpleDoubleProperty()
		}
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		@Suppress("UNCHECKED_CAST")
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, noPersist, executor) as MaskedSource<UnsignedLongType, VolatileUnsignedLongType>
		val fixture = Fixture(dimensions, masked) { error("nothing to commit to") }

		assertArrayEquals(crop.minAsLongArray(), dataSource.getDataSource(0, 0).minAsLongArray(), "the presented source is the crop")
		assertArrayEquals(crop.maxAsLongArray(), dataSource.getSource(0, 0).maxAsLongArray(), "the presented volatile source is the crop")
		assertArrayEquals(dimensions, dataSource.getGrid(0).imgDimensions, "the grid is uncropped")
		assertArrayEquals(dimensions, Intervals.dimensionsAsLongArray(fixture.canvas), "the canvas is the dataset's size")

		fixture.paint(FinalInterval(longArrayOf(72, 72, 72), longArrayOf(82, 82, 82)))
		assertEquals(paintedLabel, fixture.canvas.at(75, 75, 75), "inside the high edge is painted")
		assertEquals(Label.INVALID, fixture.canvas.at(80, 80, 80), "outside the high edge is dropped")
	}

	/* the crop rounds outward at s1, so voxel 16 covers s0 32..33 while the crop starts at 33 */
	@ParameterizedTest
	@FieldSource("multiscaleCropCases")
	fun `paint at a coarse level writes and shows only the part inside the s0 crop`(testCase: TestCase, @TempDir tmp: Path) {
		val fixture = croppedMaskedSource(testCase, tmp)
		assertEquals(16, fixture.masked.getDataSource(0, 1).min(0), "the s1 crop rounds outward for $testCase")

		fixture.paint(FinalInterval(longArrayOf(16, 16, 16), longArrayOf(18, 18, 18)), level = 1)
		assertEquals(paintedLabel, fixture.canvas.at(33, 33, 33), "the s0 half inside the crop is painted for $testCase")
		assertEquals(paintedLabel, fixture.canvas.at(37, 37, 37), "s1 voxel 18 reaches s0 37 for $testCase")
		assertEquals(Label.INVALID, fixture.canvas.at(32, 32, 32), "the s0 half outside the crop is not painted for $testCase")

		/* rendered at s1, the outside half of voxel 16 is the out of bounds value, the inside half the paint */
		val rendered = fixture.masked.getInterpolatedDataSource(0, 1, Interpolation.NEARESTNEIGHBOR).realRandomAccess()
		val outOfBounds = rendered.setPositionAndGet(-5.0, -5.0, -5.0).get()
		assertEquals(outOfBounds, rendered.setPositionAndGet(15.9, 15.9, 15.9).get(), "beyond the s0 crop nothing shows for $testCase")
		assertEquals(paintedLabel, rendered.setPositionAndGet(16.3, 16.3, 16.3).get(), "inside the s0 crop the paint shows for $testCase")

		val committed = fixture.commit()
		assertEquals(paintedLabel, committed.at(33, 33, 33), "inside for $testCase")
		assertEquals(background, committed.at(32, 32, 32), "outside keeps the background for $testCase")
	}

	@ParameterizedTest
	@FieldSource("cropCases")
	fun `the canvas spans the dataset and paint at the far edge of the crop reaches it`(testCase: TestCase, @TempDir tmp: Path) {
		val fixture = croppedMaskedSource(testCase, tmp)
		assertArrayEquals(crop.minAsLongArray(), fixture.masked.getDataSource(0, 0).minAsLongArray(), "the presented source is the crop for $testCase")
		assertArrayEquals(fixture.dimensions, Intervals.dimensionsAsLongArray(fixture.canvas), "the canvas is the dataset's size for $testCase")

		/* the masks the tools paint into are positioned like the canvas, not the crop */
		val generated = fixture.masked.generateMask(MaskInfo(0, 0), MaskedSource.VALID_LABEL_CHECK)
		assertArrayEquals(fixture.dimensions, Intervals.dimensionsAsLongArray(generated.rai), "a generated mask is the dataset's size for $testCase")
		fixture.masked.resetMasks()

		/* inside the crop, beyond crop.min from its origin: the voxels the old crop-sized canvas could not hold */
		fixture.paint(FinalInterval(longArrayOf(70, 70, 70), longArrayOf(74, 74, 74)))

		assertEquals(paintedLabel, fixture.canvas.at(72, 72, 72), "paint inside the crop must reach the canvas for $testCase")
		assertEquals(Label.INVALID, fixture.canvas.at(60, 60, 60), "unpainted voxels stay invalid for $testCase")
	}

	@ParameterizedTest
	@FieldSource("cropCases")
	fun `paint across a block boundary inside the crop commits on both sides`(testCase: TestCase, @TempDir tmp: Path) {
		val fixture = croppedMaskedSource(testCase, tmp)

		/* 46..53 spans the block boundary at 50, wholly inside the crop */
		fixture.paint(FinalInterval(longArrayOf(46, 46, 46), longArrayOf(53, 53, 53)))
		val committed = fixture.commit()

		assertEquals(paintedLabel, committed.at(47, 47, 47), "block 0 side for $testCase")
		assertEquals(paintedLabel, committed.at(52, 52, 52), "block 1 side for $testCase")
		assertEquals(background, committed.at(40, 40, 40), "unpainted inside the crop keeps the background for $testCase")
		assertEquals(background, committed.at(60, 60, 60), "unpainted inside the crop keeps the background for $testCase")
	}

	@ParameterizedTest
	@FieldSource("cropCases")
	fun `paint across both crop edges commits only the inside and leaves the outside untouched`(testCase: TestCase, @TempDir tmp: Path) {
		val fixture = croppedMaskedSource(testCase, tmp)

		/* two strokes, each half outside the crop: 28..38 over the low edge at 33, 72..82 over the high edge at 76 */
		fixture.paint(
			FinalInterval(longArrayOf(28, 28, 28), longArrayOf(38, 38, 38)),
			FinalInterval(longArrayOf(72, 72, 72), longArrayOf(82, 82, 82))
		)
		assertEquals(paintedLabel, fixture.canvas.at(35, 35, 35), "inside the low edge is painted for $testCase")
		assertEquals(Label.INVALID, fixture.canvas.at(30, 30, 30), "outside the low edge is dropped for $testCase")
		assertEquals(paintedLabel, fixture.canvas.at(75, 75, 75), "inside the high edge is painted for $testCase")
		assertEquals(Label.INVALID, fixture.canvas.at(80, 80, 80), "outside the high edge is dropped for $testCase")

		val committed = fixture.commit()
		assertEquals(paintedLabel, committed.at(35, 35, 35), "low edge inside for $testCase")
		assertEquals(background, committed.at(30, 30, 30), "low edge outside keeps the background for $testCase")
		assertEquals(paintedLabel, committed.at(75, 75, 75), "high edge inside for $testCase")
		assertEquals(background, committed.at(80, 80, 80), "high edge outside keeps the background for $testCase")
		/* the rest of the rewritten blocks, far from the crop */
		assertEquals(background, committed.at(5, 5, 5), "block 0 far outside for $testCase")
		assertEquals(background, committed.at(95, 95, 95), "block 1 far outside for $testCase")
	}
}
