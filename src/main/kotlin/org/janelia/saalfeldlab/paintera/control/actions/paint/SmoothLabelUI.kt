package org.janelia.saalfeldlab.paintera.control.actions.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.*
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.controlsfx.control.SegmentedButton
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.InvalidUserInput
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_GLYPH
import org.janelia.saalfeldlab.paintera.Style.RESET_GLYPH
import org.janelia.saalfeldlab.paintera.control.actions.paint.InfillStrategyUI.entries
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.finalizeSmoothing
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.smoothJob
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.smoothTaskLoop
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabelUI.Model
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import org.janelia.saalfeldlab.paintera.ui.hGrow
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import net.imglib2.type.label.Label as Imglib2Label

private enum class LabelSelectionUI(val strategy: LabelSelection, val makeNode: Model.() -> ToggleButton) {
	ActiveFragments(LabelSelection.ActiveFragments, {
		ToggleButton("Active Fragments").apply {
			onAction = EventHandler { labelSelectionProperty.value = LabelSelection.ActiveFragments }
		}
	}),
	ActiveSegments(LabelSelection.ActiveSegments, {
		ToggleButton("Active Segments").apply {
			onAction = EventHandler { labelSelectionProperty.value = LabelSelection.ActiveSegments }
		}
	});

	companion object {
		fun makeNode(state: Model) = let {
			TitledPane("", null).apply titlePane@{
				isExpanded = true
				isCollapsible = false
				graphic = HBox(5.0).hGrow {
					minWidthProperty().bind(this@titlePane.widthProperty())
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Label Selection")
					children += Pane().hGrow()
				}
				content = VBox(10.0).apply {
					children += LabelSelectionUI.entries
						.map {
							it.makeNode(state).apply {
								if (state.labelSelectionProperty.value == it.strategy)
									selectedProperty().set(true)
							}
						}
						.toTypedArray()
						.let { SegmentedButton(*it) }
				}
			}

		}
	}
}

private enum class InfillStrategyUI(val strategy: InfillStrategy, val makeNode: Model.() -> ToggleButton) {
	NearestLabel(InfillStrategy.NearestLabel, {
		ToggleButton("Nearest Label").apply {
			onAction = EventHandler { infillStrategyProperty.value = InfillStrategy.NearestLabel }
		}
	}),
	Background(InfillStrategy.Background, {
		ToggleButton("Background").apply {
			onAction = EventHandler { infillStrategyProperty.value = InfillStrategy.Background }
		}
	}),
	Replace(InfillStrategy.Replace, {
		ToggleButton("Specify Label").apply {
			onAction = EventHandler { infillStrategyProperty.value = InfillStrategy.Replace }
		}
	});

	companion object {
		fun makeNode(state: Model) = with(state) {
			TitledPane("", null).apply titlePane@{
				isExpanded = true
				isCollapsible = false
				graphic = HBox(5.0).hGrow {
					minWidthProperty().bind(this@titlePane.widthProperty())
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Infill Options")
					children += Pane().hGrow()
				}
				content = VBox(10.0).apply {
					lateinit var replaceWithButton: ToggleButton
					children += entries
						.map {
							it.makeNode(state).apply {
								if (state.infillStrategyProperty.value == it.strategy)
									selectedProperty().set(true)
								if (it == Replace)
									replaceWithButton = this
							}
						}
						.toTypedArray()
						.let { SegmentedButton(*it) }
					children += HBox(10.0).apply {
						disableProperty().bind(replaceWithButton.selectedProperty().not())

						children += NumberField.longField(replacementLabelProperty.get(), { it >= Imglib2Label.BACKGROUND }, *SubmitOn.entries.toTypedArray()).run {
							replacementLabelProperty.bindBidirectional(valueProperty())
							textField.hGrow()
						}
						children += Button().apply {
							styleClass += ADD_GLYPH
							graphic = FontAwesome[FontAwesomeIcon.PLUS, 2.0]
							onAction = EventHandler { replacementLabelProperty.set(newId()) }
							tooltip = Tooltip("Next New ID")
						}
					}
				}
			}

		}
	}
}

private fun Model.makeSmoothDirectionButton(direction: SmoothDirection, hover: String): RadioButton {
	return RadioButton(direction.name).apply {
		tooltip = Tooltip(hover)
		onAction = EventHandler { smoothDirectionProperty.value = direction }
	}
}

private enum class SmoothDirectionUI(val direction: SmoothDirection, val makeNode: Model.() -> ToggleButton) {
	In(SmoothDirection.In, { makeSmoothDirectionButton(SmoothDirection.In, "Only Smooth in to the selected labels (Shrink)") }),
	Out(SmoothDirection.Out, { makeSmoothDirectionButton(SmoothDirection.Out, "Only Smooth out from the selected labels (Grow)") }),
	Both(SmoothDirection.Both, { makeSmoothDirectionButton(SmoothDirection.Both, "Smooth into and out from the selected labels (Grow and Shrink)") });

