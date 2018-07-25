package org.janelia.saalfeldlab.paintera.serialization.config;

import java.lang.reflect.Type;
import java.util.Optional;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.util.Colors;

public class CrosshairConfigSerializer implements
                                       JsonSerializer<CrosshairConfig>,
                                       JsonDeserializer<CrosshairConfig>
{

	private static final String ON_FOCUS_COLOR_KEY = "onFocusColor";

	private static final String OFF_FOCUS_COLOR_KEY = "offFocusColor";

	private static final String VISIBLE_KEY = "isVisible";

	@Override
	public CrosshairConfig deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		final CrosshairConfig config = new CrosshairConfig();
		if (json != null && json.isJsonObject())
		{
			final JsonObject obj = json.getAsJsonObject();
			Optional
					.ofNullable(obj.get(ON_FOCUS_COLOR_KEY))
					.map(JsonElement::getAsString)
					.map(Color::web)
					.ifPresent(config::setOnFocusColor);
			Optional
					.ofNullable(obj.get(OFF_FOCUS_COLOR_KEY))
					.map(JsonElement::getAsString)
					.map(Color::web)
					.ifPresent(config::setOutOfFocusColor);
			Optional
					.ofNullable(obj.get(VISIBLE_KEY))
					.filter(JsonElement::isJsonPrimitive)
					.map(JsonElement::getAsJsonPrimitive)
					.filter(JsonPrimitive::isBoolean)
					.map(JsonPrimitive::getAsBoolean)
					.ifPresent(config::setShowCrosshairs);
		}
		return config;
	}

	@Override
	public JsonElement serialize(final CrosshairConfig src, final Type typeOfSrc, final JsonSerializationContext
			context)
	{
		final JsonObject map = new JsonObject();
		map.addProperty(ON_FOCUS_COLOR_KEY, Colors.toHTML(src.getOnFocusColor()));
		map.addProperty(OFF_FOCUS_COLOR_KEY, Colors.toHTML(src.getOutOfFocusColor()));
		map.addProperty(VISIBLE_KEY, src.getShowCrosshairs());
		return map;
	}

}
