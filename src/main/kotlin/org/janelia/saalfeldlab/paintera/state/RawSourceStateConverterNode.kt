package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.TilePane
import javafx.scene.paint.Color
import javafx.stage.Modality
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.util.DoubleStringFormatter
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.paintera.control.modes.RawSourceMode
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.Colors
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class RawSourceStateConverterNode<T,V>(private val converter: ARGBColorConverter<*>, private val state: ConnectomicsRawState<T, V>)
where T : RealType<T>, V : AbstractVolatileRealType<T, V> {

	private val colorProperty = SimpleObjectProperty(Color.WHITE)

	private val argbProperty = converter.colorProperty()

	private val alphaProperty = converter.alphaProperty()

	private val min = converter.minProperty()

	private val max = converter.maxProperty()

	init {
		this.colorProperty.addListener { _, _, new -> argbProperty.set(Colors.toARGBType(new)) }
		this.argbProperty.addListener { _, _, new -> colorProperty.set(Colors.toColor(new)) }
		this.colorProperty.value = Colors.toColor(argbProperty.value)
	}

	val converterNode: Node
		get() {

			val tilePane = TilePane(Orientation.VERTICAL)
			tilePane.minWidth = 0.0

			val picker = ColorPicker(Colors.toColor(argbProperty.get()))
			picker.valueProperty().bindBidirectional(this.colorProperty)
			val colorPickerBox = HBox(picker)
			HBox.setHgrow(picker, Priority.ALWAYS)
			tilePane.children.add(colorPickerBox)
			val resetMinMax = Button("Reset Min / Max")
			val autoMinMax = Button("Auto Min / Max")

			listOf(resetMinMax, autoMinMax).forEach {
				HBox.setHgrow(it, Priority.ALWAYS)
				it.maxWidth = Double.MAX_VALUE
			}

			resetMinMax.onAction = EventHandler {
				RawSourceMode.resetIntensityMinMax(state as SourceState<*, RealType<*>>)
			}
			autoMinMax.onAction = EventHandler {
				paintera.baseView.lastFocusHolder.value?.viewer()?.let { viewer ->
					RawSourceMode.autoIntensityMinMax(state as SourceState<*, RealType<*>>, viewer)
				}
			}
			val thresholdHBox = HBox(resetMinMax, autoMinMax)

			tilePane.children.add(thresholdHBox)

			val min = this.min.asString()
			val max = this.max.asString()
			val minInput = TextField(min.get())
			val maxInput = TextField(max.get())
			minInput.promptTextProperty().bind(this.min.asString("min=%f"))
			minInput.promptTextProperty().bind(this.max.asString("max=%f"))

			min.addListener { obs, oldv, newv -> minInput.text = newv }
			max.addListener { obs, oldv, newv -> maxInput.text = newv }

			val minFormatter = DoubleStringFormatter.createFormatter(this.min.get(), 2)
			val maxFormatter = DoubleStringFormatter.createFormatter(this.max.get(), 2)

			minInput.textFormatter = minFormatter
			maxInput.textFormatter = maxFormatter

			minInput.setOnKeyPressed { event ->
				if (event.code == KeyCode.ENTER) {
					minInput.commitValue()
					event.consume()
				}
			}
			maxInput.setOnKeyPressed { event ->
				if (event.code == KeyCode.ENTER) {
					maxInput.commitValue()
					event.consume()
				}
			}

			minInput.tooltip = Tooltip("min")
			maxInput.tooltip = Tooltip("max")

			minFormatter.valueProperty().addListener { _, _, newv -> this.min.set(newv!!) }
			maxFormatter.valueProperty().addListener { _, _, newv -> this.max.set(newv!!) }

			this.min.addListener { _, _, newv -> minFormatter.setValue(newv.toDouble()) }
			this.max.addListener { _, _, newv -> maxFormatter.setValue(newv.toDouble()) }

			val minMaxBox = HBox(minInput, maxInput)
			tilePane.children.add(minMaxBox)

			val alphaSliderWithField = NumericSliderWithField(0.0, 1.0, this.alphaProperty.get())
			alphaSliderWithField.slider.valueProperty().bindBidirectional(this.alphaProperty)
			alphaSliderWithField.textField.minWidth = 48.0
			alphaSliderWithField.textField.maxWidth = 48.0
			val alphaBox = HBox(alphaSliderWithField.slider, alphaSliderWithField.textField)
			Tooltip.install(alphaSliderWithField.slider, Tooltip("alpha"))
			HBox.setHgrow(alphaSliderWithField.slider, Priority.ALWAYS)
			tilePane.children.add(alphaBox)

			LOG.debug("Returning TilePane with children: ", tilePane.children)


			val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
				headerText = "Conversion of raw data into ARGB color space."
				contentText = DESCRIPTION
			}


			val tpGraphics = HBox(
				Label("Color Conversion"),
				NamedNode.bufferNode(),
				Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
				.also { it.alignment = Pos.CENTER }

			return with(TitledPaneExtensions) {
				TitledPane(null, tilePane).apply {
					isExpanded = false
					graphicsOnly(tpGraphics)
					alignment = Pos.CENTER_RIGHT
					tooltip = Tooltip(DESCRIPTION)
				}
			}
		}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val DESCRIPTION = "" +
				"Convert scalar real values into RGB color space with the contrast range " +
				"specified by the min and max values."

	}

}
