package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import net.imglib2.Interval
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend

/** Gson key for a source's virtual crop: a 3D xyz interval at s0, in voxels */
const val VIRTUAL_CROP_KEY = "virtualCrop"

/** Write [backend]'s crop under [VIRTUAL_CROP_KEY]; an uncropped source adds nothing */
fun JsonObject.addVirtualCrop(backend: SourceStateBackend<*, *>, context: JsonSerializationContext) {
	backend.xyzView.xyzCrop?.let { add(VIRTUAL_CROP_KEY, context[it]) }
}

/** Crop [backend] to the interval under [VIRTUAL_CROP_KEY], as written by [addVirtualCrop]; the slice positions are kept */
fun restoreVirtualCrop(backend: SourceStateBackend<*, *>, json: JsonElement, context: JsonDeserializationContext) {
	val saved = (json as? JsonObject)?.get(VIRTUAL_CROP_KEY)
	backend.xyzView.setCropInterval(saved?.let { context.get<Interval>(it) })
}
