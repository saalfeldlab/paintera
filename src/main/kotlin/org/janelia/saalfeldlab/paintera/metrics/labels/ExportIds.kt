package org.janelia.saalfeldlab.paintera.metrics.labels

import gnu.trove.map.TLongLongMap
import gnu.trove.map.hash.TLongLongHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import javafx.event.ActionEvent
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import net.imglib2.type.label.Label
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.metrics.labels.ExportIdsUI.Companion.getDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import java.io.Writer
import java.nio.file.Files
import java.util.function.LongPredicate

object ExportIds : MenuAction("_Export IDs...") {

	init {
		verifyPermission(MenuActionType.ExportIds)
		onActionWithState<ExportIdsState> {
			val dialog = getDialog()
			val exportButton = dialog.dialogPane.lookupButton(ButtonType.OK) as Button
			val closeButton = dialog.dialogPane.lookupButton(ButtonType.CANCEL) as Button

			exportButton.addEventFilter(ActionEvent.ACTION) { event ->
				event.consume()
				if (confirmOverwrite() && commitCanvasOrCancel())
					startExport()
			}
			closeButton.addEventFilter(ActionEvent.ACTION) { event ->
				if (phaseProperty.value == ExportPhase.RUNNING) {
					event.consume()
					cancelExport()
				}
			}
			dialog.setOnHidden { cancelExport() }
			dialog.showAndWait()
		}
	}

	private fun ExportIdsState.confirmOverwrite(): Boolean {
		if (!Files.exists(exportPath))
			return true
		val overwrite = PainteraAlerts.confirmation("_Overwrite", "_Cancel").apply {
			title = "Export IDs"
			headerText = "File already exists"
			contentText = "$exportPath exists and will be replaced."
		}.showAndWait()
		return overwrite.map { it == ButtonType.OK }.orElse(false)
	}

	private fun ExportIdsState.commitCanvasOrCancel(): Boolean {
		return sourceProperty.value?.commitCanvasOrCancel() ?: false
	}
}

class LabelMetrics(
    private val assignment: FragmentSegmentAssignmentState,
    private val isLocked: LongPredicate,
) {

	data class Snapshot(
        val segments: Int = 0,
        val fragments: Int = 0,
        val lockedSegments: Int = 0,
        val totalCount: Long = 0L
    )

	private val fragmentCounts = TLongLongHashMap()
	private val segments = TLongHashSet()
	private val lockedSegments = TLongHashSet()
	private var totalCount = 0L

	@Volatile
	var snapshot = Snapshot()
		private set

	@Synchronized
	fun addIds(ids: TLongSet) {
		ids.forEach { fragment ->
			record(fragment, 0L)
			true
		}
		updateSnapshot()
	}

	@Synchronized
	fun addCounts(counts: TLongLongMap) {
		counts.forEachEntry { fragment, count ->
			record(fragment, count)
			true
		}
		updateSnapshot()
	}

	private fun record(fragment: Long, count: Long) {
		if (fragment == Label.BACKGROUND)
			return
		if (!fragmentCounts.containsKey(fragment)) {
			val segment = assignment.getSegment(fragment)
			if (segments.add(segment) && isLocked.test(segment))
				lockedSegments.add(segment)
		}
		fragmentCounts.adjustOrPutValue(fragment, count, count)
		totalCount += count
	}

	private fun updateSnapshot() {
		snapshot = Snapshot(segments.size(), fragmentCounts.size(), lockedSegments.size(), totalCount)
	}

	@Synchronized
	internal fun writeCsv(
        writer: Writer,
        segmentColumn: Boolean,
        fragmentColumn: Boolean,
        lockedColumn: Boolean,
        countColumn: Boolean,
        sortBy: SortBy = SortBy.FRAGMENT,
        commentLines: List<String> = emptyList(),
        shouldContinue: () -> Boolean = { true },
	): Boolean {
		commentLines.forEach { writer.append("# ").append(it).append('\n') }
		val header = listOfNotNull(
			"Fragment ID".takeIf { fragmentColumn },
			"Segment ID".takeIf { segmentColumn },
			"Locked".takeIf { lockedColumn },
			"Voxel Count".takeIf { countColumn },
		)
		writer.append(header.joinToString(",")).append('\n')

		fun writeRow(segment: Long, fragment: Long, count: Long) {
			val cells = listOfNotNull(
				fragment.toString().takeIf { fragmentColumn },
				segment.toString().takeIf { segmentColumn },
				isLocked.test(segment).toString().takeIf { lockedColumn },
				count.toString().takeIf { countColumn },
			)
			writer.append(cells.joinToString(",")).append('\n')
		}

		if (fragmentColumn) {
			val fragments = fragmentCounts.keys().toList()
			val ordered = when (sortBy) {
				SortBy.FRAGMENT -> fragments.sorted()
				SortBy.SEGMENT -> fragments.sortedWith(compareBy<Long> { assignment.getSegment(it) }.thenBy { it })
				SortBy.COUNT -> fragments.sortedWith(compareByDescending<Long> { fragmentCounts[it] }.thenBy { assignment.getSegment(it) }.thenBy { it })
			}
			for (fragment in ordered) {
				if (!shouldContinue())
					return false
				writeRow(assignment.getSegment(fragment), fragment, fragmentCounts[fragment])
			}
		} else {
			val countsPerSegment = HashMap<Long, Long>()
			fragmentCounts.forEachEntry { fragment, count ->
				countsPerSegment.merge(assignment.getSegment(fragment), count, Long::plus)
				true
			}
			val segments = countsPerSegment.keys
			val ordered = when (sortBy) {
				SortBy.COUNT -> segments.sortedWith(compareByDescending<Long> { countsPerSegment[it] }.thenBy { it })
				else -> segments.sorted()
			}
			for (segment in ordered) {
				if (!shouldContinue())
					return false
				writeRow(segment, -1L, countsPerSegment.getValue(segment))
			}
		}
		return true
	}
}