package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.janelia.saalfeldlab.util.n5.N5Data
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AxesSerializationTest {

	@Test
	fun `axes assigned by hand round trip in the OME-NGFF form`(@TempDir tmp: Path) {
		val backend = backendFor(tmp, "cxyzt", longArrayOf(3, 8, 8, 8, 4))
		backend.metadataState.axes = axes("c", "x", "y", "z", "t")

		val json = JsonObject().also { it.addAxes(backend) }
		assertEquals(parse("""{"axes":[
			{"type":"channel","name":"c","unit":null},
			{"type":"space","name":"x","unit":null},
			{"type":"space","name":"y","unit":null},
			{"type":"space","name":"z","unit":null},
			{"type":"time","name":"t","unit":null}]}"""), json)

		val read = backendFor(tmp, "cxyzt2", longArrayOf(3, 8, 8, 8, 4))
		restoreAxes(read, json)
		assertEquals(listOf("c", "x", "y", "z", "t"), read.metadataState.axes.map { it.name })
		assertEquals(listOf(1, 2, 3), read.metadataState.xyzView.xyzSourceAxes.toList(), "the view must be built from the restored axes")
	}

	@Test
	fun `axes matching the metadata write nothing`(@TempDir tmp: Path) {
		val backend = backendFor(tmp, "xyzct", longArrayOf(8, 8, 8, 3, 4))
		assertEquals(JsonObject(), JsonObject().also { it.addAxes(backend) })
	}

	@Test
	fun `an axes array of the wrong length is ignored`(@TempDir tmp: Path) {
		val backend = backendFor(tmp, "xyzct", longArrayOf(8, 8, 8, 3, 4))
		restoreAxes(backend, parse("""{"axes":[{"type":"space","name":"x","unit":null}]}"""))
		assertEquals(listOf("x", "y", "z", "c", "t"), backend.metadataState.axes.map { it.name })
	}

	/** A paintera dataset's view is built from its data group; assigning axes on the state must reach it. */
	@Test
	fun `axes set on a paintera dataset state drive its view`(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("labels.n5").toString())
		N5Data.createPainteraLabelDataset(
			writer, "labels", longArrayOf(4, 16, 16, 16), intArrayOf(1, 8, 8, 8),
			DoubleArray(4) { 1.0 }, DoubleArray(4) { 0.0 },
			arrayOf(doubleArrayOf(1.0, 2.0, 2.0, 2.0)),
			labelMultisetType = false,
			axes = arrayOf(Axis(Axis.TIME, "t", "s"), Axis(Axis.SPACE, "x", "pixel"), Axis(Axis.SPACE, "y", "pixel"), Axis(Axis.SPACE, "z", "pixel"))
		)
		val metadataState = createMetadataState(N5ContainerState(writer), "labels")!!
		assertTrue(metadataState is PainteraDataMultiscaleMetadataState)

		/* declare the first dimension a channel instead */
		metadataState.axes = axes("c", "x", "y", "z")
		assertEquals(listOf(1, 2, 3), metadataState.xyzView.xyzSourceAxes.toList())
		assertEquals(listOf(0), metadataState.xyzView.nonSpatialAxes)
		assertEquals("c", metadataState.axes[0].name)
	}

	private fun parse(json: String) = JsonParser.parseString(json).asJsonObject

	private fun axes(vararg names: String) = names.map { name ->
		when (name) {
			"c" -> Axis(Axis.CHANNEL, name)
			"t" -> Axis(Axis.TIME, name)
			else -> Axis(Axis.SPACE, name)
		}
	}.toTypedArray()

	/** a raw dataset with no axis metadata, so its declared axes are the canonical fallback */
	private fun backendFor(tmp: Path, dataset: String, dimensions: LongArray): N5BackendRaw<*, *> {
		val writer: N5Writer = Paintera.n5Factory.newWriter(tmp.toAbsolutePath().toString())
		writer.createDataset(dataset, DatasetAttributes(dimensions, IntArray(dimensions.size) { 4 }, DataType.UINT8, RawCompression()))
		writer.setAttribute(dataset, "resolution", doubleArrayOf(1.0, 1.0, 1.0))
		writer.setAttribute(dataset, "offset", doubleArrayOf(0.0, 0.0, 0.0))
		val metadataState: MetadataState = MetadataUtils.createMetadataState(tmp.toAbsolutePath().toString(), dataset)!!
		return N5BackendRaw<net.imglib2.type.numeric.integer.UnsignedByteType, net.imglib2.type.volatiles.VolatileUnsignedByteType>(metadataState)
	}
}
