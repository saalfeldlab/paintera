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
import org.janelia.saalfeldlab.util.n5.N5FragmentSegmentAssignmentInitialLut;
import org.janelia.saalfeldlab.util.n5.N5FragmentSegmentAssignmentPersister;
import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.Type;


@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class N5FragmentSegmentAssignmentInitialLutSerializer implements
		PainteraSerialization.PainteraAdapter<N5FragmentSegmentAssignmentInitialLut>
{


	private static final String N5_META_KEY = "N5";

	@Override
	public N5FragmentSegmentAssignmentInitialLut deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
		try {
			final N5Meta meta = SerializationHelpers.deserializeFromClassInfo(jsonElement.getAsJsonObject().get(N5_META_KEY).getAsJsonObject(), context);
			return new N5FragmentSegmentAssignmentInitialLut(meta);
		} catch (ClassNotFoundException e) {
			throw new JsonParseException(e);
		}
	}

	@Override
	public JsonElement serialize(N5FragmentSegmentAssignmentInitialLut src, Type type, JsonSerializationContext context) {
		final JsonObject map = new JsonObject();
		map.add(N5_META_KEY, SerializationHelpers.serializeWithClassInfo(src.getMeta(), context));
		return map;
	}

	@Override
	public Class<N5FragmentSegmentAssignmentInitialLut> getTargetClass() {
		return N5FragmentSegmentAssignmentInitialLut.class;
	}
}
