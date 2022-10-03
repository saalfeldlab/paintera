package org.janelia.saalfeldlab.paintera.config

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.SimpleBooleanProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class ToolBarConfig {

    val isVisibleProperty = SimpleBooleanProperty(true)
    var isVisible: Boolean by isVisibleProperty.nonnull()

    fun toggleIsVisible() = this.isVisibleProperty.set(!this.isVisible)

    companion object {
        private const val DEFAULT_WIDTH = 350.0
    }

}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class ToolBarConfigSerializer : PainteraSerialization.PainteraAdapter<ToolBarConfig> {
    override fun serialize(
        src: ToolBarConfig,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ) = JsonObject().apply {
        addProperty(IS_VISIBLE_KEY, src.isVisible)
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ToolBarConfig {
        val config = ToolBarConfig()
        with(GsonExtensions) {
            json?.getBooleanProperty(IS_VISIBLE_KEY)?.let { config.isVisible = it }
        }
        return config
    }

    override fun getTargetClass() = ToolBarConfig::class.java

    override fun isHierarchyAdapter() = false

    companion object {
        private const val IS_VISIBLE_KEY = "isVisible"
    }
}

