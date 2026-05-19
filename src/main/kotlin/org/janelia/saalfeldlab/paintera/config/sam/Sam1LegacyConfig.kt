package org.janelia.saalfeldlab.paintera.config.sam

import com.google.gson.*
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.subscribe
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.ImageEncoding
import org.janelia.saalfeldlab.paintera.config.sam.Sam1LegacyConfig.Companion.DEFAULT_COMPRESS_ENCODING
import org.janelia.saalfeldlab.paintera.config.sam.Sam1LegacyConfig.Companion.DEFAULT_IMAGE_ENCODING
import org.janelia.saalfeldlab.paintera.config.sam.SamModelConfig.Companion.SamModelData
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class Sam1LegacyConfig : SamModelConfig<Sam1LegacyConfig>(
	DEFAULT_SERVICE_URL,
	DEFAULT_DECODER_LOCATION,
	DEFAULT_RESPONSE_TIMEOUT
) {

	private val compressEncodingProperty = SimpleBooleanProperty(DEFAULT_COMPRESS_ENCODING)
	var compressEncoding: Boolean by compressEncodingProperty.nonnull()

	internal val imageEncodingProperty = SimpleObjectProperty(ImageEncoding.JPEG)
	var imageEncoding: ImageEncoding by imageEncodingProperty.nonnull()

	override fun isDefault(): Boolean {
		/* It's semantically meaningful to know that we are comparing against the default, even if the default is
		* a boolean. It's also better to compare, in case we change the default constant in the future. */
		@Suppress("SimplifyBooleanWithConstants")
		return super.isDefault() && compressEncoding == DEFAULT_COMPRESS_ENCODING && imageEncoding == DEFAULT_IMAGE_ENCODING
	}

	init {
		listOf(compressEncodingProperty, imageEncodingProperty).subscribe {
			fireValueChangedEvent()
		}
	}

	override fun equals(other: Any?): Boolean {
        if (other !is Sam1LegacyConfig) return false
		return Sam1Data(this) == Sam1Data(other)
	}

	override fun hashCode() = Sam1Data(this).hashCode()

	override fun getValue() = this

	companion object {
		internal const val DEFAULT_SERVICE_URL = "https://samservice.janelia.org/"
		internal const val DEFAULT_DECODER_LOCATION = "sam/sam_vit_h_4b8939.onnx"

		internal const val DEFAULT_COMPRESS_ENCODING = true
		internal val DEFAULT_IMAGE_ENCODING = ImageEncoding.JPEG

		/**
		 * internal data class for equality and hashcode over a subset of the config fields
		 */
		internal data class Sam1Data(
			val baseConfigData: SamModelData,
			val compressEncoding: Boolean,
			val imageEncoding: ImageEncoding
		)  {
			constructor(config: Sam1LegacyConfig)  : this(
				SamModelData(config),
				config.compressEncoding,
				config.imageEncoding
			)
		}
	}
}


class Sam1LegacyConfigNode(val config: Sam1LegacyConfig) : SamModelConfigNode(config) {

	init {
		addBaseNodeRows(0)
		var nextRow = rowCount
		addOptionConfigRow(nextRow++, "Compress Encoding", DEFAULT_COMPRESS_ENCODING, config::compressEncoding)
		addOptionConfigRow(nextRow++, "Image Encoding", DEFAULT_IMAGE_ENCODING, config::imageEncoding)
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class Sam1LegacyAdapter : SamModelConfigAdapter<Sam1LegacyConfig>() {

	override fun getTargetClass() = Sam1LegacyConfig::class.java
	override fun newInstance() = Sam1LegacyConfig()

	override fun serialize(src: Sam1LegacyConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {

		if (src.isDefault()) {
			return JsonNull.INSTANCE
		}
		val superSerialized = super.serialize(src, typeOfSrc, context).takeUnless { !it.isJsonObject } ?: JsonObject()
		return superSerialized.asJsonObject.also {
			@Suppress("SimplifyBooleanWithConstants")
			if (src.compressEncoding != DEFAULT_COMPRESS_ENCODING)
				it[src::compressEncoding.name] = src.compressEncoding
			if (src.imageEncoding != DEFAULT_IMAGE_ENCODING)
				it[src::imageEncoding.name] = src.imageEncoding.name.lowercase()
		}
	}

	override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): Sam1LegacyConfig {
		val superDeserialized = super.deserialize(json, typeOfT, context)
		return superDeserialized.apply {
			json?.let {
				it[::compressEncoding.name, { compress: Boolean -> compressEncoding = compress }]
				it[::imageEncoding.name, { encoding: String -> imageEncoding = ImageEncoding.valueOf(encoding.trim().uppercase()) }]
			}
		}
	}
}