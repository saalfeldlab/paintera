package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
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
import javafx.scene.layout.VBox
import javafx.util.converter.LongStringConverter
import org.controlsfx.control.SegmentedButton
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelState.Mode
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelUI.IdSelection.Companion.getReplaceIdSelectionButtons
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelUI.ReplaceTargetSelection.Companion.getReplaceTargetSelectionButtons
import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow

private const val ACTIVE_FRAGMENT_TOOLTIP = "%s the last selected Fragment"
private const val ACTIVE_FRAGMENTS_TOOLTIP = "%s all currently selected Fragments"
private const val ACTIVE_SEGMENT_TOOLTIP = "%s all fragments for the currently active Segment"
private const val ACTIVE_SEGMENTS_TOOLTIP = "%s all fragments from all currently active Segments"
private const val CLEAR_SELECTION_TOOLTIP = "Clear All Fragment and Segment ID Selections"

class ReplaceLabelUI(val model: Model) : VBox() {

	interface Model {

		val mode: Mode
		val activeFragment: Long
		val activeSegment: Long
		val allActiveFragments: LongArray
		val allActiveSegments: LongArray
		val fragmentsForActiveSegment: LongArray
		val fragmentsForAllActiveSegments: LongArray

		val fragmentsToReplace: ObservableList<Long>
		val replacementLabelProperty: ObjectProperty<Long>
		val activateReplacementLabelProperty: BooleanProperty
		val canApply: BooleanExpression
		val canCancel: BooleanExpression

		fun fragmentsForSegment(segment: Long): LongArray
		fun nextId(): Long

		companion object {

			fun Model.getDialog(dialogTitle: String) = PainteraAlerts.confirmation("_Apply", "_Cancel").apply {
				title = dialogTitle
				bindDialog(this)
			}

			fun Model.bindDialog(dialog: Dialog<ButtonType>): DialogPane = dialog.dialogPane.apply {
				content = ReplaceLabelUI(this@bindDialog)
				graphic = null
				headerText = null
				header = null

				(lookupButton(ButtonType.OK) as? Button)?.apply {
					disableProperty().unbind()
					disableProperty().bind(canApply.not())
				}

				(lookupButton(ButtonType.CANCEL) as? Button)?.apply {
					disableProperty().unbind()
					disableProperty().bind(canCancel.not())
				}
			}
		}

	}


	abstract class AbstractModel(override val mode: Mode) : Model {

		override val fragmentsToReplace: ObservableList<Long> = FXCollections.observableArrayList()
		override val replacementLabelProperty: ObjectProperty<Long> = SimpleObjectProperty(0L)
		override val activateReplacementLabelProperty: BooleanProperty = SimpleBooleanProperty(false)

		override val canApply: BooleanExpression by lazy { Bindings.createBooleanBinding({ mode.labelIsValid(replacementLabelProperty.value) && fragmentsToReplace.isNotEmpty() }, replacementLabelProperty, fragmentsToReplace) }
		override val canCancel: BooleanExpression = SimpleBooleanProperty(true)
	}

	private val segmentsToReplace = FXCollections.observableArrayList<Long>()

