package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import gnu.trove.set.hash.TLongHashSet;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.Predicate;

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class LabelSourceStateSerializer
		extends SourceStateSerialization.SourceStateSerializerWithoutDependencies<LabelSourceState<?, ?>>
	implements PainteraSerialization.PainteraSerializer<LabelSourceState<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String SELECTED_IDS_KEY = "selectedIds";

	public static final String ASSIGNMENT_KEY = "assignment";

	public static final String MANAGED_MESH_SETTINGS_KEY = "meshSettings";

	public static final String LABEL_BLOCK_MAPPING_KEY = "labelBlockMapping";

	public static final String LOCKED_SEGMENTS_KEY = "lockedSegments";

	public static final String ID_SERVICE_KEY = "idService";

	public static final String TYPE_KEY = "type";

	public static final String DATA_KEY = "data";

	@Override
	public JsonObject serialize(final LabelSourceState<?, ?> state, final Type type, final JsonSerializationContext
			context)
	{
		LOG.debug("Serializing state {}", state);
		final JsonObject map = super.serialize(state, type, context);
		map.add(SELECTED_IDS_KEY, context.serialize(state.selectedIds(), state.selectedIds().getClass()));
		map.add(ASSIGNMENT_KEY, SerializationHelpers.serializeWithClassInfo(state.assignment(), context));
		map.add(
				LabelSourceStateDeserializer.LOCKED_SEGMENTS_KEY,
				context.serialize(state.lockedSegments().lockedSegmentsCopy()));
		final ManagedMeshSettings managedMeshSettings = new ManagedMeshSettings(state.managedMeshSettings()
				.getGlobalSettings());
		managedMeshSettings.set(state.managedMeshSettings());
		final TLongHashSet activeSegments = new TLongHashSet(new SelectedSegments(
				state.selectedIds(),
				state.assignment()
		).getSelectedSegmentsCopyAsArray());
		final Predicate<Long> isSelected  = activeSegments::contains;
		final Predicate<Long> isManaged   = id -> managedMeshSettings.isManagedProperty(id).get();
		managedMeshSettings.keepOnlyMatching(isSelected.and(isManaged.negate()));
		map.add(MANAGED_MESH_SETTINGS_KEY, context.serialize(managedMeshSettings));
		LOG.debug("Serializing label block lookup: {}", state.labelBlockLookup());
		map.add(LABEL_BLOCK_MAPPING_KEY, context.serialize(state.labelBlockLookup(), LabelBlockLookup.class));
		LOG.debug("Serializing IdService {}", state.idService());
		map.add(ID_SERVICE_KEY, serializeWithClassInfo(state.idService(), context));
		return map;
	}

	@Override
	public Class<LabelSourceState<?, ?>> getTargetClass() {
		return (Class<LabelSourceState<?, ?>>) (Class<?>) LabelSourceState.class;
	}

	private static <T> JsonObject serializeWithClassInfo(final T t, final JsonSerializationContext context) {
		JsonObject map = new JsonObject();
		map.addProperty(TYPE_KEY, t.getClass().getName());
		map.add(DATA_KEY, context.serialize(t));
		return map;
	}
}
