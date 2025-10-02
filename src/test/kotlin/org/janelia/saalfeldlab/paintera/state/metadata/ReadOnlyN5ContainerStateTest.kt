package org.janelia.saalfeldlab.paintera.state.metadata

import com.google.gson.GsonBuilder
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5.N5FactoryOpener
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MetadataStateTest {

	companion object {
		private val DIMS = longArrayOf(10, 20, 30)
		private val BLOCK_SIZE = intArrayOf(5, 5, 5)
		private val RES = doubleArrayOf(1.0, 1.0, 1.0)
		private val OFFSET = doubleArrayOf(0.0, 0.0, 0.0)

		@JvmStatic
		@BeforeAll
		fun setupN5Factory() {
			val builder = GsonBuilder()
			builder.registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.Companion.getJsonAdapter())
			Paintera.Companion.n5Factory.gsonBuilder(builder)
		}

		private fun writer(tmp: Path): N5Writer =
			Paintera.Companion.n5Factory.newWriter(tmp.toAbsolutePath().toString())

		private fun createDataset(
			n5: N5Writer,
			name: String,
			res: DoubleArray = RES,
			offset: DoubleArray = OFFSET,
			dims: LongArray = DIMS,
			blockSize: IntArray = BLOCK_SIZE
		) {
			val attrs = DatasetAttributes(dims, blockSize, DataType.UINT8, RawCompression())
			n5.createDataset(name, attrs)
			n5.setAttribute(name, "resolution", res)
			n5.setAttribute(name, "offset", offset)
		}

		private fun verifyCacheSize(expected: Int) {
			assertEquals(expected, N5FactoryOpener.Companion.n5ContainerStateCache.size)
		}

		private fun verifyCacheContains(path: String) {
			assertTrue(N5FactoryOpener.Companion.n5ContainerStateCache.containsKey(path))
		}

		private fun assertReadOnly(container: N5ContainerState) {
			assertEquals(null, container.writer)
			assertTrue(container.reader !is N5Writer)
		}

		private fun assertWritable(container: N5ContainerState) {
			assertNotNull(container.writer)
			assertTrue(container.reader is N5Writer)
		}
	}

	@BeforeEach
	fun clearCache() {
		N5FactoryOpener.Companion.n5ContainerStateCache.clear()
	}

	@Test
	fun `test cache on create`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_dataset"
		createDataset(n5, dataset)

		val path = tmp.toAbsolutePath().toString()
		val state1 = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(state1)
		verifyCacheSize(1)
		verifyCacheContains(path)

		val state2 = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(state2)
		verifyCacheSize(1)
		assertSame(state1.n5ContainerState, state2.n5ContainerState)
	}

	@Test
	fun `test cache from reader`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_dataset"
		createDataset(n5, dataset)

		val state1 = MetadataUtils.createMetadataState(n5, dataset)

		assertNotNull(state1)
		assertTrue(N5FactoryOpener.Companion.n5ContainerStateCache.size > 0)

		val state2 = MetadataUtils.createMetadataState(n5, dataset)

		assertNotNull(state2)
		assertSame(state1.n5ContainerState, state2.n5ContainerState)
	}

	@Test
	fun `test multiple containers in cache`(@TempDir tmp1: Path, @TempDir tmp2: Path) {
		val n51 = writer(tmp1)
		val n52 = writer(tmp2)
		val dataset = "test_dataset"

		createDataset(n51, dataset)
		createDataset(n52, dataset)

		val path1 = tmp1.toAbsolutePath().toString()
		val path2 = tmp2.toAbsolutePath().toString()

		val state1 = MetadataUtils.createMetadataState(path1, dataset)
		val state2 = MetadataUtils.createMetadataState(path2, dataset)

		assertNotNull(state1)
		assertNotNull(state2)

		verifyCacheSize(2)
		verifyCacheContains(path1)
		verifyCacheContains(path2)

		assertNotSame(state1.n5ContainerState, state2.n5ContainerState)
	}

	@Test
	fun `test multiscale creates MultiScaleMetadataState`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val group = "multiscale_group"

		n5.createGroup(group)
		n5.setAttribute(group, "multiScale", true)

		val scales = listOf(
			Triple("s0", longArrayOf(100, 100, 100), doubleArrayOf(1.0, 1.0, 1.0)),
			Triple("s1", longArrayOf(50, 50, 50), doubleArrayOf(2.0, 2.0, 2.0))
		)

		val blockSize = intArrayOf(32, 32, 32)
		scales.forEach { (scale, dims, res) ->
			val attrs = DatasetAttributes(dims, blockSize, DataType.UINT8, RawCompression())
			n5.createDataset("$group/$scale", attrs)
			n5.setAttribute("$group/$scale", "resolution", res)
		}

		val path = tmp.toAbsolutePath().toString()
		val state = MetadataUtils.createMetadataState(path, group)

		assertNotNull(state)
		assertTrue(state is MultiScaleMetadataState)
		verifyCacheContains(path)
	}

	@Test
	fun `test shared container state for datasets in same container`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset1 = "dataset1"
		val dataset2 = "dataset2"

		createDataset(n5, dataset1, res = doubleArrayOf(1.0, 1.0, 1.0))
		createDataset(n5, dataset2, res = doubleArrayOf(2.0, 2.0, 2.0))

		val path = tmp.toAbsolutePath().toString()

		val state1 = MetadataUtils.createMetadataState(path, dataset1)
		val state2 = MetadataUtils.createMetadataState(path, dataset2)

		assertNotNull(state1)
		assertNotNull(state2)

		verifyCacheSize(1)
		assertSame(state1.n5ContainerState, state2.n5ContainerState)

		assertTrue(state1.resolution[0] != state2.resolution[0])
	}

	@Test
	fun `test reader from cached container state`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_dataset"
		createDataset(n5, dataset)

		val path = tmp.toAbsolutePath().toString()
		val state = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(state)

		val reader1 = state.reader
		val reader2 = state.reader

		assertNotNull(reader1)
		assertNotNull(reader2)
		assertSame(reader1, reader2)
	}

	@Test
	fun `test container state update`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset1 = "dataset1"
		val dataset2 = "dataset2"

		createDataset(n5, dataset1, res = doubleArrayOf(1.0, 1.0, 1.0))
		createDataset(n5, dataset2, res = doubleArrayOf(2.0, 2.0, 2.0))

		val path = tmp.toAbsolutePath().toString()

		val state1 = MetadataUtils.createMetadataState(path, dataset1) as? SingleScaleMetadataState
		val state2 = MetadataUtils.createMetadataState(path, dataset2) as? SingleScaleMetadataState

		assertNotNull(state1)
		assertNotNull(state2)

		val sharedContainerState = state1.n5ContainerState
		assertSame(sharedContainerState, state2.n5ContainerState)

		val newN5 = writer(tmp)
		val newContainerState = N5ContainerState(newN5)

		state1.n5ContainerState = newContainerState

		assertSame(newContainerState, state1.n5ContainerState)

		assertSame(sharedContainerState, state2.n5ContainerState)
		assertNotSame(state1.n5ContainerState, state2.n5ContainerState)
	}

	@Test
	fun `test writable to read-only conversion`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_dataset"
		createDataset(n5, dataset)

		val writableContainer = N5ContainerState(n5)
		assertWritable(writableContainer)

		val readOnlyContainer = writableContainer.readOnlyCopy()

		assertReadOnly(readOnlyContainer)
		assertWritable(writableContainer)
	}

	@Test
	fun `test read-only copy cannot write`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_dataset"
		createDataset(n5, dataset)

		val path = tmp.toAbsolutePath().toString()
		val state = MetadataUtils.createMetadataState(path, dataset)

		assertNotNull(state)
		val originalContainer = state.n5ContainerState
		assertWritable(originalContainer)

		val readOnlyContainer = originalContainer.readOnlyCopy()

		assertReadOnly(readOnlyContainer)
		assertWritable(originalContainer)
	}

	@Test
	fun `test multiple read-only copies share same instance`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dataset = "test_dataset"
		createDataset(n5, dataset)

		val writableContainer = N5ContainerState(n5)

		val readOnly1 = writableContainer.readOnlyCopy()
		val readOnly2 = readOnly1.readOnlyCopy()

		assertSame(readOnly1, readOnly2)
		assertReadOnly(readOnly1)
		assertReadOnly(readOnly2)
	}
}