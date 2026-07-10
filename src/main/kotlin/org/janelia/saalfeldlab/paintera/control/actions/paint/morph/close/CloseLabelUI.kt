package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.beans.property.IntegerProperty
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldListCell
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.util.converter.LongStringConverter
import kotlinx.coroutines.delay
import org.controlsfx.control.SegmentedButton
import org.controlsfx.control.ToggleSwitch
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.ui.ExceptionNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_ICON
import org.janelia.saalfeldlab.paintera.Style.REMOVE_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.Status
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close.CloseGaps.mainTaskLoop
import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.denyClose
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow
import kotlin.coroutines.cancellation.CancellationException

private val LOG = KotlinLogging.logger { }

internal class CloseLabelUI(val model: CloseLabelModel) : VBox(10.0) {

	companion object {
		fun getDialog(model: CloseLabelModel, titleText: String = "Close Gaps", onApply: () -> Unit): Dialog<Boolean> {

			return Dialog<Boolean>().apply {
				setResultConverter { it == ButtonType.APPLY }

				Paintera.registerStylesheets(dialogPane)
				dialogPane.buttonTypes += ButtonType.APPLY
				dialogPane.buttonTypes += ButtonType.CANCEL
				title = titleText
				val closeLabelUI = CloseLabelUI(model)
				dialogPane.content = closeLabelUI

				initAppDialog()
				val cleanupOnDialogClose = {
					if (model.canClose.get()) {
						mainTaskLoop?.cancel()
						InvokeOnJavaFXApplicationThread { close() }
					}
				}
				dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
					val disableBinding = model.canApply.map { !it }
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
									LOG.info { "User cancelled Close Gaps" }
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

	init {
		isFillWidth = true

		children += labelsToCloseSelectionBar()

		/* the two ID lists side by side, like the ReplaceLabel fragment/segment panels */
		children += HBox(10.0).hvGrow {
			children += labelsToClosePanel().hvGrow()
			children += labelsToReplacePanel().hvGrow()
		}

		children += fillOptionsNode()

		children += HBox().apply {
			alignment = Pos.CENTER_RIGHT
			children += ToggleSwitch("Preview result").apply {
				selectedProperty().bindBidirectional(model.previewProperty)
				tooltip = Tooltip("Render the result live over the canvas; toggle off to compare against the original")
			}
		}
		children += HBox(10.0).apply {
			children += TextField().apply {
				prefColumnCount = 6
				background = null
				isEditable = false
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

	private fun labelsToCloseSelectionBar(): TitledPane {
		val presetButtons = listOf(
			presetButton("Current Fragment", "Close gaps in the last selected fragment") {
				model.labelsToClose.setAll(listOf(model.activeFragment))
			},
			presetButton("Current Segment", "Close gaps in the segment of the last selected fragment, bridging between its fragments") {
				model.labelsToClose.setAll(listOf(model.segmentForFragment(model.activeFragment)))
			},
			presetButton("Active Fragments", "Close gaps in all selected fragments; fragments only bridge with themselves") {
				model.labelsToClose.setAll(model.allActiveFragments.toList())
			},
			presetButton("Active Segments", "Close gaps in the segments of all selected fragments, bridging within each segment") {
				model.labelsToClose.setAll(model.allActiveSegments.toList())
			},
		)
		return titledPane("Labels to Close") {
			children += Button("Clear").apply {
				tooltip = Tooltip("Clear the Labels to Close")
				onAction = EventHandler { model.labelsToClose.clear() }
			}
		}.apply {
			content = HBox(5.0).hGrow { children += presetButtons }
		}
	}

	private fun labelsToClosePanel() = VBox(5.0).apply {
		alignment = Pos.CENTER
		children += Label("Labels to Close")
		children += labelListView(model.labelsToClose).hvGrow()
		children += addLabelRow(model.labelsToClose)
	}

	private fun labelsToReplacePanel(): VBox {
		val specifyToggled = model.replaceModeProperty.map { it != ReplaceMode.Specify }

		val modeButtons = ReplaceMode.entries.map { mode ->
			ToggleButton(mode.name).apply {
				tooltip = Tooltip(
					when (mode) {
						ReplaceMode.Background -> "Only fill background voxels"
						ReplaceMode.Any -> "Fill voxels of any label"
						ReplaceMode.Specify -> "Only fill voxels of the listed labels"
					}
				)
				isSelected = model.replaceModeProperty.get() == mode
				onAction = EventHandler { model.replaceModeProperty.value = mode }
			}
		}

		return VBox(5.0).apply {
			alignment = Pos.CENTER
			children += Label("Labels to Replace")
			children += SegmentedButton(*modeButtons.toTypedArray())
			children += labelListView(model.labelsToReplace).hvGrow {
				disableProperty().bind(specifyToggled)
			}
			children += addLabelRow(model.labelsToReplace).apply {
				disableProperty().bind(specifyToggled)
			}
		}
	}

	private fun labelListView(labels: ObservableList<Long>) = ListView(labels).apply {
		cellFactory = TextFieldListCell.forListView(LongStringConverter())
		selectionModel.selectionMode = SelectionMode.MULTIPLE
		prefHeight = 140.0
		onKeyPressed = EventHandler { event ->
			if (!(event.code == KeyCode.DELETE || event.code == KeyCode.BACK_SPACE)) return@EventHandler
			selectionModel.selectedItems.toTypedArray().forEach { labels.remove(it) }
			event.consume()
		}
	}

	private fun addLabelRow(labels: ObservableList<Long>): HBox {
		val addLabelField = TextField().hGrow {
			promptText = "Add a Label ID..."
			textFormatter = PositiveLongTextFormatter()
		}
		val submitLabel = {
			addLabelField.commitValue()
			(addLabelField.textFormatter as? PositiveLongTextFormatter)?.run {
				value?.let { addMissing(labels, it) }
				value = null
			}
		}
		addLabelField.onAction = EventHandler { submitLabel() }
		return HBox(5.0).apply {
			children += addLabelField
			children += Button("Add Label").apply { onAction = EventHandler { submitLabel() } }
		}
	}

	private fun presetButton(text: String, tooltipText: String, action: () -> Unit) = Button(text).hGrow {
		tooltip = Tooltip(tooltipText)
		onAction = EventHandler { action() }
	}

	private fun addMissing(labels: ObservableList<Long>, vararg ids: Long) {
		labels += ids.filter { it !in labels }
	}

	private fun titledPane(titleText: String, headerNodes: HBox.() -> Unit = {}) = TitledPane("", null).apply {
		isExpanded = true
		isCollapsible = false
		contentDisplay = ContentDisplay.GRAPHIC_ONLY
		graphic = HBox(5.0).hGrow {
			padding = Insets(0.0, 20.0, 0.0, 0.0)
			alignment = Pos.CENTER
			children += Label(titleText)
			children += Pane().hGrow()
			headerNodes()
		}
	}

	private fun fillOptionsNode(): TitledPane {
		return titledPane("Fill Options").apply {
			content = VBox(10.0).apply {
				children += HBox(10.0).apply {
					alignment = Pos.CENTER_LEFT
					children += Label("Max gap (voxels)")
					children += stepperIntField(model.gapSizeProperty)
					children += Label("Iterations").apply {
						tooltip = Tooltip("Repeat the fill; gaps filled in one pass can flank the next")
					}
					children += stepperIntField(model.iterationsProperty)
				}
				children += HBox(10.0).apply {
					alignment = Pos.CENTER_LEFT
					children += CheckBox("Specify fill label").apply {
						selectedProperty().bindBidirectional(model.specifyFillLabelProperty)
						tooltip = Tooltip("Close Gaps with this label instead of the flanking target label")
					}
					children += NumberField.longField(model.fillLabelProperty.get(), { it >= 0 }, *SubmitOn.entries.toTypedArray()).run {
						model.fillLabelProperty.bindBidirectional(valueProperty())
						textField.hGrow {
							disableProperty().bind(model.specifyFillLabelProperty.not())
						}
					}
					children += Button().apply {
						addStyleClass(ADD_ICON)
						disableProperty().bind(model.specifyFillLabelProperty.not())
						onAction = EventHandler { model.fillLabelProperty.set(model.nextId()) }
						tooltip = Tooltip("Next New ID")
					}
				}
			}
		}
	}

	private fun stepperIntField(property: IntegerProperty, min: Int = 1): HBox {
		val field = NumberField.intField(property.get(), { it >= min }, *SubmitOn.entries.toTypedArray()).apply {
			property.bindBidirectional(valueProperty())
		}
		val decrement = Button().apply {
			addStyleClass(REMOVE_ICON)
			disableProperty().bind(property.lessThanOrEqualTo(min))
			onAction = EventHandler { property.set((property.get() - 1).coerceAtLeast(min)) }
		}
		val increment = Button().apply {
			addStyleClass(ADD_ICON)
			onAction = EventHandler { property.set(property.get() + 1) }
		}
		return HBox(2.0, decrement, field.textField.hGrow(), increment).apply {
			alignment = Pos.CENTER_LEFT
		}
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {

		val model = object : CloseLabelModel by CloseLabelModel.default() {

			override fun getLevelResolution(scaleLevel: Int) = doubleArrayOf(1.0, 1.0, 1.0)
			override val activeFragment = 123L
			override val allActiveFragments = longArrayOf(1, 2, 3, 4)
			override val allActiveSegments = longArrayOf(10, 20)
			override fun fragmentsForSegment(segment: Long) = longArrayOf(segment * 10, segment * 10 + 1)
			override fun segmentForFragment(fragment: Long) = fragment

			private var fakeNextId = 100L
			override fun nextId() = fakeNextId++
		}
		model.labelsToClose.setAll(model.allActiveSegments.toList())

		val dialog = CloseLabelUI.getDialog(model, "Close Gaps") { println("Done!") }.apply {
			val reloadButton = ButtonType("Reload", ButtonBar.ButtonData.LEFT)
			dialogPane.buttonTypes += reloadButton
			(dialogPane.lookupButton(reloadButton) as? Button)?.addEventFilter(ActionEvent.ACTION) {
				dialogPane.content = CloseLabelUI(model)
				it.consume()
			}
		}

		InvokeOnJavaFXApplicationThread {
			delay(200)
			val statuses = listOf(Status.Empty, CloseStatus.Closing, Status.Applying, Status.Done)
			val curState = (dialog.dialogPane.content as? CloseLabelUI)?.model ?: return@InvokeOnJavaFXApplicationThread
			var prev = curState.progressProperty.get()
			while (prev < 1.0) {
				prev += .05
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
