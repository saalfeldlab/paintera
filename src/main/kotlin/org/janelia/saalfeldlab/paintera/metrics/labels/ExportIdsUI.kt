package org.janelia.saalfeldlab.paintera.metrics.labels

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.ObjectBinding
import javafx.beans.value.ObservableValue
import javafx.geometry.Orientation
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.stage.DirectoryChooser
import javafx.util.StringConverter
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.PainteraCache.Companion.distinctDisplayStrings
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExportIdsUI(private val model: ExportIdsModel) : VBox() {

	init {
		styleClass += "export-ids"

        children += VBox(sourceRow(), regionRow(), columnsRow(), locationRow(), fileNameRow()).apply {
            styleClass += "export-ids-options"
            disableProperty().bind(model.phaseProperty.isEqualTo(ExportPhase.RUNNING))
        }
		children += progressSection()
	}

	private fun isLabelMultisetBinding() = Bindings.createBooleanBinding({ model.sourceProperty.value?.metadataState?.isLabelMultiset == true }, model.sourceProperty)

	private fun labeledRow(label: String, node: Node) = HBox(Label(label).apply { styleClass += "export-ids-row-label" }, node).apply {
		styleClass += "export-ids-row"
		HBox.setHgrow(node, Priority.ALWAYS)
	}

	private fun sourceRow(): HBox {
		val choiceBox = ChoiceBox(model.sources).apply {
			styleClass += "export-ids-field"
			converter = object : StringConverter<ExportIdsSource>() {
				override fun toString(source: ExportIdsSource?) = source?.name ?: "Select a Source..."
				override fun fromString(string: String?) = model.sources.first { it.name == string }
			}
			valueProperty().bindBidirectional(model.sourceProperty)
			if (value == null)
				selectionModel.selectFirst()
		}
		return labeledRow("Source", choiceBox)
	}

	private fun regionRow(): HBox {
		val choiceBox = ChoiceBox<Region>().apply {
			styleClass += "export-ids-field"
			items.setAll(*Region.entries.toTypedArray())
			converter = object : StringConverter<Region>() {
				override fun toString(region: Region?) = region?.text ?: ""
				override fun fromString(string: String?) = Region.entries.first { it.text == string }
			}
			valueProperty().bindBidirectional(model.exportRegionProperty)
		}
		return labeledRow("Region", choiceBox).apply {
			val hasCrop = model.sourceProperty.createNonNullValueBinding { it?.metadataState?.xyzView?.xyzCrop != null }
			visibleProperty().bind(hasCrop)
			managedProperty().bind(hasCrop)
		}
	}

	private fun columnsRow(): HBox {
		val canCount = isLabelMultisetBinding()
		val countBox = CheckBox("Count").apply {
			selectedProperty().bindBidirectional(model.exportCountProperty)
			disableProperty().bind(canCount.not())
		}
		canCount.subscribe { canCountNow -> if (!canCountNow) model.exportCountProperty.value = false }

		val entries = GridPane().apply {
			styleClass += "export-ids-column-checks"
			add(CheckBox("Fragment ID").apply { selectedProperty().bindBidirectional(model.exportFragmentIdProperty) }, 0, 0)
			add(CheckBox("Segment ID").apply { selectedProperty().bindBidirectional(model.exportSegmentIdProperty) }, 1, 0)
			add(CheckBox("Locked").apply { selectedProperty().bindBidirectional(model.exportLockedProperty) }, 0, 1)
			add(countBox, 1, 1)
		}
		val sortChoice = ChoiceBox<SortBy>().apply {
			converter = object : StringConverter<SortBy>() {
				override fun toString(sortBy: SortBy?) = sortBy?.text ?: ""
				override fun fromString(string: String?) = SortBy.entries.first { it.text == string }
			}
			valueProperty().subscribe { sortBy -> sortBy?.let { model.sortByProperty.value = it } }
		}
		fun updateSortChoices() {
			val valid = listOfNotNull(
				SortBy.FRAGMENT.takeIf { model.exportFragmentIdProperty.value },
				SortBy.SEGMENT.takeIf { model.exportSegmentIdProperty.value },
				SortBy.COUNT.takeIf { model.exportCountProperty.value },
			)
			sortChoice.items.setAll(valid)
			model.sortByProperty.value = model.sortByProperty.value.takeIf { it in valid } ?: valid.firstOrNull() ?: SortBy.FRAGMENT
			sortChoice.value = model.sortByProperty.value.takeIf { it in valid }
		}
		model.exportFragmentIdProperty.subscribe { _ -> updateSortChoices() }
		model.exportSegmentIdProperty.subscribe { _ -> updateSortChoices() }
		model.exportCountProperty.subscribe { _ -> updateSortChoices() }

		val sortBox = HBox(Label("Sort By"), sortChoice).apply { styleClass += "export-ids-row" }
		val contents = HBox(entries, Separator(Orientation.VERTICAL), sortBox).apply { styleClass += "export-ids-columns" }
		return labeledRow("Columns", contents)
	}

	private fun locationRow(): HBox {
		val directoryField = TextField().apply {
			promptText = "Directory to write the file to"
			styleClass += "export-ids-field"
			textProperty().bindBidirectional(model.exportDirectoryProperty)
		}
		val browse = Button("Browse").apply {
			setOnAction {
				val chooser = DirectoryChooser().apply {
					title = "Export Location"
					File(directoryField.text.trim()).takeIf { it.isDirectory }?.let { initialDirectory = it }
				}
				chooser.showDialog(scene?.window)?.let { directoryField.text = it.absolutePath }
			}
		}
		val controls = HBox(directoryField, browse).apply { styleClass += "export-ids-location" }
		val recents = FXCollections.observableArrayList(PainteraCache.RECENT_METRICS_EXPORT_DIRECTORIES.distinctDisplayStrings())
		if (directoryField.text.isNullOrBlank())
			recents.firstOrNull()?.let { directoryField.text = it }
		controls.children += MatchSelectionMenuButton(recents, "_Recents", null, { removed ->
			PainteraCache.RECENT_METRICS_EXPORT_DIRECTORIES.removeEntry(removed)
			recents.remove(removed)
		}) { directoryField.text = it }.apply {
			disableProperty().bind(Bindings.isEmpty(recents))
		}
		HBox.setHgrow(directoryField, Priority.ALWAYS)
		return labeledRow("Export Location", controls)
	}

	private fun fileNameRow(): HBox {
		val fileNameField = TextField().apply {
			promptText = "Name of the CSV file"
			styleClass += "export-ids-field"
			textProperty().bindBidirectional(model.fileNameProperty)
		}
		/* follow the source with the default name, unless the name was edited */
		var lastDefault: String? = null
		model.sourceProperty.subscribe { source ->
			val default = source?.let { defaultFileName(it.name) }
			if (default != null && (fileNameField.text.isNullOrBlank() || fileNameField.text == lastDefault))
				fileNameField.text = default
			lastDefault = default
		}
		return labeledRow("File Name", fileNameField)
	}

	private fun defaultFileName(sourceName: String): String {
		val safeName = sourceName.replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
		return "${safeName}_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.csv"
	}

	private fun progressSection(): VBox {
		val progressBar = AnimatedProgressBar().apply {
			progressTargetProperty.bind(model.progressProperty)
			styleClass += "export-ids-field"
		}

		val canCount = isLabelMultisetBinding()

		fun summaryLabel(format: String, value: ObservableValue<Number>, shown: ObservableValue<Boolean>) = Label().apply {
			textProperty().bind(Bindings.format(format, value))
			visibleProperty().bind(shown)
			managedProperty().bind(shown)
		}

		val summaryBox = VBox(
			summaryLabel("Segments: %,d", model.segmentsProperty, model.exportSegmentIdProperty),
			summaryLabel("Fragments: %,d", model.fragmentsProperty, model.exportFragmentIdProperty),
			summaryLabel("Locked: %,d", model.lockedProperty, model.exportLockedProperty),
			summaryLabel("Total Count: %,d", model.totalCountProperty, model.exportCountProperty.and(canCount)),
		).apply {
            styleClass += "export-ids-summary"
        }
		val statusField = TextField().apply {
			textProperty().bind(model.statusProperty)
			isEditable = false
			styleClass += "selectable-label"
		}

		val startedBinding = model.phaseProperty.isNotEqualTo(ExportPhase.NOT_STARTED)
		return VBox(progressBar, summaryBox, statusField).apply {
			styleClass += "export-ids-progress"
			visibleProperty().bind(startedBinding)
			managedProperty().bind(startedBinding)
		}
	}

	companion object {

		internal fun ExportIdsModel.exportDisabledBinding() =
            sourceProperty.createNonNullValueBinding(exportSegmentIdProperty, exportFragmentIdProperty, exportDirectoryProperty, fileNameProperty, phaseProperty) {
                it == null
                        || !(exportSegmentIdProperty.value || exportFragmentIdProperty.value)
                        || exportDirectoryProperty.value.isNullOrBlank()
                        || fileNameProperty.value.isNullOrBlank()
                        || phaseProperty.value == ExportPhase.RUNNING
            }

		fun ExportIdsModel.getDialog(): Alert = PainteraAlerts.confirmation("_Export", "_Close").apply {
			title = "Export IDs"
			headerText = "Export Label Metrics as CSV"
			dialogPane.content = ExportIdsUI(this@getDialog)
			(dialogPane.lookupButton(ButtonType.OK) as Button).disableProperty().bind(exportDisabledBinding())

			phaseProperty.subscribe { phase ->
				if (phase == ExportPhase.RUNNING)
					Platform.runLater { dialogPane.scene?.window?.sizeToScene() }
			}
			(dialogPane.lookupButton(ButtonType.CANCEL) as Button).textProperty().bind(
				phaseProperty.createNonNullValueBinding { if (it == ExportPhase.RUNNING) "_Cancel" else "_Close" }
			)
		}
	}
}
