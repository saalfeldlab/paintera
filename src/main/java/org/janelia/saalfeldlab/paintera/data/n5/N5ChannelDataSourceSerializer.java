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

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class N5ChannelDataSourceSerializer implements PainteraSerialization.PainteraSerializer<N5ChannelDataSource<?, ?>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String META_CLASS_KEY = "metaType";

	public static final String META_KEY = "meta";

	public static final String TRANSFORM_KEY = "transform";

	public static final String CHANNEL_DIMENSION_KEY = "channelDimension";

	public static final String CHANNELS_KEY = "channels";

	@Override
	public JsonElement serialize(
			final N5ChannelDataSource<?, ?> s,
			final Type type,
			final JsonSerializationContext context) {

		final JsonObject map = new JsonObject();
		map.addProperty(META_CLASS_KEY, s.meta().getClass().getName());
		map.add(META_KEY, context.serialize(s.meta()));
		final AffineTransform3D transform = new AffineTransform3D();
		s.getSourceTransform(0, 0, transform);
		map.add(TRANSFORM_KEY, context.serialize(transform));
		map.addProperty(CHANNEL_DIMENSION_KEY, s.getChannelDimension());
		map.add(CHANNELS_KEY, context.serialize(s.getChannels()));
		return map;
	}

	@Override
	public Class<N5ChannelDataSource<?, ?>> getTargetClass() {

		return (Class<N5ChannelDataSource<?, ?>>)(Class<?>)N5ChannelDataSource.class;
	}
}
