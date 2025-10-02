package org.janelia.saalfeldlab.paintera.data.n5

import bdv.cache.SharedQueue
import com.google.gson.GsonBuilder
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SlicedDataSourceTest {

	companion object {
		private const val DEFAULT_BLOCK_SIZE = 5
		private val DEFAULT_RES = doubleArrayOf(1.0, 1.0, 1.0)
		private val DEFAULT_OFFSET = doubleArrayOf(0.0, 0.0, 0.0)
		private val DEFAULT_XYZ_AXES = intArrayOf(0, 1, 2)

		@JvmStatic
		@BeforeAll
		fun setupN5Factory() {
			val builder = GsonBuilder()
			builder.registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.getJsonAdapter())
			Paintera.Companion.n5Factory.gsonBuilder(builder)
		}

		private fun writer(tmp: Path): N5Writer =
			Paintera.Companion.n5Factory.newWriter(tmp.toAbsolutePath().toString())

		private fun createDataset(
			writer: N5Writer,
			name: String,
			dims: LongArray,
			blockSize: IntArray = IntArray(dims.size) { DEFAULT_BLOCK_SIZE }
		) {
			val attrs = DatasetAttributes(dims, blockSize, DataType.UINT8, RawCompression())
			writer.createDataset(name, attrs)
		}

		private fun openRaw(
			writer: N5Writer,
			dataset: String,
			xyzAxes: IntArray = DEFAULT_XYZ_AXES,
			forceSlice3D: Boolean? = null
		): ImagesWithTransform<*, *> {
			val queue = SharedQueue(1, 1)
			return if (forceSlice3D != null) {
				val transform = MetadataUtils.Companion.transformFromResolutionOffset(DEFAULT_RES, DEFAULT_OFFSET)
				N5Data.openRaw<Nothing, Nothing>(writer, dataset, transform, xyzAxes, queue, 0, forceSlice3D) as ImagesWithTransform<*, *>
			} else {
				N5Data.openRaw<Nothing, Nothing>(writer, dataset, DEFAULT_RES, DEFAULT_OFFSET, xyzAxes, queue, 0) as ImagesWithTransform<*, *>
			}
		}

		private fun verifyDims(
			result: ImagesWithTransform<*, *>,
			numDims: Int,
			vararg sizes: Long
		) {
			assertNotNull(result)
			assertEquals(numDims, result.data.numDimensions())
			sizes.forEachIndexed { idx, size ->
				assertEquals(size, result.data.dimension(idx))
			}
		}
	}

	@Test
	fun `test 3D with standard xyz axes`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dims = longArrayOf(10, 20, 30)
		createDataset(n5, "test3d", dims)

		val result = openRaw(n5, "test3d")

		assertNotNull(result)
		assertEquals(3, result.data.numDimensions())
		assertContentEquals(dims, result.data.dimensionsAsLongArray())
	}

	@Test
	fun `test 5D sliced to 3D`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dims = longArrayOf(10, 20, 30, 3, 2)
		createDataset(n5, "test5d", dims, intArrayOf(5, 5, 5, 1, 1))

		val result = openRaw(n5, "test5d")

		verifyDims(result, 3, 10, 20, 30)
	}

	@Test
	fun `test 4D not sliced by default`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dims = longArrayOf(10, 20, 30, 3)
		createDataset(n5, "test4d", dims, intArrayOf(5, 5, 5, 1))

		val result = openRaw(n5, "test4d", forceSlice3D = false)

		assertNotNull(result)
		assertEquals(4, result.data.numDimensions())
		assertContentEquals(dims, result.data.dimensionsAsLongArray())
	}

	@Test
	fun `test 4D force sliced to 3D`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dims = longArrayOf(10, 20, 30, 3)
		createDataset(n5, "test4d", dims, intArrayOf(5, 5, 5, 1))

		val result = openRaw(n5, "test4d", forceSlice3D = true)

		verifyDims(result, 3, 10, 20, 30)
	}

	@Test
	fun `test non-standard axis order`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dims = longArrayOf(2, 10, 3, 20, 30)
		createDataset(n5, "test5d_reordered", dims, intArrayOf(1, 5, 1, 5, 5))

		val xyzAxes = intArrayOf(1, 3, 4)
		val result = openRaw(n5, "test5d_reordered", xyzAxes)

		verifyDims(result, 3, 10, 20, 30)
	}

	@Test
	fun `test multiscale opening`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val group = "multiscale"

		n5.createGroup(group)
		n5.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true)

		val scales = listOf(
			Triple("s0", longArrayOf(100, 100, 100), doubleArrayOf(1.0, 1.0, 1.0)),
			Triple("s1", longArrayOf(50, 50, 50), doubleArrayOf(2.0, 2.0, 2.0)),
			Triple("s2", longArrayOf(25, 25, 25), doubleArrayOf(4.0, 4.0, 4.0))
		)

		val blockSize = intArrayOf(32, 32, 32)
		scales.forEach { (scale, dims, res) ->
			createDataset(n5, "$group/$scale", dims, blockSize)
			n5.setAttribute("$group/$scale", "resolution", res)
		}

		val queue = SharedQueue(1, 1)
		val results = N5Data.openRawMultiscale<UnsignedByteType, VolatileUnsignedByteType>(n5, group, queue, 0)

		assertNotNull(results)
		assertEquals(3, results.size)

		scales.forEachIndexed { idx, (_, dims, _) ->
			assertContentEquals(dims, results[idx].data.dimensionsAsLongArray())
		}
	}

	@Test
	fun `test 6D sliced to 3D`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dims = longArrayOf(10, 20, 30, 3, 2, 4)
		createDataset(n5, "test6d", dims, intArrayOf(5, 5, 5, 1, 1, 1))

		val result = openRaw(n5, "test6d")

		verifyDims(result, 3, 10, 20, 30)
	}

	@Test
	fun `test 3D unchanged`(@TempDir tmp: Path) {
		val n5 = writer(tmp)
		val dims = longArrayOf(10, 20, 30)
		createDataset(n5, "test3d", dims)

		val result = openRaw(n5, "test3d")

		assertNotNull(result)
		assertEquals(3, result.data.numDimensions())
		assertContentEquals(dims, result.data.dimensionsAsLongArray())
	}
}