package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.serialization.config.MeshSettingsSerializer;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.ThresholdingSourceStateSerializer.MESHES_ENABLED_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.ThresholdingSourceStateSerializer.MESHES_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.ThresholdingSourceStateSerializer.MESH_SETTINGS_KEY;

public class ThresholdingSourceStateDeserializer implements JsonDeserializer<ThresholdingSourceState<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final IntFunction<SourceState<?, ?>> dependsOn;

	public ThresholdingSourceStateDeserializer(final IntFunction<SourceState<?, ?>> dependsOn)
	{
		super();
		this.dependsOn = dependsOn;
	}

	@Plugin(type = StatefulSerializer.DeserializerFactory.class)
	public static class Factory implements
			StatefulSerializer.DeserializerFactory<ThresholdingSourceState<?, ?>,
													ThresholdingSourceStateDeserializer>
	{

		@Override
		public ThresholdingSourceStateDeserializer createDeserializer(
				final Arguments arguments,
				final Supplier<String> projectDirectory,
				final IntFunction<SourceState<?, ?>> dependencyFromIndex)
		{
			return new ThresholdingSourceStateDeserializer(dependencyFromIndex);
		}

		@Override
		public Class<ThresholdingSourceState<?, ?>> getTargetClass() {
			return (Class<ThresholdingSourceState<?, ?>>) (Class<?>) ThresholdingSourceState.class;
		}
	}

	@Override
	public ThresholdingSourceState<?, ?> deserialize(final JsonElement el, final Type type, final
	JsonDeserializationContext context)
	throws JsonParseException
	{
		final JsonObject map = el.getAsJsonObject();
		LOG.debug("Deserializing {}", map);
		final int[] dependsOn = context.deserialize(map.get(SourceStateSerialization.DEPENDS_ON_KEY), int[].class);

		if (dependsOn.length != 1)
		{
			throw new JsonParseException("Expected exactly one dependency, got: " + map.get(SourceStateSerialization
					.DEPENDS_ON_KEY));
		}

		final SourceState<?, ?> dependsOnState = this.dependsOn.apply(dependsOn[0]);
		if (dependsOnState == null) { return null; }

		final String name = map.get(ThresholdingSourceStateSerializer.NAME_KEY).getAsString();
		final ThresholdingSourceState<?, ?> state;
		if (dependsOnState instanceof RawSourceState<?, ?>) {
			state = new ThresholdingSourceState<>(name, (RawSourceState) dependsOnState);
		} else if (dependsOnState instanceof ConnectomicsRawState<?, ?>) {
			state = new ThresholdingSourceState<>(name, (ConnectomicsRawState) dependsOnState);
		} else {
			throw new JsonParseException("Expected " + ConnectomicsRawState.class.getName() + " or " + RawSourceState.class.getName() +
					" as dependency but got " + dependsOnState.getClass().getName() + " instead.");
		}

		final JsonObject converterMap = map.get(ThresholdingSourceStateSerializer.CONVERTER_KEY).getAsJsonObject();
		final ARGBType   foreground   = Colors.toARGBType(converterMap.get(ThresholdingSourceStateSerializer
				.FOREGROUND_COLOR_KEY).getAsString());
		final ARGBType   background   = Colors.toARGBType(converterMap.get(ThresholdingSourceStateSerializer
				.BACKGROUND_COLOR_KEY).getAsString());
		LOG.debug("Got foreground={} and background={}", foreground, background);
		state.colorProperty().set(Colors.toColor(foreground));
		state.backgroundColorProperty().set(Colors.toColor(background));

		if (map.has(ThresholdingSourceStateSerializer.COMPOSITE_KEY)) {
			try {
				state
						.compositeProperty()
						.set(SerializationHelpers.deserializeFromClassInfo(
								map.getAsJsonObject(ThresholdingSourceStateSerializer.COMPOSITE_KEY),
								context));
			} catch (final ClassNotFoundException e) {
				throw new JsonParseException(e);
			}
		}

		if (map.has(ThresholdingSourceStateSerializer.MIN_KEY))
			state.minProperty().set(map.get(ThresholdingSourceStateSerializer.MIN_KEY).getAsDouble());

		if (map.has(ThresholdingSourceStateSerializer.MAX_KEY))
			state.maxProperty().set(map.get(ThresholdingSourceStateSerializer.MAX_KEY).getAsDouble());

		if (map.has(MESHES_KEY) && map.get(MESHES_KEY).isJsonObject()) {
			final JsonObject meshesMap = map.getAsJsonObject(MESHES_KEY);
			if (meshesMap.has(MESH_SETTINGS_KEY) && meshesMap.get(MESH_SETTINGS_KEY).isJsonObject())
				MeshSettingsSerializer.deserializeInto(
						meshesMap.getAsJsonObject(MESH_SETTINGS_KEY),
						state.getMeshSettings(),
						context);
			if (meshesMap.has(MESHES_ENABLED_KEY) && meshesMap.get(MESHES_ENABLED_KEY).isJsonPrimitive())
				state.setMeshesEnabled(meshesMap.get(MESHES_ENABLED_KEY).getAsBoolean());
		}

		return state;
	}

}
