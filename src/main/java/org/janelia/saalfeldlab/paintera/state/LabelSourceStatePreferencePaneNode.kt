package org.janelia.saalfeldlab.paintera.state

import gnu.trove.set.hash.TLongHashSet
import javafx.beans.property.ObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TextFieldExtensions
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.undo.UndoFromEvents
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentStateWithActionTracker
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.exception.CannotClearCanvas
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos
import org.janelia.saalfeldlab.paintera.meshes.MeshManager
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterConfigNode
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

typealias TFE = TextFieldExtensions

class LabelSourceStatePreferencePaneNode(
	private val source: DataSource<*, *>,
	private val composite: ObjectProperty<Composite<ARGBType, ARGBType>>,
	private val converter: HighlightingStreamConverter<*>,
	private val meshManager: MeshManager<Long, TLongHashSet>,
	private val meshSettings: ManagedMeshSettings
) {

	private val stream = converter.stream
	private val selectedSegments = stream.selectedSegments
	private val selectedIds = selectedSegments.selectedIds
	private val assignment = selectedSegments.assignment

	val node: Node
		get() {
			val box = SourceState.defaultPreferencePaneNode(composite).let { if (it is VBox) it else VBox(it) }
			val nodes = arrayOf(
                HighlightingStreamConverterConfigNode(converter).node,
                SelectedIdsNode(selectedIds, assignment, selectedSegments).node,
                LabelSourceStateMeshPaneNode(meshManager, MeshInfos(selectedSegments, meshManager, meshSettings, source.numMipmapLevels)).node,
                AssignmentsNode(assignment).node,
                MaskedSourceNode(source).node)
			box.children.addAll(nodes.filterNotNull())
			return box
		}

	private class SelectedIdsNode(
		private val selectedIds: SelectedIds?,
		private val assignment: FragmentSegmentAssignmentState?,
		private val selectedSegments: SelectedSegments
	) {

		val node: Node
			get() {
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


				selectedIds.addListener {
					selectedIdsField.text = if (selectedIds.isEmpty) "" else selectedIds.activeIds.joinToString(separator = ", ") { it.toString() }
					lastSelectionField.text = selectedIds.lastSelection.takeIf(IS_FOREGROUND)?.toString() ?: ""
				}
				selectedSegments.let { sel -> sel.addListener { selectedSegmentsField.text = sel.selectedSegments.joinToString(", ") { it.toString() } } }

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

	private class AssignmentsNode(private val assignments: FragmentSegmentAssignmentState) {

		val node: Node?
			get() {
				return if (assignments is FragmentSegmentAssignmentStateWithActionTracker) {
					val title = { action: AssignmentAction ->
						when(action.type) {
							AssignmentAction.Type.MERGE -> { (action as Merge).let { "M: ${it.fromFragmentId} ${it.intoFragmentId} (${it.segmentId})"} }
							AssignmentAction.Type.DETACH -> { (action as Detach).let { "D: ${it.fragmentId} ${it.fragmentFrom}" } }
							else -> "UNSUPPORTED ACTION"
						}
					}
					val undoPane = UndoFromEvents.withUndoRedoButtons(
							assignments.events(),
							title)
							{ Labels.withTooltip("$it") }

					val helpDialog = PainteraAlerts
							.alert(Alert.AlertType.INFORMATION, true)
							.also { it.initModality(Modality.NONE) }
							.also { it.headerText = "Assignment Actions" }
							.also { it.contentText = "TODO" /* TODO */ }

					val tpGraphics = HBox(
							Label("Assignments"),
							Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
							Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
							.also { it.alignment = Pos.CENTER }

					with (TitledPaneExtensions) {
						TitledPane(null, undoPane)
								.also { it.isExpanded = false }
								.also { it.graphicsOnly(tpGraphics) }
								.also { it.alignment = Pos.CENTER_RIGHT }
								.also { it.tooltip = null /* TODO */ }
					}
				} else
					null
			}

	}

	private class MaskedSourceNode(private val source: DataSource<*, *>) {

		val node: Node?
			get() {
				return if (source is MaskedSource<*, *>) {
					val showCanvasCheckBox = CheckBox("")
							.also { it.tooltip = Tooltip("Show canvas") }
							.also { it.selectedProperty().bindBidirectional(source.showCanvasOverBackgroundProperty()) }
					val clearButton = Buttons.withTooltip(
							"Clear",
							"Clear any modifications to the canvas. Any changes that have not been committed will be lost.")
					{ showForgetAlert(source) }

					val helpDialog = PainteraAlerts
							.alert(Alert.AlertType.INFORMATION, true)
							.also { it.initModality(Modality.NONE) }
							.also { it.headerText = "Canvas" }
							.also { it.contentText = "TODO" /* TODO */ }

					val tpGraphics = HBox(
							Label("Canvas"),
							Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
							showCanvasCheckBox,
							clearButton,
							Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
							.also { it.alignment = Pos.CENTER }

					return TitledPanes
							.createCollapsed(null, null)
							.also { with (TPE) { it.graphicsOnly(tpGraphics) } }
							.also { it.alignment = Pos.CENTER_RIGHT }
							.also { it.tooltip = null /* TODO */ }
				} else
					null
			}

		private fun showForgetAlert(source: MaskedSource<*, *>) {
			if (showForgetAlert()) {
				try {
					source.forgetCanvases()
				} catch (e: CannotClearCanvas) {
					LOG.error("Unable to clear canvas.", e)
					Exceptions.exceptionAlert(Paintera.NAME, "Unable to clear canvas.", e)
				}
			}

		}

		private fun showForgetAlert() = PainteraAlerts.confirmation("_Yes", "_No", true)
					.also { it.headerText = "Clear Canvas" }
					.also { it.dialogPane.content = TextArea("Clearing canvas will remove all painted data that have not been committed yet. Proceed?")
							.also { it.isEditable = false }
							.also { it.isWrapText = true } }
					.showAndWait()
					.filter { ButtonType.OK == it }
					.isPresent

		companion object {
			private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
		}
	}

}
