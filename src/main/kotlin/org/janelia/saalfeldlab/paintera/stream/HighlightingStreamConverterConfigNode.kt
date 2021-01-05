package org.janelia.saalfeldlab.paintera.stream

import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.MapChangeListener
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Modality
import javafx.util.converter.NumberStringConverter
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.TriangleButton
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.LongPredicate

class HighlightingStreamConverterConfigNode(private val converter: HighlightingStreamConverter<*>) {

    private val alpha = SimpleDoubleProperty()

    private val activeFragmentAlpha = SimpleDoubleProperty()

    private val activeSegmentAlpha = SimpleDoubleProperty()

    private val colorFromSegment = converter.colorFromSegmentIdProperty()

    private val alphaInt = converter.alphaProperty()

    private val activeFragmentAlphaInt = converter.activeFragmentAlphaProperty()

    private val activeSegmentAlphaInt = converter.activeSegmentAlphaProperty()

    init {
        alpha.addListener { _, _, new -> alphaInt.set(toIntegerBased(new.toDouble())) }
        activeFragmentAlpha.addListener { _, _, new -> activeFragmentAlphaInt.set(toIntegerBased(new.toDouble())) }
        activeSegmentAlpha.addListener { _, _, newv -> activeSegmentAlphaInt.set(toIntegerBased(newv.toDouble())) }

        alphaInt.addListener { obs, oldv, newv -> alpha.set(toDoubleBased(newv.toInt())) }
        activeFragmentAlphaInt.addListener { _, _, new -> activeFragmentAlpha.set(toDoubleBased(new.toInt())) }
        activeSegmentAlphaInt.addListener { _, _, new -> activeSegmentAlpha.set(toDoubleBased(new.toInt())) }

		alpha.value = toDoubleBased(alphaInt.value)
		activeFragmentAlpha.value = toDoubleBased(activeFragmentAlphaInt.value)
		activeSegmentAlpha.value = toDoubleBased(activeSegmentAlphaInt.value)
    }

    val node: Node
		get() {
			val gp = GridPane()
			val contents = VBox(gp)

			val textFieldWidth = 30.0
			var row = 0

			run {
				val alphaSlider = Slider(0.0, 1.0, alpha.get())
				alphaSlider.valueProperty().bindBidirectional(alpha)
				alphaSlider.isShowTickLabels = true
				alphaSlider.tooltip = Tooltip("Alpha for inactive fragments.")
				alphaSlider.minWidth = 0.0
				GridPane.setHgrow(alphaSlider, Priority.ALWAYS)
				val alphaField = TextField().also { it.alignment = Pos.BOTTOM_RIGHT }.also { it.minWidth = 0.0 }
				alphaField.textProperty().bindBidirectional(alphaSlider.valueProperty(), NumberStringConverter())
				alphaField.prefWidth = textFieldWidth
				gp.add(alphaSlider, 0, row)
				gp.add(alphaField, 1, row)
				++row
			}

			run {
				LOG.debug("Active fragment alpha={}", activeFragmentAlpha)
				val selectedFragmentAlphaSlider = Slider(0.0, 1.0, activeFragmentAlpha.get())
				selectedFragmentAlphaSlider.valueProperty().bindBidirectional(activeFragmentAlpha)
				selectedFragmentAlphaSlider.isShowTickLabels = true
				selectedFragmentAlphaSlider.tooltip = Tooltip("Alpha for selected fragments.")
				selectedFragmentAlphaSlider.minWidth = 0.0
				GridPane.setHgrow(selectedFragmentAlphaSlider, Priority.ALWAYS)
				val selectedFragmentAlphaField = TextField().also { it.alignment = Pos.BOTTOM_RIGHT }.also { it.minWidth = 0.0 }
				selectedFragmentAlphaField.textProperty().bindBidirectional(selectedFragmentAlphaSlider.valueProperty(), NumberStringConverter())
				selectedFragmentAlphaField.prefWidth = textFieldWidth
				gp.add(selectedFragmentAlphaSlider, 0, row)
				gp.add(selectedFragmentAlphaField, 1, row)
				++row
			}

			run {
				val selectedSegmentAlphaSlider = Slider(0.0, 1.0, activeSegmentAlpha.get())
				selectedSegmentAlphaSlider.valueProperty().bindBidirectional(activeSegmentAlpha)
				selectedSegmentAlphaSlider.isShowTickLabels = true
				selectedSegmentAlphaSlider.tooltip = Tooltip("Alpha for active segments.")
				val selectedSegmentAlphaField = TextField().also { it.alignment = Pos.BOTTOM_RIGHT }.also { it.minWidth = 0.0 }
				selectedSegmentAlphaField.minWidth = 0.0
				GridPane.setHgrow(selectedSegmentAlphaField, Priority.ALWAYS)
				selectedSegmentAlphaField.textProperty().bindBidirectional(
						selectedSegmentAlphaSlider.valueProperty(),
						NumberStringConverter()
				)
				selectedSegmentAlphaField.prefWidth = textFieldWidth
				gp.add(selectedSegmentAlphaSlider, 0, row)
				gp.add(selectedSegmentAlphaField, 1, row)
				++row
			}

			run {
				val tf = NumberField.longField(1, LongPredicate { true }, *ObjectField.SubmitOn.values())
				tf.valueProperty().addListener { _, _, new -> new?.toLong()?.let { converter.stream.setSeed(it); converter.stream.clearCache() } }
				converter.stream.addListener { tf.valueProperty().value = converter.stream.seed }
				tf.valueProperty().value = converter.stream.seed
				tf.textField().tooltip = Tooltip("Press enter or focus different UI element to submit seed value.")
				tf.textField().alignment = Pos.CENTER_RIGHT
				HBox.setHgrow(tf.textField(), Priority.ALWAYS)

				val buttons = VBox(
						TriangleButton.create(12.0).also { it.onMouseClicked = EventHandler { converter.stream.incSeed(); converter.stream.clearCache() } }.also { it.rotate = 180.0 },
						TriangleButton.create(12.0).also { it.onMouseClicked = EventHandler { converter.stream.decSeed(); converter.stream.clearCache() } })
						.also { it.alignment = Pos.CENTER_LEFT }
						.also { it.spacing = 4.0 }
				// TODO how can we get the buttons inside the TextField? This does not work, either the buttons block mouse clicks
				// TODO on entire text field or are completely ignored
//				val stackPane = StackPane(
//						tf.textField(),
//						buttons.also { it.isMouseTransparent = true })
//						.also { it.alignment = Pos.CENTER_LEFT }
//						.also { it.isPickOnBounds = false }
				val seedBox = HBox(
						Labels.withTooltip("Seed", "Seed value for pseudo-random color distribution"),
						buttons,
						tf.textField())
						.also { it.spacing = 4.0 }
				seedBox.alignment = Pos.CENTER
				contents.children.add(seedBox)
			}


			run {
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
						val id = java.lang.Long.parseLong(addIdField.text)
						converter.setColor(id, addColorPicker.value)
						addIdField.text = ""
					} catch (e: NumberFormatException) {
						LOG.error("Not a valid long/integer format: {}", addIdField.text)
					}
				}

