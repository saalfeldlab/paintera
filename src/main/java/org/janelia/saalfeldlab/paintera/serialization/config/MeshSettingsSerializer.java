package org.janelia.saalfeldlab.paintera.serialization.config;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;
import java.util.Optional;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class MeshSettingsSerializer implements PainteraSerialization.PainteraAdapter<MeshSettings> {

	private static final String NUM_SCALE_LEVLES_KEY = "numScaleLevels";

	private static final String SCALE_LEVEL_KEY = "scaleLevel";

	private static final String SIMPLIFCIATION_ITERATIONS_KEY = "simplificationIterations";

	private static final String SMOOTHING_LAMBDA_KEY = "smoothingLambda";

	private static final String SMOOTHING_ITERATIONS_KEY = "smoothingIterations";

	private static final String OPACITY_KEY = "opacity";

	private static final String DRAW_MODE_KEY = "drawMode";

	private static final String CULL_FACE_KEY = "cullFace";

	private static final String INFLATE_KEY = "inflate";

	private static final String IS_VISIBLE_KEY = "isVisible";

	@Override
	public MeshSettings deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context)
			throws JsonParseException {
		final JsonObject map = json.getAsJsonObject();
		final MeshSettings settings = new MeshSettings(map.get(NUM_SCALE_LEVLES_KEY).getAsInt());
		Optional.ofNullable(map.get(SCALE_LEVEL_KEY)).map(JsonElement::getAsInt).ifPresent(settings.scaleLevelProperty()::set);
		Optional.ofNullable(map.get(SIMPLIFCIATION_ITERATIONS_KEY)).map(JsonElement::getAsInt).ifPresent(settings.simplificationIterationsProperty()::set);
		Optional.ofNullable(map.get(SMOOTHING_ITERATIONS_KEY)).map(JsonElement::getAsInt).ifPresent(settings.smoothingIterationsProperty()::set);
		Optional.ofNullable(map.get(SMOOTHING_LAMBDA_KEY)).map(JsonElement::getAsDouble).ifPresent(settings.smoothingLambdaProperty()::set);
		Optional.ofNullable(map.get(OPACITY_KEY)).map(JsonElement::getAsDouble).ifPresent(settings.opacityProperty()::set);
		Optional.ofNullable(map.get(INFLATE_KEY)).map(JsonElement::getAsDouble).ifPresent(settings.inflateProperty()::set);
		Optional.ofNullable(map.get(DRAW_MODE_KEY)).map(el -> (DrawMode) context.deserialize(el, DrawMode.class)).ifPresent(settings.drawModeProperty()::set);
		Optional.ofNullable(map.get(CULL_FACE_KEY)).map(el -> (CullFace) context.deserialize(el, CullFace.class)).ifPresent(settings.cullFaceProperty()::set);
		Optional.ofNullable(map.get(IS_VISIBLE_KEY)).map(JsonElement::getAsBoolean).ifPresent(settings.isVisibleProperty()::set);
		return settings;
	}

	@Override
	public JsonElement serialize(final MeshSettings src, final Type typeOfSrc, final JsonSerializationContext context) {
		final JsonObject map = new JsonObject();
		map.addProperty(NUM_SCALE_LEVLES_KEY, src.numScaleLevels());

		if (src.scaleLevelProperty().get() != src.numScaleLevels() - 1)
			map.addProperty(SCALE_LEVEL_KEY, src.scaleLevelProperty().get());

		if (MeshSettings.Defaults.MESH_SIMPLIFICATION_ITERATIONS != src.simplificationIterationsProperty().get())
			map.addProperty(SIMPLIFCIATION_ITERATIONS_KEY, src.simplificationIterationsProperty().get());

		if (MeshSettings.Defaults.MESH_SMOOTHING_LAMBDA != src.smoothingLambdaProperty().get())
			map.addProperty(SMOOTHING_LAMBDA_KEY, src.smoothingLambdaProperty().get());

		if (MeshSettings.Defaults.MESH_SMOOTHING_ITERATIONS != src.smoothingIterationsProperty().get())
			map.addProperty(SMOOTHING_ITERATIONS_KEY, src.smoothingIterationsProperty().get());

		if (MeshSettings.Defaults.MESH_OPACITY != src.opacityProperty().get())
			map.addProperty(OPACITY_KEY, src.opacityProperty().get());

		if (MeshSettings.Defaults.MESH_INFLATE != src.inflateProperty().get())
			map.addProperty(INFLATE_KEY, src.inflateProperty().get());

		if (MeshSettings.Defaults.MESH_IS_VISIBLE!= src.isVisibleProperty().get())
			map.addProperty(IS_VISIBLE_KEY, src.isVisibleProperty().get());

		if (MeshSettings.Defaults.MESH_DRAWMODE != src.drawModeProperty().get())
			map.add(DRAW_MODE_KEY, context.serialize(src.drawModeProperty().get()));

		if (MeshSettings.Defaults.MESH_CULLFACE != src.cullFaceProperty().get())
			map.add(CULL_FACE_KEY, context.serialize(src.cullFaceProperty().get()));

		return map;
	}

	@Override
	public Class<MeshSettings> getTargetClass() {
		return MeshSettings.class;
	}
}
