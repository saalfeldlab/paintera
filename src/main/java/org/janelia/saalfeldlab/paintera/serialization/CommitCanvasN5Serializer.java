package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;

public class CommitCanvasN5Serializer implements JsonSerializer<CommitCanvasN5>, JsonDeserializer<CommitCanvasN5>
{

	public static final String META_CLASS_KEY = "class";

	public static final String META_DATA_KEY = "data";

	@Override
	public CommitCanvasN5 deserialize(final JsonElement src, final Type type, final JsonDeserializationContext context)
	throws JsonParseException
	{
		try
		{
			final N5Meta meta = context.deserialize(
					src.getAsJsonObject().get(META_DATA_KEY),
					Class.forName(src.getAsJsonObject().get(META_CLASS_KEY).getAsString())
			                                       );
			return new CommitCanvasN5(meta.writer(), meta.dataset());
		} catch (final IOException | ClassNotFoundException e)
		{
			throw new JsonParseException(e);
		}
	}

	@Override
	public JsonElement serialize(final CommitCanvasN5 src, final Type type, final JsonSerializationContext context)
	{

		try
		{
			final N5Meta     meta = N5Meta.fromReader(src.n5(), src.dataset());
			final JsonObject map  = new JsonObject();
			map.addProperty(META_CLASS_KEY, meta.getClass().getName());
			map.add(META_DATA_KEY, context.serialize(meta));
			return map;
		} catch (final ReflectionException e)
		{
			return null;
		}
	}

}
