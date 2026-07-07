package org.janelia.saalfeldlab.paintera.state.metadata

import bdv.cache.SharedQueue
import com.google.gson.GsonBuilder
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SlicedSourceMetadataTest {

	companion object {
		private val BLOCK_SIZE = intArrayOf(32, 32, 32)
		private val RES = doubleArrayOf(1.0, 1.0, 1.0)
		private val OFFSET = doubleArrayOf(0.0, 0.0, 0.0)

		@JvmStatic
		@BeforeAll
		fun setupN5Factory() {
			val builder = GsonBuilder()
			builder.registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.getJsonAdapter())
			Paintera.n5Factory.options { it.gsonBuilder(builder) }
		}

		private fun writer(tmp: Path): N5Writer =
			Paintera.n5Factory.newWriter(tmp.toAbsolutePath().toString())

		private fun createDataset(
			n5: N5Writer,
			name: String,
			dims: LongArray,
			blockSize: IntArray = IntArray(dims.size) { if (it < 3) BLOCK_SIZE[it] else 1 }
		) {
			val attrs = DatasetAttributes(dims, blockSize, DataType.UINT8, RawCompression())
			n5.createDataset(name, attrs)
			n5.setAttribute(name, "resolution", RES)
			n5.setAttribute(name, "offset", OFFSET)
		}

		private fun assertReadOnly(container: N5ContainerState) {
			assertEquals(null, container.writer)
			assertTrue(container.reader !is N5Writer)
		}

		private fun assertWritable(container: N5ContainerState) {
			assertNotNull(container.writer)
			assertTrue(container.reader is N5Writer)
		}

		private fun createLabelDataset(
			n5: N5Writer,
			name: String,
			dims: LongArray,
			blockSize: IntArray = IntArray(dims.size) { if (it < 3) BLOCK_SIZE[it] else 1 }
		) {
			val attrs = DatasetAttributes(dims, blockSize, DataType.UINT64, RawCompression())
			n5.createDataset(name, attrs)
			n5.setAttribute(name, "resolution", RES)
			n5.setAttribute(name, "offset", OFFSET)
		}

		private fun getSources(metadataState: MetadataState, queue: SharedQueue = SharedQueue(1, 1)): Array<ImagesWithTransform<*, *>> =
			metadataState.getData<Nothing, Nothing>(queue, 0) as Array<ImagesWithTransform<*, *>>

		private fun assertSource3D(source: ImagesWithTransform<*, *>, x: Long, y: Long, z: Long) {
			/* the data is kept nD and projected to 3D live by the source; the carried grid is the 3D (x, y, z) presentation */
			val grid = source.grid
			assertEquals(3, grid.numDimensions())
			assertEquals(x, grid.imgDimension(0))
			assertEquals(y, grid.imgDimension(1))
			assertEquals(z, grid.imgDimension(2))
		}
	}

	@Test
	fun `test 5D dataset creates sliced 3D source`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test5d"
		val dims = longArrayOf(50, 60, 70, 3, 2)
		createDataset(n5, dataset, dims)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(5, metadataState.datasetAttributes.numDimensions)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}

	@Test
	fun `test 4D dataset remains 4D for channels`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test4d"
		val dims = longArrayOf(50, 60, 70, 3)
		createDataset(n5, dataset, dims)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(4, metadataState.datasetAttributes.numDimensions)

		val queue = SharedQueue(1, 1)
		val sources = metadataState.getData<Nothing, Nothing>(queue, 0)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		val source = sources[0]
		assertEquals(4, source.data.numDimensions())
		assertEquals(50, source.data.dimension(0))
		assertEquals(60, source.data.dimension(1))
		assertEquals(70, source.data.dimension(2))
		assertEquals(3, source.data.dimension(3))
	}

	@Test
	fun `test 6D dataset creates sliced 3D source`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test6d"
		val dims = longArrayOf(50, 60, 70, 3, 2, 5)
		createDataset(n5, dataset, dims)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(6, metadataState.datasetAttributes.numDimensions)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}

	@Test
	fun `test read-only metadata state source access`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_readonly"
		val dims = longArrayOf(50, 60, 70)
		createDataset(n5, dataset, dims)

		val path = tmp.toAbsolutePath().toString()
		val writableState = MetadataUtils.createMetadataState(path, dataset) as? SingleScaleMetadataState

		assertNotNull(writableState)
		assertWritable(writableState.n5ContainerState)

		val readOnlyContainer = writableState.n5ContainerState.readOnlyCopy()
		writableState.n5ContainerState = readOnlyContainer

		assertReadOnly(writableState.n5ContainerState)

		val sources = getSources(writableState)

		assertNotNull(sources)
		assertEquals(1, sources.size)
	}

	@Test
	fun `test multiscale 5D dataset creates sliced 3D sources`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val group = "multiscale5d"

		n5.createGroup(group)
		n5.setAttribute(group, "multiScale", true)

		val scales = listOf(
			Triple("s0", longArrayOf(100, 100, 100, 3, 2), doubleArrayOf(1.0, 1.0, 1.0)),
			Triple("s1", longArrayOf(50, 50, 50, 3, 2), doubleArrayOf(2.0, 2.0, 2.0))
		)

		scales.forEach { (scale, dims, res) ->
			createDataset(n5, "$group/$scale", dims)
			n5.setAttribute("$group/$scale", "resolution", res)
		}

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, group)

		assertNotNull(metadataState)
		assertTrue(metadataState is MultiScaleMetadataState)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(2, sources.size)

		sources.forEachIndexed { idx, source ->
			val expectedSize = if (idx == 0) 100L else 50L
			assertSource3D(source, expectedSize, expectedSize, expectedSize)
		}
	}

	@Test
	fun `test 1D dataset is not yet openable (metadata parsing limitation)`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test1d"
		createDataset(n5, dataset, longArrayOf(50), intArrayOf(32))

		/* SpatialMapping embeds 1D to [n, 1, 1] (covered in SpatialMappingTest), but n5-universe metadata parsing
		 * does not recognize a 1D dataset, so it cannot be opened end-to-end yet */
		assertEquals(null, MetadataUtils.createMetadataState(tmp.toAbsolutePath().toString(), dataset))
	}

	@Test
	fun `test 2D dataset embedded as 3D source`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test2d"
		val dims = longArrayOf(50, 60)
		createDataset(n5, dataset, dims, intArrayOf(32, 32))

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(2, metadataState.datasetAttributes.numDimensions)

		/* a 2D source embeds a singleton z so Paintera sees a 3D source */
		val sources = getSources(metadataState)
		assertNotNull(sources)
		assertEquals(1, sources.size)
		assertSource3D(sources[0], 50, 60, 1)
	}

	@Test
	fun `test 3D dataset unchanged through metadata state`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test3d"
		val dims = longArrayOf(50, 60, 70)
		createDataset(n5, dataset, dims)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(3, metadataState.datasetAttributes.numDimensions)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}

	@Test
	fun `test 4D raw dataset not sliced`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test4d_raw"
		val dims = longArrayOf(50, 60, 70, 3)
		createDataset(n5, dataset, dims)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(4, metadataState.datasetAttributes.numDimensions)
		assertEquals(false, metadataState.isLabel)

		val queue = SharedQueue(1, 1)
		val sources = metadataState.getData<Nothing, Nothing>(queue, 0)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		val source = sources[0]
		assertEquals(4, source.data.numDimensions())
	}

	@Test
	fun `test 4D label dataset sliced to 3D`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test4d_label"
		val dims = longArrayOf(50, 60, 70, 3)
		createLabelDataset(n5, dataset, dims, intArrayOf(32, 32, 32, 1))

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(4, metadataState.datasetAttributes.numDimensions)
		assertEquals(true, metadataState.isLabel)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}

	@Test
	fun `test sliced labels are read-only`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test5d_label"
		val dims = longArrayOf(50, 60, 70, 3, 2)
		createLabelDataset(n5, dataset, dims)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset) as SingleScaleMetadataState

		assertEquals(5, metadataState.datasetAttributes.numDimensions)
		assertEquals(true, metadataState.isLabel)
		assertWritable(metadataState.n5ContainerState)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		metadataState.n5ContainerState = metadataState.n5ContainerState.readOnlyCopy()
		assertReadOnly(metadataState.n5ContainerState)
	}

	@Test
	fun `test 3D writable then 4D read-only independence`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset3d = "test3d_label"
		val dataset4d = "test4d_label"

		val dims3d = longArrayOf(50, 60, 70)
		val dims4d = longArrayOf(50, 60, 70, 3)
		createLabelDataset(n5, dataset3d, dims3d, intArrayOf(32, 32, 32))
		createLabelDataset(n5, dataset4d, dims4d, intArrayOf(32, 32, 32, 1))

		val path = tmp.toAbsolutePath().toString()

		val state3d = MetadataUtils.createMetadataState(path, dataset3d) as SingleScaleMetadataState
		assertEquals(3, state3d.datasetAttributes.numDimensions)
		assertEquals(true, state3d.isLabel)
		assertWritable(state3d.n5ContainerState)

		val sources3d = getSources(state3d)
		assertNotNull(sources3d)
		assertWritable(state3d.n5ContainerState)

		val state4d = MetadataUtils.createMetadataState(path, dataset4d) as SingleScaleMetadataState
		assertEquals(4, state4d.datasetAttributes.numDimensions)
		assertEquals(true, state4d.isLabel)

		val sources4d = getSources(state4d)
		assertNotNull(sources4d)
		assertEquals(3, sources4d[0].grid.numDimensions())

		state4d.n5ContainerState = state3d.n5ContainerState.readOnlyCopy()
		assertReadOnly(state4d.n5ContainerState)

		assertWritable(state3d.n5ContainerState)
	}

	@Test
	fun `test non-standard spatial axes XYCZT slicing`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_xyczt"
		val dims = longArrayOf(50, 60, 3, 70, 2)
		createLabelDataset(n5, dataset, dims, intArrayOf(32, 32, 1, 32, 1))

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(5, metadataState.datasetAttributes.numDimensions)

		metadataState.axes = arrayOf(
			Axis(Axis.SPACE, "x"),
			Axis(Axis.SPACE, "y"),
			Axis(Axis.CHANNEL, "c"),
			Axis(Axis.SPACE, "z"),
			Axis(Axis.TIME, "t")
		)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}

	@Test
	fun `test 4D scalar label sliced to 3D`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test4d_label_scalar"
		val dims = longArrayOf(50, 60, 70, 3)
		val blockSize = intArrayOf(32, 32, 32, 1)
		val attrs = DatasetAttributes(dims, blockSize, DataType.UINT64, RawCompression())
		n5.createDataset(dataset, attrs)
		n5.setAttribute(dataset, "resolution", RES)
		n5.setAttribute(dataset, "offset", OFFSET)
		// Note: No isLabelMultiset attribute - this makes it a scalar label dataset

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(4, metadataState.datasetAttributes.numDimensions)
		assertEquals(DataType.UINT64, metadataState.datasetAttributes.dataType)
		assertEquals(true, metadataState.isLabel)
		assertEquals(false, metadataState.isLabelMultiset)

		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}

	@Test
	fun `test sliced and embedded label sources carry a 3D block grid`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		/* a sliced view is not a cell image, so the source must carry its 3D block grid for canvas/commit/mesh */
		createLabelDataset(n5, "label5d", longArrayOf(100, 100, 100, 2, 3), intArrayOf(50, 50, 50, 1, 1))
		createDataset(n5, "label2d", longArrayOf(80, 90), intArrayOf(40, 45))

		val path = tmp.toAbsolutePath().toString()

		val grid5d = getSources(MetadataUtils.createMetadataState(path, "label5d")!!)[0].grid
		assertNotNull(grid5d)
		assertEquals(listOf(100L, 100L, 100L), grid5d!!.imgDimensions.toList())
		assertEquals(listOf(50, 50, 50), IntArray(3).also { grid5d.cellDimensions(it) }.toList())

		val grid2d = getSources(MetadataUtils.createMetadataState(path, "label2d")!!)[0].grid
		assertNotNull(grid2d)
		assertEquals(listOf(80L, 90L, 1L), grid2d!!.imgDimensions.toList())
		assertEquals(listOf(40, 45, 1), IntArray(3).also { grid2d.cellDimensions(it) }.toList())
	}

	@Test
	fun `test 4D LabelMultiset reduced to 3D multiset`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test4d_label_multiset"
		val dims = longArrayOf(50, 60, 70, 3)
		val blockSize = intArrayOf(32, 32, 32, 1)
		val attrs = DatasetAttributes(dims, blockSize, DataType.INT8, RawCompression())
		n5.createDataset(dataset, attrs)
		n5.setAttribute(dataset, "resolution", RES)
		n5.setAttribute(dataset, "offset", OFFSET)
		n5.setAttribute(dataset, "isLabelMultiset", true)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(4, metadataState.datasetAttributes.numDimensions)
		assertEquals(DataType.INT8, metadataState.datasetAttributes.dataType)
		assertEquals(true, metadataState.isLabel)
		assertEquals(true, metadataState.isLabelMultiset)

		/* a >3D label multiset is reduced to a 3D view, preserving the LabelMultisetType */
		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}

	@Test
	fun `test 5D LabelMultiset reduced to 3D multiset`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test5d_label_multiset"
		val dims = longArrayOf(50, 60, 70, 3, 2)
		val blockSize = intArrayOf(32, 32, 32, 1, 1)
		val attrs = DatasetAttributes(dims, blockSize, DataType.INT8, RawCompression())
		n5.createDataset(dataset, attrs)
		n5.setAttribute(dataset, "resolution", RES)
		n5.setAttribute(dataset, "offset", OFFSET)
		n5.setAttribute(dataset, "isLabelMultiset", true)

		val path = tmp.toAbsolutePath().toString()
		val metadataState = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(metadataState)
		assertEquals(5, metadataState.datasetAttributes.numDimensions)
		assertEquals(DataType.INT8, metadataState.datasetAttributes.dataType)
		assertEquals(true, metadataState.isLabel)
		assertEquals(true, metadataState.isLabelMultiset)

		/* a >3D label multiset is reduced to a 3D view, preserving the LabelMultisetType */
		val sources = getSources(metadataState)

		assertNotNull(sources)
		assertEquals(1, sources.size)

		assertSource3D(sources[0], 50, 60, 70)
	}
}