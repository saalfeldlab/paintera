package org.janelia.saalfeldlab.paintera.serialization.idservice;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.exception.PainteraRuntimeException;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class N5IdServiceAdapter implements PainteraSerialization.PainteraAdapter<N5IdService> {

  private static final String N5_META_KEY = "N5";

  @Override
  public N5IdService deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {

	try {
	  final N5Meta meta = SerializationHelpers.deserializeFromClassInfo(jsonElement.getAsJsonObject().get(N5_META_KEY).getAsJsonObject(), context);
	  return (N5IdService)N5Helpers.idService(meta.writer(), meta.getDataset());
	} catch (ClassNotFoundException | IOException | N5Helpers.MaxIDNotSpecified e) {
	  throw new JsonParseException(e);
	}
  }

  @Override
  public JsonElement serialize(N5IdService n5IdService, Type type, JsonSerializationContext context) {

	try {
	  final N5Meta meta = N5Meta.fromReader(n5IdService.getWriter(), n5IdService.getDataset());
	  final JsonObject map = new JsonObject();
	  map.add(N5_META_KEY, SerializationHelpers.serializeWithClassInfo(meta, context));
	  return map;
	} catch (ReflectionException e) {
	  throw new PainteraRuntimeException(e);
	}
  }

  @Override
  public Class<N5IdService> getTargetClass() {

	return N5IdService.class;
  }
}
