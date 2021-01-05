package org.janelia.saalfeldlab.paintera.serialization.config;

import java.lang.reflect.Type;
import java.util.Optional;

import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class MeshSettingsSerializer implements PainteraSerialization.PainteraAdapter<MeshSettings> {

	private static final String NUM_SCALE_LEVELS_KEY = "numScaleLevels";

	private static final String LEVEL_OF_DETAIL_KEY = "levelOfDetail";

	private static final String COARSEST_SCALE_LEVEL_KEY = "coarsestScaleLevel";

	private static final String FINEST_SCALE_LEVEL_KEY = "finestScaleLevel";

	private static final String SIMPLIFICATION_ITERATIONS_KEY = "simplificationIterations";

	private static final String SMOOTHING_LAMBDA_KEY = "smoothingLambda";

	private static final String SMOOTHING_ITERATIONS_KEY = "smoothingIterations";

	private static final String MIN_LABEL_RATIO_KEY = "minLabelRatio";

	private static final String OPACITY_KEY = "opacity";

	private static final String DRAW_MODE_KEY = "drawMode";

	private static final String CULL_FACE_KEY = "cullFace";

	private static final String INFLATE_KEY = "inflate";

	private static final String IS_VISIBLE_KEY = "isVisible";

	public static MeshSettings deserializeInto(
			final JsonObject map,
			final MeshSettings settings,
			final JsonDeserializationContext context) {
		Optional.ofNullable(map.get(COARSEST_SCALE_LEVEL_KEY)).map(JsonElement::getAsInt).ifPresent(settings::setCoarsetsScaleLevel);
		Optional.ofNullable(map.get(FINEST_SCALE_LEVEL_KEY)).map(JsonElement::getAsInt).ifPresent(settings::setFinestScaleLevel);
		Optional.ofNullable(map.get(SIMPLIFICATION_ITERATIONS_KEY)).map(JsonElement::getAsInt).ifPresent(settings::setSimplificationIterations);
		Optional.ofNullable(map.get(SMOOTHING_ITERATIONS_KEY)).map(JsonElement::getAsInt).ifPresent(settings::setSmoothingIterations);
		Optional.ofNullable(map.get(SMOOTHING_LAMBDA_KEY)).map(JsonElement::getAsDouble).ifPresent(settings::setSmoothingLambda);
		Optional.ofNullable(map.get(OPACITY_KEY)).map(JsonElement::getAsDouble).ifPresent(settings::setOpacity);
		Optional.ofNullable(map.get(INFLATE_KEY)).map(JsonElement::getAsDouble).ifPresent(settings::setInflate);
		Optional.ofNullable(map.get(DRAW_MODE_KEY)).map(el -> (DrawMode) context.deserialize(el, DrawMode.class)).ifPresent(settings::setDrawMode);
		Optional.ofNullable(map.get(CULL_FACE_KEY)).map(el -> (CullFace) context.deserialize(el, CullFace.class)).ifPresent(settings::setCullFace);
		Optional.ofNullable(map.get(IS_VISIBLE_KEY)).map(JsonElement::getAsBoolean).ifPresent(settings::setVisible);
		Optional.ofNullable(map.get(MIN_LABEL_RATIO_KEY)).map(JsonElement::getAsDouble).ifPresent(settings::setMinLabelRatio);
		Optional.ofNullable(map.get(LEVEL_OF_DETAIL_KEY)).map(JsonElement::getAsInt).ifPresent(settings::setLevelOfDetail);
		return settings;
	}

	@Override
	public MeshSettings deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context)
			throws JsonParseException {
		final JsonObject map = json.getAsJsonObject();
		final MeshSettings settings = new MeshSettings(map.get(NUM_SCALE_LEVELS_KEY).getAsInt());
		return deserializeInto(map, settings, context);
	}

	@Override
	public JsonElement serialize(final MeshSettings src, final Type typeOfSrc, final JsonSerializationContext context) {
		final JsonObject map = new JsonObject();
		final MeshSettings.Defaults defaults = src.getDefaults();
		map.addProperty(NUM_SCALE_LEVELS_KEY, src.getNumScaleLevels());

		if (defaults.getSimplificationIterations() != src.getSimplificationIterations())
			map.addProperty(SIMPLIFICATION_ITERATIONS_KEY, src.getSimplificationIterations());

		if (src.getLevelOfDetail() != defaults.getLevelOfDetail())
			map.addProperty(LEVEL_OF_DETAIL_KEY, src.levelOfDetailProperty().get());

		if (src.coarsestScaleLevelProperty().get() != MeshSettings.Defaults.getDefaultCoarsestScaleLevel(src.getNumScaleLevels()))
			map.addProperty(COARSEST_SCALE_LEVEL_KEY, src.coarsestScaleLevelProperty().get());

		if (src.getFinestScaleLevel() != MeshSettings.Defaults.getDefaultFinestScaleLevel(src.getNumScaleLevels()))
			map.addProperty(FINEST_SCALE_LEVEL_KEY, src.finestScaleLevelProperty().get());

		if (defaults.getSimplificationIterations() != src.simplificationIterationsProperty().get())
			map.addProperty(SIMPLIFICATION_ITERATIONS_KEY, src.simplificationIterationsProperty().get());

		if (defaults.getSmoothingLambda() != src.getSmoothingLambda())
			map.addProperty(SMOOTHING_LAMBDA_KEY, src.getSmoothingLambda());

		if (defaults.getSmoothingIterations() != src.getSmoothingIterations())
			map.addProperty(SMOOTHING_ITERATIONS_KEY, src.getSmoothingIterations());

		if (defaults.getOpacity() != src.getOpacity())
			map.addProperty(OPACITY_KEY, src.getOpacity());

		if (defaults.getMinLabelRatio() != src.minLabelRatioProperty().get())
			map.addProperty(MIN_LABEL_RATIO_KEY, src.minLabelRatioProperty().get());

		if (defaults.getOpacity() != src.opacityProperty().get())
			map.addProperty(OPACITY_KEY, src.opacityProperty().get());

		if (defaults.getInflate() != src.getInflate())
			map.addProperty(INFLATE_KEY, src.getInflate());

		if (defaults.isVisible() != src.isVisible())
			map.addProperty(IS_VISIBLE_KEY, src.isVisible());

		if (defaults.getDrawMode() != src.getDrawMode())
			map.add(DRAW_MODE_KEY, context.serialize(src.getDrawMode()));

		if (defaults.getCullFace() != src.getCullFace())
			map.add(CULL_FACE_KEY, context.serialize(src.getCullFace()));

		return map;
	}

	@Override
	public Class<MeshSettings> getTargetClass() {
		return MeshSettings.class;
	}
}
