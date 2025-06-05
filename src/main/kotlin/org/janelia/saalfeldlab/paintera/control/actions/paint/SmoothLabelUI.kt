package org.janelia.saalfeldlab.paintera.control.actions.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.*
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.delay
import org.controlsfx.control.SegmentedButton
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.ui.ExceptionNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_GLYPH
import org.janelia.saalfeldlab.paintera.Style.RESET_GLYPH
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.smoothTaskLoop
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabelUI.Model
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.denyClose
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import net.imglib2.type.label.Label as Imglib2Label

private val LOG = KotlinLogging.logger { }

private enum class LabelSelectionUI(val strategy: LabelSelection, val makeNode: Model.() -> ToggleButton) {
	ActiveFragments(LabelSelection.ActiveFragments, {
		RadioButton("Active Fragments").apply {
			onAction = EventHandler { labelSelectionProperty.value = LabelSelection.ActiveFragments }
		}
	}),
	ActiveSegments(LabelSelection.ActiveSegments, {
		RadioButton("Active Segments").apply {
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
	In(SmoothDirection.Shrink, { makeSmoothDirectionButton(SmoothDirection.Shrink, "Only Smooth inward over the selected labels (Shrink)") }),
	Out(SmoothDirection.Expand, { makeSmoothDirectionButton(SmoothDirection.Expand, "Only Smooth out from the selected labels (Expand)") }),
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
		val labelsToSmoothProperty: ObjectProperty<LongArray?>
		val infillStrategyProperty: ObjectProperty<InfillStrategy>
		val smoothDirectionProperty: ObjectProperty<SmoothDirection>
		val replacementLabelProperty: LongProperty
		val statusProperty: ObjectProperty<SmoothStatus>
		val progressProperty: DoubleProperty
		val kernelSizeProperty: IntegerProperty
		val scaleLevel: Int
		val timepoint: Int
		val canApply: BooleanExpression
		val canClose: BooleanExpression

		fun newId(): Long
		fun getLevelResolution(level: Int = scaleLevel): DoubleArray

		companion object {

			fun Model.getDialog(titleText: String = "Smooth Label", onApply: () -> Unit): Dialog<Boolean> {

				return Dialog<Boolean>().apply {
					Paintera.registerStylesheets(dialogPane)
					dialogPane.buttonTypes += ButtonType.APPLY
					dialogPane.buttonTypes += ButtonType.CANCEL
					title = titleText
					dialogPane.content = SmoothLabelUI(this@getDialog)

					this.initAppDialog()
					val cleanupOnDialogClose = {
						if (canClose.get()) {
							smoothTaskLoop?.cancel()
							InvokeOnJavaFXApplicationThread { close() }
						}
					}
					dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
						val disableBinding = canApply.map { !it }
						applyButton.disableProperty().bind(disableBinding)
						applyButton.addEventFilter(ActionEvent.ACTION) { event ->
							//So the dialog doesn't close until the smoothing is done
							event.consume()
							// but listen for when the smoothTask finishes
							smoothTaskLoop?.invokeOnCompletion { cause ->
								cleanupOnDialogClose()
								cause?.let {
									LOG.error(it) { "Exception during Smooth Action" }
									InvokeOnJavaFXApplicationThread {
										ExceptionNode.exceptionDialog(it as Exception).showAndWait()
									}
								}
							}
							// indicate the smoothTask should try to apply the current smoothing mask to canvas
							progressProperty.set(0.0)
							onApply()
						}
					}
					val disableBinding = canClose.map { !it }
					val cancelButton = dialogPane.lookupButton(ButtonType.CANCEL)
					cancelButton.disableProperty().bind(disableBinding)
					(cancelButton as? Button)?.setOnAction { cleanupOnDialogClose() }
					denyClose(canClose.not())
				}
			}
		}
	}

	class Default : Model {
		override val labelSelectionProperty = SimpleObjectProperty(LabelSelection.ActiveSegments)
		override val labelsToSmoothProperty = SimpleObjectProperty<LongArray?>()
		override val infillStrategyProperty = SimpleObjectProperty(InfillStrategy.NearestLabel)
		override val smoothDirectionProperty = SimpleObjectProperty(SmoothDirection.Both)
		override val replacementLabelProperty = SimpleLongProperty(0L)

		override val statusProperty = SimpleObjectProperty(SmoothStatus.Empty)
		override val canApply = statusProperty.isEqualTo(SmoothStatus.Done)
		override val canClose = statusProperty.isNotEqualTo(SmoothStatus.Applying)
		override val progressProperty = SimpleDoubleProperty(0.0)
		override val kernelSizeProperty = SimpleIntegerProperty()

		//TODO Caleb: Currently, this is always 0
		//  At higher scale levels, currently it can leave small artifacts at the previous boundary when
		//  going back to higher resolution
		override val scaleLevel = 0
		override val timepoint = 0 //NOTE: this is aspirational; timepoint other than 0 currently not tested


		/* A bit misleading to make this a `class`, but it allows us to instantiate and use as a delegate for `Model`
		* and then override `newId` in the implementor of `Model`. This lets us use this as an interface
		* instead of a class. */
		override fun newId() = throw(NotImplementedError("Must be implemented in subclass"))
		override fun getLevelResolution(level: Int) = throw(NotImplementedError("Must be implemented in subclass"))
	}

	init {
		isFillWidth = true
		children += HBox(10.0).apply {
			children += SmoothDirectionUI.makeNode(model).hvGrow {
				maxHeight = Double.MAX_VALUE
				maxWidth = Double.MAX_VALUE
			}
			children += LabelSelectionUI.makeNode(model).hvGrow {
				maxHeight = Double.MAX_VALUE
				maxWidth = Double.MAX_VALUE
			}
		}
		children += InfillStrategyUI.makeNode(model)

		val kernelIsChanging = SimpleBooleanProperty(false)
		children += VBox(10.0).apply {
			children += kernelSizeNode(kernelIsChanging)
		}
		children += HBox(10.0).apply {
			children += TextField().apply {
				prefColumnCount = 6
				background = null
				isEditable = false
				HBox.setHgrow(this, Priority.NEVER)
				model.statusProperty.`when`(kernelIsChanging.not()).subscribe { status ->
					InvokeOnJavaFXApplicationThread {
						text = status.text
						requestLayout()
					}
				}
			}
			children += AnimatedProgressBar().apply {
				progressTargetProperty.unbind()
				/* `when` stops the flickering when the kernel slider is being dragged */
				progressTargetProperty.bind(model.progressProperty.`when`(kernelIsChanging.not()))
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

	private fun kernelSizeNode(kernelIsChanging : BooleanProperty): Node {

		val label = Label("Kernel size (physical units)")

		val resolution = model.getLevelResolution()
		val min = resolution.min()
		val max = resolution.max()
		val defaultKernelSize = model.smoothDirectionProperty.get().defaultKernelSize(resolution)

		val minKernelSize = floor(min / 2).toInt()
		val maxKernelSize = (max * 10).toInt()
		val kernelSizeSlider = Slider(
			log10(minKernelSize.toDouble()).coerceAtLeast(0.0),
			log10(maxKernelSize.toDouble()),
			log10(defaultKernelSize.toDouble())
		)
		val kernelSizeField = NumberField.intField(defaultKernelSize, { it > 0.0 }, *SubmitOn.entries.toTypedArray())

		/* slider sets field */
		kernelSizeSlider.valueProperty().subscribe { old, new ->
			kernelSizeField.valueProperty().set(10.0.pow(new.toDouble()).roundToInt())
		}

		kernelIsChanging.bind(kernelSizeSlider.valueChangingProperty())

		/* field sets slider and model property*/
		kernelSizeField.valueProperty().subscribe { fieldVal ->
			/* Let the user go over if they want to explicitly type a larger number in the field.
			* otherwise, set the slider */
			if (fieldVal.toDouble() <= maxKernelSize) {
				val sliderVal = log10(fieldVal.toDouble())
				kernelSizeSlider.valueProperty().set(sliderVal)
			}

			model.kernelSizeProperty.set(fieldVal.toInt())
		}

		/* the field and slider should both be responsive to direct changes to the model */
		model.kernelSizeProperty.subscribe { size ->
			kernelSizeField.value = size
		}

		val resetBtn = Button().apply {
			styleClass += RESET_GLYPH
			graphic = FontAwesome[FontAwesomeIcon.UNDO, 2.0]
			setOnAction { kernelSizeField.valueProperty().set(defaultKernelSize) }
			tooltip = Tooltip("Reset Threshold")
		}

		return SliderWithTextInputNode(label, resetBtn, kernelSizeField.textField, kernelSizeSlider).makeNode()
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {

		val newIds = sequence {
			repeat(Int.MAX_VALUE) {
				yield(it.toLong())
			}
		}.iterator()
		val model = object : Model by SmoothLabelUI.Default() {

			override fun getLevelResolution(level: Int) = doubleArrayOf(1.0, 1.0, 1.0)
			override fun newId() = newIds.next()
		}

		val dialog = model.getDialog { println("Done!") }.apply {
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
	}.invokeOnCompletion { cause ->
		if (cause != null)
			cause.printStackTrace()
		else
			println("Done!")
		Platform.exit()
	}
}