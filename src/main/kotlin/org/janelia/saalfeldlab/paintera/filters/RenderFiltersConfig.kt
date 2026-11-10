package org.janelia.saalfeldlab.paintera.filters

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

/** The screen-space filters of a source in the order they render; each entry is enabled on its own */
class RenderFiltersConfig(configs: List<RenderFilterConfig> = RenderFilterPlugins.newConfigs()) {

	val filterConfigs: ObservableList<RenderFilterConfig> = FXCollections.observableArrayList(configs)

	fun isDefault() = filterConfigs.map { it.typeKey } == RenderFiltersConfig().filterConfigs.map { it.typeKey } && filterConfigs.none { it.enabledProperty.get() }

	/** sets the order and each entry's settings from [that]; an entry [that] does not have keeps its place after the others */
	fun set(that: RenderFiltersConfig) {
		val matched = that.filterConfigs.mapNotNull { thatConfig ->
			filterConfigs.firstOrNull { it.typeKey == thatConfig.typeKey }?.also { it.set(thatConfig) }
		}
		filterConfigs.setAll(matched + filterConfigs.filterNot { it in matched })
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class RenderFiltersConfigAdapter : PainteraSerialization.PainteraAdapter<RenderFiltersConfig> {

	override fun getTargetClass() = RenderFiltersConfig::class.java

	override fun serialize(src: RenderFiltersConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
		if (src.isDefault())
			return JsonNull.INSTANCE
		return JsonArray().also { array ->
			src.filterConfigs.forEach { config ->
				array.add(JsonObject().also {
					it[TYPE_KEY] = config.typeKey
					context.serialize(config).takeUnless { settings -> settings.isJsonNull }?.let { settings -> it[SETTINGS_KEY] = settings }
				})
			}
		}
	}

	override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): RenderFiltersConfig {
		val defaults = RenderFiltersConfig()
		val saved = (json?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()).mapNotNull { element ->
			val entry = element.asJsonObject
			val typeKey = entry.get<String>(TYPE_KEY)
			val default = defaults.filterConfigs.firstOrNull { it.typeKey == typeKey }
			if (default == null) {
				LOG.warn { "Ignoring the settings of render filter $typeKey; no plugin for it is on the classpath" }
				return@mapNotNull null
			}
			entry.get<JsonElement>(SETTINGS_KEY)?.let { settings -> context.deserialize<RenderFilterConfig>(settings, default::class.java) } ?: default
		}
		return RenderFiltersConfig(saved + defaults.filterConfigs.filter { default -> saved.none { it.typeKey == default.typeKey } })
	}

	private companion object {
		private val LOG = KotlinLogging.logger { }

		const val TYPE_KEY = "type"
		const val SETTINGS_KEY = "settings"
	}
}
