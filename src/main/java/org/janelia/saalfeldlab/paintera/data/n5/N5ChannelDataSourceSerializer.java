package org.janelia.saalfeldlab.paintera.data.n5;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

public class N5ChannelDataSourceSerializer implements JsonSerializer<N5ChannelDataSource<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String META_CLASS_KEY = "metaType";

	public static final String META_KEY = "meta";

	public static final String TRANSFORM_KEY = "transform";

	public static final String CHANNEL_DIMENSION_KEY = "channelDimension";

	public static final String CHANNEL_MIN_KEY = "channelMin";

	public static final String CHANNEL_MAX_KEY = "channelMin";

	@Override
	public JsonElement serialize(
			final N5ChannelDataSource<?, ?> s,
			final Type type,
			final JsonSerializationContext context)
	{
		final JsonObject map = new JsonObject();
		map.addProperty(META_CLASS_KEY, s.meta().getClass().getName());
		map.add(META_KEY, context.serialize(s.meta()));
		final AffineTransform3D transform = new AffineTransform3D();
		s.getSourceTransform(0, 0, transform);
		map.add(TRANSFORM_KEY, context.serialize(transform));
		map.addProperty(CHANNEL_DIMENSION_KEY, s.getChannelDimension());
		map.addProperty(CHANNEL_MIN_KEY, s.getChannelMin());
		map.addProperty(CHANNEL_MAX_KEY, s.getChannelMax());
		return map;
	}

}
