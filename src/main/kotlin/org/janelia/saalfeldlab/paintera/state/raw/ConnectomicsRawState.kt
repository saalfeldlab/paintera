package org.janelia.saalfeldlab.paintera.state.raw

import bdv.util.volatiles.SharedQueue
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
import net.imglib2.converter.ARGBColorConverter
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.withClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.ARGBComposite
import org.janelia.saalfeldlab.paintera.state.RawSourceStateConverterNode
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.SourceStateWithBackend
import org.janelia.saalfeldlab.util.Colors
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier

typealias ARGBComoposite = Composite<ARGBType, ARGBType>

class ConnectomicsRawState<D, T>(
    override val backend: ConnectomicsRawBackend<D, T>,
    queue: SharedQueue,
    priority: Int,
    name: String,
) : SourceStateWithBackend<D, T>
    where D : RealType<D>, T : AbstractVolatileRealType<D, T> {

    private val converter = ARGBColorConverter.InvertingImp0<T>()

    private val source: DataSource<D, T> = backend.createSource(queue, priority, name)

    override fun getDataSource(): DataSource<D, T> = source

    override fun converter(): ARGBColorConverter<T> = converter

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
        box.children.add(RawSourceStateConverterNode(converter).converterNode)
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
        const val OFFSET = "offset"
    }

    @Plugin(type = PainteraSerialization.PainteraSerializer::class)
    class Serializer<D : RealType<D>, T : AbstractVolatileRealType<D, T>> : PainteraSerialization.PainteraSerializer<ConnectomicsRawState<D, T>> {
        override fun serialize(state: ConnectomicsRawState<D, T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
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
            }
            return map
        }

        override fun getTargetClass(): Class<ConnectomicsRawState<D, T>> = ConnectomicsRawState::class.java as Class<ConnectomicsRawState<D, T>>
    }

    class Deserializer<D : RealType<D>, T : AbstractVolatileRealType<D, T>>(
        private val queue: SharedQueue,
        private val priority: Int
    ) : PainteraSerialization.PainteraDeserializer<ConnectomicsRawState<D, T>> {

        @Plugin(type = StatefulSerializer.DeserializerFactory::class)
        class Factory<D, T> : StatefulSerializer.DeserializerFactory<ConnectomicsRawState<D, T>, Deserializer<D, T>>
            where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {
            override fun createDeserializer(
                arguments: StatefulSerializer.Arguments,
                projectDirectory: Supplier<String>,
                dependencyFromIndex: IntFunction<SourceState<*, *>>
            ): Deserializer<D, T> = Deserializer(
                arguments.viewer.queue,
                0
            )

            override fun getTargetClass() = ConnectomicsRawState::class.java as Class<ConnectomicsRawState<D, T>>
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ConnectomicsRawState<D, T> {
            return with(SerializationKeys) {
                with(GsonExtensions) {
                    val backend = context.fromClassInfo<ConnectomicsRawBackend<D, T>>(json, BACKEND)!!
                    val resolution = context[json, RESOLUTION] ?: backend.getMetadataState().resolution
                    val offset = context[json, OFFSET] ?: backend.getMetadataState().translation
                    backend.getMetadataState().updateTransform(resolution, offset)
                    ConnectomicsRawState(
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
            }
        }

        override fun getTargetClass(): Class<ConnectomicsRawState<D, T>> = ConnectomicsRawState::class.java as Class<ConnectomicsRawState<D, T>>
    }

}
