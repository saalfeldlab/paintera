package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.imglib2.Interval;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.cache.BlocksForLabelFromFile;
import org.janelia.saalfeldlab.paintera.serialization.FragmentSegmentAssignmentOnlyLocalSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LabelSourceStateDeserializer<C extends HighlightingStreamConverter<?>>
		extends SourceStateSerialization.SourceStateDeserializerWithoutDependencies<LabelSourceState<?, ?>, C>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String SELECTED_IDS_KEY = "selectedIds";

	public static final String ASSIGNMENT_KEY = "assignment";

	public static final String FRAGMENTS_KEY = "fragments";

	public static final String SEGMENTS_KEY = "segments";

	public static final String LOCKED_SEGMENTS_KEY = "lockedSegments";

	private final Arguments arguments;

	public LabelSourceStateDeserializer(final Arguments arguments)
	{
		super();
		this.arguments = arguments;
	}

	public static class Factory<C extends HighlightingStreamConverter<?>>
			implements StatefulSerializer.Deserializer<LabelSourceState<?, ?>, LabelSourceStateDeserializer<C>>
	{

		@Override
		public LabelSourceStateDeserializer<C> createDeserializer(final Arguments arguments, final Supplier<String>
				projectDirectory, final IntFunction<SourceState<?, ?>> dependencyFromIndex)
		{
			return new LabelSourceStateDeserializer<>(arguments);
		}

	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	protected LabelSourceState<?, ?> makeState(
			final JsonObject map,
			final DataSource<?, ?> source,
			final Composite<ARGBType, ARGBType> composite,
			final C converter,
			final String name,
			final SourceState<?, ?>[] dependsOn,
			final JsonDeserializationContext context) throws IOException, ClassNotFoundException
	{
		final boolean isMaskedSource = source instanceof MaskedSource<?, ?>;
		LOG.debug("Is {} masked source? {}", source, isMaskedSource);
		if (isMaskedSource)
		{
			LOG.debug("Underlying source: {}", ((MaskedSource<?, ?>) source).underlyingSource());
		}

		if (isMaskedSource && !(((MaskedSource<?, ?>) source).underlyingSource() instanceof N5DataSource<?, ?>))
		{
			LOG.error("Underlying source is not n5! Returning null pointer!");
			return null;
		}

		if (!isMaskedSource && !(source instanceof N5DataSource<?, ?>))
		{
			LOG.error("Source is not n5! Returning null pointer!");
			return null;
		}

		final N5DataSource<?, ?> n5Source = (N5DataSource<?, ?>) (isMaskedSource
		                                                          ? ((MaskedSource<?, ?>) source).underlyingSource()
		                                                          : source);

		final N5Writer writer  = n5Source.writer();
		final String   dataset = n5Source.dataset();

		final SelectedIds selectedIds = context.deserialize(map.get(SELECTED_IDS_KEY), SelectedIds.class);
		final long[] locallyLockedSegments = Optional
				.ofNullable(map.get(LOCKED_SEGMENTS_KEY))
				.map(el -> (long[]) context.deserialize(el, long[].class))
				.orElseGet(() -> new long[] {});
		final JsonObject assignmentMap = map.get(ASSIGNMENT_KEY).getAsJsonObject();
		final IdService  idService     = N5Helpers.idService(writer, dataset);
		final FragmentSegmentAssignmentState assignment = N5Helpers.assignments(
				writer,
				dataset
		                                                                       );

		if (assignmentMap != null && assignmentMap.has(FragmentSegmentAssignmentOnlyLocalSerializer.ACTIONS_KEY))
		{
			final JsonArray              serializedActions = assignmentMap.get(
					FragmentSegmentAssignmentOnlyLocalSerializer.ACTIONS_KEY).getAsJsonArray();
			final List<AssignmentAction> actions           = new ArrayList<>();
			for (int i = 0; i < serializedActions.size(); ++i)
			{
				final JsonObject            entry  = serializedActions.get(i).getAsJsonObject();
				final AssignmentAction.Type type   = context.deserialize(entry.get(
						FragmentSegmentAssignmentOnlyLocalSerializer.TYPE_KEY), AssignmentAction.Type.class);
				final AssignmentAction      action = context.deserialize(entry.get(
						FragmentSegmentAssignmentOnlyLocalSerializer.DATA_KEY), type.getClassForType());
				actions.add(action);
			}
			assignment.apply(actions);
		}

		final LockedSegmentsOnlyLocal lockedSegments = new LockedSegmentsOnlyLocal(locked -> {
		}, locallyLockedSegments);

		final AbstractHighlightingARGBStream stream = converter.getStream();
		stream.setHighlightsAndAssignmentAndLockedSegments(selectedIds, assignment, lockedSegments);

		final JsonElement                               labelBlockMappers = map.get(LabelSourceStateSerializer
				.LABEL_BLOCK_MAPPING_KEY);
		final InterruptibleFunction<Long, Interval[]>[] blockLoaders;
		if (labelBlockMappers == null || !(labelBlockMappers instanceof JsonArray) || ((JsonArray) labelBlockMappers)
				.size() != source.getNumMipmapLevels())
		{
			blockLoaders = Stream.generate(() -> new BlocksForLabelFromFile(null)).limit(source.getNumMipmapLevels())
					.toArray(
					InterruptibleFunction[]::new);
		}
		else
		{
			blockLoaders = new InterruptibleFunction[source.getNumMipmapLevels()];
			final JsonArray labelBlockMappersArray = labelBlockMappers.getAsJsonArray();
			for (int level = 0; level < blockLoaders.length; ++level)
			{
				final String                                                   className = labelBlockMappersArray.get(
						level).getAsJsonObject().get(LabelSourceStateSerializer.TYPE_KEY).getAsString();
				final Class<? extends InterruptibleFunction<Long, Interval[]>> clazz     = (Class<? extends
						InterruptibleFunction<Long, Interval[]>>) Class.forName(
						className);
				final InterruptibleFunction<Long, Interval[]>                  mapper    = context.deserialize(
						labelBlockMappersArray.get(level).getAsJsonObject().get(LabelSourceStateSerializer.DATA_KEY),
						clazz
				                                                                                              );
				blockLoaders[level] = mapper;
			}
		}

		final LabelSourceState state = new LabelSourceState(
				source,
				converter,
				composite,
				name,
				assignment,
				lockedSegments,
				idService,
				selectedIds,
				arguments.meshesGroup,
				blockLoaders,
				arguments.meshManagerExecutors,
				arguments.meshWorkersExecutors
		);

		if (map.has(LabelSourceStateSerializer.MANAGED_MESH_SETTINGS_KEY))
		{
			final ManagedMeshSettings meshSettings = context.deserialize(
					map.get(LabelSourceStateSerializer.MANAGED_MESH_SETTINGS_KEY),
					ManagedMeshSettings.class
			                                                            );
			state.managedMeshSettings().set(meshSettings);
		}
		return state;

	}

}
