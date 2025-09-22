package org.janelia.saalfeldlab.paintera.state.metadata

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization.PainteraAdapter
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

/**
 * Information to slice an nD image (where n > 3) to a 3D Image with support for channels, timepoints, and arbitrary dimensions via hyper slicing.
 *
 * @property x index of X dimension
 * @property y index of Y dimension
 * @property z index of X dimension
 * @property channel index of channel dimension (if present)
 * @property timepoint index of timepoint dimension (if present)
 * @property sliceMap mapping of indices that should be slices to the position they are to be sliced to.
 */
@Plugin(type = PainteraAdapter::class)
data class Slice3D(val x: Int, val y: Int, val z: Int, val channel: Int? = null, val timepoint: Int? = null, val sliceMap: Map<Int, Int> = emptyMap()) : PainteraSerialization.PainteraAdapter<Slice3D> {
	override fun serialize(src: Slice3D?, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
		src ?: return null

		return JsonObject().also { json ->
			json["x"] = x
			json["y"] = y
			json["z"] = z
			channel?.let { json["channel"] = it }
			timepoint?.let { json["timepoint"] = it }
			sliceMap.takeUnless { it.isEmpty() }?.let {
				json["sliceMap"] = it.asSequence()
					.fold(JsonObject()) { obj, (idx, pos) ->
						obj.also { obj["$idx"] = pos }
					}
			}
		}
	}

	override fun getTargetClass() = Slice3D::class.java

	override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Slice3D? {
		json ?: return null

		val obj = json.asJsonObject
		val res = runCatching {
			val x = obj["x"].asInt
			val y = obj["y"].asInt
			val z = obj["z"].asInt
			val channel = obj["channel"]?.asInt
			val timepoint = obj["timepoint"]?.asInt
			val sliceMap = obj["sliceMap"]?.asJsonObject?.let { map ->
				map.entrySet().associate { it.key.toInt() to it.value.asInt }
			} ?: emptyMap()
			Slice3D(x, y, z, channel, timepoint, sliceMap)
		}
		return res.getOrNull()
	}


}