	private enum class IdSelection(
		val text: String,
		val tooltipText: ReplaceLabelUI.() -> String,
		val fragments: Model.() -> LongArray,
		val segments: Model.() -> LongArray,
	) {
		ACTIVE_FRAGMENT(
			"Active Fragment",
			{ ACTIVE_FRAGMENT_TOOLTIP.format(model.mode.name) },
			{ longArrayOf(activeFragment) },
			{ longArrayOf() }
		),
		ACTIVE_FRAGMENTS(
			"All Active Fragments",
			{ ACTIVE_FRAGMENTS_TOOLTIP.format(model.mode.name) },
			{ allActiveFragments },
			{ longArrayOf() }
		),
		ACTIVE_SEGMENT(
			"Segment For Active Fragment",
			{ ACTIVE_SEGMENT_TOOLTIP.format(model.mode.name) },
			{ fragmentsForActiveSegment },
			{ longArrayOf(activeSegment) }
		),
		ACTIVE_SEGMENTS(
			"Segments For All Active Fragments",
			{ ACTIVE_SEGMENTS_TOOLTIP.format(model.mode.name) },
			{ fragmentsForAllActiveSegments },
			{ allActiveSegments }
		),
		CLEAR(
			"Clear Selections",
			{ CLEAR_SELECTION_TOOLTIP.format(model.mode.name) },
			{ longArrayOf() },
			{ longArrayOf() }
		);

		fun fragmentsToReplace(model: Model) = model.fragments()
		fun segmentsToReplace(model: Model) = model.segments()

		fun button(ui: ReplaceLabelUI) = Button().hGrow {
			text = this@IdSelection.text
			tooltip = Tooltip().apply {
				text = this@IdSelection.tooltipText(ui)
			}
			userData = this@IdSelection
			onAction = EventHandler {
				ui.model.fragmentsToReplace.setAll(*fragmentsToReplace(ui.model).toTypedArray())
				ui.segmentsToReplace.setAll(*segmentsToReplace(ui.model).toTypedArray())
			}
		}

		companion object {
			fun ReplaceLabelUI.getReplaceIdSelectionButtons() = entries.filter { it != CLEAR }.map { it.button(this) }
		}
	}

	private enum class ReplaceTargetSelection(val text: String, val tooltip: String, val label: Long? = null) {
		REPLACE("Replace Label", "Specify Replacement Label"),
		DELETE("Delete Label", "Replace Label with 0", 0);

		val ReplaceLabelUI.button: ToggleButton
			get() = ToggleButton().apply {
				text = this@ReplaceTargetSelection.text
				tooltip = Tooltip(this@ReplaceTargetSelection.tooltip)
				userData = this@ReplaceTargetSelection
				onAction = EventHandler {
					replaceWithIdFormatter.value = label
				}
			}

		companion object {
			fun ReplaceLabelUI.getReplaceTargetSelectionButtons() = entries.map { it.run { button } }
		}
	}

	private val idSelectionButtonBar = let {
		val buttons = getReplaceIdSelectionButtons().toTypedArray()
		/* default replace ID behavior is ACTIVE_SEGMENT */
		buttons.first { it.userData == IdSelection.ACTIVE_SEGMENT }.fire()
		HBox(*buttons)
	}


	private val replaceActionButtonBar = let {
		val buttons = getReplaceTargetSelectionButtons().toTypedArray()
		/* default replace action is DELETE */
		buttons.first { toggle -> toggle.userData == ReplaceTargetSelection.DELETE }.isSelected = true
		SegmentedButton(*buttons)
	}

	private val deleteToggled = replaceActionButtonBar.toggleGroup
		.selectedToggleProperty()
		.createNullableValueBinding {
			model.mode == Mode.Delete || it?.userData == ReplaceTargetSelection.DELETE
		}

	private val customMapper: ((String?) -> String?)? =
		if (model.mode == Mode.Replace) { text -> text?.toLongOrNull()?.takeIf { it != 0L }?.toString() ?: "" }
		else null
	private val replaceWithIdFormatter = PositiveLongTextFormatter(customMapping = customMapper).apply {
		valueProperty().bindBidirectional(this@ReplaceLabelUI.model.replacementLabelProperty)
	}

	private val replaceWithIdField = TextField().hGrow {
		disableProperty().bind(deleteToggled)
		promptText = "ID to Replace Fragment/Segment IDs with... "
		textFormatter = replaceWithIdFormatter
	}


	private val fragmentsListView = ListView<Long>(model.fragmentsToReplace).apply {
		cellFactory = TextFieldListCell.forListView(LongStringConverter())
		selectionModel.selectionMode = SelectionMode.MULTIPLE
		onKeyPressed = EventHandler {
			if (!(it.code == KeyCode.DELETE || it.code == KeyCode.BACK_SPACE)) return@EventHandler
			selectionModel.selectedItems.toTypedArray().forEach { removeFragment(it) }
			it.consume()
		}
	}

