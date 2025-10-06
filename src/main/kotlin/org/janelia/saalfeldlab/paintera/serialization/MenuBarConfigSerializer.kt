package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.*
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig.Companion.isDefault
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class MenuBarConfigSerializer : PainteraSerialization.PainteraAdapter<MenuBarConfig> {
	override fun serialize(
		src: MenuBarConfig,
		typeOfSrc: Type?,
		context: JsonSerializationContext
	) = if (src.isDefault()) JsonNull.INSTANCE else JsonObject().also { json ->
		json[IS_VISIBLE_KEY] = src.isVisible
		json[MODE_KEY] = src.mode.name

	}

	override fun deserialize(
		json: JsonElement?,
		typeOfT: Type?,
		context: JsonDeserializationContext
	): MenuBarConfig {
		return json?.run {
			MenuBarConfig().apply {
				get<String>(IS_VISIBLE_KEY)?.let { isVisible = it.toBoolean() }
				get<String>(MODE_KEY)?.runCatching { mode = MenuBarConfig.Mode.valueOf(this) }
			}
		} ?: MenuBarConfig()
	}

	override fun getTargetClass() = MenuBarConfig::class.java

	override fun isHierarchyAdapter() = false

	companion object {
		private const val IS_VISIBLE_KEY = "isVisible"
		private const val MODE_KEY = "mode"
	}
}
