package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import org.janelia.saalfeldlab.paintera.config.SideBarConfig
import java.lang.reflect.Type

class SideBarConfigSerializer : PainteraSerialization.PainteraAdapter<SideBarConfig> {
	override fun serialize(
			src: SideBarConfig?,
			typeOfSrc: Type?,
			context: JsonSerializationContext?) = JsonObject().also { map -> src?.let { map.addProperty(IS_VISIBLE_KEY, it.isVisible) } }

	override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): SideBarConfig {
		val config = SideBarConfig()
		with(GsonExtensions) {
			json?.getBooleanProperty(IS_VISIBLE_KEY)?.let { config.isVisible = it }
		}
		return config
	}

	override fun getTargetClass() = SideBarConfig::class.java

	override fun isHierarchyAdapter() = false

	companion object {
		private const val IS_VISIBLE_KEY = "isVisible"
	}
}
