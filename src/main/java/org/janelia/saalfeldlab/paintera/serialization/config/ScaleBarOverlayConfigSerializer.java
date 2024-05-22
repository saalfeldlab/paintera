package org.janelia.saalfeldlab.paintera.serialization.config;

import org.janelia.saalfeldlab.bdv.fx.viewer.scalebar.ScaleBarOverlayConfig;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class ScaleBarOverlayConfigSerializer implements PainteraSerialization.PainteraAdapter<ScaleBarOverlayConfig> {

	private static final String OVERLAY_FONT_KEY = "overlayFont";

	private static final String FOREGROUND_COLOR_KEY = "foregroundColor";

	private static final String BACKGROUND_COLOR_KEY = "backgroundColor";

	private static final String IS_SHOWING_KEY = "isShowing";

	private static final String TARGET_SCALE_BAR_LENGTH = "targetScaleBarLength";

	@Override
	public ScaleBarOverlayConfig deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context) throws JsonParseException {

		final ScaleBarOverlayConfig config = new ScaleBarOverlayConfig();
		if (json.isJsonObject()) {
			final JsonObject map = json.getAsJsonObject();

			if (map.has(OVERLAY_FONT_KEY))
				config.setOverlayFont(context.deserialize(map.get(OVERLAY_FONT_KEY), Font.class));

			if (map.has(FOREGROUND_COLOR_KEY))
				config.setForegroundColor(Color.web(map.get(FOREGROUND_COLOR_KEY).getAsString()));

			if (map.has(BACKGROUND_COLOR_KEY))
				config.setBackgroundColor(Color.web(map.get(BACKGROUND_COLOR_KEY).getAsString()));

			if (map.has(IS_SHOWING_KEY))
				config.setIsShowing(map.get(IS_SHOWING_KEY).getAsBoolean());

			if (map.has(TARGET_SCALE_BAR_LENGTH))
				config.setTargetScaleBarLength(map.get(TARGET_SCALE_BAR_LENGTH).getAsDouble());
		}
		return config;
	}

	@Override
	public JsonElement serialize(
			final ScaleBarOverlayConfig config,
			final Type typeOfSrc,
			final JsonSerializationContext context) {

		final JsonObject map = new JsonObject();
		map.add(OVERLAY_FONT_KEY, context.serialize(config.getOverlayFont()));
		map.addProperty(FOREGROUND_COLOR_KEY, Colors.toHTML(config.getForegroundColor()));
		map.addProperty(BACKGROUND_COLOR_KEY, Colors.toHTML(config.getBackgroundColor()));
		map.addProperty(IS_SHOWING_KEY, config.getIsShowing());
		map.addProperty(TARGET_SCALE_BAR_LENGTH, config.getTargetScaleBarLength());
		return map;
	}

	@Override
	public Class<ScaleBarOverlayConfig> getTargetClass() {

		return ScaleBarOverlayConfig.class;
	}

	@Override
	public boolean isHierarchyAdapter() {

		return false;
	}
}