				run {
					val hideLockedSegments = CheckBox("Hide locked segments.")
					hideLockedSegments.tooltip = Tooltip("Hide locked segments (toggle lock with L)")
					hideLockedSegments.selectedProperty().bindBidirectional(converter.hideLockedSegmentsProperty())
					contents.children.add(hideLockedSegments)
				}

				run {
					val colorFromSegmentId = CheckBox("Color From segment Id.")
					colorFromSegmentId.tooltip = Tooltip(
							"Generate fragment color from segment id (on) or fragment id (off)")
					colorFromSegmentId.selectedProperty().bindBidirectional(colorFromSegment)
					contents.children.add(colorFromSegmentId)
				}

				val colorContents = GridPane()
				colorContents.hgap = 5.0
				val colorPane = TitledPane("Custom Colors", colorContents).also { it.isExpanded = false }
				val colorsChanged = MapChangeListener<Long, Color> { change ->
					InvokeOnJavaFXApplicationThread.invoke {
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
				contents.children.add(colorPane)
			}


			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = "Conversion of label data into ARGB color space." }
					.also { it.contentText = COLOR_CONVERSION_DESCRIPTION }

			val tpGraphics = HBox(
					Label("Color Conversion"),
					Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER }

			return with (TitledPaneExtensions) {
				TitledPane(null, contents)
						.also { it.isExpanded = false }
						.also { it.graphicsOnly(tpGraphics) }
						.also { it.alignment = Pos.CENTER_RIGHT }
						.also { it.tooltip = Tooltip(COLOR_CONVERSION_DESCRIPTION) }
			}
		}

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val COLOR_CONVERSION_DESCRIPTION = "" +
				"Label data is converted into RGB space by assigning pseudo-randomly distributed, " +
				"fully saturated colors to label ids. Different alpha (opacity) values can be configured for " +
				"(1) regular labels, " +
				"(2) selected fragments, and " +
				"(3) fragments that are not selected but are contained in the same segment as any selected fragment."

        private fun toIntegerBased(opacity: Double): Int {
            return (255 * opacity + 0.5).toInt()
        }

        private fun toDoubleBased(opacity: Int): Double {
            return opacity / 255.0
        }
    }

}
