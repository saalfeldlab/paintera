package bdv.fx.viewer.multibox

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class MultiBoxOverlayConfig {
    private val _isVisible: BooleanProperty = SimpleBooleanProperty(DefaultValues.IS_VISIBLE)
    var isVisible: Boolean
        get() = _isVisible.value
        set(isVisible) = _isVisible.set(isVisible)
    fun isVisibleProperty() = _isVisible

    private val _isVisibleOnlyInFocusedViewer: BooleanProperty = SimpleBooleanProperty(DefaultValues.IS_VISIBLE_ONLY_IN_FOCUSED_VIEWER)
    var isVisibleOnlyInFocusedViewer: Boolean
        get() = _isVisibleOnlyInFocusedViewer.value
        set(isVisibleOnlyInFocusedViewer) = _isVisibleOnlyInFocusedViewer.set(isVisibleOnlyInFocusedViewer)
    fun isVisibleOnlyInFocusedViewerProperty() = _isVisibleOnlyInFocusedViewer

    object SerializationKeys {
        val IS_VISIBLE = "isVisible"
        val IS_VISIBLE_ONLY_IN_FOCUSED_VIEWER = "isVisibleOnlyInFocusedViewer"
    }

    object DefaultValues {
        val IS_VISIBLE = true
        val IS_VISIBLE_ONLY_IN_FOCUSED_VIEWER = true
    }

    companion object {
    }

    @Plugin(type = PainteraSerialization.PainteraAdapter::class)
    class Adapter : PainteraSerialization.PainteraAdapter<MultiBoxOverlayConfig>
    {
        override fun serialize(src: MultiBoxOverlayConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
            val map = JsonObject()
            src.isVisible.takeIf { it != DefaultValues.IS_VISIBLE }?.let { map.addProperty(SerializationKeys.IS_VISIBLE, it) }
            src.isVisibleOnlyInFocusedViewer.takeIf { it != DefaultValues.IS_VISIBLE_ONLY_IN_FOCUSED_VIEWER }?.let { map.addProperty(SerializationKeys.IS_VISIBLE_ONLY_IN_FOCUSED_VIEWER, it) }
            return if (map.size() == 0) null else map
        }
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): MultiBoxOverlayConfig {
            val config = MultiBoxOverlayConfig()
            with (GsonExtensions){
                json?.getBooleanProperty(SerializationKeys.IS_VISIBLE)?.let { config.isVisible = it }
                json?.getBooleanProperty(SerializationKeys.IS_VISIBLE_ONLY_IN_FOCUSED_VIEWER)?.let { config.isVisibleOnlyInFocusedViewer = it }
            }
            return config
        }

        override fun getTargetClass(): Class<MultiBoxOverlayConfig> = MultiBoxOverlayConfig::class.java
    }

}
