package org.janelia.saalfeldlab.paintera.config

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.config.ToolBarConfig.Companion.isDefault
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class ToolBarConfig {

	val isVisibleProperty = SimpleBooleanProperty(Default.isVisible)
	var isVisible: Boolean by isVisibleProperty.nonnull()

	val modeProperty = SimpleObjectProperty<Mode>(Default.mode)
	var mode: Mode by modeProperty.nonnull()

	fun toggleIsVisible() {
		isVisible = !isVisible
	}

	fun cycleModes() {
		mode = mode.next()
	}

	enum class Mode {
		OVERLAY,
		RIGHT;

		fun next() = Mode.entries[(ordinal + 1) % Mode.entries.size]
	}

	companion object {
		private data class Config( val isVisible: Boolean = true, val mode: Mode = Mode.OVERLAY)
		private val Default = Config()

		fun ToolBarConfig.isDefault() = Config(isVisible, mode) == Default
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class ToolBarConfigSerializer : PainteraSerialization.PainteraAdapter<ToolBarConfig> {
	override fun serialize(
		src: ToolBarConfig,
		typeOfSrc: Type?,
		context: JsonSerializationContext?,
	) = if (src.isDefault()) JsonNull.INSTANCE else JsonObject().apply {
		addProperty(IS_VISIBLE_KEY, src.isVisible)
	}

	override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ToolBarConfig {
		json ?: return ToolBarConfig()
		val config = ToolBarConfig().apply {
			json.get<String>(IS_VISIBLE_KEY)?.let { isVisible = it.toBoolean() }
			json.get<String>(MODE_KEY)?.runCatching { mode = ToolBarConfig.Mode.valueOf(this) }
		}
		return config
	}

	override fun getTargetClass() = ToolBarConfig::class.java

	override fun isHierarchyAdapter() = false

	companion object {
		private const val IS_VISIBLE_KEY = "isVisible"
		private const val MODE_KEY = "mode"
	}
}

