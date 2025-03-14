package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldListCell
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.converter.LongStringConverter
import kotlinx.coroutines.delay
import org.controlsfx.control.SegmentedButton
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelState.Mode
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelUI.IdSelection.Companion.getReplaceIdSelectionButtons
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelUI.IdSelection.entries
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelUI.ReplaceTargetSelection.Companion.getReplaceTargetSelectionButtons
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelUI.ReplaceTargetSelection.entries

import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow
private const val ACTIVE_FRAGMENT_TOOLTIP = "%s the last selected Fragment"
private const val ACTIVE_FRAGMENTS_TOOLTIP = "%s all currently selected Fragments"
private const val ACTIVE_SEGMENT_TOOLTIP = "%s all fragments for the currently active Segment"
private const val ACTIVE_SEGMENTS_TOOLTIP = "%s all fragments from all currently active Segments"
private const val CLEAR_SELECTION_TOOLTIP = "Clear All Fragment and Segment ID Selections"

class ReplaceLabelUI(initialState: ReplaceLabelUIState, val mode: Mode) : VBox() {

	private val replacementLabelProperty = SimpleObjectProperty<Long>()
	private val activateReplacementLabelProperty = SimpleBooleanProperty(false)
	private val progressProperty = SimpleDoubleProperty()
	private val progressTextProperty = SimpleStringProperty()
	private val segmentsToReplace = FXCollections.observableArrayList<Long>()
	private val fragmentsToReplace = FXCollections.observableArrayList<Long>()

	val stateProperty: ObjectProperty<ReplaceLabelUIState> = SimpleObjectProperty(initialState).apply {
		var oldFragmentListener: ListChangeListener<Long>? = null

		/* this is triggered only when changed (i.e. not on first call) */
		subscribe { old, _ ->
			replacementLabelProperty.unbindBidirectional(old.replacementLabelProperty)
			activateReplacementLabelProperty.unbindBidirectional(old.activateReplacementLabelProperty)
			progressProperty.unbind()
			progressTextProperty.unbind()
			oldFragmentListener?.let { fragmentsToReplace.removeListener(it) }
		}

		/* this is triggered immediately, AND on each change */
		subscribe { new ->
			replacementLabelProperty.bindBidirectional(new.replacementLabelProperty)
			activateReplacementLabelProperty.bindBidirectional(new.activateReplacementLabelProperty)

			progressProperty.bind(new.progressProperty)
			progressTextProperty.bind(new.progressTextProperty)

			fragmentsToReplace.setAll(new.fragmentsToReplace)
			fragmentsToReplace.addListener(ListChangeListener {
				while (it.next())
					if (it.wasAdded())
						new.fragmentsToReplace.addAll(it.addedSubList)
				if (it.wasRemoved())
					new.fragmentsToReplace.removeAll(it.removed)
			})
		}
	}
	private var state by stateProperty.nonnull()

	private val actionInProgress = progressProperty.createObservableBinding { it.value > 0.0 && it.value < 1.0 }

