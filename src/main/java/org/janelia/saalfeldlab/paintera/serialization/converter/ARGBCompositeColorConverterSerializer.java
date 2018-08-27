package org.janelia.saalfeldlab.paintera.serialization.converter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.util.Colors;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.stream.IntStream;

public class ARGBCompositeColorConverterSerializer<R extends RealType<R>, C extends RealComposite<R>, V extends Volatile<C>>
		implements JsonSerializer<ARGBCompositeColorConverter<R, C, V>>, JsonDeserializer<ARGBCompositeColorConverter<R, C, V>>
{

	private static final String NUM_CHANNELS_KEY = "numChannels";

	private static final String ALPHA_KEY = "alpha";

	private static final String CHANNEL_ALPHA_KEY = "channelAlpha";

	private static final String COLOR_KEY = "color";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	@Override
	public ARGBCompositeColorConverter<R, C, V> deserialize(final JsonElement json, final Type type, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		try
		{
			final JsonObject map = json.getAsJsonObject();
			final int numChannels = map.get(NUM_CHANNELS_KEY).getAsInt();

			@SuppressWarnings("unchecked") final ARGBCompositeColorConverter<R, C, V> converter = (ARGBCompositeColorConverter<R, C, V>) Class
					.forName(
					type.getTypeName()).getConstructor(int.class).newInstance(numChannels);
			Optional.ofNullable(map.get(ALPHA_KEY)).map(JsonElement::getAsDouble).ifPresent(converter.alphaProperty()
					::set);

			Optional
					.ofNullable(map.get(COLOR_KEY))
					.map(JsonElement::getAsJsonArray)
					.ifPresent(a -> converter.setColors( c -> Colors.toARGBType(a.get(c).getAsString())));

			Optional
					.ofNullable(map.get(MIN_KEY))
					.map(JsonElement::getAsJsonArray)
					.ifPresent(a -> converter.setMins( c -> a.get(c).getAsDouble()));

			Optional
					.ofNullable(map.get(MAX_KEY))
					.map(JsonElement::getAsJsonArray)
					.ifPresent(a -> converter.setMaxs( c -> a.get(c).getAsDouble()));

			Optional
					.ofNullable(map.get(CHANNEL_ALPHA_KEY))
					.map(JsonElement::getAsJsonArray)
					.ifPresent(a -> converter.setAlphas( c -> a.get(c).getAsDouble()));

			return converter;
		} catch (InstantiationException
				| IllegalAccessException
				| IllegalArgumentException
				| InvocationTargetException
				| NoSuchMethodException
				| SecurityException
				| ClassNotFoundException e)
		{
			throw new JsonParseException(e);
		}
	}

	@Override
	public JsonElement serialize(final ARGBCompositeColorConverter<R, C, V> src, final Type type, final JsonSerializationContext
			context)
	{
		final int numChannels = src.numChannels();
		final JsonObject map = new JsonObject();
		map.addProperty(ALPHA_KEY, src.alphaProperty().get());
		map.addProperty(NUM_CHANNELS_KEY, numChannels);
		map.add(COLOR_KEY, context.serialize(IntStream.range(0, numChannels).mapToObj(c -> src.colorProperty(c).get()).map(Colors::toHTML).toArray(String[]::new)));
		map.add(MIN_KEY, context.serialize(IntStream.range(0, numChannels).mapToDouble(c -> src.minProperty(c).get()).toArray()));
		map.add(MAX_KEY, context.serialize(IntStream.range(0, numChannels).mapToDouble(c -> src.maxProperty(c).get()).toArray()));
		map.add(CHANNEL_ALPHA_KEY, context.serialize(IntStream.range(0, numChannels).mapToDouble(c -> src.channelAlphaProperty(c).get()).toArray()));
		return map;
	}

}
