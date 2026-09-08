package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5

private val LOG = KotlinLogging.logger { }

/** Gson key for a source's non-spatial slice positions. */
const val SLICE_POSITIONS_KEY = "slicePositions"

/**
 * Write [backend]'s non-spatial slice positions under [SLICE_POSITIONS_KEY] by axis name.
 *
 * Axes at position 0 are omitted, so a 3D source adds nothing.
 *
 * @param backend the source to read the positions from; ignored unless it is a [SourceStateBackendN5]
 */
fun JsonObject.addSlicePositions(backend: SourceStateBackend<*, *>) {
	val metadataState = (backend as? SourceStateBackendN5<*, *>)?.metadataState ?: return
	val xyzView = metadataState.xyzView
	val positionByAxis = JsonObject()
	for (axis in xyzView.nonSpatialAxes) {
		val position = xyzView.slicePosition(axis)
		if (position != 0L)
			positionByAxis.addProperty(metadataState.axes[axis].name, position)
	}
	if (!positionByAxis.isEmpty)
		add(SLICE_POSITIONS_KEY, positionByAxis)
}

/**
 * Slice [backend] at the positions under [SLICE_POSITIONS_KEY], as written by [addSlicePositions].
 *
 * A legacy project stores an array holding a position for every source axis in source order rather than an object
 * keyed by axis name. That form is read but never written; it is only correct while the axis order is unchanged.
 *
 * @param backend the source to slice; ignored unless it is a [SourceStateBackendN5]
 * @param json
 */
fun restoreSlicePositions(backend: SourceStateBackend<*, *>, json: JsonElement) {
	val saved = (json as? JsonObject)?.get(SLICE_POSITIONS_KEY) ?: return
	val metadataState = (backend as? SourceStateBackendN5<*, *>)?.metadataState ?: return
	val xyzView = metadataState.xyzView

	if (saved.isJsonObject) {
		val positionByAxis = saved.asJsonObject
		for (axis in xyzView.nonSpatialAxes)
			positionByAxis[metadataState.axes[axis].name]?.let { xyzView.sliceAt(axis, it.asLong) }
		return
	}

	val positions = saved.asJsonArray
	if (positions.size() != xyzView.numDimensions) {
		LOG.error { "Ignoring saved slice positions $positions. Expected ${xyzView.numDimensions} dimensions, but found ${positions.size()}" }
		return
	}
	/* only the dropped axes carry a slice */
	for (axis in xyzView.nonSpatialAxes)
		xyzView.sliceAt(axis, positions[axis].asLong)
}
