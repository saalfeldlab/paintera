package org.janelia.saalfeldlab.paintera.serialization.converter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Optional;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.util.Colors;

public class ARGBColorConverterSerializer<T extends RealType<T>>
		implements JsonSerializer<ARGBColorConverter<T>>, JsonDeserializer<ARGBColorConverter<T>>
{

	private static final String ALPHA_KEY = "alpha";

	private static final String COLOR_KEY = "color";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	@Override
	public ARGBColorConverter<T> deserialize(final JsonElement json, final Type type, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		try
		{
			@SuppressWarnings("unchecked") final ARGBColorConverter<T> converter = (ARGBColorConverter<T>) Class
					.forName(
					type.getTypeName()).getConstructor(double.class, double.class).newInstance(0.0, 255.0);
			final JsonObject map = json.getAsJsonObject();
			Optional.ofNullable(map.get(ALPHA_KEY)).map(JsonElement::getAsDouble).ifPresent(converter.alphaProperty()
					::set);
			Optional.ofNullable(map.get(COLOR_KEY)).map(JsonElement::getAsString).map(Colors::toARGBType).ifPresent(
					converter.colorProperty()::set);
			Optional.ofNullable(map.get(MIN_KEY)).map(JsonElement::getAsDouble).ifPresent(converter::setMin);
			Optional.ofNullable(map.get(MAX_KEY)).map(JsonElement::getAsDouble).ifPresent(converter::setMax);
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
	public JsonElement serialize(final ARGBColorConverter<T> src, final Type type, final JsonSerializationContext
			cpntext)
	{
		final JsonObject map = new JsonObject();
		map.addProperty(ALPHA_KEY, src.alphaProperty().get());
		map.addProperty(COLOR_KEY, Colors.toHTML(src.getColor()));
		map.addProperty(MIN_KEY, src.getMin());
		map.addProperty(MAX_KEY, src.getMax());
		return map;
	}

}
