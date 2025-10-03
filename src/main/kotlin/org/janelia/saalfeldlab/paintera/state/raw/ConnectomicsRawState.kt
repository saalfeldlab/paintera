package org.janelia.saalfeldlab.paintera.state.raw

import bdv.cache.SharedQueue
import bdv.viewer.Interpolation
import com.google.gson.*
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions.Companion.graphicsOnly
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.RawSourceStateKeys
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.control.modes.RawSourceMode
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.withClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.DeserializerFactory
import org.janelia.saalfeldlab.paintera.state.*
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.BACKEND
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.COMPOSITE
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.CONVERTER
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.CONVERTER_ALPHA
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.CONVERTER_COLOR
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.CONVERTER_MAX
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.CONVERTER_MIN
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.INTERPOLATION
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.IS_VISIBLE
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.NAME
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.OFFSET
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.RESOLUTION
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState.SerializationKeys.VIRTUAL_CROP
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.n5.N5Helpers.serializeTo
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.function.BiConsumer
import java.util.function.IntFunction
import java.util.function.Supplier
import kotlin.jvm.optionals.getOrNull

typealias ARGBComoposite = Composite<ARGBType, ARGBType>

open class ConnectomicsRawState<D, T>(
	override val backend: ConnectomicsRawBackend<D, T>,
	queue: SharedQueue,
	priority: Int,
	name: String,
) : SourceStateWithBackend<D, T>
	where D : RealType<D>, T : AbstractVolatileRealType<D, T> {

	private val converter = ARGBColorConverter.InvertingImp0<T>().apply {
		(backend as? SourceStateBackendN5<*, *>)?.metadataState?.let {
			min = it.minIntensity
			max = it.maxIntensity
		}
	}

	private val source: DataSource<D, T> = backend.createSource(queue, priority, name)

	override fun createKeyAndMouseBindings(): KeyAndMouseBindings {
		return KeyAndMouseBindings(RawSourceStateKeys.namedCombinationsCopy())
	}

	override fun getDataSource(): DataSource<D, T> = source

	override fun converter(): ARGBColorConverter<T> = converter

	override fun getDefaultMode(): ControlMode {
		return RawSourceMode()
	}

	private val _composite: ObjectProperty<ARGBComoposite> = SimpleObjectProperty(CompositeCopy())
	var composite: ARGBComposite
		get() = _composite.value
		set(composite) = _composite.set(composite)

	private val _name = SimpleStringProperty(name)
	var name: String
		get() = _name.value
		set(name) = _name.set(name)

	private val _statusText = SimpleStringProperty(null)

	private val _isVisible = SimpleBooleanProperty(true)
	var isVisible: Boolean
		get() = _isVisible.value
		set(isVisible) = _isVisible.set(isVisible)

	private val _interpolationProperty = SimpleObjectProperty(Interpolation.NEARESTNEIGHBOR)
	var interpolation: Interpolation
		get() = _interpolationProperty.value
		set(interpolation) = _interpolationProperty.set(interpolation)

	override fun compositeProperty(): ObjectProperty<Composite<ARGBType, ARGBType>> = _composite

	override fun nameProperty() = _name

	override fun statusTextProperty() = _statusText

	override fun isVisibleProperty() = _isVisible

	override fun interpolationProperty() = _interpolationProperty

	override fun dependsOn(): Array<SourceState<*, *>> = arrayOf()

	override fun preferencePaneNode(): Node {
		val node = super.preferencePaneNode()
		val box = node as? VBox ?: VBox(node)
		box.children.add(RawSourceStateConverterNode(converter, this).converterNode)

		val backendMeta = backend.createMetaDataNode()
		val metaDataContents = VBox(backendMeta)

		val tpGraphics = HBox(
			Label("Metadata"),
			NamedNode.bufferNode(),
		).apply { alignment = Pos.CENTER }

		val metaData = TitledPanes.createCollapsed(null, metaDataContents).apply {
			graphicsOnly(tpGraphics)
			alignment = Pos.CENTER_RIGHT
		}
		box.children.add(metaData)


		return box
	}

	override fun onAdd(paintera: PainteraBaseView) {
		converter().minProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
		converter().maxProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
		converter().alphaProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
		converter().colorProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
	}

	private object SerializationKeys {
		const val BACKEND = "backend"
		const val NAME = "name"
		const val COMPOSITE = "composite"
		const val CONVERTER = "converter"
		const val CONVERTER_MIN = "min"
		const val CONVERTER_MAX = "max"
		const val CONVERTER_ALPHA = "alpha"
		const val CONVERTER_COLOR = "color"
		const val INTERPOLATION = "interpolation"
		const val IS_VISIBLE = "isVisible"
		const val RESOLUTION = "resolution"
		const val VIRTUAL_CROP = "virtualCrop"
		const val OFFSET = "offset"
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer : PainteraSerialization.PainteraSerializer<ConnectomicsRawState<*, *>> {
		override fun serialize(state: ConnectomicsRawState<*, *>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = JsonObject()
			with(SerializationKeys) {
				map.add(BACKEND, context.withClassInfo(state.backend))
				map.addProperty(NAME, state.name)
				map.add(COMPOSITE, context.withClassInfo(state.composite))
				JsonObject().let { m ->
					m.addProperty(CONVERTER_MIN, state.converter.min)
					m.addProperty(CONVERTER_MAX, state.converter.max)
					m.addProperty(CONVERTER_ALPHA, state.converter.alphaProperty().get())
					m.addProperty(CONVERTER_COLOR, Colors.toHTML(state.converter.color))
					map.add(CONVERTER, m)
				}
				map.add(INTERPOLATION, context[state.interpolation])
				map.addProperty(IS_VISIBLE, state.isVisible)
				map.add(RESOLUTION, context[state.resolution])
				map.add(OFFSET, context[state.offset])
				state.virtualCrop?.let { map.add(VIRTUAL_CROP, context[it]) }
			}
			return map
		}

		override fun getTargetClass(): Class<ConnectomicsRawState<*, *>> = ConnectomicsRawState::class.java
	}

	class Deserializer(
		private val queue: SharedQueue,
		private val priority: Int
	) : PainteraSerialization.PainteraDeserializer<ConnectomicsRawState<*, *>> {

		override fun isHierarchyAdapter(): Boolean {
			return true
		}

		@Plugin(type = DeserializerFactory::class)
		class Factory : DeserializerFactory<ConnectomicsRawState<*, *>, Deserializer> {
			override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>
			): Deserializer = Deserializer(arguments.viewer.queue, 0)

			override fun getTargetClass() = ConnectomicsRawState::class.java
		}

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ConnectomicsRawState<*, *> {
			return deserializeConnectomicsRawState(context, json)
		}

		private fun <D, T> deserializeConnectomicsRawState(context: JsonDeserializationContext, json: JsonElement): ConnectomicsRawState<*, *>
			where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {
			val backend: ConnectomicsRawBackend<D, T> = context.fromClassInfo<ConnectomicsRawBackend<D, T>>(json, BACKEND)!!
			val resolution = context[json, RESOLUTION] ?: backend.resolution
			val offset = context[json, OFFSET] ?: backend.translation
			val virtualCrop = context.get<RealInterval?>(json, VIRTUAL_CROP) as? Interval
			backend.updateTransform(resolution, offset)
			backend.virtualCrop = virtualCrop

			return ConnectomicsRawState(
				backend,
				queue,
				priority,
				json[NAME] ?: backend.name
			).apply {
				context.fromClassInfo<Composite<ARGBType, ARGBType>>(json, COMPOSITE) { composite = it }
				json.get<JsonObject>(CONVERTER) { conv ->
					conv.get<Double>(CONVERTER_MIN) { converter.min = it }
					conv.get<Double>(CONVERTER_MAX) { converter.max = it }
					conv.get<Double>(CONVERTER_ALPHA) { converter.alphaProperty().value = it }
					conv.get<String>(CONVERTER_COLOR) { converter.color = Colors.toARGBType(it) }
				}
				context.get<Interpolation>(json, INTERPOLATION) { interpolation = it }
				json.get<Boolean>(IS_VISIBLE) { isVisible = it }
			}
		}

		override fun getTargetClass(): Class<ConnectomicsRawState<*, *>> = ConnectomicsRawState::class.java

		companion object {

			private const val DEPRECATED_RAW_SOURCE_STATE = "org.janelia.saalfeldlab.paintera.state.RawSourceState"

			@JvmField
			val DEPRECATED_STATE_CONVERTERS: Map<String, BiConsumer<Gson, JsonObject>> = mapOf(
				DEPRECATED_RAW_SOURCE_STATE to BiConsumer { gson, json -> deprecatedRawSourceStateToConnectomicsSourceState(gson, json) }
			)

			private fun deprecatedRawSourceStateToConnectomicsSourceState(gson: Gson, json: JsonObject) {
				if (json.get<String>("type") != DEPRECATED_RAW_SOURCE_STATE) return
				try {
					json.addProperty("type", ConnectomicsRawState::class.java.name)

					val state: JsonObject = json.getAsJsonObject("state");
					state.remove(COMPOSITE)

					val source: JsonElement = state["source"]!!
					val meta: JsonElement = source["meta"]!!
					val n5: String = meta["n5"]!!
					val dataset: String = meta["dataset"]!!
					val metadata = MetadataUtils.createMetadataState(n5, dataset)!!

					source.get<JsonArray>("transform") { array ->
						val transform = gson.fromJson(array, DoubleArray::class.java)
						val affine = AffineTransform3D().apply { set(*transform) }
						metadata.updateTransform(affine)
						state.add("resolution", gson.toJsonTree(metadata.resolution))
						state.add("offset", gson.toJsonTree(metadata.translation))
					}

					val backend = N5BackendRaw(metadata)
					val container = JsonObject()
					backend.container.serializeTo(container)
					container.addProperty("dataset", backend.dataset)

					val backendObj = JsonObject().also {
						it.add("data", container)
						it.addProperty("type", backend::class.java.name)
					}

					state.add("backend", backendObj)
				} catch (_: Exception) {
					LOG.error("Error migrating deprecated RawSourceState to ConnectomicsRawState")
				}
			}
		}
	}
}
