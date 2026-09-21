package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class SlicePositionSerializationTest {

	@Test
	fun `slice positions round trip by axis name`(@TempDir tmp: Path) {
		val backend = backendFor(tmp, "xyzct", longArrayOf(8, 8, 8, 3, 4), axes("x", "y", "z", "c", "t"))
		backend.metadataState.xyzView.sliceAt(3, 1L)
		backend.metadataState.xyzView.sliceAt(4, 3L)

		val json = JsonObject().also { it.addSlicePositions(backend) }
		assertEquals(parse("""{"slicePositions":{"c":1,"t":3}}"""), json)

		backend.metadataState.xyzView.reset()
		restoreSlicePositions(backend, json)
		assertEquals(1L, backend.metadataState.xyzView.slicePosition(3))
		assertEquals(3L, backend.metadataState.xyzView.slicePosition(4))
	}

	@Test
	fun `an all default source writes nothing`(@TempDir tmp: Path) {
		val backend = backendFor(tmp, "unsliced", longArrayOf(8, 8, 8, 3), axes("x", "y", "z", "t"))
		assertEquals(JsonObject(), JsonObject().also { it.addSlicePositions(backend) })
	}

	/** The point of keying by name: the writer's axis order need not be the reader's. */
	@Test
	fun `positions written in one axis order restore into another`(@TempDir tmp: Path) {
		val written = backendFor(tmp, "xyzct", longArrayOf(8, 8, 8, 3, 4), axes("x", "y", "z", "c", "t"))
		written.metadataState.xyzView.sliceAt(3, 1L)
		written.metadataState.xyzView.sliceAt(4, 3L)
		val json = JsonObject().also { it.addSlicePositions(written) }

		/* c and t sit at 0 and 4 here, not 3 and 4 */
		val read = backendFor(tmp, "cxyzt", longArrayOf(3, 8, 8, 8, 4), axes("c", "x", "y", "z", "t"))
		restoreSlicePositions(read, json)
		assertEquals(1L, read.metadataState.xyzView.slicePosition(0), "c must follow its name, not its index")
		assertEquals(3L, read.metadataState.xyzView.slicePosition(4))
	}

	@Test
	fun `a legacy positional array still restores`(@TempDir tmp: Path) {
		val backend = backendFor(tmp, "xyzct", longArrayOf(8, 8, 8, 3, 4), axes("x", "y", "z", "c", "t"))
		restoreSlicePositions(backend, parse("""{"slicePositions":[0,0,0,1,3]}"""))
		assertEquals(1L, backend.metadataState.xyzView.slicePosition(3))
		assertEquals(3L, backend.metadataState.xyzView.slicePosition(4))
	}

	@Test
	fun `a legacy array of the wrong length is ignored`(@TempDir tmp: Path) {
		val backend = backendFor(tmp, "xyzct", longArrayOf(8, 8, 8, 3, 4), axes("x", "y", "z", "c", "t"))
		restoreSlicePositions(backend, parse("""{"slicePositions":[0,0,0,1]}"""))
		assertEquals(0L, backend.metadataState.xyzView.slicePosition(3))
		assertEquals(0L, backend.metadataState.xyzView.slicePosition(4))
	}

	private fun parse(json: String) = JsonParser.parseString(json).asJsonObject

	private fun axes(vararg names: String) = names.map { name ->
		when (name) {
			"c" -> Axis(Axis.CHANNEL, name)
			"t" -> Axis(Axis.TIME, name)
			else -> Axis(Axis.SPACE, name)
		}
	}.toTypedArray()

	private fun backendFor(tmp: Path, dataset: String, dimensions: LongArray, axes: Array<Axis>): N5BackendRaw<*, *> {
		val writer: N5Writer = Paintera.n5Factory.newWriter(tmp.toAbsolutePath().toString())
		writer.createDataset(dataset, DatasetAttributes(dimensions, IntArray(dimensions.size) { 4 }, DataType.UINT8, RawCompression()))
		writer.setAttribute(dataset, "resolution", doubleArrayOf(1.0, 1.0, 1.0))
		writer.setAttribute(dataset, "offset", doubleArrayOf(0.0, 0.0, 0.0))
		val metadataState: MetadataState = MetadataUtils.createMetadataState(tmp.toAbsolutePath().toString(), dataset)!!
		metadataState.axes = axes
		return N5BackendRaw<net.imglib2.type.numeric.integer.UnsignedByteType, net.imglib2.type.volatiles.VolatileUnsignedByteType>(metadataState)
	}
}
