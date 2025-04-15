package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBCompositeColorConverter
import org.janelia.saalfeldlab.fx.Menus
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.Colors
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class ChannelSourceStateConverterNode(private val converter: ARGBCompositeColorConverter<*, *, *>) {

	private val numChannels: Int = converter.numChannels()

	private val argbProperty = (0 until numChannels).map { converter.colorProperty(it) }

	private val colorProperty = (0 until numChannels).map {
		SimpleObjectProperty<Color>()
			.also { c -> argbProperty[it].addListener { _, _, new -> c.value = Colors.toColor(new) } }
			.also { c -> c.addListener { _, _, new -> argbProperty[it].value = Colors.toARGBType(new) } }
			.also { c -> c.value = Colors.toColor(argbProperty[it].value) }
	}

	private val alphaProperty = converter.alphaProperty()

	private val min = (0 until numChannels).map { converter.minProperty(it) }

	private val max = (0 until numChannels).map { converter.maxProperty(it) }

	private val channelAlphaProperty = (0 until numChannels).map { converter.channelAlphaProperty(it) }

	val converterNode: Node
		get() {

			val channelsBox = VBox()

			for (channel in 0 until numChannels) {
				val minField = NumberField.doubleField(
					min[channel].get(),
					{ d -> true },
					ObjectField.SubmitOn.ENTER_PRESSED,
					ObjectField.SubmitOn.FOCUS_LOST
				)
				val maxField = NumberField.doubleField(
					max[channel].get(),
					{ d -> true },
					ObjectField.SubmitOn.ENTER_PRESSED,
					ObjectField.SubmitOn.FOCUS_LOST
				)
				minField.valueProperty().bindBidirectional(min[channel])
				maxField.valueProperty().bindBidirectional(max[channel])
				minField.textField.tooltip = Tooltip("min")
				maxField.textField.tooltip = Tooltip("max")
				val minMaxBox = HBox(minField.textField, maxField.textField)

				val alphaSliderWithField = NumericSliderWithField(0.0, 1.0, this.channelAlphaProperty[channel].get())
				alphaSliderWithField.slider.valueProperty().bindBidirectional(this.channelAlphaProperty[channel])
				alphaSliderWithField.textField.minWidth = 48.0
				alphaSliderWithField.textField.maxWidth = 48.0
				val alphaBox = HBox(alphaSliderWithField.slider, alphaSliderWithField.textField)
				Tooltip.install(alphaSliderWithField.slider, Tooltip("alpha"))
				HBox.setHgrow(alphaSliderWithField.slider, Priority.ALWAYS)

				val channelPane = TitledPanes.createCollapsed(String.format("Channel % 2d", channel), VBox(minMaxBox, alphaBox))
				channelsBox.children.add(channelPane)
				val colorSelector = ColorPicker(colorProperty[channel].get())
				colorSelector.valueProperty().bindBidirectional(colorProperty[channel])
				channelPane.graphic = colorSelector
			}

			val channels = TitledPanes.createCollapsed("Channels", channelsBox)

			val alphaSliderWithField = NumericSliderWithField(0.0, 1.0, this.alphaProperty.get())
			alphaSliderWithField.slider.valueProperty().bindBidirectional(this.alphaProperty)
			alphaSliderWithField.textField.minWidth = 48.0
			alphaSliderWithField.textField.maxWidth = 48.0
			val alphaBox = HBox(alphaSliderWithField.slider, alphaSliderWithField.textField)
			Tooltip.install(alphaSliderWithField.slider, Tooltip("alpha"))
			HBox.setHgrow(alphaSliderWithField.slider, Priority.ALWAYS)

			val setButton = MenuButton(
				"Set",
				null,
				Menus.menuItem("Equidistant Colors (Hue)") { e -> setColorsEquidistant() },
				Menus.menuItem("Value Range") { e -> setAllMinMax() })
			setButton.tooltip = Tooltip("Change channels globally.")

			val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
				headerText = "Conversion of channel data into ARGB color space."
				contentText = DESCRIPTION
			}

			val tpGraphics = HBox(
				Label("Color Conversion"),
				NamedNode.bufferNode(),
				Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
				.also { it.alignment = Pos.CENTER }

			val contents = VBox(alphaBox, setButton, channels)

			return with(TitledPaneExtensions) {
				TitledPane(null, contents)
					.also { it.isExpanded = false }
					.also { it.graphicsOnly(tpGraphics) }
					.also { it.alignment = Pos.CENTER_RIGHT }
					.also { it.tooltip = Tooltip(DESCRIPTION) }
			}
		}

	private fun setColorsEquidistant() {
		val colorBackup = colorProperty.map { it.value }
		val picker = ColorPicker(colorProperty[0].get())
		val step = 360.0 / numChannels
		picker.valueProperty().addListener { obs, oldv, newv ->
			val hue = newv.hue
			for (channel in 0 until numChannels)
				colorProperty[channel].set(Color.hsb(hue + channel * step, newv.saturation, newv.brightness))
		}

		val dialog = Alert(Alert.AlertType.CONFIRMATION)
		dialog.isResizable = true
		dialog.title = Constants.NAME
		dialog.headerText = "Set channel colors equidistant across hue value of HSB space."

		val maximizeButton = MenuButton(
			"Maximize", null,
			Menus.menuItem("Saturation") { e -> picker.value = Color.hsb(picker.value.hue, 1.0, picker.value.brightness) },
			Menus.menuItem("Brightness") { e -> picker.value = Color.hsb(picker.value.hue, picker.value.saturation, 1.0) }
		)
		maximizeButton.tooltip = Tooltip("Maximize saturation or brightness of selected colors.")


		dialog.dialogPane.content = VBox(picker, maximizeButton)


		val bt = dialog.showAndWait()
		if (ButtonType.OK == bt.orElse(ButtonType.CANCEL)) {
			val hue = picker.valueProperty().get().hue
			val saturation = picker.valueProperty().get().saturation
			val brightness = picker.valueProperty().get().brightness
			for (channel in 0 until numChannels)
				colorProperty[channel].set(Color.hsb(hue + channel * step, saturation, brightness))

		} else {
			LOG.debug("Resetting colors")
			for (channel in 0 until numChannels)
				colorProperty[channel].set(colorBackup[channel])
		}

	}

	private fun setAllMinMax() {
		val minBackUp = min.map { it.value }
		val maxBackUp = max.map { it.value }
		val minLabel = Label("Min")
		val maxLabel = Label("Min")
		val minField = NumberField.doubleField(min[0].get(), { m -> true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
		val maxField = NumberField.doubleField(max[0].get(), { m -> true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)

		minField.textField.prefWidth = 50.0
		maxField.textField.prefWidth = 50.0

		minField.valueProperty().addListener { _, _, new -> min.forEach { it.value = new.toDouble() } }
		maxField.valueProperty().addListener { _, _, new -> max.forEach { it.value = new.toDouble() } }

		GridPane.setHgrow(minLabel, Priority.ALWAYS)
		GridPane.setHgrow(maxLabel, Priority.ALWAYS)

		val grid = GridPane()
		grid.add(minLabel, 0, 0)
		grid.add(maxLabel, 0, 1)
		grid.add(minField.textField, 1, 0)
		grid.add(maxField.textField, 1, 1)

		val dialog = Alert(Alert.AlertType.CONFIRMATION)
		dialog.isResizable = true
		dialog.title = Constants.NAME
		dialog.headerText = "Set the same value range for all channels."
		dialog.dialogPane.content = grid

		val bt = dialog.showAndWait()

		if (ButtonType.OK == bt.orElse(ButtonType.CANCEL)) {
			min.forEach { it.set(minField.valueProperty().get()) }
			max.forEach { it.set(maxField.valueProperty().get()) }
		} else {
			for (channel in 0 until numChannels) {
				min[channel].set(minBackUp[channel])
				max[channel].set(maxBackUp[channel])
			}
		}

	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val DESCRIPTION = "" +
				"Convert multi-channel real values into RGB color space with the contrast range " +
				"specified by the min and max values. Each channel is mapped to a user-specified" +
				"color. "
	}

}
