package org.janelia.saalfeldlab.paintera.data.n5;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

@Plugin(type = N5DataSourceSerializer.class)
public class N5DataSourceSerializer implements PainteraSerialization.PainteraSerializer<N5DataSource<?, ?>>
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

	@Override
	public Class<N5DataSource<?, ?>> getTargetClass() {
		return (Class<N5DataSource<?, ?>>) (Class<?>) N5DataSource.class;
	}
}
