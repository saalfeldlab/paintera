package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;

import java.util.Map;

public class SerializationHelpers {

  public static final String TYPE_KEY = "type";

  public static final String DATA_KEY = "data";

  public static <T> JsonElement serializeWithClassInfo(final T object, final JsonSerializationContext context) {

	return serializeWithClassInfo(object, context, TYPE_KEY, DATA_KEY);
  }

  public static <T> T deserializeFromClassInfo(final JsonObject map, final JsonDeserializationContext context) throws ClassNotFoundException {

	return deserializeFromClassInfo(map, context, TYPE_KEY, DATA_KEY);
  }

  public static <T> JsonElement serializeWithClassInfo(
		  final T object,
		  final JsonSerializationContext context,
		  final String typeKey,
		  final String dataKey) {

	final JsonObject map = new JsonObject();
	map.addProperty(typeKey, object.getClass().getName());
	map.add(dataKey, context.serialize(object));
	return map;
  }

  public static <T> T deserializeFromClassInfo(
		  final JsonObject map,
		  final JsonDeserializationContext context,
		  final String typeKey,
		  final String dataKey) throws ClassNotFoundException {

	String clsName = map.get(typeKey).getAsString();
	clsName = TMP_FQDN_METADATA_MAP.getOrDefault(clsName, clsName);

	final Class<T> clazz = (Class<T>)Class.forName(clsName);
	return context.deserialize(map.get(dataKey), clazz);
  }

  private static final Map<String, String> TMP_FQDN_METADATA_MAP = Map.of(
		  //		  "org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel", "org.janelia.saalfeldlab.paintera.state.channel.n5.N5MetadataBackendChannel"
  );

}
