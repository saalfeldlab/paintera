package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.serialization.FragmentSegmentAssignmentOnlyLocalSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		// TODO decouple this from n5!
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

		final N5DataSource<?, ?> n5Source = (N5DataSource) (isMaskedSource
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
		final IdService  idService     = getIdService(writer, dataset);
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

		LabelBlockLookup lookup = map.has(LabelSourceStateSerializer.LABEL_BLOCK_MAPPING_KEY)
				? deserializeFromClassInfo(map.getAsJsonObject(LabelSourceStateSerializer.LABEL_BLOCK_MAPPING_KEY), context)
				: getLabelBlockLookupFromN5IfPossible(isMaskedSource ? ((MaskedSource<?, ?>)source).underlyingSource() : source);

		InterruptibleFunction<Long, Interval[]>[] blockLoaders = IntStream
				.range(0, source.getNumMipmapLevels())
				.mapToObj(level -> InterruptibleFunction.fromFunction( MakeUnchecked.function((MakeUnchecked.CheckedFunction<Long, Interval[]>) id -> lookup.read(level, id))))
				.toArray(InterruptibleFunction[]::new);

		final MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
				(DataSource) source,
				selectedIds,
				assignment,
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

	private static IdService getIdService(final N5Writer writer, final String dataset) throws IOException {
		try {
			return N5Helpers.idService(writer, dataset);
		}
		catch (final N5Helpers.MaxIDNotSpecified e) {
			LOG.warn("Max id was not specified -- will not use an id service. " +
					"If that is not the intended behavior, please check the attributes of data set {}",
					dataset,
					e);
			return new IdService.IdServiceNotProvided();
		}

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
		} catch (N5Helpers.NotAPainteraDataset e) {
			return PainteraAlerts.getLabelBlockLookupFromDataSource(source);
		}
	}

}
