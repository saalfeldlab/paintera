package org.janelia.saalfeldlab.paintera.config.sam

import com.google.gson.*
import javafx.beans.property.SimpleStringProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.config.sam.SamModelConfig.Companion.SamModelData
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import java.lang.reflect.Type
import java.net.URI

sealed class SamTritonConfig<T>(val defaultEncoderName: String) : SamModelConfig<T>(
    DEFAULT_TRITON_SERVICE,
    DEFAULT_DECODER_LOCATION,
    DEFAULT_RESPONSE_TIMEOUT
) {

    private val encoderNameProperty = SimpleStringProperty(defaultEncoderName)
    var encoderName: String by encoderNameProperty.nonnull()

    val host: String
        get() = URI.create(serviceUrl).host ?: serviceUrl

    val port: Int
        get() = URI.create(serviceUrl).let { uri ->
            val port = uri.port.takeIf { it != -1 }
            when {
                port != null -> port
                uri.scheme?.lowercase() == "https" -> 443
                uri.scheme?.lowercase() == "http" -> 80
                else -> 8001
            }
        }

    init {
        encoderNameProperty.subscribe { _, new ->
            if (new.isNullOrBlank())
                encoderName = defaultEncoderName
            fireValueChangedEvent()
        }
    }

    override fun isDefault(): Boolean {
        return super.isDefault() && encoderName == defaultEncoderName
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SamTritonConfig<*>) return false
        return SamTritonData(this) == SamTritonData(other)
    }

    override fun hashCode() = SamTritonData(this).hashCode()

    companion object {

        internal const val DEFAULT_DECODER_LOCATION = ""


        /**
         * internal data class for equality and hashcode over a subset of the config fields
         */
        internal data class SamTritonData(
            val baseConfigData: SamModelData,
            val encoderName: String,
        ) {
            constructor(config: SamTritonConfig<*>) : this(
                SamModelData(config),
                config.encoderName
            )
        }
    }
}


abstract class SamTritonConfigNode(val config: SamTritonConfig<*>) : SamModelConfigNode(config) {

    init {
        val row = 0
        with (config) {
            addOptionConfigRow(row, "Service URL ", defaultServiceUrl, ::serviceUrl)
            addOptionConfigRow(row + 1, "Timeout (ms) ", defaultResponseTimeout, ::responseTimeout)
            addOptionConfigRow(row + 2, "Encoder Name", defaultEncoderName, ::encoderName)
        }
    }
}


abstract class SamTritonAdapter<T : SamTritonConfig<T>> : SamModelConfigAdapter<T>() {

    override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {

        if (src.isDefault()) {
            return JsonNull.INSTANCE
        }
        val superSerialized = super.serialize(src, typeOfSrc, context).takeUnless { !it.isJsonObject } ?: JsonObject()
        return superSerialized.asJsonObject.also {
            if (src.encoderName != src.defaultEncoderName)
                it[src::encoderName.name] = src.encoderName
        }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): T {
        val superDeserialized = super.deserialize(json, typeOfT, context)
        return superDeserialized.apply {
            json?.let {
                it[::encoderName.name, { encoder: String -> encoderName = encoder }]
            }
        }
    }
}