package org.janelia.saalfeldlab.paintera.serialization.fx;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.scene.text.Font;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class FontSerializer implements PainteraSerialization.PainteraAdapter<Font> {

	private static final String SIZE_KEY = "size";

	private static final String NAME_KEY = "name";

	@Override
	public Font deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context) throws JsonParseException {
		final JsonObject map = json.getAsJsonObject();
		return new Font(
				map.has(NAME_KEY) ? map.get(NAME_KEY).getAsString() : "SansSerif",
				map.has(SIZE_KEY) ? map.get(SIZE_KEY).getAsDouble() : 18.0);
	}

	@Override
	public JsonElement serialize(
			final Font font,
			final Type typeOfSrc,
			final JsonSerializationContext context) {
		final JsonObject map = new JsonObject();
		map.addProperty(NAME_KEY, font.getName());
		map.addProperty(SIZE_KEY, font.getSize());
		return map;
	}

	@Override
	public Class<Font> getTargetClass() {
		return Font.class;
	}

	@Override
	public boolean isHierarchyAdapter() {
		return false;
	}
}
