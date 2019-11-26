package org.janelia.saalfeldlab.paintera.state.raw

import bdv.viewer.Interpolation
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import net.imglib2.converter.ARGBColorConverter
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.paintera.NamedKeyCombination
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers
import org.janelia.saalfeldlab.paintera.state.*
import org.janelia.saalfeldlab.util.Colors
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type

typealias ARGBComoposite = Composite<ARGBType, ARGBType>

class ConnectomicsRawState<D, T>(override val backend: ConnectomicsRawBackend<D, T>) : SourceStateWithBackend<D, T>
		where D: RealType<D>, T: AbstractVolatileRealType<D, T> {

	private val converter = ARGBColorConverter.InvertingImp0<T>()

	override fun getDataSource(): DataSource<D, T> = backend.source

	override fun converter(): ARGBColorConverter<T> = converter

	private val _composite: ObjectProperty<ARGBComoposite> = SimpleObjectProperty(CompositeCopy())
	var composite: ARGBComposite
		get() = _composite.value
		set(composite) = _composite.set(composite)

	private val _name = SimpleStringProperty(backend.source.name)
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

	override fun axisOrderProperty() = SimpleObjectProperty(AxisOrder.XYZ)

	override fun getDisplayStatus() = null

	override fun stateSpecificGlobalEventHandler(paintera: PainteraBaseView, keyTracker: KeyTracker): EventHandler<Event> {
		val bindings = paintera.keyAndMouseBindings.getConfigFor(this)
		LOG.debug("Returning {}-specific global handler", javaClass.simpleName)
		val handler = DelegateEventHandlers.handleAny()
		val threshold = RawSourceStateThreshold(this)
			.keyPressedHandler(paintera) { bindings.keyCombinations[BindingKeys.THRESHOLD]!!.primaryCombination }
		handler.addEventHandler(KeyEvent.KEY_PRESSED, threshold)
		return handler
	}

	override fun preferencePaneNode(): Node {
		val node = super.preferencePaneNode()
		val box = node as? VBox ?: VBox(node)
		box.children.add(RawSourceStateConverterNode(converter).converterNode)
		return box
	}

	override fun createKeyAndMouseBindings(): KeyAndMouseBindings {
		val bindings = KeyAndMouseBindings()
		try {
			bindings.keyCombinations.addCombination(
				NamedKeyCombination(
					BindingKeys.THRESHOLD,
					KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN)
				)
			)
		} catch (e: NamedKeyCombination.CombinationMap.KeyCombinationAlreadyInserted) {
			// TOOD no reason to ever throw anything with only a single inserted combination
		}

		return bindings
	}

	override fun onAdd(paintera: PainteraBaseView) {
		converter().minProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
		converter().maxProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
		converter().alphaProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
		converter().colorProperty().addListener { _, _, _ -> paintera.orthogonalViews().requestRepaint() }
	}

	private object BindingKeys {
		const val THRESHOLD = "threshold"
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
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer<D: RealType<D>, T: AbstractVolatileRealType<D, T>> : PainteraSerialization.PainteraSerializer<ConnectomicsRawState<D, T>> {
		override fun serialize(state: ConnectomicsRawState<D, T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = JsonObject()
			with (SerializationKeys) {
				map.add(BACKEND, SerializationHelpers.serializeWithClassInfo(state.backend, context))
				map.addProperty(NAME, state.name)
				map.add(COMPOSITE, SerializationHelpers.serializeWithClassInfo(state.composite, context))
				JsonObject().let { m ->
					m.addProperty(CONVERTER_MIN, state.converter.min)
					m.addProperty(CONVERTER_MAX, state.converter.max)
					m.addProperty(CONVERTER_ALPHA, state.converter.alphaProperty().get())
					m.addProperty(CONVERTER_COLOR, Colors.toHTML(state.converter.color))
					map.add(CONVERTER, m)
				}
				map.add(INTERPOLATION, context.serialize(state.interpolation))
				map.addProperty(IS_VISIBLE, state.isVisible)
			}
			return map
		}

		override fun getTargetClass(): Class<ConnectomicsRawState<D, T>> = ConnectomicsRawState::class.java as Class<ConnectomicsRawState<D, T>>
	}

	@Plugin(type = PainteraSerialization.PainteraDeserializer::class)
	class Deserializer<D: RealType<D>, T: AbstractVolatileRealType<D, T>> : PainteraSerialization.PainteraDeserializer<ConnectomicsRawState<D, T>> {

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ConnectomicsRawState<D, T> {
			return with (SerializationKeys) {
				with (GsonExtensions) {
					ConnectomicsRawState<D, T>(
						SerializationHelpers.deserializeFromClassInfo(json.getJsonObject(BACKEND)!!, context))
						.also { state -> json.getStringProperty(NAME)?.let { state.name = it } }
						.also { state -> json.getJsonObject(COMPOSITE)?.let { state.composite = SerializationHelpers.deserializeFromClassInfo(it, context) } }
						.also { state ->
							json.getJsonObject(CONVERTER)?.let { converter ->
								converter.getDoubleProperty(CONVERTER_MIN)?.let { state.converter.min = it }
								converter.getDoubleProperty(CONVERTER_MAX)?.let { state.converter.max = it }
								converter.getDoubleProperty(CONVERTER_ALPHA)?.let { state.converter.alphaProperty().value = it }
								converter.getStringProperty(CONVERTER_COLOR)?.let { state.converter.color = Colors.toARGBType(it) }
							}
						}
						.also { state -> json.getProperty(INTERPOLATION)?.let { state.interpolation = context.deserialize(it, Interpolation::class.java) } }
						.also { state -> json.getBooleanProperty(IS_VISIBLE)?.let { state.isVisible = it } }
				}
			}
		}

		override fun getTargetClass(): Class<ConnectomicsRawState<D, T>> = ConnectomicsRawState::class.java as Class<ConnectomicsRawState<D, T>>
	}

}