	private enum class IdSelection(
		val text: String,
		val tooltipText: ReplaceLabelUI.() -> String,
		val fragments: ReplaceLabelUIState.() -> LongArray,
		val segments: ReplaceLabelUIState.() -> LongArray
	) {
		ACTIVE_FRAGMENT(
			"Active Fragment",
			{ ACTIVE_FRAGMENT_TOOLTIP.format(mode.name) },
			{ longArrayOf(activeFragment) },
			{ longArrayOf() }
		),
		ACTIVE_FRAGMENTS(
			"All Active Fragments",
			{ ACTIVE_FRAGMENTS_TOOLTIP.format(mode.name) },
			{ allActiveFragments },
			{ longArrayOf() }
		),
		ACTIVE_SEGMENT(
			"Segment For Active Fragment",
			{ ACTIVE_SEGMENT_TOOLTIP.format(mode.name) },
			{ fragmentsForActiveSegment },
			{ longArrayOf(activeSegment) }
		),
		ACTIVE_SEGMENTS(
			"Segments For All Active Fragments",
			{ ACTIVE_SEGMENTS_TOOLTIP.format(mode.name) },
			{ fragmentsForAllActiveSegments },
			{ allActiveSegments }
		),
		CLEAR(
			"Clear Selections",
			{ CLEAR_SELECTION_TOOLTIP.format(mode.name) },
			{ longArrayOf() },
			{ longArrayOf() }
		);

		fun fragmentsToReplace(state: ReplaceLabelUIState) = state.fragments()
		fun segmentsToReplace(state: ReplaceLabelUIState) = state.segments()

		fun button(ui: ReplaceLabelUI) = Button().hGrow {
			text = this@IdSelection.text
			tooltip = Tooltip().apply {
				text = this@IdSelection.tooltipText(ui)
			}
			userData = this@IdSelection
			onAction = EventHandler {
				ui.fragmentsToReplace.setAll(*fragmentsToReplace(ui.state).toTypedArray())
				ui.segmentsToReplace.setAll(*segmentsToReplace(ui.state).toTypedArray())
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
		.createObservableBinding {
			mode == Mode.Delete || it.value?.userData == ReplaceTargetSelection.DELETE
		}

	private val customMapper: ((String?) -> String?)? =
		if (mode == Mode.Replace) { text -> text?.toLongOrNull()?.takeIf { it != 0L }?.toString() ?: "" }
		else null
	private val replaceWithIdFormatter = PositiveLongTextFormatter(customMapping = customMapper).apply {
		valueProperty().bindBidirectional(replacementLabelProperty)
	}

	private val replaceWithIdField = TextField().hGrow {
		disableProperty().bind(deleteToggled)
		promptText = "ID to Replace Fragment/Segment IDs with... "
		textFormatter = replaceWithIdFormatter
	}


	private val fragmentsListView = ListView<Long>(fragmentsToReplace).apply {
		actionInProgress.subscribe { busy -> isEditable = !busy }
		cellFactory = TextFieldListCell.forListView(LongStringConverter())
		selectionModel.selectionMode = SelectionMode.MULTIPLE
		onKeyPressed = EventHandler {
			if (actionInProgress.value) return@EventHandler
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
		actionInProgress.subscribe { busy -> isEditable = !busy }
		cellFactory = TextFieldListCell.forListView(LongStringConverter())
		selectionModel.selectionMode = SelectionMode.MULTIPLE
		onKeyPressed = EventHandler {
			if (actionInProgress.value) return@EventHandler
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
		val removedFragments = state.fragmentsForSegment(segment)
		fragmentsToReplace.removeIf { it in removedFragments }
	}

	private fun removeFragment(fragment: Long) {
		fragmentsToReplace -= fragment
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
		fragmentsToReplace += state.fragmentsForSegment(segment)
			.filter { it !in fragmentsToReplace }
			.toSet()
		segment.takeIf { it !in segmentsToReplace }?.let { segmentsToReplace += it }
	}

	private fun addFragment(fragment: Long) {
		fragment.takeIf { it !in fragmentsToReplace }?.let { fragmentsToReplace += it }
	}

	private val setNextNewID = EventHandler<ActionEvent> {
		replaceWithIdFormatter.value = state.nextId()
	}

	init {
		hvGrow()
		spacing = 10.0
		padding = Insets(10.0)
		children += VBox().apply {
			spacing = 5.0
			disableProperty().bind(actionInProgress)

			children += TitledPane("", null).hvGrow titlePane@{
				isExpanded = true
				isCollapsible = false
				graphic = HBox(5.0).hGrow {
					minWidthProperty().bind(this@titlePane.widthProperty())
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Select Labels")
					children += Pane().hGrow()
					children += IdSelection.CLEAR.button(this@ReplaceLabelUI)
				}
				content = idSelectionButtonBar.hGrow()
			}
		}

		if (mode != Mode.Delete) {
			val addActivateReplacementNodes : Pane.() -> Unit = {
				children += Label("Activate Label After Replacement?").apply {
					disableProperty().bind(deleteToggled)
				}
				children += CheckBox().apply {
					activateReplacementLabelProperty.bindBidirectional(selectedProperty())
					disableProperty().bind(deleteToggled)
					deleteToggled.subscribe { it ->
						if (it) isSelected = false
					}
				}
			}
			children += VBox(5.0).hGrow {
				disableProperty().bind(actionInProgress)
				if (mode == Mode.Replace) {
					children += TitledPane("", null).hvGrow titlePane@{
						isExpanded = true
						isCollapsible = false
						graphic = HBox(5.0).hGrow {
							minWidthProperty().bind(this@titlePane.widthProperty())
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
				}
				else if (mode == Mode.All) {

					children += TitledPane("", null).hvGrow titlePane@{
						isExpanded = true
						isCollapsible = false
						graphic = HBox(5.0).hGrow {
							minWidthProperty().bind(this@titlePane.widthProperty())
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
				children += Label("Fragments to ${mode.name}")
				children += fragmentsListView.hvGrow()
				children += HBox().hGrow {
					disableProperty().bind(actionInProgress)
					children += addFragmentField
					children += Button("Add Fragment ID").apply {
						onAction = EventHandler { submitFragmentHandler() }
					}
				}
			}
			children += VBox().hvGrow {
				alignment = Pos.CENTER
				children += Label("Segments to ${mode.name}")
				children += segmentsListView.hvGrow()
				children += HBox().hGrow {
					disableProperty().bind(actionInProgress)
					children += addSegmentField
					children += Button("Add Segment ID").apply {
						onAction = EventHandler { submitSegmentHandler() }
					}
				}
			}
		}
		children += HBox().hGrow {
			val visibleIfProgress = progressProperty.createObservableBinding(progressTextProperty) {
				it.value > 0.0 || progressTextProperty.value?.isNotBlank() == true
			}
			visibleProperty().bind(visibleIfProgress)
			spacing = 10.0
			var labelWidth = widthProperty().createObservableBinding { it.value * .25 }
			children += Label().hGrow label@{
				alignment = Pos.CENTER_LEFT
				textProperty().bind(progressTextProperty)
				prefWidthProperty().bind(labelWidth)
				maxWidthProperty().bind(labelWidth)
			}
			children += AnimatedProgressBar().hGrow {
				maxWidth = Double.MAX_VALUE
				progressTargetProperty.bind(progressProperty)
			}
		}
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {

		class UIState : ReplaceLabelUIState {
			override val activeFragment: Long
				get() = 123
			override val activeSegment: Long
				get() = 456
			override val allActiveFragments: LongArray
				get() = longArrayOf(1, 2, 3, 4)
			override val allActiveSegments: LongArray
				get() = longArrayOf(1, 2, 3)
			override val fragmentsForActiveSegment: LongArray
				get() = longArrayOf(1, 2)
			override val fragmentsForAllActiveSegments: LongArray
				get() = longArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
			override val activateReplacementLabelProperty = SimpleBooleanProperty(false)

			override val progressProperty = SimpleDoubleProperty()
			override val progressTextProperty = SimpleStringProperty()

			override val fragmentsToReplace: ObservableList<Long> = FXCollections.observableArrayList()
			override val replacementLabelProperty: ObjectProperty<Long> = SimpleObjectProperty()

			private val fragSegMap = mapOf(
				1L to longArrayOf(1, 2, 3, 4),
				2L to longArrayOf(21, 22, 23, 24),
				3L to longArrayOf(31, 32, 33, 34),
				4L to longArrayOf(41, 42, 43, 44),
			)

			override fun fragmentsForSegment(segment: Long): LongArray {
				return fragSegMap[segment] ?: LongArray((0..10).random()) { (0..100L).random() }
			}

			var next = 0L

			override fun nextId(): Long {
				return ++next
			}
		}

		val simulateProgress = { state: ReplaceLabelUIState ->
			InvokeOnJavaFXApplicationThread {
				delay(200)
				var prev = state.progressProperty.value
				while (prev < 1.0) {
					prev = prev + .05
					state.progressTextProperty.value = "%.${(1..20).random()}f".format(prev)
					state.progressProperty.set(prev)
					delay(200)
				}
			}
		}

		val root = VBox()
		val modeChoice = ComboBox<Mode>(FXCollections.observableArrayList(Mode.entries)).apply {
			selectionModel.select(Mode.All)
			onAction = EventHandler {
				selectionModel.selectedItem?.let { mode ->
					root.children.removeIf { it is ReplaceLabelUI }

					val state = UIState()
					root.children.add(ReplaceLabelUI(state, mode))
					simulateProgress(state)
				}
			}
		}
		root.apply {
			children += HBox().apply {
				children += modeChoice
				children += Button("Reload").apply {
					onAction = EventHandler {
						root.children.removeIf { it is ReplaceLabelUI }

						val state = UIState()
						root.children.add(ReplaceLabelUI(state, modeChoice.selectionModel.selectedItem))
						simulateProgress(state)
					}
				}
			}
			val state = UIState()
			root.children.add(ReplaceLabelUI(state, modeChoice.selectionModel.selectedItem))
			simulateProgress(state)
		}

		val scene = Scene(root)
		val stage = Stage()
		stage.scene = scene
		stage.show()
	}
}

