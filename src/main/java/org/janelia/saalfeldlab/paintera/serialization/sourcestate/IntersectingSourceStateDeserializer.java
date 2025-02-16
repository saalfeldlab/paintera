package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import bdv.cache.SharedQueue;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.IntersectableSourceState;
import org.janelia.saalfeldlab.paintera.state.IntersectingSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.COMPOSITE_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.COMPOSITE_TYPE_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.IS_VISIBLE_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.MESHES_ENABLED_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.MESHES_KEY;
import static org.janelia.saalfeldlab.paintera.serialization.sourcestate.IntersectingSourceStateSerializer.NAME_KEY;

public class IntersectingSourceStateDeserializer implements JsonDeserializer<IntersectingSourceState<?, ?>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final IntFunction<SourceState<?, ?>> dependsOn;

	private final SharedQueue queue;

	private final int priority;

	private final PainteraBaseView viewer;

	public IntersectingSourceStateDeserializer(
			final IntFunction<SourceState<?, ?>> dependsOn,
			final SharedQueue queue,
			final int priority,
			final PainteraBaseView viewer) {

		super();
		this.dependsOn = dependsOn;
		this.queue = queue;
		this.priority = priority;
		this.viewer = viewer;
	}

	@Plugin(type = StatefulSerializer.DeserializerFactory.class)
	public static class Factory implements StatefulSerializer.DeserializerFactory<IntersectingSourceState<?, ?>, IntersectingSourceStateDeserializer> {

		@Override
		public IntersectingSourceStateDeserializer createDeserializer(
				final Arguments arguments,
				final Supplier<String> projectDirectory,
				final IntFunction<SourceState<?, ?>> dependencyFromIndex) {

			return new IntersectingSourceStateDeserializer(dependencyFromIndex, arguments.viewer.getQueue(), 0, arguments.viewer);
		}

		@Override
		public Class<IntersectingSourceState<?, ?>> getTargetClass() {

			return (Class<IntersectingSourceState<?, ?>>)(Class<?>)IntersectingSourceState.class;
		}
	}

	@Override
	public IntersectingSourceState<?, ?> deserialize(final JsonElement el, final Type type, final JsonDeserializationContext context)
			throws JsonParseException {

		final JsonObject map = el.getAsJsonObject();
		LOG.debug("Deserializing {}", map);
		final int[] dependsOn = context.deserialize(map.get(SourceStateSerialization.DEPENDS_ON_KEY), int[].class);

		if (dependsOn.length != 2) {
			throw new JsonParseException("Expected exactly two dependencies, got: " + map.get(SourceStateSerialization.DEPENDS_ON_KEY));
		}

		final SourceState<?, ?> fillState = this.dependsOn.apply(dependsOn[0]);
		final SourceState<?, ?> seedState = this.dependsOn.apply(dependsOn[1]);
		if (fillState == null || seedState == null) {
			return null;
		}

		if (!(fillState instanceof IntersectableSourceState<?, ?, ?>)) {
			throw new JsonParseException("Expected " + IntersectableSourceState.class.getName() + " as first " +
					"dependency but got " + fillState.getClass().getName() + " instead.");
		}

		if (!(seedState instanceof IntersectableSourceState<?, ?, ?>)) {
			throw new JsonParseException("Expected "
					+ IntersectableSourceState.class.getName() + " as second dependency but got "
					+ seedState.getClass().getName() + " instead.");
		}

		try {
			final var compositeType = (Class<Composite<ARGBType, ARGBType>>)Class.forName(map.get(COMPOSITE_TYPE_KEY).getAsString());
			final Composite<ARGBType, ARGBType> composite = context.deserialize(map.get(COMPOSITE_KEY), compositeType);

			final String name = map.get(NAME_KEY).getAsString();

			LOG.debug(
					"Creating {} with first source={} second source={}",
					IntersectingSourceState.class.getSimpleName(),
					fillState,
					seedState);

			final IntersectingSourceState<?, ?> state = new IntersectingSourceState<>(
					(IntersectableSourceState<?, ?, ?>)fillState,
					(IntersectableSourceState<?, ?, ?>)seedState,
					composite,
					name,
					queue,
					priority,
					viewer);

			if (map.has(ManagedMeshSettings.MESH_SETTINGS_KEY)) {
				state.getMeshManager().getManagedSettings().set(context.deserialize(map.get(ManagedMeshSettings.MESH_SETTINGS_KEY), ManagedMeshSettings.class));
			}

			if (map.has(MESHES_KEY) && map.get(MESHES_KEY).isJsonObject()) {
				final JsonObject meshesMap = map.get(MESHES_KEY).getAsJsonObject();
				if (meshesMap.has(MESHES_ENABLED_KEY) && meshesMap.get(MESHES_ENABLED_KEY).isJsonPrimitive())
					state.setMeshesEnabled(meshesMap.get(MESHES_ENABLED_KEY).getAsBoolean());
			}

			//TODO this is bad, centralize the keys to maybe the colorconverter or somewhere more general.
			var CONVERTER = "converter";
			var CONVERTER_MIN = "min";
			var CONVERTER_MAX = "max";
			var CONVERTER_ALPHA = "alpha";
			var CONVERTER_COLOR = "color";
			if (map.has(CONVERTER) && map.get(CONVERTER).isJsonObject()) {
				final var converter = map.getAsJsonObject(CONVERTER);
				Optional.ofNullable(converter.get(CONVERTER_MIN)).map(JsonElement::getAsDouble).ifPresent(state.converter().minProperty()::set);
				Optional.ofNullable(converter.get(CONVERTER_MAX)).map(JsonElement::getAsDouble).ifPresent(state.converter().maxProperty()::set);
				Optional.ofNullable(converter.get(CONVERTER_ALPHA)).map(JsonElement::getAsDouble).ifPresent(state.converter().alphaProperty()::set);
				Optional.ofNullable(converter.get(CONVERTER_COLOR)).map(JsonElement::getAsString).map(Colors::toARGBType)
						.ifPresent(state.converter().colorProperty()::set);
			}
			if (map.has(IS_VISIBLE_KEY)) {
				state.isVisibleProperty().set(map.get(IS_VISIBLE_KEY).getAsBoolean());
			}
			return state;

		} catch (final ClassNotFoundException e) {
			throw new JsonParseException(e);
		}
	}

}