	companion object {

		fun makeNode(state: Model) = let {
			TitledPane("", null).apply titlePane@{
				isExpanded = true
				isCollapsible = false
				graphic = HBox(5.0).hGrow {
					minWidthProperty().bind(this@titlePane.widthProperty())
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Smooth Direction")
					children += Pane().hGrow()
				}
				content = VBox(10.0).apply {
					val toggleGroup = ToggleGroup()
					children += SmoothDirectionUI.entries
						.map {
							it.makeNode(state).apply {
								this.toggleGroup = toggleGroup
								if (state.smoothDirectionProperty.value == it.direction)
									selectedProperty().set(true)
							}
						}.toTypedArray()
				}
			}

		}
	}
}

internal class SmoothLabelUI(val model: Model) : VBox(10.0) {

	interface Model {

		val labelSelectionProperty: ObjectProperty<LabelSelection>
		val infillStrategyProperty: ObjectProperty<InfillStrategy>
		val smoothDirectionProperty: ObjectProperty<SmoothDirection>
		val replacementLabelProperty: LongProperty
		val statusProperty: ObjectProperty<SmoothStatus>
		val progressProperty: DoubleProperty
		val kernelSizeProperty: IntegerProperty
		val smoothThresholdProperty: DoubleProperty
		val minKernelSize: Int
		val maxKernelSize: Int
		val canApply: BooleanExpression
		val canCancel: BooleanExpression

		fun newId(): Long

