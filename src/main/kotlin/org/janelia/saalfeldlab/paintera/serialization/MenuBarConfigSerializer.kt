package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig
import org.janelia.saalfeldlab.paintera.config.SideBarConfig
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class MenuBarConfigSerializer : PainteraSerialization.PainteraAdapter<MenuBarConfig> {
	override fun serialize(
			src: MenuBarConfig?,
			typeOfSrc: Type?,
			context: JsonSerializationContext) = JsonObject().also { map -> src?.let {
		map.addProperty(IS_VISIBLE_KEY, it.isVisible)
		map.addProperty(MODE_KEY, it.mode.name)
	} }

	override fun deserialize(
			json: JsonElement?,
			typeOfT: Type?,
			context: JsonDeserializationContext): MenuBarConfig {
		val config = MenuBarConfig()
		with(GsonExtensions) {
			json?.getBooleanProperty(IS_VISIBLE_KEY)?.let { config.isVisible = it }
			json?.getStringProperty(MODE_KEY)?.let { config.mode = MenuBarConfig.Mode.valueOf(it) }
		}
		return config
	}

	override fun getTargetClass() = MenuBarConfig::class.java

	override fun isHierarchyAdapter() = false

	companion object {
		private const val IS_VISIBLE_KEY = "isVisible"
		private const val MODE_KEY = "mode"
	}
}
