package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers
import org.janelia.saalfeldlab.paintera.serialization.Properties2
import org.janelia.saalfeldlab.paintera.serialization.SourceInfoSerializer
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.Supplier

typealias PropertiesListener = BiConsumer<Properties2?, Properties2?>

class PainteraMainWindow {

    val baseView = PainteraBaseView(
            PainteraBaseView.reasonableNumFetcherThreads(),
            ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()))

    val paneWithStatus = BorderPaneWithStatusBars2(baseView)

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

    private lateinit var defaultHandlers: PainteraDefaultHandlers2

	private lateinit var properties: Properties2

	private fun initProperties(properties: Properties2) {
		this.properties = properties
		defaultHandlers = PainteraDefaultHandlers2(
				baseView,
				keyTracker,
				mouseTracker,
				paneWithStatus,
				Supplier { projectDirectory.actualDirectory.absolutePath },
				this.properties)
		paneWithStatus.arbitraryMeshConfigNode().config.bindTo(this.properties.arbitraryMeshConfig)
		paneWithStatus.bookmarkConfigNode().bookmarkConfig = this.properties.bookmarkConfig
		paneWithStatus.crosshairConfigNode().bind(this.properties.crosshairConfig)
		paneWithStatus.navigationConfigNode().bind(this.properties.navigationConfig)
//		paneWithStatus.orthoSliceConfigNode().bind(this.properties.orthoSliceConfig)
		paneWithStatus.scaleBarOverlayConfigNode().bindBidirectionalTo(this.properties.scaleBarOverlayConfig)
		paneWithStatus.screenScalesConfigNode().bind(this.properties.screenScalesConfig)
		paneWithStatus.viewer3DConfigNode().bind(this.properties.viewer3DConfig)
	}

	private fun initProperties(json: JsonObject?, gson: Gson) {
		val properties = json?.let { gson.fromJson(it, Properties2::class.java)
		}
		initProperties(properties ?: Properties2())
	}

	fun deserialize() {
		val indexToState = mutableMapOf<Int, SourceState<*, *>>()
		val builder = GsonHelpers
				.builderWithAllRequiredDeserializers(
						StatefulSerializer.Arguments(baseView),
						{ projectDirectory.actualDirectory.absolutePath },
						{ indexToState[it] })
		val gson = builder.create()
		val json = projectDirectory
				.actualDirectory
				?.let { N5FSReader(it.absolutePath).getAttribute("/", PAINTERA_KEY, JsonElement::class.java) }
				?.takeIf { it.isJsonObject }
				?.let { it.asJsonObject }
		deserialize(json, gson, indexToState)
	}

	private fun deserialize(json: JsonObject?, gson: Gson, indexToState: MutableMap<Int, SourceState<*, *>>) {
		initProperties(json, gson)
		baseView.orthogonalViews().grid().manage(properties.gridConstraints)
		json
				?.takeIf { it.has(SOURCES_KEY) }
				?.get(SOURCES_KEY)
				?.takeIf { it.isJsonObject }
				?.asJsonObject
				?.let { SourceInfoSerializer.populate(
						{ baseView.addState(it) },
						{ baseView.sourceInfo().currentSourceIndexProperty().set(it) },
						it.asJsonObject,
						{ k, v -> indexToState.put(k, v) },
						gson)
				}
	}

	companion object{

		private const val PAINTERA_KEY = "paintera"

		private const val SOURCES_KEY = "sourceInfo"

	}


}
