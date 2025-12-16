package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.smooth

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.ActionEvent
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import net.imglib2.algorithm.convolution.fast_gauss.FastGauss
import net.imglib2.algorithm.convolution.fast_gauss.FastGaussCalculator
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.ui.ExceptionNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.RESET_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.*
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.smooth.SmoothLabel.mainTaskLoop
import org.janelia.saalfeldlab.paintera.ui.SliderWithTextInputNode
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.denyClose
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import org.janelia.saalfeldlab.paintera.ui.hvGrow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val LOG = KotlinLogging.logger { }

internal class SmoothLabelUI(val model: SmoothLabelModel) : VBox(10.0) {

	companion object {
		fun getDialog(model: SmoothLabelModel, titleText: String = "Smooth Label", onApply: () -> Unit): Dialog<Boolean> {

			return Dialog<Boolean>().apply {
				setResultConverter { it == ButtonType.APPLY }

				Paintera.registerStylesheets(dialogPane)
				dialogPane.buttonTypes += ButtonType.APPLY
				dialogPane.buttonTypes += ButtonType.CANCEL
				title = titleText
				val smoothLabelUI = SmoothLabelUI(model)
				dialogPane.content = smoothLabelUI

				initAppDialog()
				val cleanupOnDialogClose = {
					if (model.canClose.get()) {
						mainTaskLoop?.cancel()
						InvokeOnJavaFXApplicationThread { close() }
					}
				}
				dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
					val disableBinding = model.canApply.and(smoothLabelUI.sliderIsChanging.not()).map { !it }
					applyButton.disableProperty().bind(disableBinding)
					var applyDone = false
					applyButton.addEventFilter(ActionEvent.ACTION) { event ->
						//So the dialog doesn't close until the its done
						if (applyDone)
							return@addEventFilter

						event.consume()
						// but listen for when the task finishes
						mainTaskLoop?.invokeOnCompletion { cause ->
							when (cause) {
								null -> {
									applyDone = true
									InvokeOnJavaFXApplicationThread {
										(dialogPane.lookupButton(ButtonType.APPLY) as Button).fire()
									}
								}
								is CancellationException -> {
									LOG.info { "User cancelled smooth label" }
									InvokeOnJavaFXApplicationThread {
										(dialogPane.lookupButton(ButtonType.CANCEL) as Button).fire()
									}
								}
								else -> {
									LOG.error(cause) { "Exception during $titleText Action" }
									InvokeOnJavaFXApplicationThread {

										(dialogPane.lookupButton(ButtonType.CANCEL) as Button).fire()
										ExceptionNode.exceptionDialog(cause as Exception).showAndWait()
									}
								}
							}
						}
						// indicate the task should try to apply the current mask to canvas
						onApply()
					}
				}
				val disableBinding = model.canClose.map { !it }
				val cancelButton = dialogPane.lookupButton(ButtonType.CANCEL)
				cancelButton.disableProperty().bind(disableBinding)
				(cancelButton as? Button)?.setOnAction { cleanupOnDialogClose() }
				denyClose(model.canClose.not())
			}
		}
	}

	private val sliderIsChanging = SimpleBooleanProperty(false)

	init {
		isFillWidth = true
		children += HBox(10.0).apply {
			children += MorphDirectionUI.makeConfigurationNode(model).hvGrow {
				maxHeight = Double.MAX_VALUE
				maxWidth = Double.MAX_VALUE
			}
			children += LabelSelectionUI.makeConfigurationNode(model).hvGrow {
				maxHeight = Double.MAX_VALUE
				maxWidth = Double.MAX_VALUE
			}
		}
		children += InfillStrategyUI.makeConfigurationNode(model)



		val kernelIsChanging = SimpleBooleanProperty(false)
		val thresholdIsChanging = SimpleBooleanProperty(false)
		sliderIsChanging.bind(kernelIsChanging.or(thresholdIsChanging))

		children += VBox(10.0).apply {
			children += kernelSizeNode(kernelIsChanging)
		}
		children += VBox(10.0).apply {
			children += gaussianThresholdNode(thresholdIsChanging)
		}

		children += HBox(10.0).apply {
			children += TextField().apply {
				prefColumnCount = 6
				background = null
				isEditable = false
				HBox.setHgrow(this, Priority.NEVER)
				model.statusProperty.`when`(sliderIsChanging.not()).subscribe { status ->
					InvokeOnJavaFXApplicationThread {
						text = status.text
						requestLayout()
					}
				}
			}
			children += AnimatedProgressBar().apply {
				progressTargetProperty.unbind()
				/* `when` stops the flickering when the kernel slider is being dragged */
				progressTargetProperty.bind(model.progressProperty.`when`(sliderIsChanging.not()))
				HBox.setHgrow(this, Priority.ALWAYS)
				maxWidth = Double.MAX_VALUE
			}
		}
	}

	private fun kernelSizeNode(kernelIsChanging: BooleanProperty): Node {

		val label = Label("Kernel size (physical units)")

		val resolution = model.getLevelResolution(model.scaleLevel)
		val min = resolution.min()
		val max = resolution.max()
		val defaultKernelSize = model.morphDirectionProperty.get().defaultKernelSize(resolution)

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
		kernelSizeField.valueProperty().subscribe { _, fieldVal ->
			/* Let the user go over if they want to explicitly type a larger number in the field.
			* otherwise, set the slider */
			if (fieldVal.toDouble() <= maxKernelSize) {
				val sliderVal = log10(fieldVal.toDouble())
				kernelSizeSlider.valueProperty().set(sliderVal)
			}

			model.kernelSizeProperty.set(fieldVal.toInt())
		}

		/* the field and slider should both be responsive to direct changes to the model */
		model.kernelSizeProperty.subscribe { _, size ->
			kernelSizeField.value = size
		}

		val resetBtn = Button().apply {
			addStyleClass(RESET_ICON)
			setOnAction { kernelSizeField.valueProperty().set(defaultKernelSize) }
			tooltip = Tooltip("Reset Threshold")
		}

		return SliderWithTextInputNode(label, resetBtn, kernelSizeField.textField, kernelSizeSlider).makeNode()
	}

	private fun gaussianThresholdNode(thresholdIsChanging: BooleanProperty): Node {

		val label = Label("Gaussian Threshold")

		val min = 0.0
		val max = 1.0
		val default = 0.5

		val thresholdSlider = Slider(
			min,
			max,
			default
		)
		val thresholdField = NumberField.doubleField(default, { it in (0.0 .. 1.0) }, *SubmitOn.entries.toTypedArray())

		/* slider sets field */
		thresholdSlider.valueProperty().subscribe { old, new ->
			thresholdField.value = new
		}

		thresholdSlider.valueProperty().bindBidirectional(thresholdField.valueProperty())
		model.gaussianThresholdProperty.bindBidirectional(thresholdField.valueProperty())

		thresholdIsChanging.bind(thresholdSlider.valueChangingProperty())

		val resetBtn = Button().apply {
			addStyleClass(RESET_ICON)
			setOnAction { thresholdField.valueProperty().set(default) }
			tooltip = Tooltip("Reset Threshold")
		}

		return SliderWithTextInputNode(label, resetBtn, thresholdField.textField, thresholdSlider).makeNode()
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {

		val newIds = sequence {
			repeat(Int.MAX_VALUE) {
				yield(it.toLong())
			}
		}.iterator()
		val model = object : SmoothLabelModel by SmoothLabelModel.default() {

			override fun getLevelResolution(scaleLevel: Int) = doubleArrayOf(1.0, 1.0, 1.0)
			override fun nextId() = newIds.next()
		}

		val dialog = SmoothLabelUI.getDialog(model, "Smooth Label") { print("Done!") } .apply {
			val reloadButton = ButtonType("Reload", ButtonBar.ButtonData.LEFT)
			dialogPane.buttonTypes += reloadButton
			(dialogPane.lookupButton(reloadButton) as? Button)?.addEventFilter(ActionEvent.ACTION) {
				dialogPane.content = SmoothLabelUI(model)
				it.consume()
			}
		}

		InvokeOnJavaFXApplicationThread {
			delay(200)
			val statuses = listOf(Status.Empty, SmoothStatus.Smoothing, Status.Applying, Status.Done)
			val curState = (dialog.dialogPane.content as? SmoothLabelUI)?.model ?: return@InvokeOnJavaFXApplicationThread
			var prev = curState.progressProperty.get()
			while (prev < 1.0) {
				prev = prev + .05
				curState.apply {
					statusProperty.value = statuses.random()
					progressProperty.set(prev)
				}
				delay(200)
			}
		}

		dialog.showAndWait()
	}.invokeOnCompletion { cause ->
		cause?.printStackTrace()
		Platform.exit()
	}
}