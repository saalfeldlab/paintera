package org.janelia.saalfeldlab.bdv.fx.viewer.multibox

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class MultiBoxOverlayConfig {

	enum class Visibility(val description: String) {
		ON("Show multi-box overlay in all viewers."),
		OFF("Turn off multi-box overlay"),
		ONLY_IN_FOCUSED_VIEWER("Show multi-box overlay only in currently focused viewer.");
	}

	private val _visibility = SimpleObjectProperty(DefaultValues.VISIBILITY)
	var visibility: Visibility
		get() = _visibility.value
		set(visibility) = _visibility.set(visibility)

	fun visibilityProperty() = _visibility

	object SerializationKeys {
		val VISIBILITY = "visibility"
	}

	object DefaultValues {
		val VISIBILITY = Visibility.ON
	}

	@Plugin(type = PainteraSerialization.PainteraAdapter::class)
	class Adapter : PainteraSerialization.PainteraAdapter<MultiBoxOverlayConfig> {
		override fun serialize(src: MultiBoxOverlayConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
			return JsonObject().also {
				it[SerializationKeys.VISIBILITY] = context.serialize(src.visibility)
			}
		}

		override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext): MultiBoxOverlayConfig {
			val config = MultiBoxOverlayConfig()
			with(GsonExtensions) {
				json
					?.getProperty(SerializationKeys.VISIBILITY)
					?.let { context.deserialize<Visibility>(it, Visibility::class.java) }
					?.let { config.visibility = it }
			}
			return config
		}

		override fun getTargetClass(): Class<MultiBoxOverlayConfig> = MultiBoxOverlayConfig::class.java
	}

}
