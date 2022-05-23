package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.ObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.Modality
import javafx.util.StringConverter
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.extensions.TextFieldExtensions
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.extensions.addKeyAndScrollHandlers
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.undo.UndoFromEvents
import org.janelia.saalfeldlab.paintera.Constants
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
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfos
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterConfigNode
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.text.DecimalFormat

typealias TFE = TextFieldExtensions

//TODO maybe rename this? Or make it subclass Pane/Node like the name indicates
class LabelSourceStatePreferencePaneNode(
    private val source: DataSource<*, *>,
    private val composite: ObjectProperty<Composite<ARGBType, ARGBType>>,
    private val converter: HighlightingStreamConverter<*>,
    private val meshManager: MeshManagerWithAssignmentForSegments,
    private val meshSettings: ManagedMeshSettings,
    private val brushProperties: BrushProperties?
) {

    private val stream = converter.stream
    private val selectedSegments = stream.selectedSegments
    private val selectedIds = selectedSegments.selectedIds
    private val assignment = selectedSegments.assignment

    val node: Node
        get() {
            val box = SourceState.defaultPreferencePaneNode(composite)
            val nodes = arrayOf(
                HighlightingStreamConverterConfigNode(converter).node,
                SelectedIdsNode(selectedIds, assignment, selectedSegments).node,
                LabelSourceStateMeshPaneNode(source, meshManager, SegmentMeshInfos(selectedSegments, meshManager, meshSettings, source.numMipmapLevels)).node,
                AssignmentsNode(assignment).node,
                when (source) {
                    is MaskedSource -> brushProperties?.let { MaskedSourceNode(source, brushProperties).node }
                    else -> null
                }
            )
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
                        val bt = PainteraAlerts.confirmation("_Set", "_Cancel", true).apply {
                            headerText = "Set last selected fragment."
                            dialogPane.content = VBox(
                                Label(LAST_SELECTION_DIALOG_DESCRIPTION).apply { isWrapText = true },
                                HBox(Label("Fragment:"), tf).apply {
                                    alignment = Pos.CENTER_LEFT
                                    spacing = 5.0
                                }
                            )
                            dialogPane.buttonTypes.setAll(append, setOnly, ButtonType.CANCEL)
                        }.showAndWait()
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
                        val bt = PainteraAlerts.confirmation("_Set", "_Cancel", true).apply {
                            headerText = "Select active fragments."
                            dialogPane.content = VBox(
                                Label(SELECTION_DIALOG_DESCRIPTION).apply { isWrapText = true },
                                HBox(Label("Fragments:"), tf).apply {
                                    alignment = Pos.CENTER_LEFT
                                    spacing = 5.0
                                }
                            )
                        }.showAndWait()
                        bt.filter { ButtonType.OK == it }.orElse(null)?.let {
                            val selection = (tf.text ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toLong() }.toLongArray()
                            val lastSelected = selectedIds.lastSelection.takeIf { selection.contains(it) }
                            selectedIds.activate(*selection)
                            lastSelected?.let { selectedIds.activateAlso(it) }
                        }
                    }
                }


                selectedIds.addListener {
                    selectedIdsField.text = if (selectedIds.isEmpty) "" else selectedIds.activeIdsCopyAsArray.joinToString(separator = ", ") { it.toString() }
                    lastSelectionField.text = selectedIds.lastSelection.takeIf(IS_FOREGROUND)?.toString() ?: ""
                }
                selectedSegments.let { sel ->
                    sel.addListener {
                        selectedSegmentsField.text = sel.selectedSegmentsCopyAsArray.joinToString(", ") { it.toString() }
                    }
                }

                val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
                    initModality(Modality.NONE)
                    headerText = "Fragment Selection"
                    contentText = DESCRIPTION
                }

                val tpGraphics = HBox(
                    Label("Fragment Selection"),
                    NamedNode.bufferNode(),
                    Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                    .also { it.alignment = Pos.CENTER }

                return with(TitledPaneExtensions) {
                    TitledPane(null, grid).apply {
                        isExpanded = false
                        graphicsOnly(tpGraphics)
                        alignment = Pos.CENTER_RIGHT
                        tooltip = Tooltip(DESCRIPTION)
                    }
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
                        when (action.type) {
                            AssignmentAction.Type.MERGE -> {
                                (action as Merge).let { "M: ${it.fromFragmentId} ${it.intoFragmentId} (${it.segmentId})" }
                            }
                            AssignmentAction.Type.DETACH -> {
                                (action as Detach).let { "D: ${it.fragmentId} ${it.fragmentFrom}" }
                            }
                            else -> "UNSUPPORTED ACTION"
                        }
                    }
                    val undoPane = UndoFromEvents.withUndoRedoButtons(
                        assignments.events(),
                        title
                    )
                    { Labels.withTooltip("$it") }

                    val helpDialog = PainteraAlerts
                        .alert(Alert.AlertType.INFORMATION, true)
                        .also { it.initModality(Modality.NONE) }
                        .also { it.headerText = "Assignment Actions" }
                        .also { it.contentText = "TODO" /* TODO */ }

                    val tpGraphics = HBox(
                        Label("Assignments"),
                        NamedNode.bufferNode(),
                        Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                        .also { it.alignment = Pos.CENTER }

                    with(TitledPaneExtensions) {
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

    private class MaskedSourceNode(
        private val source: DataSource<*, *>,
        private val brushProperties: BrushProperties
    ) {

        val node: Node?
            get() {
                return if (source is MaskedSource<*, *>) {
                    val showCanvasCheckBox = CheckBox("")
                        .also { it.tooltip = Tooltip("Show canvas") }
                        .also { it.selectedProperty().bindBidirectional(source.showCanvasOverBackgroundProperty()) }
                    val clearButton = Buttons.withTooltip(
                        "Clear",
                        "Clear any modifications to the canvas. Any changes that have not been committed will be lost."
                    )
                    { showForgetAlert(source) }

                    val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
                        initModality(Modality.NONE)
                        headerText = "Canvas"
                        contentText = "TODO" /* TODO */
                    }

                    val tpGraphics = HBox(
                        Label("Canvas"),
                        NamedNode.bufferNode(),
                        showCanvasCheckBox,
                        clearButton,
                        Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } }
                    ).also { it.alignment = Pos.CENTER }

                    val brushSizeLabel = Labels.withTooltip(
                        "Brush Size",
                        "Brush Size. Has to be positive."
                    ).also { it.alignment = Pos.CENTER_LEFT }

                    val doubleConverter = object : StringConverter<Double>() {
                        private val formatter = DecimalFormat("###.#")
                        override fun toString(double: Double?) = double?.let { formatter.format(it) }
                        override fun fromString(string: String?) = string
                            ?.trim { it <= ' ' }
                            ?.ifEmpty { null }
                            ?.let { formatter.parse(it).toDouble() }
                    }

                    val radiusSpinner = Spinner<Double>()
                    val radiusSpinnerValueFactory = SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, Double.MAX_VALUE, brushProperties.brushRadius, 0.5)
                    /* Note: Unfortunately, `bindBidirectional` seems not to work here :( */
                    radiusSpinnerValueFactory.valueProperty().addListener { _, _, new -> brushProperties.brushRadiusProperty.set(new) }
                    brushProperties.brushRadiusProperty.addListener { _, _, new -> radiusSpinnerValueFactory.valueProperty().set(new.toDouble()) }
                    radiusSpinnerValueFactory.converter = doubleConverter
                    radiusSpinner.valueFactory = radiusSpinnerValueFactory
                    radiusSpinner.isEditable = true
                    radiusSpinner.addKeyAndScrollHandlers()

                    val paintSettingsPane = GridPane().apply {
                        hgap = 5.0
                        padding = Insets(3.0, 10.0, 3.0, 10.0)

                        val bufferNode = NamedNode.bufferNode()
                        GridPane.setHgrow(bufferNode, Priority.ALWAYS)

                        add(brushSizeLabel, 0, 0)
                        add(bufferNode, 1, 0)
                        add(radiusSpinner, 2, 0)
                    }

                    val contents = VBox(paintSettingsPane).also { it.padding = Insets.EMPTY }
                    return TitledPanes.createCollapsed(null, contents).apply {
                        with(TPE) { graphicsOnly(tpGraphics) }
                        alignment = Pos.CENTER_RIGHT
                        tooltip = null /* TODO */
                    }

                } else
                    null
            }

        private fun showForgetAlert(source: MaskedSource<*, *>) {
            if (showForgetAlert()) {
                try {
                    source.forgetCanvases()
                } catch (e: CannotClearCanvas) {
                    LOG.error("Unable to clear canvas.", e)
                    Exceptions.exceptionAlert(Constants.NAME, "Unable to clear canvas.", e, owner = node?.scene?.window)
                }
            }

        }

        private fun showForgetAlert() = PainteraAlerts.confirmation("_Yes", "_No", true)
            .also { it.headerText = "Clear Canvas" }
            .also {
                it.dialogPane.content = TextArea("Clearing canvas will remove all painted data that have not been committed yet. Proceed?")
                    .also { it.isEditable = false }
                    .also { it.isWrapText = true }
            }
            .showAndWait()
            .filter { ButtonType.OK == it }
            .isPresent

        companion object {
            private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        }
    }

}
