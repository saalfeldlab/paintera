package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Optional;

public class ScreenScalesConfigSerializer implements JsonSerializer<ScreenScalesConfig>, JsonDeserializer<ScreenScalesConfig> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String SCALES_KEY = "scales";

	@Override
	public ScreenScalesConfig deserialize(
			JsonElement jsonElement,
			Type type,
			JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
		LOG.debug("De-serializing screen scales config from {}", jsonElement);

		if (!jsonElement.isJsonObject())
			throw new JsonParseException("Expected Json Object but got " + jsonElement);

		final JsonObject obj = jsonElement.getAsJsonObject();

		final ScreenScalesConfig config = new ScreenScalesConfig();
		Optional
				.ofNullable(obj.get(SCALES_KEY))
				.map(el -> (double[]) jsonDeserializationContext.deserialize(el, double[].class))
				.map(ScreenScalesConfig.ScreenScales::new)
				.ifPresent(config.screenScalesProperty()::set);
		return config;
	}

	@Override
	public JsonElement serialize(ScreenScalesConfig screenScalesConfig, Type type, JsonSerializationContext jsonSerializationContext) {
		LOG.debug("Serializing {}", screenScalesConfig);
		final JsonObject obj = new JsonObject();
		Optional
				.ofNullable(screenScalesConfig.screenScalesProperty().get())
				.map(scales -> scales.getScalesCopy())
				.ifPresent(scales -> obj.add(SCALES_KEY, jsonSerializationContext.serialize(scales)));
		return obj;
	}
}
