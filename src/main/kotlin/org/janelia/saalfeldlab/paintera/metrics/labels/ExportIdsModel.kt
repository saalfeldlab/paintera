package org.janelia.saalfeldlab.paintera.metrics.labels

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.n5.universe.metadata.N5MultiScaleMetadata
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.CommitHandler
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendPainteraDataset
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiscaleGroup
import java.util.function.LongPredicate

internal val PainteraDataMultiscaleMetadataState.uniqueLabelsMetadata: N5MultiScaleMetadata?
	get() = (painteraDataMultiscaleMetadata as? N5PainteraLabelMultiscaleGroup)?.uniqueLabelsMetadata

enum class ExportPhase {
	NOT_STARTED,
	RUNNING,
	COMPLETED,
	CANCELED,
	FAILED
}

enum class Region(val text: String) {
	CROP("Crop Only"),
	FULL("Full Source")
}

enum class SortBy(val text: String) {
	FRAGMENT("Fragment ID"),
	SEGMENT("Segment ID"),
	COUNT("Count")
}

class ExportIdsSource(
	val name: String,
	val metadataState: PainteraDataMultiscaleMetadataState,
	val fragmentSegmentAssignment: FragmentSegmentAssignmentState,
	val isLocked: LongPredicate,
	private val commitCanvas: () -> Boolean,
) {

	fun commitCanvasOrCancel() = commitCanvas()

	companion object {

		fun from(sourceState: ConnectomicsLabelState<*, *>): ExportIdsSource? {
			val metadataState = (sourceState.backend as? N5BackendPainteraDataset<*, *>)?.metadataState as? PainteraDataMultiscaleMetadataState ?: return null
			if (metadataState.uniqueLabelsMetadata == null)
				return null
			return ExportIdsSource(
				sourceState.nameProperty().get(),
				metadataState,
				sourceState.fragmentSegmentAssignment,
				{ segment -> sourceState.lockedSegments.isLocked(segment) },
			) {
                CommitHandler.commitCanvasOrCancel(
                    sourceState,
                    { _, name -> "IDs are exported from committed data only.\nCommit source ( $name ) before exporting?" },
                    "_Cancel Export",
                    "Commi_t and Export"
                )
			}
		}
	}
}

internal fun getValidExportIdSources(): List<ExportIdsSource> {
	val sourceInfo = paintera.baseView.sourceInfo()
	return sourceInfo.trackSources().mapNotNull { source ->
		(sourceInfo.getState(source) as? ConnectomicsLabelState<*, *>)?.let { ExportIdsSource.from(it) }
	}
}

interface ExportIdsModel {

	val sources: ObservableList<ExportIdsSource>
	val sourceProperty: ObjectProperty<ExportIdsSource?>

	val exportRegionProperty: ObjectProperty<Region>

	val exportSegmentIdProperty: BooleanProperty
	val exportFragmentIdProperty: BooleanProperty
	val exportLockedProperty: BooleanProperty
	val exportCountProperty: BooleanProperty

	val sortByProperty: ObjectProperty<SortBy>

	val exportDirectoryProperty: StringProperty
	val fileNameProperty: StringProperty

	val phaseProperty: ObjectProperty<ExportPhase>
	val progressProperty: DoubleProperty

	val statusProperty: StringProperty

	val segmentsProperty: LongProperty
	val fragmentsProperty: LongProperty
	val lockedProperty: LongProperty
	val totalCountProperty: LongProperty

	companion object {
		fun default(sources: ObservableList<ExportIdsSource> = FXCollections.observableArrayList()): ExportIdsModel = DefaultExportIdsModel(sources)

		private var sessionModel = DefaultExportIdsModel(FXCollections.observableArrayList())

		fun forCurrentSession(): ExportIdsModel {
			val sources = getValidExportIdSources()
			val prevSource = sessionModel.sourceProperty.value
			if (prevSource != null && sources.none { it.metadataState === prevSource.metadataState })
				sessionModel = DefaultExportIdsModel(FXCollections.observableArrayList())
			return sessionModel.apply {
				refreshSources(sources)
				resetProgress()
			}
		}
	}
}

internal class DefaultExportIdsModel(override val sources: ObservableList<ExportIdsSource>) : ExportIdsModel {
	override val sourceProperty = SimpleObjectProperty<ExportIdsSource?>()
	override val exportRegionProperty = SimpleObjectProperty(Region.CROP)
	override val exportSegmentIdProperty = SimpleBooleanProperty(true)
	override val exportFragmentIdProperty = SimpleBooleanProperty(true)
	override val exportLockedProperty = SimpleBooleanProperty(false)
	override val exportCountProperty = SimpleBooleanProperty(false)
	override val sortByProperty = SimpleObjectProperty(SortBy.FRAGMENT)
	override val exportDirectoryProperty = SimpleStringProperty("")
	override val fileNameProperty = SimpleStringProperty("")
	override val phaseProperty = SimpleObjectProperty(ExportPhase.NOT_STARTED)
	override val progressProperty = SimpleDoubleProperty(0.0)
	override val statusProperty = SimpleStringProperty("")
	override val segmentsProperty = SimpleLongProperty(0)
	override val fragmentsProperty = SimpleLongProperty(0)
	override val lockedProperty = SimpleLongProperty(0)
	override val totalCountProperty = SimpleLongProperty(0)

	fun refreshSources(currentSources: List<ExportIdsSource>) {
		val prevSource = sourceProperty.value
		sources.setAll(currentSources)
		sourceProperty.value = currentSources.firstOrNull { it.metadataState === prevSource?.metadataState }
	}

	fun resetProgress() {
		phaseProperty.value = ExportPhase.NOT_STARTED
		progressProperty.value = 0.0
		statusProperty.value = ""
		segmentsProperty.value = 0
		fragmentsProperty.value = 0
		lockedProperty.value = 0
		totalCountProperty.value = 0
	}
}
