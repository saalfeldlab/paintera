package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import bdv.viewer.Interpolation;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceStateSerialization
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String DEPENDS_ON_KEY = "dependsOn";

	public static final String COMPOSITE_TYPE_KEY = "compositeType";

	public static final String CONVERTER_TYPE_KEY = "converterType";

	public static final String COMPOSITE_KEY = "composite";

	public static final String CONVERTER_KEY = "converter";

	public static final String INTERPOLATION_KEY = "interpolation";

	public static final String IS_VISIBLE_KEY = "isVisible";

	public static final String SOURCE_TYPE_KEY = "sourceType";

	public static final String SOURCE_KEY = "source";

	public static final String NAME_KEY = "name";

	public static final String AXIS_ORDER_KEY = "axisOrder";

	private static abstract class AbstractSourceStateSerializer<S extends SourceState<?, ?>>
			implements JsonSerializer<S>
	{

		@Override
		public JsonObject serialize(final S state, final Type type, final JsonSerializationContext context)
		{
			final JsonObject map = new JsonObject();
			map.add(COMPOSITE_KEY, context.serialize(state.compositeProperty().get()));
			map.add(CONVERTER_KEY, context.serialize(state.converter()));
			map.addProperty(COMPOSITE_TYPE_KEY, state.compositeProperty().get().getClass().getName());
			map.addProperty(CONVERTER_TYPE_KEY, state.converter().getClass().getName());
			map.add(INTERPOLATION_KEY, context.serialize(state.interpolationProperty().get()));
			map.addProperty(IS_VISIBLE_KEY, state.isVisibleProperty().get());
			map.addProperty(SOURCE_TYPE_KEY, state.getDataSource().getClass().getName());
			map.add(SOURCE_KEY, context.serialize(state.getDataSource()));
			map.addProperty(NAME_KEY, state.nameProperty().get());
			map.add(DEPENDS_ON_KEY, context.serialize(getDependencies(state)));
			map.add(AXIS_ORDER_KEY, context.serialize(Optional.ofNullable(state.getAxisOrder()).map(AxisOrder::spatialOnly).orElse(AxisOrder.XYZ)));
			return map;
		}

		protected abstract int[] getDependencies(S state);
	}

	public static class SourceStateSerializerWithoutDependencies<S extends SourceState<?, ?>>
			extends AbstractSourceStateSerializer<S>
	{
		@Override
		protected int[] getDependencies(final S state)
		{
			return new int[] {};
		}
	}

	public static class SourceStateSerializerWithDependencies<S extends SourceState<?, ?>>
			extends AbstractSourceStateSerializer<S>
	{
		private final ToIntFunction<SourceState<?, ?>> getIndices;

		public SourceStateSerializerWithDependencies(final ToIntFunction<SourceState<?, ?>> getIndices)
		{
			super();
			this.getIndices = getIndices;
		}

		@Override
		protected int[] getDependencies(final S state)
		{
			return Arrays
					.stream(state.dependsOn())
					.mapToInt(getIndices)
					.toArray();
		}
	}

	private static abstract class AbstractSourceStateDeserializer<S extends SourceState<?, ?>, C extends Converter<?,
			ARGBType>>
			implements JsonDeserializer<S>
	{
		@SuppressWarnings("unchecked")
		@Override
		public S deserialize(
				final JsonElement el,
				final Type type,
				final JsonDeserializationContext context) throws JsonParseException
		{
			try
			{
				LOG.debug("Deserializing from {}", el);
				final JsonObject map = el.getAsJsonObject();

				final SourceState<?, ?>[] dependsOn = Optional
						.ofNullable(map.get(DEPENDS_ON_KEY))
						.map(s -> (int[]) context.deserialize(s, int[].class))
						.map(this::dependenciesFromIndices)
						.orElseGet(() -> new SourceState[] {});

				if (Arrays.stream(dependsOn).filter(d -> d == null).count() > 0)
				{
					LOG.debug(
							"At least one (out of {}) dependency not initialized yet: {}",
							dependsOn.length,
							dependsOn
					         );
					return null;
				}

				LOG.debug("composite class: {} (key={})", map.get(COMPOSITE_TYPE_KEY), COMPOSITE_TYPE_KEY);
				final Class<? extends Composite<ARGBType, ARGBType>> compositeClass  = (Class<? extends
						Composite<ARGBType, ARGBType>>) Class.forName(
						map.get(COMPOSITE_TYPE_KEY).getAsString());
				final Class<? extends DataSource<?, ?>>              dataSourceClass = (Class<? extends DataSource<?,
						?>>) Class.forName(
						map.get(SOURCE_TYPE_KEY).getAsString());

				final Composite<ARGBType, ARGBType> composite  = context.deserialize(
						map.get(COMPOSITE_KEY),
						compositeClass
				                                                                    );
				final DataSource<?, ?>              dataSource = context.deserialize(
						map.get(SOURCE_KEY),
						dataSourceClass
				                                                                    );
				final String                        name       = map.get(NAME_KEY).getAsString();
				final boolean                       isVisible  = map.get(IS_VISIBLE_KEY).getAsBoolean();
				LOG.debug("Is visible? {}", isVisible);
				final Interpolation      interpolation  = context.deserialize(
						map.get(INTERPOLATION_KEY),
						Interpolation.class
				                                                             );
				final Class<? extends C> converterClass = (Class<? extends C>) Class.forName(map.get
						(CONVERTER_TYPE_KEY).getAsString());
				LOG.debug("Deserializing converter class {} from {}", converterClass, map.get(CONVERTER_KEY));
				final C converter = context.deserialize(map.get(CONVERTER_KEY), converterClass);

				final AxisOrder axisOrder = Optional
						.ofNullable((AxisOrder)context.deserialize(map.get(AXIS_ORDER_KEY), AxisOrder.class))
						.map(AxisOrder::spatialOnly)
						.orElse(AxisOrder.XYZ);

				final S state = makeState(map, dataSource, composite, converter, name, dependsOn, context);
				LOG.debug("Got state {}", state);
				state.isVisibleProperty().set(isVisible);
				state.interpolationProperty().set(interpolation);
				state.setAxisOrder(axisOrder);
				return state;
			} catch (final Exception e)
			{
				throw e instanceof JsonParseException ? (JsonParseException) e : new JsonParseException(e);
			}
		}

		protected abstract SourceState<?, ?>[] dependenciesFromIndices(int[] indices);

		protected abstract S makeState(
				final JsonObject map,
				final DataSource<?, ?> source,
				final Composite<ARGBType, ARGBType> composite,
				final C converter,
				String name,
				SourceState<?, ?>[] dependsOn,
				JsonDeserializationContext context) throws Exception;
	}

	public static abstract class SourceStateDeserializerWithoutDependencies<S extends SourceState<?, ?>, C extends
			Converter<?, ARGBType>>
			extends AbstractSourceStateDeserializer<S, C>
	{
		@Override
		protected SourceState<?, ?>[] dependenciesFromIndices(final int[] indices)
		{
			return new SourceState[] {};
		}
	}

	public static abstract class SourceStateDeserializerWithDependencies<S extends SourceState<?, ?>, C extends
			Converter<?, ARGBType>>
			extends AbstractSourceStateDeserializer<S, C>
	{

		private final IntFunction<SourceState<?, ?>> stateFromIndex;

		public SourceStateDeserializerWithDependencies(final IntFunction<SourceState<?, ?>> stateFromIndex)
		{
			super();
			this.stateFromIndex = stateFromIndex;
		}

		@Override
		protected SourceState<?, ?>[] dependenciesFromIndices(final int[] indices)
		{
			return Arrays.stream(indices).mapToObj(stateFromIndex).toArray(SourceState[]::new);
		}
	}

}
