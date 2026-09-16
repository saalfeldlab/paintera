package org.janelia.saalfeldlab.paintera.state.raw

import bdv.cache.SharedQueue
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBCompositeColorConverter
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.data.n5.ChannelCompositeSource
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet
import org.janelia.saalfeldlab.paintera.serialization.converter.ARGBCompositeColorConverterSerializer
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState
import org.janelia.saalfeldlab.paintera.state.raw.n5.Deserializer
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.janelia.saalfeldlab.util.Colors
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/* the JSON a ConnectomicsChannelState wrote, read by the raw state deserializer as if the source had been opened here */
class ChannelStateMigrationTest {

	private fun container(tmp: Path): String {
		val container = tmp.resolve("data.n5").toAbsolutePath().toString()
		val writer = Paintera.n5Factory.newWriter(container)
		writer.createDataset("raw", DatasetAttributes(longArrayOf(8, 6, 4, 3), intArrayOf(8, 6, 4, 1), DataType.UINT8, RawCompression()))
		return container
	}

	@Suppress("DEPRECATION")
	private fun gson(): Gson = GsonBuilder()
		.registerTypeAdapter(ConnectomicsChannelState::class.java, ConnectomicsChannelState.Deserializer(SharedQueue(1), 0))
		.registerTypeAdapter(N5BackendRaw::class.java, Deserializer<UnsignedByteType, VolatileUnsignedByteType>())
		.registerTypeHierarchyAdapter(
			ARGBCompositeColorConverter::class.java,
			ARGBCompositeColorConverterSerializer<VolatileUnsignedByteType, RealComposite<VolatileUnsignedByteType>, VolatileWithSet<RealComposite<VolatileUnsignedByteType>>>()
		)
		.create()

	/* through the adapter, as SourceInfoSerializer does: fromJson rejects a raw state for the removed type */
	@Suppress("DEPRECATION")
	private fun load(state: JsonObject): ConnectomicsRawState<*, *> =
		assertIs<ConnectomicsRawState<*, *>>(gson().getAdapter(ConnectomicsChannelState::class.java).fromJsonTree(state))

	@Test
	@Suppress("DEPRECATION")
	fun `the removed state type is still found reflectively`() {
		assertEquals(ConnectomicsChannelState::class.java, Class.forName("org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState"))
	}

	@Test
	fun `a saved channel state loads as a composited raw state`(@TempDir tmp: Path) {
		val container = container(tmp)
		val state = JsonParser.parseString(
			"""
			{
			  "backend": {
			    "type": "${ConnectomicsChannelState.BACKEND_TYPE}",
			    "data": { "uri": "file://$container", "dataset": "raw", "channels": [0, 2] }
			  },
			  "name": "old channels",
			  "composite": { "type": "${ARGBCompositeAlphaAdd::class.java.name}", "data": {} },
			  "converter": {
			    "type": "${ARGBCompositeColorConverter.InvertingImp0::class.java.name}",
			    "data": {
			      "alpha": 0.8,
			      "numChannels": 2,
			      "color": ["#ff0000", "#00ff00"],
			      "min": [10.0, 20.0],
			      "max": [100.0, 200.0],
			      "channelAlpha": [1.0, 0.5]
			    }
			  },
			  "interpolation": "NEARESTNEIGHBOR",
			  "isVisible": true,
			  "resolution": [4.0, 4.0, 40.0],
			  "offset": [0.0, 0.0, 0.0]
			}
			"""
		).asJsonObject

		val loaded = load(state)

		assertEquals("old channels", loaded.name)
		assertIs<ARGBCompositeAlphaAdd>(loaded.composite)
		assertContentEquals(doubleArrayOf(4.0, 4.0, 40.0), loaded.resolution)
		val channels = loaded.channels!!
		assertEquals(3, channels.axis)
		assertEquals(listOf(0, 2), channels.activeChannels)

		/* the selection-sized converter is spread onto the dataset channels: old channel 1 was dataset channel 2 */
		val settings = channels.converter
		assertEquals(3, settings.numChannels())
		assertEquals(0.8, settings.alphaProperty().get())
		assertEquals(Colors.toARGBType("#ff0000").get(), settings.colorProperty(0).get().get())
		assertEquals(Colors.toARGBType("#00ff00").get(), settings.colorProperty(2).get().get())
		assertEquals(10.0, settings.minProperty(0).get())
		assertEquals(200.0, settings.maxProperty(2).get())
		assertEquals(0.5, settings.channelAlphaProperty(2).get())

		val rendered = assertIs<ChannelCompositeSource<*, *>>(loaded.sourceAndConverter.spimSource)
		assertEquals(2, rendered.numChannels)
	}

	@Test
	fun `an explicit channel index is the composite axis`(@TempDir tmp: Path) {
		val container = container(tmp)
		val state = JsonParser.parseString(
			"""
			{
			  "backend": {
			    "type": "${ConnectomicsChannelState.BACKEND_TYPE}",
			    "data": { "uri": "file://$container", "dataset": "raw", "channels": [1], "channelIndex": 3 }
			  },
			  "name": "one channel"
			}
			"""
		).asJsonObject

		val loaded = load(state)

		val channels = loaded.channels!!
		assertEquals(listOf(1), channels.activeChannels)
		/* no saved converter: the defaults, sized to the dataset's channels */
		assertEquals(3, channels.converter.numChannels())
		assertEquals(1, assertIs<ChannelCompositeSource<*, *>>(loaded.sourceAndConverter.spimSource).numChannels)
	}
}
