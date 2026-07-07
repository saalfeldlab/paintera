package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5

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

/** Restore [saved] slice positions into the backend's live position array, so the source projects at the saved slice on load. */
fun restoreSlicePositions(backend: SourceStateBackend<*, *>, saved: LongArray?) {
	if (saved == null) return
	val positions = (backend as? SourceStateBackendN5<*, *>)?.metadataState?.slicePositions ?: return
	saved.copyInto(positions, destinationOffset = 0, startIndex = 0, endIndex = minOf(saved.size, positions.size))
}
