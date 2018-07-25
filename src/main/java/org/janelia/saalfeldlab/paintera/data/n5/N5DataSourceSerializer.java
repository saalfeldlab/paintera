package org.janelia.saalfeldlab.paintera.data.n5;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class N5DataSourceSerializer implements JsonSerializer<N5DataSource<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String META_CLASS_KEY = "metaType";

	private static final String META_KEY = "meta";

	private static final String TRANSFORM_KEY = "transform";

	@Override
	public JsonElement serialize(
			final N5DataSource<?, ?> s,
			final Type type,
			final JsonSerializationContext context)
	{
		final JsonObject map = new JsonObject();
		map.addProperty(META_CLASS_KEY, s.meta().getClass().getName());
		map.add(META_KEY, context.serialize(s.meta()));
		final AffineTransform3D transform = new AffineTransform3D();
		s.getSourceTransform(0, 0, transform);
		map.add(TRANSFORM_KEY, context.serialize(transform));
		return map;
	}

}
