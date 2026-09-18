package org.janelia.saalfeldlab.paintera.serialization

import bdv.cache.SharedQueue
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.imglib2.FinalInterval
import net.imglib2.RealInterval
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.paintera.state.raw.n5.Deserializer
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Type
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs

/* the crop is a 3D xyz interval under `virtualCrop`, the shape legacy projects already hold */
class VirtualCropSerializationTest {

	companion object {
		@JvmStatic
		@BeforeAll
		fun registerLookupAdapter() = TestData.registerLabelBlockLookupAdapter()
	}

	private val crop = FinalInterval(longArrayOf(2, 3, 4), longArrayOf(5, 6, 7))

	private fun container(tmp: Path, dataType: DataType, vararg dimensions: Long): N5ContainerState {
		val writer = Paintera.n5Factory.newWriter(tmp.resolve("data.n5").toAbsolutePath().toString())
		writer.createDataset("data", DatasetAttributes(dimensions, IntArray(dimensions.size) { 8 }, dataType, RawCompression()))
		return N5ContainerState(writer)
	}

	private fun rawBackend(tmp: Path, vararg dimensions: Long) =
		N5BackendRaw<UnsignedByteType, VolatileUnsignedByteType>(MetadataUtils.createMetadataState(container(tmp, DataType.UINT8, *dimensions), "data")!!)

	private fun labelBackend(tmp: Path) =
		N5BackendLabel.createFrom<UnsignedLongType, VolatileUnsignedLongType>(container(tmp, DataType.UINT64, 8, 9, 10), "data", Executors.newSingleThreadExecutor())

	private val intervalGson: Gson = GsonBuilder().registerTypeHierarchyAdapter(RealInterval::class.java, RealIntervalSerializer()).create()

	private val serializationContext = object : JsonSerializationContext {
		override fun serialize(src: Any?): JsonElement = intervalGson.toJsonTree(src)
		override fun serialize(src: Any?, typeOfSrc: Type): JsonElement = intervalGson.toJsonTree(src, typeOfSrc)
	}

	private val deserializationContext = object : JsonDeserializationContext {
		override fun <T> deserialize(json: JsonElement, typeOfT: Type): T = intervalGson.fromJson(json, typeOfT)
	}

	/* through the helpers alone, the way the two state serializers call them */
	private fun roundTrip(backend: SourceStateBackend<*, *>, restoreInto: SourceStateBackend<*, *>): JsonObject {
		val json = JsonObject()
		json.addVirtualCrop(backend, serializationContext)
		restoreVirtualCrop(restoreInto, json, deserializationContext)
		return json
	}

	@Test
	fun `a cropped label backend reads back cropped`(@TempDir tmp: Path) {
		val saved = labelBackend(tmp).apply { xyzView.setCropInterval(crop) }
		val loaded = labelBackend(tmp.resolve("loaded"))
		val json = roundTrip(saved, loaded)

		assertEquals("""{"min":[2,3,4],"max":[5,6,7]}""", json[VIRTUAL_CROP_KEY].toString())
		assertTrue(Intervals.equals(crop, loaded.xyzView.xyzCrop!!))
	}

	@Test
	fun `an uncropped backend writes nothing and reads back uncropped`(@TempDir tmp: Path) {
		val loaded = rawBackend(tmp.resolve("loaded"), 8, 9, 10).apply { xyzView.setCropInterval(crop) }
		val json = roundTrip(rawBackend(tmp, 8, 9, 10), loaded)

		assertFalse(json.has(VIRTUAL_CROP_KEY))
		assertNull(loaded.xyzView.xyzCrop, "a missing key removes a crop the backend had")
	}

	@Test
	fun `a crop on a 5D source keeps the slice positions`(@TempDir tmp: Path) {
		val saved = rawBackend(tmp, 8, 9, 10, 3, 4).apply { xyzView.setCropInterval(crop) }
		val loaded = rawBackend(tmp.resolve("loaded"), 8, 9, 10, 3, 4).apply { xyzView.sliceAt(3, 2); xyzView.sliceAt(4, 1) }
		roundTrip(saved, loaded)

		assertTrue(Intervals.equals(crop, loaded.xyzView.xyzCrop!!))
		assertEquals(2, loaded.xyzView.slicePosition(3))
		assertEquals(1, loaded.xyzView.slicePosition(4))
	}

	/* the crop written by a 1.14 project; past the data it is clamped, not rejected */
	@Test
	fun `a legacy crop restores`(@TempDir tmp: Path) {
		val loaded = rawBackend(tmp, 8, 9, 10)
		val legacy = JsonParser.parseString("""{"virtualCrop": {"min": [0, 0, 0], "max": [999, 999, 999]}}""")
		restoreVirtualCrop(loaded, legacy, deserializationContext)

		assertNull(loaded.xyzView.xyzCrop, "the full extent is not a crop")

		val partial = JsonParser.parseString("""{"virtualCrop": {"min": [2, 3, 4], "max": [999, 999, 999]}}""")
		restoreVirtualCrop(loaded, partial, deserializationContext)
		assertTrue(Intervals.equals(FinalInterval(longArrayOf(2, 3, 4), longArrayOf(7, 8, 9)), loaded.xyzView.xyzCrop!!))
	}

	/* the whole raw state through its real serializer and deserializer */
	@Test
	fun `a raw state round trips its crop`(@TempDir tmp: Path) {
		val backend = rawBackend(tmp, 8, 9, 10).apply { xyzView.setCropInterval(crop) }
		val queue = SharedQueue(1)
		val state = ConnectomicsRawState(backend, queue, 0, "raw")

		val gson = GsonBuilder()
			.registerTypeHierarchyAdapter(ConnectomicsRawState::class.java, ConnectomicsRawState.Serializer())
			.registerTypeHierarchyAdapter(ConnectomicsRawState::class.java, ConnectomicsRawState.Deserializer(queue, 0))
			/* the real backend serializer resolves the URI against the running project; write the absolute one its deserializer reads */
			.registerTypeAdapter(N5BackendRaw::class.java, JsonSerializer<N5BackendRaw<*, *>> { src, _, _ ->
				JsonObject().apply { addProperty("uri", src.container.uri.toString()); addProperty("dataset", src.dataset) }
			})
			.registerTypeAdapter(N5BackendRaw::class.java, Deserializer<UnsignedByteType, VolatileUnsignedByteType>())
			.registerTypeHierarchyAdapter(RealInterval::class.java, RealIntervalSerializer())
			.create()

		val json = gson.toJsonTree(state).asJsonObject
		assertEquals("""{"min":[2,3,4],"max":[5,6,7]}""", json[VIRTUAL_CROP_KEY].toString())

		val loaded = assertIs<ConnectomicsRawState<*, *>>(gson.fromJson(json, ConnectomicsRawState::class.java))
		assertTrue(Intervals.equals(crop, loaded.backend.xyzView.xyzCrop!!))
		assertTrue(Intervals.equals(crop, loaded.dataSource.getDataSource(0, 0)), "the source presents the crop")
	}
}