	private val addFragmentField = TextField().hGrow {
		promptText = "Add a Fragment ID..."
		textFormatter = PositiveLongTextFormatter()
		onAction = EventHandler { submitFragmentHandler() }
	}

	private val segmentsListView = ListView<Long>(segmentsToReplace).apply {
		cellFactory = TextFieldListCell.forListView(LongStringConverter())
		selectionModel.selectionMode = SelectionMode.MULTIPLE
		onKeyPressed = EventHandler {
			if (!(it.code == KeyCode.DELETE || it.code == KeyCode.BACK_SPACE)) return@EventHandler
			selectionModel.selectedItems.toTypedArray().forEach { removeSegment(it) }
			it.consume()
		}
	}
	private val addSegmentField = TextField().hGrow {
		promptText = "Add a Segment ID..."
		textFormatter = PositiveLongTextFormatter()
		onAction = EventHandler { submitSegmentHandler() }
	}

	private val submitSegmentHandler: () -> Unit = {
		addSegmentField.run {
			commitValue()
			(textFormatter as? PositiveLongTextFormatter)?.run {
				value?.let { addSegment(it) }
				value = null
			}
		}
	}

	private fun removeSegment(segment: Long) {
		segmentsToReplace -= segment
		val removedFragments = model.fragmentsForSegment(segment)
		model.fragmentsToReplace.removeIf { it in removedFragments }
	}

	private fun removeFragment(fragment: Long) {
		model.fragmentsToReplace -= fragment
	}

	private val submitFragmentHandler: () -> Unit = {
		addFragmentField.run {
			commitValue()
			(textFormatter as? PositiveLongTextFormatter)?.run {
				value?.let { addFragment(it) }
				value = null
			}
		}
	}

	private fun addSegment(segment: Long) {
		model.fragmentsToReplace += model.fragmentsForSegment(segment)
			.filter { it !in model.fragmentsToReplace }
			.toSet()
		segment.takeIf { it !in segmentsToReplace }?.let { segmentsToReplace += it }
	}

	private fun addFragment(fragment: Long) {
		fragment.takeIf { it !in model.fragmentsToReplace }?.let { model.fragmentsToReplace += it }
	}

	private val setNextNewID = EventHandler<ActionEvent> {
		replaceWithIdFormatter.value = model.nextId()
	}

