package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get

object SerializationHelpers {
	private const val TYPE_KEY = "type"
	private const val DATA_KEY = "data"

	@JvmStatic
	fun <T> serializeWithClassInfo(obj: T, context: JsonSerializationContext): JsonElement {
		return serializeWithClassInfo(obj, context, TYPE_KEY, DATA_KEY)
	}

	@JvmStatic
	@Throws(ClassNotFoundException::class)
	fun <T> deserializeFromClassInfo(map: JsonObject, context: JsonDeserializationContext): T {
		return deserializeFromClassInfo(map, context, TYPE_KEY, DATA_KEY)
	}

	@JvmStatic
	fun <T> serializeWithClassInfo(
		obj: T,
		context: JsonSerializationContext,
		typeKey: String?,
		dataKey: String?
	): JsonElement {
		val map = JsonObject()
		map.addProperty(typeKey, obj!!.javaClass.name)
		map.add(dataKey, context.serialize(obj))
		return map
	}

	@JvmStatic
	@Throws(ClassNotFoundException::class)
	fun <T> deserializeFromClassInfo(
		map: JsonObject,
		context: JsonDeserializationContext,
		typeKey: String?,
		dataKey: String?
	): T {
		val clsName = map[typeKey].asString
		val clazz = Class.forName(clsName) as Class<T>
		return context.deserialize(map[dataKey], clazz)
	}

	fun <T> JsonSerializationContext.withClassInfo(obj: T): JsonElement = serializeWithClassInfo(obj, this)

	inline fun <reified T> JsonDeserializationContext.fromClassInfo(jsonObject: JsonObject?, letIt: (T) -> Unit = {}): T? {
		return jsonObject?.let {
			deserializeFromClassInfo<T>(jsonObject, this)?.also { letIt(it) }
		}
	}

	inline fun <reified T> JsonDeserializationContext.fromClassInfo(json: JsonElement, key: String, letIt: (T) -> Unit = {}): T? {
		return this.fromClassInfo(json[key], letIt)
	}

	inline fun <reified T> JsonDeserializationContext.fromClassInfo(jsonObject: JsonObject?, typeKey: String?, dataKey: String?, letIt: (T) -> Unit = {}): T? {
		return jsonObject?.let {
			deserializeFromClassInfo<T>(jsonObject, this, typeKey, dataKey)?.also { letIt(it) }
		}
	}

	inline fun <reified T> JsonDeserializationContext.fromClassInfo(
		json: JsonElement,
		key: String,
		typeKey: String?,
		dataKey: String?,
		letIt: (T) -> Unit = {}
	): T? {
		return this.fromClassInfo(json[key], typeKey, dataKey, letIt)
	}
}
