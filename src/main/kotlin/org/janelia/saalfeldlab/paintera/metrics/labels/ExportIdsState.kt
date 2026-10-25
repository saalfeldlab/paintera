package org.janelia.saalfeldlab.paintera.metrics.labels

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.event.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.ui.ExceptionNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.cropInterval
import org.janelia.saalfeldlab.paintera.control.actions.state.PainteraActionState
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.LongAdder
import kotlin.coroutines.cancellation.CancellationException

private val LOG = KotlinLogging.logger { }

private const val UPDATE_INTERVAL_MILLIS = 250L

class ExportIdsState(
    delegate: ExportIdsModel = ExportIdsModel.forCurrentSession(),
) : PainteraActionState(), ExportIdsModel by delegate {

	private var job: Job? = null

	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)
		action.verify("A Paintera data source is open") { sources.isNotEmpty() }
	}

	val exportPath: Path
		get() = Path.of(exportDirectoryProperty.value.trim()).resolve(fileNameProperty.value.trim()).toAbsolutePath()

	fun cancelExport() {
		job?.cancel()
	}

	fun startExport(): Job? {
		val source = sourceProperty.value ?: return null
		val metadataState = source.metadataState
		val path = exportPath
		val countPixels = exportCountProperty.value && metadataState.isLabelMultiset

		val scanner = try {
			UniqueLabelsForRegion.from(metadataState, countPixels)
		} catch (e: Exception) {
			LOG.error(e) { "Could not scan ${metadataState.dataset} for ids" }
			ExceptionNode.exceptionDialog(e).showAndWait()
			return null
		}

		val exportRegion = metadataState.xyzView.xyzCrop
			?.takeIf { exportRegionProperty.value == Region.CROP }
			?.let { cropInterval(metadataState, 0, scanner.firstLevelBounds().dimensionsAsLongArray(), it) }
			?: scanner.firstLevelBounds()
		val totalPixels = Intervals.numElements(exportRegion).coerceAtLeast(1L)

		val metrics = LabelMetrics(source.fragmentSegmentAssignment, source.isLocked)
		val scanned = LongAdder()

		val segmentColumn = exportSegmentIdProperty.value
		val fragmentColumn = exportFragmentIdProperty.value
		val lockedColumn = exportLockedProperty.value
		val sortBy = sortByProperty.value

        phaseProperty.value = ExportPhase.RUNNING
		progressProperty.value = 0.0
		statusProperty.value = ""
		showSnapshot(LabelMetrics.Snapshot())

		val exportJob = CoroutineScope(Dispatchers.Default).launch {
			scanner.process(exportRegion, metrics, scanned)
			ensureActive()
			path.parent?.let { Files.createDirectories(it) }
			val commentLines = listOf(
				"source: ${N5Helpers.canonicalString(metadataState.reader)}",
				"dataset: ${metadataState.dataset}",
				"volume: min=${exportRegion.minAsLongArray().contentToString()} max=${exportRegion.maxAsLongArray().contentToString()}",
				"exported: ${Instant.now()}",
			)
			val completed = Files.newBufferedWriter(path).use { writer ->
				metrics.writeCsv(writer, segmentColumn, fragmentColumn, lockedColumn, countPixels, sortBy, commentLines) { isActive }
			}
			if (!completed)
				Files.deleteIfExists(path)
			ensureActive()
		}
		this@ExportIdsState.job = exportJob

		InvokeOnJavaFXApplicationThread {
			while (exportJob.isActive) {
				progressProperty.value = (scanned.sum().toDouble() / totalPixels).coerceAtMost(0.99)
				showSnapshot(metrics.snapshot)
				delay(UPDATE_INTERVAL_MILLIS)
			}
		}
		exportJob.invokeOnCompletion { cause ->
			InvokeOnJavaFXApplicationThread {
				showSnapshot(metrics.snapshot)
				when {
					cause == null -> {
						phaseProperty.value = ExportPhase.COMPLETED
						progressProperty.value = 1.0
						statusProperty.value = "Exported to $path"
						path.parent?.let { PainteraCache.RECENT_METRICS_EXPORT_DIRECTORIES.appendLine(it.toString()) }
					}

					cause is CancellationException -> {
						phaseProperty.value = ExportPhase.CANCELED
						statusProperty.value = "Cancelled"
					}

					else -> {
						phaseProperty.value = ExportPhase.FAILED
						LOG.error(cause) { "Could not export ids of ${metadataState.dataset}" }
						statusProperty.value = "Failed: ${cause.message}"
						ExceptionNode.exceptionDialog(cause as? Exception ?: RuntimeException(cause)).showAndWait()
					}
				}
			}
		}
		return exportJob
	}

	private fun showSnapshot(snapshot: LabelMetrics.Snapshot) {
		segmentsProperty.value = snapshot.segments.toLong()
		fragmentsProperty.value = snapshot.fragments.toLong()
		lockedProperty.value = snapshot.lockedSegments.toLong()
		totalCountProperty.value = snapshot.totalCount
	}
}
