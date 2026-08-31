package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5

private val LOG = KotlinLogging.logger { }

/** Gson key for a source's fixed non-spatial (channel/time/...) slice positions. */
const val SLICE_POSITIONS_KEY = "slicePositions"

/**
 * Persist the backend's non-spatial slice positions, if the source is an nD source parked at a non-default
 * timepoint/channel. Spatial position and orientation come from the global viewer transform, so only this per-source
 * slice is stored. No-op for a plain 3D source (all-zero positions).
 */
fun JsonObject.addSlicePositions(backend: SourceStateBackend<*, *>, context: JsonSerializationContext) {
	(backend as? SourceStateBackendN5<*, *>)?.metadataState?.slicePositions
		?.takeIf { positions -> positions.any { it != 0L } }
		?.let { add(SLICE_POSITIONS_KEY, context.serialize(it)) }
}

fun restoreSlicePositions(backend: SourceStateBackend<*, *>, saved: LongArray?) {
	if (saved == null) return
	val xyzView = (backend as? SourceStateBackendN5<*, *>)?.metadataState?.xyzView ?: return
	if (saved.size != xyzView.numDimensions) {
		LOG.error { "Ignoring saved slice positions ${saved.toList()}. Expected ${saved.size} dimensions, but found ${xyzView.numDimensions}" }
		return
	}
	/* only the dropped axes carry a slice */
	for (axis in xyzView.nonSpatialAxes)
		xyzView.sliceAt(axis, saved[axis])
}
