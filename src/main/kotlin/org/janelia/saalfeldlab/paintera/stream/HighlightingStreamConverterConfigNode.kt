package org.janelia.saalfeldlab.paintera.stream

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.MapChangeListener
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.converter.NumberStringConverter
import net.imglib2.type.label.Label.*
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.TriangleButton
import org.slf4j.LoggerFactory
import java.lang.Long.parseLong
import java.lang.invoke.MethodHandles

class HighlightingStreamConverterConfigNode(private val converter: HighlightingStreamConverter<*>) {

	private val alpha = SimpleDoubleProperty()

	private val activeFragmentAlpha = SimpleDoubleProperty()

	private val activeSegmentAlpha = SimpleDoubleProperty()

	private val colorFromSegment = converter.colorFromSegmentIdProperty()

	private val alphaInt = converter.alphaProperty()

	private val activeFragmentAlphaInt = converter.activeFragmentAlphaProperty()

	private val activeSegmentAlphaInt = converter.activeSegmentAlphaProperty()

	init {
		/* bind the label alpha */
		alpha.addListener { _, _, new -> alphaInt.set(toIntegerBased(new.toDouble())) }
		alphaInt.addListener { _, _, newv -> alpha.set(toDoubleBased(newv.toInt())) }
		alpha.value = toDoubleBased(alphaInt.value)

		/* bidn the active fragment alpha */
		activeFragmentAlpha.addListener { _, _, new -> activeFragmentAlphaInt.set(toIntegerBased(new.toDouble())) }
		activeFragmentAlphaInt.addListener { _, _, new -> activeFragmentAlpha.set(toDoubleBased(new.toInt())) }
		activeFragmentAlpha.value = toDoubleBased(activeFragmentAlphaInt.value)

		/* bind the active segment alpha */
		activeSegmentAlpha.addListener { _, _, newv -> activeSegmentAlphaInt.set(toIntegerBased(newv.toDouble())) }
		activeSegmentAlphaInt.addListener { _, _, new -> activeSegmentAlpha.set(toDoubleBased(new.toInt())) }
		activeSegmentAlpha.value = toDoubleBased(activeSegmentAlphaInt.value)

	}

