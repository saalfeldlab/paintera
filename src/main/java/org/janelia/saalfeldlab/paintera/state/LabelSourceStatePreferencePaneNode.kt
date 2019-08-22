package org.janelia.saalfeldlab.paintera.state

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.TextFieldExtensions
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterConfigNode
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

typealias TFE = TextFieldExtensions

class LabelSourceStatePreferencePaneNode(val state: LabelSourceState<*, *>) {

	val node: Node
		get() {
			val box = SourceState.defaultPreferencePaneNode(state.compositeProperty()).let { if (it is VBox) it else VBox(it) }
			box.children.addAll(
					HighlightingStreamConverterConfigNode(state.converter()).node,
					SelectedIdsNode(state).node,
					LabelSourceStateMeshPaneNode(state.meshManager(), meshInfosFromState(state)).node
					//		// TODO
					////		assignmentPane(state),
					//		// MaskedSourcePane
			)
			return box
		}

	private class SelectedIdsNode(private val state: LabelSourceState<*, *>) {

		val node: Node
			get() {
				val selectedIds = state.selectedIds()
				val assignment = state.assignment()

				if (selectedIds == null || assignment == null)
					return Region()

				val lastSelectionField = TextField()
				val selectedIdsField = TextField()
				val selectedSegmentsField = TextField()
				val grid = GridPane().also { it.hgap = 5.0 }

				grid.add(lastSelectionField, 1, 0)
				grid.add(selectedIdsField, 1, 1)
				grid.add(selectedSegmentsField, 1, 2)

				val lastSelectionLabel = Label("Last Selection")
				val fragmentLabel = Label("Fragments")
				val segmentLabel = Label("Segments")
				lastSelectionLabel.tooltip = Tooltip("Last selected fragment id")
				fragmentLabel.tooltip = Tooltip("Active fragment ids")
				segmentLabel.tooltip = Tooltip("Active segment ids")

				val activeFragmentsToolTip = Tooltip()
				val activeSegmentsToolTip = Tooltip()
				activeFragmentsToolTip.textProperty().bind(selectedIdsField.textProperty())
				activeSegmentsToolTip.textProperty().bind(selectedSegmentsField.textProperty())
				selectedIdsField.tooltip = activeFragmentsToolTip
				selectedSegmentsField.tooltip = activeSegmentsToolTip

				grid.add(lastSelectionLabel, 0, 0)
				grid.add(fragmentLabel, 0, 1)
				grid.add(segmentLabel, 0, 2)

				GridPane.setHgrow(lastSelectionField, Priority.ALWAYS)
				GridPane.setHgrow(selectedIdsField, Priority.ALWAYS)
				GridPane.setHgrow(selectedSegmentsField, Priority.ALWAYS)
				lastSelectionField.isEditable = false
				selectedIdsField.isEditable = false
				selectedSegmentsField.isEditable = false

				lastSelectionField.setOnMousePressed { event ->
					if (event.clickCount == 2) {
						event.consume()
						val tf = with(TFE) { TextField(lastSelectionField.text).also { it.acceptOnly(LAST_SELECTION_REGEX) } }
						val setOnly = ButtonType("_Set", ButtonBar.ButtonData.OK_DONE)
						val append = ButtonType("_Append", ButtonBar.ButtonData.OK_DONE)
						val bt = PainteraAlerts
								.confirmation("_Set", "_Cancel", true)
								.also { it.headerText = "Set last selected fragment." }
								.also { it.dialogPane.content = VBox(
										Label(LAST_SELECTION_DIALOG_DESCRIPTION).also { it.isWrapText = true },
										HBox(Label("Fragment:"), tf).also { it.alignment = Pos.CENTER_LEFT }.also { it.spacing = 5.0 })
								}
								.also { it.dialogPane.buttonTypes.setAll(append, setOnly, ButtonType.CANCEL) }
								.showAndWait()
						bt.orElse(null)?.let { b ->
							if (setOnly == b) tf.text?.let { selectedIds.activate(it.toLong()) }
							else if (append == b) tf.text?.let { selectedIds.activateAlso(it.toLong()) }
							else null
						}
					}
				}

				selectedIdsField.setOnMousePressed { event ->
					if (event.clickCount == 2) {
						event.consume()
						val tf = with(TFE) { TextField(selectedIdsField.text).also { it.acceptOnly(SELECTION_REGEX) } }
						val bt = PainteraAlerts
								.confirmation("_Set", "_Cancel", true)
								.also { it.headerText = "Select active fragments." }
								.also { it.dialogPane.content = VBox(
										Label(SELECTION_DIALOG_DESCRIPTION).also { it.isWrapText = true },
										HBox(Label("Fragments:"), tf).also { it.alignment = Pos.CENTER_LEFT }.also { it.spacing = 5.0 })
								}
								.showAndWait()
						bt.filter { ButtonType.OK == it }.orElse(null)?.let {
							val selection = (tf.text ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toLong() }.toLongArray()
							val lastSelected = selectedIds.lastSelection.takeIf { selection.contains(it) }
							selectedIds.activate(*selection)
							lastSelected?.let { selectedIds.activateAlso(it) }
						}
					}
				}


				state.selectedIds().addListener {
					selectedIdsField.text = if (selectedIds.isEmpty) "" else selectedIds.activeIds.joinToString(separator = ", ") { it.toString() }
					lastSelectionField.text = selectedIds.lastSelection.takeIf(IS_FOREGROUND)?.toString() ?: ""
				}
				state.converter().stream.selectedSegments.let { sel -> sel.addListener { selectedSegmentsField.text = sel.selectedSegments.joinToString(", ") { it.toString() } } }

				val helpDialog = PainteraAlerts
						.alert(Alert.AlertType.INFORMATION, true)
						.also { it.initModality(Modality.NONE) }
						.also { it.headerText = "Fragment Selection" }
						.also { it.contentText = DESCRIPTION }

				val tpGraphics = HBox(
						Label("Fragment Selection"),
						Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
						Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
						.also { it.alignment = Pos.CENTER }

				return with (TitledPaneExtensions) {
					TitledPane(null, grid)
							.also { it.isExpanded = false }
							.also { it.graphicsOnly(tpGraphics) }
							.also { it.alignment = Pos.CENTER_RIGHT }
							.also { it.tooltip = Tooltip(DESCRIPTION) }
				}

			}

		companion object {
			private val IS_FOREGROUND = { id: Long -> net.imglib2.type.label.Label.isForeground(id) }

			private const val LAST_SELECTION_DIALOG_DESCRIPTION = "" +
					"The last selected fragment is used for painting, and assignment actions. " +
					"The new selection can be appended to the currently active fragments or set as the only active fragment. " +
					"If no fragments are currently active, both choices are equivalent."

			private const val SELECTION_DIALOG_DESCRIPTION = "" +
					"Active fragments (and the containing segments) will be highlighted in the 2D cross-sections and rendered " +
					"in the 3D viewer. If the current last selected fragment is not part of the new selection, an arbitrary fragment " +
					"of the new selection will be chosen to be last selected fragment."

			private val LAST_SELECTION_REGEX = "^$|\\d+".toRegex()

			// empty string or one integer followed by any number of commas followed by optional space and integer number
			private val SELECTION_REGEX = "^$|\\d+(, *\\d*)*".toRegex()

			private const val DESCRIPTION = "" +
					"Fragment can be selected with left mouse click. When used with the CTRL key or right mouse click append the " +
					"fragment to the set of currently active (selected) fragments. In either case, the last selected fragment will " +
					"be used for tasks that require a fragment id, such as painting or merge/split actions. Alternatively, the last " +
					"selection and set of currently active fragments can be modified by double clicking the respective text fields."
		}
	}

	companion object {
		private fun meshInfosFromState(state: LabelSourceState<*, *>) = MeshInfos(
				state.converter().stream.selectedSegments,
				state.meshManager(),
				state.managedMeshSettings(),
				state.getDataSource().numMipmapLevels)
	}

}
