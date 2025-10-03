package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class WindowPropertiesSerializer implements PainteraSerialization.PainteraAdapter<WindowProperties> {

	public static final String WIDTH_KEY = "width";

	public static final String HEIGHT_KEY = "height";

	private static final String IS_FULL_SCREEN_KEY = "isFullScreen";

	@Override
	public JsonElement serialize(final WindowProperties src, final Type typeOfSrc, final JsonSerializationContext context) {

		final JsonObject obj = new JsonObject();
		obj.addProperty(WIDTH_KEY, src.getWidth());
		obj.addProperty(HEIGHT_KEY, src.getHeight());
		obj.addProperty(IS_FULL_SCREEN_KEY, src.isFullScreen());
		return obj;
	}

	@Override
	public WindowProperties deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context) throws JsonParseException {

		final WindowProperties properties = new WindowProperties();
		if (json.isJsonObject()) {
			final JsonObject map = json.getAsJsonObject();
			if (map.has(WIDTH_KEY))
				properties.setWidth(map.get(WIDTH_KEY).getAsInt());
			if (map.has(HEIGHT_KEY))
				properties.setHeight(map.get(HEIGHT_KEY).getAsInt());
			if (map.has(IS_FULL_SCREEN_KEY))
				properties.setFullScreen(map.get(IS_FULL_SCREEN_KEY).getAsBoolean());
		}
		return properties;
	}

	@Override
	public boolean isHierarchyAdapter() {

		return false;
	}

	@Override
	public Class<WindowProperties> getTargetClass() {

		return WindowProperties.class;
	}
}
