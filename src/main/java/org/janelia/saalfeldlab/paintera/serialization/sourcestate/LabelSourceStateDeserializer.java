package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.serialization.assignments.FragmentSegmentAssignmentOnlyLocalSerializer;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.imglib2.Interval;
import net.imglib2.type.numeric.ARGBType;
import pl.touk.throwing.ThrowingFunction;

public class LabelSourceStateDeserializer<C extends HighlightingStreamConverter<?>>
		extends SourceStateSerialization.SourceStateDeserializerWithoutDependencies<LabelSourceState<?, ?>, C>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String SELECTED_IDS_KEY = LabelSourceStateSerializer.SELECTED_IDS_KEY;

	public static final String ASSIGNMENT_KEY = LabelSourceStateSerializer.ASSIGNMENT_KEY;

	public static final String LOCKED_SEGMENTS_KEY = LabelSourceStateSerializer.LOCKED_SEGMENTS_KEY;

	private final Arguments arguments;

	public LabelSourceStateDeserializer(final Arguments arguments)
	{
		super();
		this.arguments = arguments;
	}

	@Plugin(type = StatefulSerializer.DeserializerFactory.class)
	public static class Factory<C extends HighlightingStreamConverter<?>>
			implements StatefulSerializer.DeserializerFactory<LabelSourceState<?, ?>, LabelSourceStateDeserializer<C>>
	{

		@Override
		public LabelSourceStateDeserializer<C> createDeserializer(final Arguments arguments, final Supplier<String>
				projectDirectory, final IntFunction<SourceState<?, ?>> dependencyFromIndex)
		{
			return new LabelSourceStateDeserializer<>(arguments);
		}

		@Override
		public Class<LabelSourceState<?, ?>> getTargetClass() {
			return (Class<LabelSourceState<?, ?>>) (Class<?>) LabelSourceState.class;
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
			final JsonDeserializationContext context) throws IOException, ClassNotFoundException, ReflectionException {
		final boolean isMaskedSource = source instanceof MaskedSource<?, ?>;
		LOG.debug("Is {} masked source? {}", source, isMaskedSource);
		if (isMaskedSource)
		{
			LOG.debug("Underlying source: {}", ((MaskedSource<?, ?>) source).underlyingSource());
		}

		final SelectedIds selectedIds = context.deserialize(map.get(SELECTED_IDS_KEY), SelectedIds.class);
		final long[] locallyLockedSegments = Optional
				.ofNullable(map.get(LOCKED_SEGMENTS_KEY))
				.map(el -> (long[]) context.deserialize(el, long[].class))
				.orElseGet(() -> new long[] {});

		final JsonObject assignmentMap                  = map.get(ASSIGNMENT_KEY).getAsJsonObject();
		final FragmentSegmentAssignmentState assignment = tryDeserializeOrFallBackToN5(assignmentMap, context, source);

		final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);

		final JsonObject idServiceMap = map.has(LabelSourceStateSerializer.ID_SERVICE_KEY)
				? map.get(LabelSourceStateSerializer.ID_SERVICE_KEY).getAsJsonObject()
				: null;
		final IdService idService     = tryDeserializeIdServiceOrFallBacktoN5(idServiceMap, context, source);

		final LockedSegmentsOnlyLocal lockedSegments = new LockedSegmentsOnlyLocal(locked -> {}, locallyLockedSegments);

		final AbstractHighlightingARGBStream stream = converter.getStream();
		stream.setSelectedAndLockedSegments(
				selectedSegments, lockedSegments);

		LOG.debug("Deserializing lookup from map {} with key {}", map, LabelSourceStateSerializer.LABEL_BLOCK_MAPPING_KEY);
		final LabelBlockLookup lookup = map.has(LabelSourceStateSerializer.LABEL_BLOCK_MAPPING_KEY)
				? context.deserialize(map.get(LabelSourceStateSerializer.LABEL_BLOCK_MAPPING_KEY), LabelBlockLookup.class)
				: getLabelBlockLookupFromN5IfPossible(isMaskedSource ? ((MaskedSource<?, ?>)source).underlyingSource() : source);

		final InterruptibleFunction<Long, Interval[]>[] blockLoaders = IntStream
				.range(0, source.getNumMipmapLevels())
				.mapToObj(level -> InterruptibleFunction.fromFunction( ThrowingFunction.unchecked((ThrowingFunction<Long, Interval[], Exception>) id -> lookup.read(level, id))))
				.toArray(InterruptibleFunction[]::new);

		final MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
				(DataSource) source,
				selectedSegments,
				stream,
				arguments.meshesGroup,
				blockLoaders,
				arguments.globalCache::createNewCache,
				arguments.meshManagerExecutors,
				arguments.meshWorkersExecutors
		);

		LOG.debug("Creating state with converter {}", converter);
		final LabelSourceState state = new LabelSourceState(
				source,
				converter,
				composite,
				name,
				assignment,
				lockedSegments,
				idService,
				selectedIds,
				meshManager,
				lookup
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

	private static IdService tryDeserializeIdServiceOrFallBacktoN5(
			final JsonObject serializedIdService,
			final JsonDeserializationContext context,
			final DataSource<?, ?> source) {
		try {
			final IdService idService = SerializationHelpers.deserializeFromClassInfo(serializedIdService, context);
			LOG.debug("Successfully deserialized IdService: {}", idService);
			return idService;
		} catch (ClassNotFoundException | NullPointerException e) {

			try {
				LOG.debug("Caught exception when trying to deserialize IdService", e);
				LOG.warn("Trying to load IdService with legacy loader, assuming the data source is N5. " +
						"If successfully loaded, this will not be necessary anymore after you save the project.");
				final IdService service = idServiceN5FallBack((N5DataSource<?, ?>) getUnderlyingSource(source));
				LOG.warn("Successfully loaded IdService with legacy loader, assuming the data source is N5. " +
						"This will not be necessary anymore after you save the project.");
				return service;
			} catch (final Exception ex) {
				// catch any exception and log here.
				LOG.error("Unable to load IdService with legacy loader.", e);
				throw new JsonParseException(e);
			}
		}
	}

	private static IdService idServiceN5FallBack(final N5DataSource<?, ?> source) throws IOException, N5Helpers.MaxIDNotSpecified {
		return idServiceN5FallBack(source.writer(), source.dataset());
	}

	private static IdService idServiceN5FallBack(final N5Writer writer, final String dataset) throws IOException, N5Helpers.MaxIDNotSpecified {
		return N5Helpers.idService(writer, dataset);
	}

	private static <T> T deserializeFromClassInfo(final JsonObject map, final JsonDeserializationContext context) throws ClassNotFoundException {

		final Class<T> clazz = (Class<T>) Class.forName(map.get(LabelSourceStateSerializer.TYPE_KEY).getAsString());
		return context.deserialize(map.get(LabelSourceStateSerializer.DATA_KEY), clazz);
	}

	private static LabelBlockLookup getLabelBlockLookupFromN5IfPossible(final DataSource<?, ?> source) throws IOException {
		return source instanceof N5DataSource<?, ?>
				? getLabelBlockLookupFromN5((N5DataSource<?, ?>) source)
				: PainteraAlerts.getLabelBlockLookupFromDataSource(source);
	}

	private static LabelBlockLookup getLabelBlockLookupFromN5(final N5DataSource<?, ?> source) throws IOException {
		try {
			return N5Helpers.getLabelBlockLookup(source.writer(), source.dataset());
		} catch (final N5Helpers.NotAPainteraDataset e) {
			return PainteraAlerts.getLabelBlockLookupFromDataSource(source);
		}
	}

	private static FragmentSegmentAssignmentState tryDeserializeOrFallBackToN5(
			final JsonObject assignmentMap,
			final JsonDeserializationContext context,
			final DataSource<?, ?> source
	) throws ClassNotFoundException {
		try {
			LOG.debug("Deserializing {} from {}", FragmentSegmentAssignmentState.class.getName(), assignmentMap);
			return SerializationHelpers.deserializeFromClassInfo(assignmentMap, context);
		} catch (final FragmentSegmentAssignmentOnlyLocalSerializer.NoPersisterFound
				| FragmentSegmentAssignmentOnlyLocalSerializer.NoInitialLutFound
				| NullPointerException e) {
			LOG.debug("Caught exception when trying to deserialize assignment", e);
			LOG.warn("Trying to load fragment-segment-assignment with legacy loader, assuming the underlying persister is N5. " +
					"If successfully loaded, this will not be necessary anymore after you save the project.");
			try {
				final N5DataSource<?, ?> n5Source = (N5DataSource<?, ?>) getUnderlyingSource(source);
				final FragmentSegmentAssignmentState assignment = N5Helpers.assignments(n5Source.writer(), n5Source.dataset());

				if (assignmentMap != null && assignmentMap.has(FragmentSegmentAssignmentOnlyLocalSerializer.ACTIONS_KEY)) {
					final JsonArray serializedActions = assignmentMap.get(
							FragmentSegmentAssignmentOnlyLocalSerializer.ACTIONS_KEY).getAsJsonArray();
					final List<AssignmentAction> actions = new ArrayList<>();
					for (int i = 0; i < serializedActions.size(); ++i) {
						final JsonObject entry = serializedActions.get(i).getAsJsonObject();
						final AssignmentAction.Type type = context.deserialize(entry.get(
								FragmentSegmentAssignmentOnlyLocalSerializer.TYPE_KEY), AssignmentAction.Type.class);
						final AssignmentAction action = context.deserialize(entry.get(
								FragmentSegmentAssignmentOnlyLocalSerializer.DATA_KEY), type.getClassForType());
						actions.add(action);
					}
					assignment.apply(actions);
				}
				LOG.warn("Successfully loaded fragment-segment-assignment with legacy loader, assuming the underlying persister is N5. " +
						"This will not be necessary anymore after you save the project.");
				return assignment;
			}
			catch (final IOException ioEx) {
				throw new JsonParseException(ioEx);
			}
		}
	}

	private static DataSource<?, ?> getUnderlyingSource(final DataSource<?, ?> source) {
		final boolean isMaskedSource = source instanceof MaskedSource<?, ?>;
		return isMaskedSource
				? ((MaskedSource<?, ?>) source).underlyingSource()
				: source;
	}

}
