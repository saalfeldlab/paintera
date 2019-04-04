package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThresholdingSourceStateSerializer implements JsonSerializer<ThresholdingSourceState<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String NAME_KEY = "name";

	public static final String DEPENDS_ON_KEY = "dependsOn";

	public static final String CONVERTER_KEY = "converter";

	public static final String FOREGROUND_COLOR_KEY = "foreground";

	public static final String BACKGROUND_COLOR_KEY = "background";

	public static final String COMPOSITE_KEY = "composite";

	private final ToIntFunction<SourceState<?, ?>> stateToIndex;

	public ThresholdingSourceStateSerializer(final ToIntFunction<SourceState<?, ?>> stateToIndex)
	{
		super();
		this.stateToIndex = stateToIndex;
	}

	@Plugin(type = StatefulSerializer.SerializerFactory.class)
	public static class Factory
			implements StatefulSerializer.SerializerFactory<ThresholdingSourceState<?, ?>, ThresholdingSourceStateSerializer>
	{

		@Override
		public ThresholdingSourceStateSerializer createSerializer(
				final Supplier<String> projectDirectory,
				final ToIntFunction<SourceState<?, ?>> stateToIndex)
		{
			return new ThresholdingSourceStateSerializer(stateToIndex);
		}

		@Override
		public Class<ThresholdingSourceState<?, ?>> getTargetClass() {
			return (Class<ThresholdingSourceState<?, ?>>) (Class<?>) ThresholdingSourceState.class;
		}
	}

	@Override
	public JsonObject serialize(final ThresholdingSourceState<?, ?> state, final Type type, final
	JsonSerializationContext context)
	{
		final JsonObject map = new JsonObject();
		map.addProperty(NAME_KEY, state.nameProperty().get());
		map.add(DEPENDS_ON_KEY, context.serialize(Arrays.stream(state.dependsOn()).mapToInt(stateToIndex).toArray()));
		final JsonObject converterMap = new JsonObject();
		converterMap.addProperty(FOREGROUND_COLOR_KEY, Colors.toHTML(state.converter().getMasked()));
		converterMap.addProperty(BACKGROUND_COLOR_KEY, Colors.toHTML(state.converter().getNotMasked()));
		map.add(CONVERTER_KEY, converterMap);
		map.add(COMPOSITE_KEY, SerializationHelpers.serializeWithClassInfo(state.compositeProperty().get(), context));
		return map;
	}

}
