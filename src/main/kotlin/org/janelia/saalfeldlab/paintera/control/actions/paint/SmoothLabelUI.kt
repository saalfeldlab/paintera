package org.janelia.saalfeldlab.paintera.control.actions.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.*
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_GLYPH
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.PositiveDoubleTextFormatter
import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.hGrow
import kotlin.math.log10
import kotlin.math.pow

interface SmoothLabelUIState {
	val replacementLabelProperty: Property<Long?>
	val kernelSizeProperty: Property<Double?>
	val activateReplacementLabelProperty: BooleanProperty

	val minKernelSize: Double
	val maxKernelSize: Double
	val statusProperty: StringProperty
	val progressProperty: DoubleProperty
	val isApplyingMaskProperty : BooleanExpression

	fun nextNewId(): Long

}

class SmoothLabelUI(val state: SmoothLabelUIState) : VBox(10.0) {

	init {
		isFillWidth = true
		padding = Insets(10.0)
		children += HBox(10.0).apply {
			{
				//TODO Caleb: Check if this is necesary (esp. the cursor) and how to improve?
				disableProperty().bind(paintera.baseView.isDisabledProperty)
				cursorProperty().bind(paintera.baseView.node.cursorProperty())
			}
			alignment = Pos.CENTER_RIGHT
			children += Label("Replacement Label").apply { alignment = Pos.BOTTOM_RIGHT }
			children += Button().apply {
				styleClass += ADD_GLYPH
				graphic = FontAwesome[FontAwesomeIcon.PLUS, 2.0]
				onAction = EventHandler { state.replacementLabelProperty.value = state.nextNewId() }
				tooltip = Tooltip("Next New ID")
			}
			children += TextField().hGrow {
				textFormatter = PositiveLongTextFormatter(state.replacementLabelProperty.value).apply {
					state.replacementLabelProperty.bindBidirectional(valueProperty())
				}
			}
			children += CheckBox("").apply {
				alignment = Pos.CENTER_LEFT
				tooltip = Tooltip("Select Replacement ID")
				selectedProperty().bindBidirectional(state.activateReplacementLabelProperty)
				selectedProperty().set(true)
			}
		}
		children += HBox(10.0).apply {
			alignment = Pos.CENTER_LEFT
			children += Label("Kernel Size (physical units)")
			children += TextField().hGrow {
				textFormatter = PositiveDoubleTextFormatter().apply {
					valueProperty().bindBidirectional(state.kernelSizeProperty)
				}
			}
		}
		children += Slider(
			log10(state.minKernelSize),
			log10(state.maxKernelSize),
			log10(state.kernelSizeProperty.value ?: 0.0)
		).apply {
			valueProperty().subscribe { value ->
				state.kernelSizeProperty.value = 10.0.pow(value.toDouble())
			}
		}

		children += HBox(10.0).apply {
			children += Label().hGrow {
				alignment = Pos.CENTER_LEFT
				textProperty().bind(state.statusProperty)
			}
			children += AnimatedProgressBar().hGrow {
				progressTargetProperty.bind(state.progressProperty)
				maxWidth = Double.MAX_VALUE
				progressProperty().subscribe { progress ->
					val prog = progress.toDouble()
					val status = when {
						prog <= 0.0 -> ""
						prog >= 1.0 -> "Done"
						state.isApplyingMaskProperty.value -> "Applying..."
						else -> "Smoothing..."
					}
					InvokeOnJavaFXApplicationThread {
						state.statusProperty.set(status)
					}
				}
			}
		}
	}
}



fun main() {
	InvokeOnJavaFXApplicationThread {

		val state = object : SmoothLabelUIState {
			override val replacementLabelProperty = SimpleObjectProperty<Long>(123L)
			override val kernelSizeProperty = SimpleObjectProperty<Double>(20.0)
			override val activateReplacementLabelProperty = SimpleBooleanProperty(true)
			override val minKernelSize = 1.0
			override val maxKernelSize = 100.0
			override val statusProperty = SimpleStringProperty("Test!")
			override val progressProperty = SimpleDoubleProperty(0.0)
			override val isApplyingMaskProperty = SimpleBooleanProperty(false)

			override fun nextNewId() = (0L..100).random()
		}

		val root = VBox()
		root.apply {
			children += Button("Reload").apply {
				onAction = EventHandler {
					root.children.removeIf { it is SmoothLabelUI }
					root.children.add(SmoothLabelUI(state))
				}
			}
			children += SmoothLabelUI(state)
		}

		CoroutineScope(Dispatchers.Default).launch {
			while (state.progressProperty.value < 1.0) {
				delay(200)
				InvokeOnJavaFXApplicationThread {
					if ( state.progressProperty.value > .5 )
						state.isApplyingMaskProperty.value = true
					state.progressProperty.value += .1
				}
			}
		}

		Paintera.registerStylesheets(root)
		val scene = Scene(root)
		val stage = Stage()
		stage.scene = scene
		stage.show()
	}
}

