package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.state.InvertingRawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvertingSourceStateSerializer implements JsonSerializer<InvertingRawSourceState<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String NAME_KEY = "name";

	public static final String DEPENDS_ON_KEY = "dependsOn";

	private final ToIntFunction<SourceState<?, ?>> stateToIndex;

	public InvertingSourceStateSerializer(final ToIntFunction<SourceState<?, ?>> stateToIndex)
	{
		super();
		this.stateToIndex = stateToIndex;
	}

	public static class Factory
			implements StatefulSerializer.Serializer<InvertingRawSourceState<?, ?>, InvertingSourceStateSerializer>
	{

		@Override
		public InvertingSourceStateSerializer createSerializer(
				final Supplier<String> projectDirectory,
				final ToIntFunction<SourceState<?, ?>> stateToIndex)
		{
			return new InvertingSourceStateSerializer(stateToIndex);
		}

	}

	@Override
	public JsonObject serialize(final InvertingRawSourceState<?, ?> state, final Type type, final
	JsonSerializationContext context)
	{
		final JsonObject map = new JsonObject();
		map.addProperty(NAME_KEY, state.nameProperty().get());
		map.add(DEPENDS_ON_KEY, context.serialize(Arrays.stream(state.dependsOn()).mapToInt(stateToIndex).toArray()));
		return map;
	}

}
