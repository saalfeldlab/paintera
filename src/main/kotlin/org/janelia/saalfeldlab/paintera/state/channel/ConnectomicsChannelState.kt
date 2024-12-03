package org.janelia.saalfeldlab.paintera.state.channel

import bdv.cache.SharedQueue
import bdv.viewer.Interpolation
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Node
import javafx.scene.layout.VBox
import net.imglib2.Interval
import net.imglib2.Volatile
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBCompositeColorConverter
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.withClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.*
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier

typealias ARGBComoposite = org.janelia.saalfeldlab.paintera.composition.Composite<ARGBType, ARGBType>

private fun ARGBCompositeColorConverter.InvertingImp0<*, *, *>.setIntensityFrom(metadataState: MetadataState) {
	setMaxs { metadataState.maxIntensity }
	setMins { metadataState.minIntensity }
}

class ConnectomicsChannelState<D, T, CD, CT, V>
@JvmOverloads constructor(
	override val backend: ConnectomicsChannelBackend<CD, V>,
	queue: SharedQueue,
	priority: Int,
	name: String,
	private val converter: ARGBCompositeColorConverter<T, CT, V> = ARGBCompositeColorConverter.InvertingImp0<T, CT, V>(backend.numChannels).also {
		(backend as? SourceStateBackendN5<*, *>)?.let { n5Backend -> it.setIntensityFrom(n5Backend.metadataState) }
	}
) : SourceStateWithBackend<CD, V>
		where D : RealType<D>, T : AbstractVolatileRealType<D, T>, CD : RealComposite<D>, CT : RealComposite<T>, V : Volatile<CT> {

	private val source: ChannelDataSource<CD, V> = backend.createSource(queue, priority, name)

	override fun getDataSource(): ChannelDataSource<CD, V> = source

	override fun converter(): ARGBCompositeColorConverter<T, CT, V> = converter

	val numChannels = source.numChannels()

	private val _composite: ObjectProperty<ARGBComoposite> = SimpleObjectProperty(ARGBCompositeAlphaAdd())
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
	var interpolation: Interpolation by _interpolationProperty.nonnull()

	override fun compositeProperty(): ObjectProperty<ARGBComposite> = _composite

	override fun nameProperty() = _name

	override fun statusTextProperty() = _statusText

	override fun isVisibleProperty() = _isVisible

	override fun interpolationProperty() = _interpolationProperty

	override fun dependsOn(): Array<SourceState<*, *>> = arrayOf()

	override fun preferencePaneNode(): Node {
		val node = super.preferencePaneNode()
		val box = node as? VBox ?: VBox(node)
		box.children.add(ChannelSourceStateConverterNode(this.converter()).converterNode)
		return box
	}

	override fun onAdd(paintera: PainteraBaseView) {
		for (channel in 0 until numChannels.toInt()) {
			converter().colorProperty(channel).addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
			converter().minProperty(channel).addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
			converter().maxProperty(channel).addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
			converter().channelAlphaProperty(channel).addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
		}
	}

	private object SerializationKeys {
		const val BACKEND = "backend"
		const val NAME = "name"
		const val COMPOSITE = "composite"
		const val CONVERTER = "converter"
		const val VIRTUAL_CROP = "virtualCrop"
		const val INTERPOLATION = "interpolation"
		const val IS_VISIBLE = "isVisible"
		const val RESOLUTION = "resolution"
		const val OFFSET = "offset"
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer<D, T, CD, CT, V> : PainteraSerialization.PainteraSerializer<ConnectomicsChannelState<D, T, CD, CT, V>>
			where D : RealType<D>, T : AbstractVolatileRealType<D, T>, CD : RealComposite<D>, CT : RealComposite<T>, V : Volatile<CT> {
		override fun serialize(state: ConnectomicsChannelState<D, T, CD, CT, V>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = JsonObject()
			with(SerializationKeys) {
				map.add(BACKEND, context.withClassInfo(state.backend))
				map.addProperty(NAME, state.name)
				map.add(COMPOSITE, context.withClassInfo(state.composite))
				map.add(CONVERTER, context.withClassInfo(state.converter))
				map.add(INTERPOLATION, context[state.interpolation])
				map.addProperty(IS_VISIBLE, state.isVisible)
				state.resolution.let { map.add(RESOLUTION, context[it]) }
				state.offset.let { map.add(OFFSET, context[it]) }
				state.virtualCrop?.let { map.add(VIRTUAL_CROP, context[it]) }

			}
			return map
		}

		override fun getTargetClass(): Class<ConnectomicsChannelState<D, T, CD, CT, V>> =
			ConnectomicsChannelState::class.java as Class<ConnectomicsChannelState<D, T, CD, CT, V>>
	}

	class Deserializer<D, T, CD, CT, V>(
		private val queue: SharedQueue,
		private val priority: Int
	) : PainteraSerialization.PainteraDeserializer<ConnectomicsChannelState<D, T, CD, CT, V>>
			where D : RealType<D>, T : AbstractVolatileRealType<D, T>, CD : RealComposite<D>, CT : RealComposite<T>, V : Volatile<CT> {

		@Plugin(type = StatefulSerializer.DeserializerFactory::class)
		class Factory<D, T, CD, CT, V> : StatefulSerializer.DeserializerFactory<ConnectomicsChannelState<D, T, CD, CT, V>, Deserializer<D, T, CD, CT, V>>
				where D : RealType<D>, T : AbstractVolatileRealType<D, T>, CD : RealComposite<D>, CT : RealComposite<T>, V : Volatile<CT> {
			override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>
			): Deserializer<D, T, CD, CT, V> = Deserializer(
				arguments.viewer.queue,
				0
			)

			override fun getTargetClass() = ConnectomicsChannelState::class.java as Class<ConnectomicsChannelState<D, T, CD, CT, V>>
		}

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ConnectomicsChannelState<D, T, CD, CT, V> {
			return with(SerializationKeys) {
				with(GsonExtensions) {
					val backend = context.fromClassInfo<ConnectomicsChannelBackend<CD, V>>(json, BACKEND)!!
					backend.virtualCrop = context.get<Interval?>(json, VIRTUAL_CROP)

					ConnectomicsChannelState(
						backend,
						queue,
						priority,
						json.getStringProperty(NAME) ?: backend.name,
						context.fromClassInfo(json, CONVERTER)!!
					).apply {
						json.get<String>(NAME) { name = it }
						composite = context.fromClassInfo(json, COMPOSITE)!!
						interpolation = context[json, INTERPOLATION]!!
						json.get<Boolean>(IS_VISIBLE) { isVisible = it }
					}
				}
			}
		}

		override fun getTargetClass(): Class<ConnectomicsChannelState<D, T, CD, CT, V>> =
			ConnectomicsChannelState::class.java as Class<ConnectomicsChannelState<D, T, CD, CT, V>>
	}

}