	init {
		hvGrow()
		spacing = 10.0
		padding = Insets(10.0)
		children += VBox().apply {
			spacing = 5.0

			children += TitledPane("", null).hvGrow titlePane@{
				isExpanded = true
				isCollapsible = false
				contentDisplay = ContentDisplay.GRAPHIC_ONLY
				graphic = HBox(5.0).hGrow {
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Select Labels")
					children += Pane().hGrow()
					children += IdSelection.CLEAR.button(this@ReplaceLabelUI)
				}
				content = idSelectionButtonBar.hGrow()
			}
		}

		if (model.mode != Mode.Delete) {
			val addActivateReplacementNodes: Pane.() -> Unit = {
				children += Label("Activate Label After Replacement?").apply {
					disableProperty().bind(deleteToggled)
				}
				children += CheckBox().apply {
					this@ReplaceLabelUI.model.activateReplacementLabelProperty.bindBidirectional(selectedProperty())
					disableProperty().bind(deleteToggled)
					deleteToggled.subscribe { it ->
						if (it) isSelected = false
					}
				}
			}
			children += VBox(5.0).hGrow {
				if (model.mode == Mode.Replace) {
					children += TitledPane("", null).hvGrow titlePane@{
						isExpanded = true
						isCollapsible = false
						graphic = HBox(5.0).hGrow {
							padding = Insets(0.0, 20.0, 0.0, 0.0)
							alignment = Pos.CENTER
							children += Label("Replace Label")
							children += Pane().hGrow()
							addActivateReplacementNodes()
						}
						content = HBox().apply {
							children += replaceWithIdField.hGrow()
							children += Button("Next New ID").apply {
								disableProperty().bind(deleteToggled)
								onAction = setNextNewID
							}
						}
					}
				} else if (model.mode == Mode.All) {

					children += TitledPane("", null).hvGrow titlePane@{
						isExpanded = true
						isCollapsible = false
						graphic = HBox(5.0).hGrow {
							padding = Insets(0.0, 20.0, 0.0, 0.0)
							alignment = Pos.CENTER
							children += Label("Replace or Delete Label")
							children += Pane().hGrow()
							addActivateReplacementNodes()
						}
						content = HBox(5.0).apply {
							children += replaceActionButtonBar
							children += HBox().hGrow {
								children += replaceWithIdField.hGrow()
								children += Button("Next New ID").apply {
									disableProperty().bind(deleteToggled)
									onAction = setNextNewID
								}
							}
						}
					}
				}
			}
		}
		children += HBox().hvGrow {
			children += VBox().hvGrow {
				alignment = Pos.CENTER
				children += Label("Fragments to ${model.mode.name}")
				children += fragmentsListView.hvGrow()
				children += HBox().hGrow {
					children += addFragmentField
					children += Button("Add Fragment ID").apply {
						onAction = EventHandler { submitFragmentHandler() }
					}
				}
			}
			children += VBox().hvGrow {
				alignment = Pos.CENTER
				children += Label("Segments to ${model.mode.name}")
				children += segmentsListView.hvGrow()
				children += HBox().hGrow {
					children += addSegmentField
					children += Button("Add Segment ID").apply {
						onAction = EventHandler { submitSegmentHandler() }
					}
				}
			}
		}
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {

		class TestModel(mode: Mode) : ReplaceLabelUI.AbstractModel(mode) {
			override val activeFragment = 123L
			override val activeSegment = 456L
			override val allActiveFragments = longArrayOf(1, 2, 3, 4)
			override val allActiveSegments = longArrayOf(1, 2, 3)
			override val fragmentsForActiveSegment = longArrayOf(1, 2)
			override val fragmentsForAllActiveSegments = longArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)

			override val canCancel = SimpleBooleanProperty(true)

			private val fragSegMap = mapOf(
				1L to longArrayOf(1, 2, 3, 4),
				2L to longArrayOf(21, 22, 23, 24),
				3L to longArrayOf(31, 32, 33, 34),
				4L to longArrayOf(41, 42, 43, 44),
			)

			override fun fragmentsForSegment(segment: Long): LongArray {
				return fragSegMap[segment] ?: LongArray((0..10).random()) { (0..100L).random() }
			}

			private var nextId = 1L
			override fun nextId() = nextId++
		}

		var model = TestModel(Mode.All)

		val modeChoice = ComboBox<Mode>(FXCollections.observableArrayList(Mode.entries)).apply {
			selectionModel.select(Mode.All)
		}


		model.getDialog("${model.mode}").apply {
			val modeChoiceButton = ButtonType("", ButtonBar.ButtonData.HELP_2)
			dialogPane.buttonTypes += modeChoiceButton
			(dialogPane.lookupButton(modeChoiceButton) as? Button)?.apply {
				graphic = modeChoice
				addEventFilter(ActionEvent.ACTION) { it.consume() }
			}

			val reloadButton = ButtonType("Reload", ButtonBar.ButtonData.HELP_2)
			dialogPane.buttonTypes += reloadButton
			(dialogPane.lookupButton(reloadButton) as? Button)?.addEventFilter(ActionEvent.ACTION) {
				model = TestModel(modeChoice.selectionModel.selectedItem)
				dialogPane.content = ReplaceLabelUI(model)
				dialogPane.headerText = "${model.mode}"
				it.consume()
			}
		}.showAndWait().let { println("Result: $it") }
	}
}

