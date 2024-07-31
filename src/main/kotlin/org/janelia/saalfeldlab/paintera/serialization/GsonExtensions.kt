package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.*
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer

object GsonExtensions {

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

	inline operator fun <reified R> JsonElement.get(key: String, letIt: (R) -> Unit = {}): R? {
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
		}?.also {
			letIt(it)
		}
	}

	operator fun JsonObject.set(property: String, value: JsonElement) = this.add(property, value)
	operator fun JsonObject.set(property: String, value: String) = this.addProperty(property, value)
	operator fun JsonObject.set(property: String, value: Number) = this.addProperty(property, value)
	operator fun JsonObject.set(property: String, value: Boolean) = this.addProperty(property, value)
	operator fun JsonObject.set(property: String, value: Char) = this.addProperty(property, value)


	inline operator fun <reified O> Gson.get(json: JsonElement?): O? {
		return fromJson(json, O::class.java)
	}

	operator fun JsonSerializationContext.get(obj: Any): JsonElement = serialize(obj)

	inline operator fun <reified T> JsonDeserializationContext.get(obj: JsonElement, letIt: (T) -> Unit = {}): T? {
		return this.deserialize<T?>(obj, T::class.java)?.also {
			letIt(it)
		}
	}

	inline operator fun <reified T> JsonDeserializationContext.get(json: JsonElement, property: String, letIt: (T) -> Unit = {}): T? {
		return this.deserialize<T?>(json[property], T::class.java)?.also {
			letIt(it)
		}
	}

	inline operator fun <reified T> JsonDeserializationContext.get(obj: JsonObject, letIt: (T) -> Unit = {}) = get<T>(obj as JsonElement, letIt)

	inline operator fun <reified T> JsonDeserializationContext.get(json: JsonObject, property: String, letIt: (T) -> Unit = {}) =
		get<T>(json as JsonElement, property, letIt)


	inline operator fun <reified T> N5Reader.get(dataset : String, attribute : String) : T? {
		return getAttribute(dataset, attribute, T::class.java)
	}

	fun N5Writer.set(dataset : String, attrPath : String, attribute : Any) {
		return setAttribute(dataset, attrPath, attribute)
	}

}
