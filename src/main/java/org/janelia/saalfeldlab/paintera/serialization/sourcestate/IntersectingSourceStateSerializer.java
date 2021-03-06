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
import org.janelia.saalfeldlab.paintera.state.IntersectingSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntersectingSourceStateSerializer implements JsonSerializer<IntersectingSourceState> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String NAME_KEY = "name";

  public static final String COMPOSITE_KEY = "composite";

  public static final String COMPOSITE_TYPE_KEY = "compositeType";

  public static final String MESHES_KEY = "meshes";

  public static final String MESHES_ENABLED_KEY = "enabled";

  private final ToIntFunction<SourceState<?, ?>> stateToIndex;

  public IntersectingSourceStateSerializer(final ToIntFunction<SourceState<?, ?>> stateToIndex) {

	super();
	this.stateToIndex = stateToIndex;
  }

  @Plugin(type = StatefulSerializer.SerializerFactory.class)
  public static class Factory
		  implements StatefulSerializer.SerializerFactory<IntersectingSourceState, IntersectingSourceStateSerializer> {

	@Override
	public IntersectingSourceStateSerializer createSerializer(
			final Supplier<String> projectDirectory,
			final ToIntFunction<SourceState<?, ?>> stateToIndex) {

	  return new IntersectingSourceStateSerializer(stateToIndex);
	}

	@Override
	public Class<IntersectingSourceState> getTargetClass() {

	  return IntersectingSourceState.class;
	}
  }

  @Override
  public JsonObject serialize(
		  final IntersectingSourceState state,
		  final Type type,
		  final JsonSerializationContext context) {

	final JsonObject map = new JsonObject();
	map.addProperty(NAME_KEY, state.nameProperty().get());
	map.add(
			SourceStateSerialization.DEPENDS_ON_KEY,
			context.serialize(Arrays.stream(state.dependsOn()).mapToInt(stateToIndex).toArray()));
	map.addProperty(COMPOSITE_TYPE_KEY, state.compositeProperty().get().getClass().getName());
	map.add(COMPOSITE_KEY, context.serialize(state.compositeProperty().get()));

	final JsonObject meshesMap = new JsonObject();
	if (state.areMeshesEnabled() != IntersectingSourceState.DEFAULT_MESHES_ENABLED)
	  meshesMap.addProperty(MESHES_ENABLED_KEY, state.areMeshesEnabled());
	if (meshesMap.size() > 0)
	  map.add(MESHES_KEY, meshesMap);

	return map;
  }

}
