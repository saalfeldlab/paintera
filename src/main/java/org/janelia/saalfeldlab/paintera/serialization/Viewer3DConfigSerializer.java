package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class Viewer3DConfigSerializer implements PainteraSerialization.PainteraAdapter<Viewer3DConfig> {

	private static final String AFFINE_KEY = "affine";

	private static final String ARE_MESHES_ENABLED_KEY = "meshesEnabled";

	private static final String BACKGROUND_KEY = "background";

	private static final String RENDERER_BLOCK_SIZE_KEY = "rendererBlockSize";

	private static final String SHOW_BLOCK_BOUNDARIES_KEY = "showBlockBoundaries";

	private static final String NUM_ELEMENTS_PER_FRAME_KEY = "numElementsPerFrame";

	private static final String FRAME_DELAY_MSEC_KEY = "frameDelayMsec";

	private static final String SCENE_UPDATE_DELAY_MSEC_KEY = "sceneUpdateDelayMsec";

	@Override
	public Viewer3DConfig deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context) throws JsonParseException {
		final Viewer3DConfig config = new Viewer3DConfig();
		final JsonObject map = json.getAsJsonObject();
		if (map.has(AFFINE_KEY))
			config.setAffine(context.deserialize(map.get(AFFINE_KEY), Affine.class));
		if (map.has(ARE_MESHES_ENABLED_KEY))
			config.areMeshesEnabledProperty().set(map.get(ARE_MESHES_ENABLED_KEY).getAsBoolean());
		if (map.has(BACKGROUND_KEY))
			config.backgroundColorProperty().set(Color.web(map.get(BACKGROUND_KEY).getAsString()));
		if (map.has(SHOW_BLOCK_BOUNDARIES_KEY))
			config.showBlockBoundariesProperty().set(map.get(SHOW_BLOCK_BOUNDARIES_KEY).getAsBoolean());
		if (map.has(RENDERER_BLOCK_SIZE_KEY))
			config.rendererBlockSizeProperty().set(map.get(RENDERER_BLOCK_SIZE_KEY).getAsInt());
		if (map.has(NUM_ELEMENTS_PER_FRAME_KEY))
			config.numElementsPerFrameProperty().set(map.get(NUM_ELEMENTS_PER_FRAME_KEY).getAsInt());
		if (map.has(FRAME_DELAY_MSEC_KEY))
			config.frameDelayMsecProperty().set(map.get(FRAME_DELAY_MSEC_KEY).getAsLong());
		if (map.has(SCENE_UPDATE_DELAY_MSEC_KEY))
			config.sceneUpdateDelayMsecProperty().set(map.get(SCENE_UPDATE_DELAY_MSEC_KEY).getAsLong());
		return config;
	}

	@Override
	public JsonElement serialize(
			final Viewer3DConfig config,
			final Type typeOfSrc,
			final JsonSerializationContext context) {
		final JsonObject map = new JsonObject();
		if (config.isWasAffineSet())
			map.add(AFFINE_KEY, context.serialize(config.getAffineCopy()));
		map.addProperty(ARE_MESHES_ENABLED_KEY, config.areMeshesEnabledProperty().get());
		map.addProperty(BACKGROUND_KEY, Colors.toHTML(config.backgroundColorProperty().get()));
		map.addProperty(SHOW_BLOCK_BOUNDARIES_KEY, config.showBlockBoundariesProperty().get());
		map.addProperty(RENDERER_BLOCK_SIZE_KEY, config.rendererBlockSizeProperty().get());
		map.addProperty(NUM_ELEMENTS_PER_FRAME_KEY, config.numElementsPerFrameProperty().get());
		map.addProperty(FRAME_DELAY_MSEC_KEY, config.frameDelayMsecProperty().get());
		map.addProperty(SCENE_UPDATE_DELAY_MSEC_KEY, config.sceneUpdateDelayMsecProperty().get());
		return map;
	}

	@Override
	public Class<Viewer3DConfig> getTargetClass() {
		return Viewer3DConfig.class;
	}

	@Override
	public boolean isHierarchyAdapter() {
		return false;
	}
}