		companion object {

			fun Model.getDialog(titleText: String = "Smooth Label"): Dialog<Boolean> {

				return Dialog<Boolean>().apply {
					Paintera.registerStylesheets(dialogPane)
					dialogPane.buttonTypes += ButtonType.APPLY
					dialogPane.buttonTypes += ButtonType.CANCEL
					title = titleText
					dialogPane.content = SmoothLabelUI(this@getDialog)

					this.initAppDialog()
					val cleanupOnDialogClose = {
						smoothTaskLoop?.cancel()
						InvokeOnJavaFXApplicationThread { close() }
					}
					dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
						val disableBinding = canApply.map { !it }
						applyButton.disableProperty().bind(disableBinding)
						applyButton.addEventFilter(ActionEvent.ACTION) { event ->
							//So the dialog doesn't close until the smoothing is done
							event.consume()
							// but listen for when the smoothTask finishes
							smoothTaskLoop?.invokeOnCompletion { cause ->
								cause?.let {
									cleanupOnDialogClose()
								}
							}
							// indicate the smoothTask should try to apply the current smoothing mask to canvas
							progressProperty.set(0.0)
							finalizeSmoothing = true
						}
					}
					val cancelButton = dialogPane.lookupButton(ButtonType.CANCEL)
					val disableBinding = canCancel.map { !it }
					cancelButton.disableProperty().bind(disableBinding)
					cancelButton.addEventFilter(ActionEvent.ACTION) { _ -> cleanupOnDialogClose() }
					dialogPane.scene.window.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
						if (event.code == KeyCode.ESCAPE && (statusProperty.value == SmoothStatus.Smoothing || (smoothJob?.isActive == true))) {
							/* Cancel if still running */
							event.consume()
							smoothJob?.cancel("Escape Pressed")
							progressProperty.set(0.0)
						}
					}
					dialogPane.scene.window.setOnCloseRequest {
						if (canCancel.value)
							cleanupOnDialogClose()
					}
				}
			}
		}
	}

	class Default : Model {
		override val labelSelectionProperty = SimpleObjectProperty(LabelSelection.ActiveSegments)
		override val infillStrategyProperty = SimpleObjectProperty(InfillStrategy.NearestLabel)
		override val smoothDirectionProperty = SimpleObjectProperty(SmoothDirection.Both)
		override val replacementLabelProperty = SimpleLongProperty(0L)

		override val statusProperty = SimpleObjectProperty(SmoothStatus.Empty)
		override val canApply = statusProperty.isEqualTo(SmoothStatus.Done)
		override val canCancel = statusProperty.isNotEqualTo(SmoothStatus.Applying)
		override val progressProperty = SimpleDoubleProperty(0.0)
		override val kernelSizeProperty = SimpleIntegerProperty()
		override val smoothThresholdProperty = SimpleDoubleProperty(0.5)
		override val minKernelSize = 0
		override val maxKernelSize = 100

		override fun newId(): Long {
			TODO("Not yet implemented")
		}

	}

	init {
		isFillWidth = true
		children += SmoothDirectionUI.makeNode(model)
		children += LabelSelectionUI.makeNode(model)
		children += InfillStrategyUI.makeNode(model)

		children += VBox(10.0).apply {
			children += smoothThresholdNode()
			children += kernelSizeNode()
		}
		children += HBox(10.0).apply {
			children += Label().apply {
				HBox.setHgrow(this, Priority.NEVER)
				model.statusProperty.subscribe { status ->
					InvokeOnJavaFXApplicationThread {
						text = status.text
						requestLayout()
					}
				}
			}
			children += AnimatedProgressBar().apply {
				progressTargetProperty.unbind()
				progressTargetProperty.bind(model.progressProperty)
				HBox.setHgrow(this, Priority.ALWAYS)
				maxWidth = Double.MAX_VALUE
			}
		}
	}

	data class SliderWithTextInputNode(val label: Label, val resetBtn: Button, val textField: TextField, val slider: Slider) {

		fun makeNode() = VBox(5.0).hGrow(Priority.ALWAYS) {
			children += HBox(10.0, label.hGrow(Priority.NEVER), textField.hGrow(), resetBtn.hGrow(Priority.NEVER)).apply { alignment = Pos.CENTER_RIGHT }
			children += HBox(10.0, slider.hGrow(Priority.ALWAYS))
		}
	}

	private fun kernelSizeNode(): Node {

		val label = Label("Kernel size (physical units)")

		val minKernelSize = model.minKernelSize.toDouble()
		val maxKernelSize = model.maxKernelSize.toDouble()
		val initialKernelSize = model.kernelSizeProperty.get()
		val kernelSizeSlider = Slider(log10(minKernelSize).coerceAtLeast(0.0), log10(maxKernelSize), log10(initialKernelSize.toDouble()))
		val kernelSizeField = NumberField.intField(initialKernelSize, { it > 0.0 }, *SubmitOn.values())

		/* slider sets field */
		kernelSizeSlider.valueProperty().subscribe { old, new ->
			kernelSizeField.valueProperty().set(10.0.pow(new.toDouble()).roundToInt())
		}
		/* field sets slider*/
		kernelSizeField.valueProperty().subscribe { fieldVal ->
			/* Let the user go over if they want to explicitly type a larger number in the field.
			* otherwise, set the slider */
			if (fieldVal.toDouble() <= maxKernelSize) {
				val sliderVal = log10(fieldVal.toDouble())
				kernelSizeSlider.valueProperty().set(sliderVal)
			}
		}

		val prevStableKernelSize = SimpleIntegerProperty(initialKernelSize)

		val stableKernelSizeBinding = Bindings
			.`when`(kernelSizeSlider.valueChangingProperty().not())
			.then(kernelSizeField.valueProperty())
			.otherwise(prevStableKernelSize)

		model.kernelSizeProperty.bind(stableKernelSizeBinding)
		model.kernelSizeProperty.subscribe { kernelSize ->
			kernelSize ?: return@subscribe

			prevStableKernelSize.value = kernelSize.toInt()
		}

		val resetBtn = Button().apply {
			styleClass += RESET_GLYPH
			graphic = FontAwesome[FontAwesomeIcon.UNDO, 2.0]
			setOnAction { kernelSizeField.valueProperty().set(initialKernelSize) }
			tooltip = Tooltip("Reset Threshold")
		}

		return SliderWithTextInputNode(label, resetBtn, kernelSizeField.textField, kernelSizeSlider).makeNode()
	}

	private fun smoothThresholdNode(): Node {

		val label = Label("Smooth Threshold")

		val minThreshold = .001
		val maxThreshold = .999
		val threshold = model.smoothThresholdProperty.get()
		val smoothThresholdSlider = Slider(minThreshold, maxThreshold, threshold)

		val converter = object : StringConverter<Number>() {
			override fun toString(`object`: Number?): String? {
				`object` ?: return null
				return BigDecimal(`object`.toDouble()).setScale(3, RoundingMode.HALF_UP).toString()
			}

			override fun fromString(string: String?): Number? {
				string ?: return null
				val value = BigDecimal(string).setScale(3, RoundingMode.HALF_UP).toDouble()
				if (value <= minThreshold || value >= maxThreshold)
					throw InvalidUserInput("Illegal value: $string")
				return value
			}
		}
		val smoothThresholdField = NumberField(SimpleDoubleProperty(threshold), converter, *SubmitOn.entries.toTypedArray())
		/* slider and field set each other */
		smoothThresholdSlider.valueProperty().bindBidirectional(smoothThresholdField.valueProperty())

		model.smoothThresholdProperty.bind(smoothThresholdField.valueProperty())

		val resetBtn = Button().apply {
			styleClass += RESET_GLYPH
			graphic = FontAwesome[FontAwesomeIcon.UNDO, 2.0]
			setOnAction { smoothThresholdField.valueProperty().set(0.5) }
			tooltip = Tooltip("Reset Threshold")
		}
		return SliderWithTextInputNode(label, resetBtn, smoothThresholdField.textField, smoothThresholdSlider).makeNode()
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {
		val model = SmoothLabelUI.Default()

		val dialog = model.getDialog().apply {
			val reloadButton = ButtonType("Reload", ButtonBar.ButtonData.LEFT)
			dialogPane.buttonTypes += reloadButton
			(dialogPane.lookupButton(reloadButton) as? Button)?.addEventFilter(ActionEvent.ACTION) {
				dialogPane.content = SmoothLabelUI(model)
				it.consume()
			}
		}

		InvokeOnJavaFXApplicationThread {
			delay(200)
			val curState = (dialog.dialogPane.content as? SmoothLabelUI)?.model ?: return@InvokeOnJavaFXApplicationThread
			var prev = curState.progressProperty.get()
			while (prev < 1.0) {
				prev = prev + .05
				curState.apply {
					statusProperty.value = SmoothStatus.entries.random()
					progressProperty.set(prev)
				}
				delay(200)
			}
		}

		dialog.show()
	}
}