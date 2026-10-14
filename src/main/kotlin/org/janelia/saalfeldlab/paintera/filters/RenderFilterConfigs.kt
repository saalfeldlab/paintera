package org.janelia.saalfeldlab.paintera.filters

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.Property
import javafx.beans.property.SimpleBooleanProperty
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import java.lang.reflect.Type

abstract class AbstractRenderFilterConfig : RenderFilterConfig {

	final override val enabledProperty = SimpleBooleanProperty(false)

	protected abstract val settings: List<Property<*>>

	@Suppress("UNCHECKED_CAST")
	override fun set(that: RenderFilterConfig) {
		that as AbstractRenderFilterConfig
		enabledProperty.set(that.enabledProperty.get())
		settings.zip(that.settings).forEach { (own, other) -> (own as Property<Any?>).value = other.value }
	}
}

/** A disabled config is saved as `null`, and a saved one is enabled; the subclass writes and reads its settings */
abstract class RenderFilterConfigAdapter<C : RenderFilterConfig>(private val targetClass: Class<C>) : PainteraSerialization.PainteraAdapter<C> {

	protected abstract fun newConfig(): C

	/** writes the settings of [src] that differ from their defaults */
	protected abstract fun serializeSettings(src: C, settings: JsonObject)

	protected abstract fun deserializeSettings(json: JsonElement, config: C)

	override fun getTargetClass() = targetClass

	override fun serialize(src: C, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
		if (!src.enabledProperty.get())
			return JsonNull.INSTANCE
		return JsonObject().also { serializeSettings(src, it) }
	}

	override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext) = newConfig().also { config ->
		config.enabledProperty.set(true)
		json?.let { deserializeSettings(it, config) }
	}
}
