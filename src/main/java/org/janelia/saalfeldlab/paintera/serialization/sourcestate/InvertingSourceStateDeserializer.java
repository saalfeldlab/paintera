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
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.InvertingRawSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvertingSourceStateDeserializer implements JsonDeserializer<InvertingRawSourceState<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String NAME_KEY = "name";

	private final IntFunction<SourceState<?, ?>> dependsOn;

	public InvertingSourceStateDeserializer(final IntFunction<SourceState<?, ?>> dependsOn)
	{
		super();
		this.dependsOn = dependsOn;
	}

	public static class Factory
			implements StatefulSerializer.Deserializer<InvertingRawSourceState<?, ?>, InvertingSourceStateDeserializer>
	{

		@Override
		public InvertingSourceStateDeserializer createDeserializer(
				final Arguments arguments,
				final Supplier<String> projectDirectory,
				final IntFunction<SourceState<?, ?>> dependencyFromIndex)
		{
			return new InvertingSourceStateDeserializer(dependencyFromIndex);
		}

	}

	@Override
	public InvertingRawSourceState<?, ?> deserialize(final JsonElement el, final Type type, final
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

		final InvertingRawSourceState<?, ?> state = new InvertingRawSourceState<>(
				map.get(NAME_KEY).getAsString(),
				(RawSourceState<?, ?>) dependsOnState
		);

		return state;
	}

}
