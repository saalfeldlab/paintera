package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.axes.AxisAdapter
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.fallbackAxes
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.getAxes

private val LOG = KotlinLogging.logger { }

/** Gson key for a source's axes, one OME-NGFF axis object (`type`, `name`, `unit`) per source dimension. */
const val AXES_KEY = "axes"

/**
 * Write [backend]'s axes under [AXES_KEY] when they differ from the axes its metadata declares, so the project holds
 * only what was assigned by hand.
 *
 * @param backend the source to read the axes from; ignored unless it is a [SourceStateBackendN5]
 */
fun JsonObject.addAxes(backend: SourceStateBackend<*, *>) {
	val metadataState = (backend as? SourceStateBackendN5<*, *>)?.metadataState ?: return
	val declared = metadataState.getAxes() ?: metadataState.fallbackAxes()
	if (metadataState.axes.map(::AxisFields) == declared.map(::AxisFields))
		return
	val adapter = AxisAdapter()
	add(AXES_KEY, JsonArray().also { array -> metadataState.axes.forEach { array.add(adapter.serialize(it, Axis::class.java, null)) } })
}

/**
 * Set [backend]'s axes from [AXES_KEY], as written by [addAxes]. The view is built from the axes, so this must run
 * before the source is created and before the slice positions are restored, which are keyed by axis name.
 *
 * @param backend the source to set the axes on; ignored unless it is a [SourceStateBackendN5]
 * @param json
 */
fun restoreAxes(backend: SourceStateBackend<*, *>, json: JsonElement) {
	val saved = (json as? JsonObject)?.get(AXES_KEY)?.takeIf { it.isJsonArray }?.asJsonArray ?: return
	val metadataState = (backend as? SourceStateBackendN5<*, *>)?.metadataState ?: return
	val numDimensions = metadataState.datasetAttributes.numDimensions
	if (saved.size() != numDimensions) {
		LOG.error { "Ignoring saved axes $saved. Expected $numDimensions dimensions, but found ${saved.size()}" }
		return
	}
	val adapter = AxisAdapter()
	metadataState.axes = Array(numDimensions) { adapter.deserialize(saved[it], Axis::class.java, null) }
}

/* Axis.equals throws on a null unit, so compare through the fields */
private data class AxisFields(val type: String?, val name: String?, val unit: String?) {
	constructor(axis: Axis) : this(axis.type, axis.name, axis.unit)
}
