package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

class GsonExtensions {

    companion object {

        fun JsonElement?.getProperty(key: String) = this
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.takeIf { it.has(key) }
            ?.get(key)

        fun JsonElement?.getJsonArray(key: String) = this
            ?.getProperty(key)
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray

        fun JsonElement?.getJsonObject(key: String) = this
            ?.getProperty(key)
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject

        fun JsonElement?.getJsonPrimitiveProperty(key: String) = this
            ?.getProperty(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asJsonPrimitive

        fun JsonElement?.getBooleanProperty(key: String) = this
            ?.getJsonPrimitiveProperty(key)
            ?.takeIf { it.isBoolean }
            ?.asBoolean

        fun JsonElement?.getStringProperty(key: String) = this
            ?.getJsonPrimitiveProperty(key)
            ?.takeIf { it.isString }
            ?.asString


        fun JsonElement?.getNumberProperty(key: String) = this
            ?.getJsonPrimitiveProperty(key)
            ?.takeIf { it.isNumber }
            ?.asNumber

        fun JsonElement?.getDoubleProperty(key: String) = this
            ?.getJsonPrimitiveProperty(key)
            ?.takeIf { it.isNumber }
            ?.asDouble

        fun JsonElement?.getIntProperty(key: String) = this
            ?.getJsonPrimitiveProperty(key)
            ?.takeIf { it.isNumber }
            ?.asInt

        fun JsonElement?.getLongProperty(key: String) = this
            ?.getJsonPrimitiveProperty(key)
            ?.takeIf { it.isNumber }
            ?.asLong

        fun <R> JsonElement.letProperty(key: String, withElement: (JsonElement) -> R): R? {
            return getProperty(key)?.let { withElement(it) }
        }

        fun <R> JsonElement.letLongProperty(key: String, withElement: (Long) -> R): R? {
            return getLongProperty(key)?.let { withElement(it) }
        }

        fun <R> JsonElement.letBooleanProperty(key: String, withElement: (Boolean) -> R): R? {
            return getBooleanProperty(key)?.let { withElement(it) }
        }

        fun <R> JsonElement.letJsonObject(key: String, withElement: (JsonObject) -> R): R? {
            return getJsonObject(key)?.let { withElement(it) }
        }

        inline operator fun <reified R> JsonElement.get(key: String): R? {
            return when (R::class) {
                Double::class -> getDoubleProperty(key) as? R
                Long::class -> getLongProperty(key) as? R
                Int::class -> getIntProperty(key) as? R
                String::class -> getStringProperty(key) as? R
                Number::class -> getNumberProperty(key) as? R
                Boolean::class -> getBooleanProperty(key) as? R
                JsonObject::class -> getJsonObject(key) as? R
                JsonArray::class -> getJsonArray(key) as? R
                JsonPrimitive::class -> getJsonPrimitiveProperty(key) as? R
                JsonElement::class -> getProperty(key) as? R
                else -> null
            }
        }

        inline operator fun <reified O> Gson.get(json: JsonElement?): O? {
            return fromJson(json, O::class.java)
        }
    }
}
