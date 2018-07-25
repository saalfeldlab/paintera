package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThresholdingSourceStateDeserializer implements JsonDeserializer<ThresholdingSourceState<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final IntFunction<SourceState<?, ?>> dependsOn;

	public ThresholdingSourceStateDeserializer(final IntFunction<SourceState<?, ?>> dependsOn)
	{
		super();
		this.dependsOn = dependsOn;
	}

	public static class Factory implements
	                            StatefulSerializer.Deserializer<ThresholdingSourceState<?, ?>,
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

		if (!(dependsOnState instanceof RawSourceState<?, ?>))
		{
			throw new JsonParseException("Expected " + RawSourceState.class.getName() + " as dependency but got " +
					dependsOnState.getClass().getName() + " instead.");
		}

		final ThresholdingSourceState<?, ?> state = new ThresholdingSourceState(
				map.get(ThresholdingSourceStateSerializer.NAME_KEY).getAsString(),
				(RawSourceState) dependsOnState
		);

		final JsonObject converterMap = map.get(ThresholdingSourceStateSerializer.CONVERTER_KEY).getAsJsonObject();
		final ARGBType   foreground   = Colors.toARGBType(converterMap.get(ThresholdingSourceStateSerializer
				.FOREGROUND_COLOR_KEY).getAsString());
		final ARGBType   background   = Colors.toARGBType(converterMap.get(ThresholdingSourceStateSerializer
				.BACKGROUND_COLOR_KEY).getAsString());
		LOG.debug("Got foreground={} and background={}", foreground, background);
		state.converter().setMasked(foreground);
		state.converter().setNotMasked(background);
		return state;
	}

}
