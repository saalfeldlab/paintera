package org.janelia.saalfeldlab.paintera.serialization.assignments;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.janelia.saalfeldlab.util.n5.N5FragmentSegmentAssignmentPersister;
import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class N5FragmentSegmentAssignmentPersisterSerializer implements
		PainteraSerialization.PainteraAdapter<N5FragmentSegmentAssignmentPersister> {

  private static final String N5_META_KEY = "N5";

  @Override
  public N5FragmentSegmentAssignmentPersister deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {

	try {
	  final N5Meta meta = SerializationHelpers.deserializeFromClassInfo(jsonElement.getAsJsonObject().get(N5_META_KEY).getAsJsonObject(), context);
	  // TODO: Keep using deprecated version until https://youtrack.jetbrains.com/issue/KT-40609 is closed.
	  return new N5FragmentSegmentAssignmentPersister(meta.writer(), meta.getDataset());
	} catch (ClassNotFoundException | IOException e) {
	  throw new JsonParseException(e);
	}
  }

  @Override
  public JsonElement serialize(N5FragmentSegmentAssignmentPersister src, Type type, JsonSerializationContext context) {

	final JsonObject map = new JsonObject();
	try {
	  map.add(N5_META_KEY, SerializationHelpers.serializeWithClassInfo(N5Meta.fromReader(src.getWriter(), src.getDataset()), context));
	} catch (ReflectionException e) {
	  throw new JsonParseException(e);
	}
	return map;
  }

  @Override
  public Class<N5FragmentSegmentAssignmentPersister> getTargetClass() {

	return N5FragmentSegmentAssignmentPersister.class;
  }
}