	val node: Node
		get() {
			val gp = GridPane()
			val contents = VBox(gp)

			var row = 0
			listOf(
				newAlphaSlider("Alpha for inactive fragments.", alpha),
				newAlphaSlider("Alpha for active fragments.", activeFragmentAlpha),
				newAlphaSlider("Alpha for active segments.", activeSegmentAlpha)
			).forEach { (slider, textfield) ->
				gp.add(slider, 0, row)
				gp.add(textfield, 1, row)
				++row
			}

			val streamSeedField = NumberField.longField(1, { true }, *ObjectField.SubmitOn.values())
			streamSeedField.valueProperty().addListener { _, _, new -> new?.toLong()?.let { converter.stream.setSeed(it); converter.stream.clearCache() } }
			converter.stream.addListener { streamSeedField.valueProperty().value = converter.stream.seed }
			streamSeedField.valueProperty().value = converter.stream.seed
			streamSeedField.textField.tooltip = Tooltip("Press enter or focus different UI element to submit seed value.")
			streamSeedField.textField.alignment = Pos.CENTER_RIGHT
			HBox.setHgrow(streamSeedField.textField, Priority.ALWAYS)

			val buttons = VBox(
				TriangleButton.create(12.0).apply {
					onMouseClicked = EventHandler { converter.stream.incSeed(); converter.stream.clearCache() }
					rotate = 180.0
				},
				TriangleButton.create(12.0).apply { onMouseClicked = EventHandler { converter.stream.decSeed(); converter.stream.clearCache() } }
			).apply {
				alignment = Pos.CENTER_LEFT
				spacing = 4.0
			}
			val seedBox = HBox(
				Labels.withTooltip("Seed", "Seed value for pseudo-random color distribution"),
				buttons,
				streamSeedField.textField
			).apply { spacing = 4.0 }

			seedBox.alignment = Pos.CENTER
			contents.children.add(seedBox)


			val colorPickerWidth = 30.0
			val buttonWidth = 40.0
			val colorsMap = converter.userSpecifiedColors()
			val addButton = Button("+")
			addButton.prefWidth = buttonWidth
			val addColorPicker = ColorPicker()
			addColorPicker.prefWidth = colorPickerWidth
			val addIdField = TextField().also { it.promptText = "Fragment/Segment id" }
			GridPane.setHgrow(addIdField, Priority.ALWAYS)
			addButton.setOnAction { event ->
				event.consume()
				try {
					val id = parseLong(addIdField.text)
					converter.setColor(id, addColorPicker.value)
					addIdField.text = ""
				} catch (e: NumberFormatException) {
					LOG.error("Not a valid long/integer format: {}", addIdField.text)
				}
			}

			val hideLockedSegments = CheckBox("Hide locked segments.")
			hideLockedSegments.tooltip = Tooltip("Hide locked segments (toggle lock with L)")
			hideLockedSegments.selectedProperty().bindBidirectional(converter.hideLockedSegmentsProperty())
			contents.children.add(hideLockedSegments)

			val colorFromSegmentId = CheckBox("Color From segment ID.")
			colorFromSegmentId.tooltip = Tooltip( "Generate fragment color from segment ID (on) or fragment ID (off)" )
			colorFromSegmentId.selectedProperty().bindBidirectional(colorFromSegment)
			contents.children.add(colorFromSegmentId)

			val colorContents = GridPane()
			colorContents.hgap = 5.0
			val colorPane = TitledPane("Custom Colors", colorContents).also { it.isExpanded = false }
			val colorsChanged = MapChangeListener<Long, Color> { change ->
				InvokeOnJavaFXApplicationThread {
					var gridRow = 0
					colorContents.children.clear()
					val it = colorsMap.entries.iterator()
					while (it.hasNext()) {
						val entry = it.next()
						val tf = TextField(entry.key.toString())
						tf.isEditable = false
						GridPane.setHgrow(tf, Priority.ALWAYS)
						val colorPicker = ColorPicker(entry.value)
						colorPicker.prefWidth = colorPickerWidth
						colorPicker.valueProperty().addListener { _, _, newv -> converter.setColor(entry.key, newv) }
						val removeButton = Button("X")
						removeButton.prefWidth = buttonWidth
						removeButton.setOnAction { converter.removeColor(entry.key) }
						colorContents.add(tf, 0, gridRow)
						colorContents.add(colorPicker, 1, gridRow)
						colorContents.add(removeButton, 2, gridRow)
						++gridRow
					}
					colorContents.add(addIdField, 0, gridRow)
					colorContents.add(addColorPicker, 1, gridRow)
					colorContents.add(addButton, 2, gridRow)
				}
			}
			colorsMap.addListener(colorsChanged)
			colorsChanged.onChanged(null)


			val backgroundIdVisible = CheckBox("Background ID (0) Visible ")
			backgroundIdVisible.tooltip = Tooltip( "Make the background ID visible" )
			val backgroundIsVisible = converter.getStream().overrideAlpha.get(BACKGROUND) != 0
			backgroundIdVisible.selectedProperty().set(backgroundIsVisible)
			backgroundIdVisible.selectedProperty().addListener { obs, oldv, visible ->
				if (visible)
					converter.getStream().overrideAlpha.remove(BACKGROUND)
				else
					converter.getStream().overrideAlpha.put(BACKGROUND, 0)
				converter.getStream().clearCache()
			}
			contents.children.add(backgroundIdVisible)


			contents.children.add(colorPane)


			val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
					headerText = "Conversion of label data into ARGB color space."
					contentText = COLOR_CONVERSION_DESCRIPTION
				}

			val tpGraphics = HBox(
				Label("Color Conversion"),
				NamedNode.bufferNode(),
				Button("").apply {
					addStyleClass(Style.HELP_ICON)
					setOnAction { helpDialog.show() }
				}
			)
				.also { it.alignment = Pos.CENTER }

			return with(TitledPaneExtensions) {
				TitledPane(null, contents).apply {
					isExpanded = false
					graphicsOnly(tpGraphics)
					alignment = Pos.CENTER_RIGHT
					tooltip = Tooltip(COLOR_CONVERSION_DESCRIPTION)
				}
			}
		}

	private data class SliderWithTextField(val slider: Slider, val textField: TextField) {}

	private fun newAlphaSlider(toolTipText: String, toBind: DoubleProperty): SliderWithTextField {
		val textField = TextField().also { it.alignment = Pos.BOTTOM_RIGHT }.also { it.minWidth = 0.0 }
		val slider = Slider(0.0, 1.0, toBind.get()).apply {
			valueProperty().bindBidirectional(toBind)
			isShowTickLabels = true
			minWidth = 0.0
			tooltip = Tooltip(toolTipText)
			GridPane.setHgrow(this, Priority.ALWAYS)
		}

		textField.apply {
			textProperty().bindBidirectional(slider.valueProperty(), NumberStringConverter())
			prefWidth = TEXT_FIELD_WIDTH
		}

		return SliderWithTextField(slider, textField)
	}

	companion object {
		const val TEXT_FIELD_WIDTH = 100.0

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val COLOR_CONVERSION_DESCRIPTION = "" +
				"Label data is converted into RGB space by assigning pseudo-randomly distributed, " +
				"fully saturated colors to label ids. Different alpha (opacity) values can be configured for:\n" +
				"\t(1) regular labels\n " +
				"\t(2) selected fragments\n" +
				"\t(3) unselected fragments in selected segments"

		private fun toIntegerBased(opacity: Double): Int {
			return (255 * opacity + 0.5).toInt()
		}

		private fun toDoubleBased(opacity: Int): Double {
			return opacity / 255.0
		}
	}

}
