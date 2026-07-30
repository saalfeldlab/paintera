package org.janelia.saalfeldlab.paintera.state

import gnu.trove.set.hash.TLongHashSet
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.control.ContentDisplay
import javafx.scene.layout.*
import javafx.stage.Window
import javafx.util.StringConverter
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.extensions.addKeyAndScrollHandlers
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.undo.UndoFromEvents
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
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
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfoList
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterConfigNode
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.kordamp.ikonli.fontawesome.FontAwesome
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicLong
import org.janelia.saalfeldlab.labels.Label.Companion as Imglib2Labels

//TODO maybe rename this? Or make it subclass Pane/Node like the name indicates
class LabelSourceStatePreferencePaneNode(
	private val source: DataSource<*, *>,
	private val composite: ObjectProperty<Composite<ARGBType, ARGBType>>,
	private val converter: HighlightingStreamConverter<*>,
	private val meshManager: MeshManagerWithAssignmentForSegments,
	private val brushProperties: BrushProperties?,
) {

	private val stream = converter.stream
	private val selectedSegments = stream.selectedSegments
	private val selectedIds = selectedSegments.selectedIds
	private val assignment = selectedSegments.assignment

	val node: Node
		get() {
			val box = SourceState.defaultPreferencePaneNode(composite)

			val meshInfos = SegmentMeshInfoList(meshManager)
			val selectedSegmentUpdateListener: (observable: Observable) -> Unit = {
				meshInfos.selectedSegmentsProperty.set(selectedSegments.segments.toArray().toList())
			}
			selectedSegments.addListener(selectedSegmentUpdateListener)

			val nodes = arrayOf(
				HighlightingStreamConverterConfigNode(converter).node,
				SelectedIdsNode(selectedIds, assignment, selectedSegments).node,
				LabelSourceStateMeshPaneNode(source, meshManager, meshInfos).node,
				AssignmentsNode(assignment).node,
				when (source) {
					is MaskedSource -> brushProperties?.let { MaskedSourceNode(source, brushProperties, meshManager::refreshMeshes).node }
					else -> null
				}
			)
			box.children.addAll(nodes.filterNotNull())
			return box
		}

	private class SelectedIdsNode(
		private val selectedIds: SelectedIds?,
		private val assignment: FragmentSegmentAssignmentState?,
		private val selectedSegments: SelectedSegments,
	) {

		class SelectedSegmentsConverter(val selectedSegments: SelectedSegments) : StringConverter<LongArray>() {
			override fun toString(ids: LongArray?): String = ids?.joinToString(",") ?: ""

			override fun fromString(string: String?): LongArray {
				val lastFragmentSelection = selectedSegments.selectedIds.lastSelection
				val segments = string?.split(Regex("\\D+"))?.filter { it.isNotBlank() }
					?.map { it.toLong() }
					?: emptyList()
				/* get fragments from segments and update */
				segments
					.flatMap { selectedSegments.assignment.getFragments(it).toArray().asIterable() }
					.toLongArray().let { fragments ->
						if (fragments.isEmpty()) {
							selectedSegments.selectedIds.activateAlso(selectedSegments.selectedIds.lastSelection)
							return@let
						}
						selectedSegments.selectedIds.activate(*fragments)
						if (selectedSegments.selectedIds.isActive(lastFragmentSelection)) {
							selectedSegments.selectedIds.activateAlso(lastFragmentSelection)
						}
					}
				return segments.toLongArray()
			}
		}

		class SelectedFragmentsConverter(val selectedSegments: SelectedSegments) : StringConverter<LongArray>() {

			companion object {
				fun fragmentIds(selectedSegments: SelectedSegments): LongArray {
					val fragmentIds = TLongHashSet()
					selectedSegments.selectedIds.activeIds.forEach { id ->
						val fragmentsForId = selectedSegments.assignment.getFragments(id)
						if (fragmentsForId.contains(id)) {
							// ID is both a segment and fragment ID; Fragment/Segment may or may not exist
							fragmentIds.add(id)

						}
						true
					}
					return fragmentIds.toArray()
				}
			}

			override fun toString(ids: LongArray?): String = ids?.joinToString(",") ?: ""

			override fun fromString(string: String?): LongArray {
				val lastFragmentSelection = selectedSegments.selectedIds.lastSelection
				val fragments = string?.split(Regex("\\D+"))?.filter { it.isNotBlank() }
					?.map { it.toLong() }
					?.toLongArray()
					?: LongArray(0)

				fragments.let {
					if (it.isEmpty()) {
						selectedSegments.selectedIds.activateAlso(selectedSegments.selectedIds.lastSelection)
						return@let
					}
					selectedSegments.selectedIds.activate(*it)
					if (selectedSegments.selectedIds.isActive(lastFragmentSelection)) {
						selectedSegments.selectedIds.activateAlso(lastFragmentSelection)
					}
				}
				return fragments
			}
		}

		val node: Node
			get() {
				if (selectedIds == null || assignment == null)
					return Region()

				val grid = GridPane().also { it.hgap = 5.0 }

				val lastSelectionLabel = Label("Last Selection")
				val fragmentLabel = Label("Fragments")
				val segmentLabel = Label("Segments")
				Platform.runLater {
					lastSelectionLabel.maxWidth = Label.USE_COMPUTED_SIZE
					fragmentLabel.maxWidth = Label.USE_COMPUTED_SIZE
					segmentLabel.maxWidth = Label.USE_COMPUTED_SIZE
				}
				lastSelectionLabel.tooltip = Tooltip("Last selected fragment id")
				fragmentLabel.tooltip = Tooltip("Active fragment ids")
				segmentLabel.tooltip = Tooltip("Active segment ids")

				grid.add(lastSelectionLabel, 0, 0)
				grid.add(fragmentLabel, 0, 1)
				grid.add(segmentLabel, 0, 2)
				grid.columnConstraints += ColumnConstraints().also { it.hgrow = Priority.NEVER }


				/* the field values are the id arrays themselves, so the fields render and copy them without any
				 * help from here; SelectedSegments is not usable as the value because it is mutated in place and
				 * so never triggers a change invalidation */
				val segmentIdsProperty = SimpleObjectProperty(selectedSegments.segments.toArray())
				val fragmentIdsProperty = SimpleObjectProperty(SelectedFragmentsConverter.fragmentIds(selectedSegments))

				val selectedSegmentsConverter = SelectedSegmentsConverter(selectedSegments)
				val segmentsField = ObjectField(
					segmentIdsProperty,
					selectedSegmentsConverter,
					interceptCopyTextField { selectedSegmentsConverter.toString(segmentIdsProperty.value) },
					ObjectField.SubmitOn.ENTER_PRESSED,
					ObjectField.SubmitOn.FOCUS_LOST,
				)

				val selectedFragmentsConverter = SelectedFragmentsConverter(selectedSegments)
				val fragmentsField = ObjectField(
					fragmentIdsProperty,
					selectedFragmentsConverter,
					interceptCopyTextField { selectedFragmentsConverter.toString(fragmentIdsProperty.value) },
					ObjectField.SubmitOn.ENTER_PRESSED,
					ObjectField.SubmitOn.FOCUS_LOST,
				)

				/* For large numbers of IDs (the test dataset I used had ~70k) selected all at once, just
				 * rendering every id as a string in the text field far too slow.
				 * This solution lets the rendered string be shortened in some way, while intercepting the
				 * clipboard copy content, so all entries can still be copied.
				 * Editing the field still behaves, but may be rendered after if the text is too long. */
				segmentsField.displayConverter = { renderIds(it) }
				fragmentsField.displayConverter = { renderIds(it) }

				val lastSelectionField = NumberField.longField(
					selectedSegments.selectedIds.lastSelection,
					{ it >= 0 && it.toULong() < Imglib2Labels.MAX_ID.toULong() },
					ObjectField.SubmitOn.ENTER_PRESSED,
					ObjectField.SubmitOn.FOCUS_LOST
				)
				lastSelectionField.valueProperty().addListener { _, _, newId ->
					val activeFragment = newId.toLong()
					if (selectedSegments.selectedIds.isActive(activeFragment))
						selectedSegments.selectedIds.activateAlso(activeFragment)
					else
						selectedSegments.selectedIds.activate(activeFragment)
				}

				/* generating a string from a very large selection can be slow, do it off the UI thread, and just provide it
				* to the UI thread when done. However, we need to ensure earlier, slower requests are not applied if
				* superseded by a new, faster request.  */
				val selectionTextGeneration = AtomicLong()
				selectedSegments.addListener { _ ->
					val generation = selectionTextGeneration.incrementAndGet()
					val segmentIds = selectedSegments.segments.toArray()
					val fragmentIds = SelectedFragmentsConverter.fragmentIds(selectedSegments)
					val lastSelection = selectedSegments.selectedIds.lastSelection
					InvokeOnJavaFXApplicationThread {
						/* a large selection can be collected after a smaller, newer one; don't let it overwrite */
						if (generation == selectionTextGeneration.get()) {
							segmentIdsProperty.value = segmentIds
							fragmentIdsProperty.value = fragmentIds
							lastSelectionField.value = lastSelection
						}
					}
				}

				val activeFragmentsToolTip = Tooltip()
				val activeSegmentsToolTip = Tooltip()
				activeFragmentsToolTip.textProperty().bind(fragmentsField.textField.textProperty())
				activeSegmentsToolTip.textProperty().bind(segmentsField.textField.textProperty())
				fragmentsField.textField.tooltip = activeFragmentsToolTip
				segmentsField.textField.tooltip = activeSegmentsToolTip

				listOf(lastSelectionField, fragmentsField, segmentsField).forEach { it.textField.alignment = Pos.BASELINE_LEFT }


				Platform.runLater {
					lastSelectionField.textField.maxWidth = Region.USE_COMPUTED_SIZE
					fragmentsField.textField.maxWidth = Region.USE_COMPUTED_SIZE
					segmentsField.textField.maxWidth = Region.USE_COMPUTED_SIZE
				}

				grid.add(lastSelectionField.textField, 1, 0)
				grid.add(fragmentsField.textField, 1, 1)
				grid.add(segmentsField.textField, 1, 2)
				grid.columnConstraints += ColumnConstraints().also { it.hgrow = Priority.ALWAYS }

				grid.add(copyToClipboardButton("Copy all active fragment ids") { selectedFragmentsConverter.toString(fragmentIdsProperty.value) }, 2, 1)
				grid.add(copyToClipboardButton("Copy all active segment ids") { selectedSegmentsConverter.toString(segmentIdsProperty.value) }, 2, 2)
				grid.columnConstraints += ColumnConstraints().also { it.hgrow = Priority.NEVER }


				val lastSelectionHelp = Button().apply {
					addStyleClass(Style.HELP_ICON)
					onAction = EventHandler {
						PainteraAlerts.information("Ok", true).also {
							it.title = "Last Selection"
							it.headerText = it.title
							it.dialogPane.content = TextArea().also { area ->
								area.isWrapText = true
								area.isEditable = false
								area.text = LAST_SELECTION_DIALOG_DESCRIPTION
							}
						}.showAndWait()
					}
				}
				val fragmentSelectionHelp = Button().apply {
					addStyleClass(Style.HELP_ICON)
					onAction = EventHandler {
						PainteraAlerts.information("Ok", true).also {
							it.title = "Fragment Selection"
							it.headerText = it.title
							it.dialogPane.content = TextArea().also { area ->
								area.isWrapText = true
								area.text = FRAGMENT_SELECTION_DIALOG_DESCRIPTION
							}
						}.showAndWait()
					}
				}
				val segmentSelectionHelp = Button().apply {
					addStyleClass(Style.HELP_ICON)
					onAction = EventHandler {
						PainteraAlerts.information("Ok", true).also {
							it.title = "Segment Selection"
							it.headerText = it.title
							it.dialogPane.content = TextArea().also { area ->
								area.isWrapText = true
								area.isEditable = false
								area.text = SEGMENT_SELECTION_DIALOG_DESCRIPTION
							}
						}.showAndWait()
					}
				}
				grid.add(lastSelectionHelp, 3, 0)
				grid.add(fragmentSelectionHelp, 3, 1)
				grid.add(segmentSelectionHelp, 3, 2)
				grid.columnConstraints += ColumnConstraints().also { it.hgrow = Priority.NEVER }

				val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
					headerText = "Fragment Selection"
					dialogPane.content = TextArea().also {
						it.isWrapText = true
						it.isEditable = false
						it.text = DESCRIPTION
					}
				}

				val tpGraphics = HBox(
					Label("Fragment Selection"),
					NamedNode.bufferNode(),
					Button("").apply {
						addStyleClass(Style.HELP_ICON)
						setOnAction { helpDialog.show() }
					}
				).also { it.alignment = Pos.CENTER }

				return TitledPane(null, grid).apply {
                        isExpanded = false
                        graphic = tpGraphics
                        contentDisplay = ContentDisplay.GRAPHIC_ONLY
                        alignment = Pos.CENTER_RIGHT
                        tooltip = Tooltip(DESCRIPTION)
					}

			}

		companion object {

			private val LAST_SELECTION_DIALOG_DESCRIPTION = """
				The last selected fragment is used for painting, and assignment actions.
				The new selection can be appended to the currently active fragments or set as the only active fragment.
				If no fragments are currently active, both choices are equivalent.
			""".trimIndent()

			private val FRAGMENT_SELECTION_DIALOG_DESCRIPTION = """
				Active fragments (and the containing segments) will be highlighted in the 2D cross-sections and rendered
				in the 3D viewer. All segments that contain the specified fragments will also be selected.
				If the current Last Selection ID is not one of the listed Fragment Selection IDs, then
				the last fragment specified will be used for the Last Selection ID. 
			""".trimIndent()

			private val SEGMENT_SELECTION_DIALOG_DESCRIPTION = """
				Active segments (and all contained fragments) will be highlighted in the 2D cross-sections and rendered
				in the 3D viewer. When manually specified, all fragments contained by the selected fragments will also be selected.
				If the current last selection is not part of the specified selected segment(s), the resulting last fragment will be used
				for the Last Selection ID.
			""".trimIndent()

			private val DESCRIPTION = """
				Fragment can be selected with left mouse click. When used with the CTRL key or right mouse click append the
				fragment to the set of currently active (selected) fragments. In either case, the last selected fragment will
				be used for tasks that require a fragment id, such as painting or merge/split actions. Alternatively, the last
				selection and set of currently active fragments can be modified by double clicking the respective text fields.
			""".trimIndent()
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
						title,
						{ Labels.withTooltip("$it") },
						{ action -> assignments.deleteAction(action) },
						{ deleteAllAssignments(assignments) }
					)

					val tpGraphics = HBox(
						Label("Assignments"),
						NamedNode.bufferNode()
					).also { it.alignment = Pos.CENTER }

					TitledPane(null, undoPane).apply {
						isExpanded = false
						graphic = tpGraphics
						contentDisplay = ContentDisplay.GRAPHIC_ONLY
						alignment = Pos.CENTER_RIGHT
						tooltip = null /* TODO */
					}
				} else
					null
			}

		private fun deleteAllAssignments(assignments: FragmentSegmentAssignmentStateWithActionTracker) {
			val confirm = PainteraAlerts.confirmation("_Delete", "_Cancel").apply {
				headerText = "Delete all assignments for this source?"
				contentText = "The merges and splits stored in the Paintera project will be removed. Assignments already committed to the data backend are not affected."
			}
			if (confirm.showAndWait().orElse(null) == ButtonType.OK)
				assignments.deleteAllActions()
		}

	}

	private class MaskedSourceNode(
		private val source: DataSource<*, *>,
		private val brushProperties: BrushProperties,
		private val refreshMeshes: () -> Unit,
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
					{
						askForgetCanvasAlert(source)
						refreshMeshes()
					}

					val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
						headerText = "Canvas"
						contentText = "TODO" /* TODO */
					}

					val tpGraphics = HBox(
						Label("Canvas"),
						NamedNode.bufferNode(),
						showCanvasCheckBox,
						clearButton,
						Button("").apply {
							addStyleClass(Style.HELP_ICON)
							setOnAction { helpDialog.show() }
						}
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
					TitledPanes.createCollapsed(null, contents).apply {
						graphic = tpGraphics
						contentDisplay = ContentDisplay.GRAPHIC_ONLY
						alignment = Pos.CENTER_RIGHT
						tooltip = null /* TODO */
					}
				} else
					null
			}
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
		fun askForgetCanvasAlert(source: MaskedSource<*, *>, owner: Window? = null) {
			if (askForgetCanvasAlert()) {
				try {
					source.forgetCanvases()
				} catch (e: CannotClearCanvas) {
					LOG.error(e) { "Unable to clear canvas." }
					Exceptions.exceptionAlert(Constants.NAME, "Unable to clear canvas.", e, owner = owner)
				}
			}

		}

		private fun askForgetCanvasAlert() = PainteraAlerts.confirmation("_Yes", "_No").apply {
			headerText = "Clear Canvas"
			dialogPane.content = TextArea("Clearing canvas will remove all painted data that have not been committed yet. Proceed?").apply {
				isEditable = false
				isWrapText = true
			}
		}
			.showAndWait()
			.filter { ButtonType.OK == it }
			.isPresent
	}
}
private const val MAX_DISPLAYED_IDS = 100

/** the leading [MAX_DISPLAYED_IDS] of [ids], marked with how many were left out */
private fun renderIds(ids: LongArray): String {
	if (ids.size <= MAX_DISPLAYED_IDS)
		return ids.joinToString(",")
	return ids.take(MAX_DISPLAYED_IDS).joinToString(",") + ", … +${ids.size - MAX_DISPLAYED_IDS} more"
}

/** a field whose displayed text may be abridged; selecting all of it copies [replacementText] instead */
private fun interceptCopyTextField(replacementText: () -> String) = object : TextField() {
	override fun copy() {
		val replacement = replacementText()
		if (selectedText == text && text != replacement)
			Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(replacement) })
		else
			super.copy()
	}
}

/** copies [wholeText] rather than what the field shows, which may be shortened */
private fun copyToClipboardButton(description: String, wholeText: () -> String) =
	Buttons.withTooltip(null, description) {
		val content = ClipboardContent().also { it.putString(wholeText()) }
		Clipboard.getSystemClipboard().setContent(content)
	}.apply { addStyleClass(Style.fontAwesome(FontAwesome.COPY)) }
