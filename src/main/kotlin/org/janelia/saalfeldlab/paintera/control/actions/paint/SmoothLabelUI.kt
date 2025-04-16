package org.janelia.saalfeldlab.paintera.control.actions.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlinx.coroutines.delay
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Style.ADD_GLYPH
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import kotlin.math.floor
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import net.imglib2.type.label.Label as Imglib2Label

internal class SmoothLabelUI(
	val state: SmoothLabelUIState
) : VBox(10.0) {

	init {
		isFillWidth = true
		children += HBox(10.0).apply {
//				disableProperty().bind(paintera.baseView.isDisabledProperty)
//				cursorProperty().bind(paintera.baseView.node.cursorProperty())
			children += Label("Replacement Label").apply { alignment = Pos.BOTTOM_RIGHT }
			val replacementLabelField = NumberField.longField(Imglib2Label.BACKGROUND, { it >= Imglib2Label.BACKGROUND }, *SubmitOn.entries.toTypedArray())
			HBox.setHgrow(replacementLabelField.textField, Priority.ALWAYS)
			state.replacementLabelProperty.bindBidirectional(replacementLabelField.valueProperty())
			children += nextIdButton { replacementLabelField.valueProperty().set(state.newId()) }
			children += replacementLabelField.textField
			children += CheckBox("").apply {
				alignment = Pos.CENTER_LEFT
				tooltip = Tooltip("Select Replacement ID")
				selectedProperty().bindBidirectional(state.activateReplacementLabelProperty)
				selectedProperty().set(true)
			}
		}
		children += Separator(Orientation.HORIZONTAL)

		children += VBox(10.0).apply {
			kernelSizeNodes().let { (label, textField, slider) ->
				children += HBox(10.0, label, textField)
				children += HBox(10.0, slider)
				HBox.setHgrow(label, Priority.NEVER)
				HBox.setHgrow(slider, Priority.ALWAYS)
				HBox.setHgrow(textField, Priority.ALWAYS)
			}
		}
		children += HBox(10.0).apply {
			children += Label().apply {
				HBox.setHgrow(this, Priority.NEVER)
				state.statusProperty.subscribe { status ->
					InvokeOnJavaFXApplicationThread {
						text = status.text
						requestLayout()
					}
				}
			}
			children += AnimatedProgressBar().apply {
				progressTargetProperty.unbind()
				progressTargetProperty.bind(state.progressProperty)
				HBox.setHgrow(this, Priority.ALWAYS)
				maxWidth = Double.MAX_VALUE
			}
		}
	}

	private fun nextIdButton(actionEvent: EventHandler<ActionEvent>) = Button().apply {
		styleClass += ADD_GLYPH
		graphic = FontAwesome[FontAwesomeIcon.PLUS, 2.0]
		onAction = actionEvent
		tooltip = Tooltip("Next New ID")
	}

	private fun kernelSizeNodes(): KernelSizeNodes {

		val label = Label("Kernel size (physical units)").apply { alignment = Pos.BOTTOM_RIGHT }

		val minRes = state.resolution.min()
		val minKernelSize = floor(minRes / 2)
		val maxKernelSize = state.resolution.max() * 10
		val initialKernelSize = state.defaultKernelSize
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

		state.kernelSizeProperty.bind(stableKernelSizeBinding)
		state.kernelSizeProperty.subscribe { kernelSize ->
			kernelSize ?: return@subscribe

			prevStableKernelSize.value = kernelSize.toInt()
		}

		return KernelSizeNodes(label, kernelSizeField.textField, kernelSizeSlider)
	}

	private data class KernelSizeNodes(val label: Label, val textField: TextField, val slider: Slider)
}

fun main() = InvokeOnJavaFXApplicationThread.invokeAndWait {
	val state = object : SmoothLabelUIState {

		override val labelSelectionProperty = SimpleObjectProperty(LabelSelection.ActiveFragments)

		override val infillStrategyProperty = SimpleObjectProperty(InfillStrategy.NearestLabel)
		override val replacementLabelProperty = SimpleLongProperty(0L)
		override val activateReplacementLabelProperty = SimpleBooleanProperty(false)

		override val resolution = doubleArrayOf(1.0, 10.0, 100.0)
		override val defaultKernelSize: Int
			get() {
				val levelResolution = resolution
				val min = levelResolution.min()
				val max = levelResolution.max()
				return (min + (max - min) / 2.0).roundToInt()
			}
		override val kernelSizeProperty = SimpleIntegerProperty(defaultKernelSize)
		override val statusProperty = SimpleObjectProperty(SmoothStatus.Empty)
		override val progressProperty = SimpleDoubleProperty(0.0)

		override fun newId() = (0L..Int.MAX_VALUE).random()
	}

	val root = VBox()
	root.children += Button("Reload").apply {
		onAction = EventHandler {
			root.children.removeIf { it is SmoothLabelUI }
			root.children.add(SmoothLabelUI(state))
		}
	}
	root.children += SmoothLabelUI(state)

	InvokeOnJavaFXApplicationThread {
		delay(200)
		var prev = root.children.firstNotNullOf { it as? SmoothLabelUI }.state.progressProperty.get()
		while (prev < 1.0) {
			prev = prev + .05
			root.children.firstNotNullOf { it as? SmoothLabelUI }.state.apply {
				statusProperty.value = SmoothStatus.entries.random()
				progressProperty.set(prev)
			}
			delay(200)
		}
	}

	Stage().apply {
		scene = Scene(root)
		show()
	}

}