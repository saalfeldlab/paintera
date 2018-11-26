package org.janelia.saalfeldlab.paintera.serialization.assignments;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import javafx.util.Pair;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class FragmentSegmentAssignmentOnlyLocalSerializer implements PainteraSerialization.PainteraAdapter<FragmentSegmentAssignmentOnlyLocal>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String TYPE_KEY = "type";

	public static final String DATA_KEY = "data";

	public static final String ACTIONS_KEY = "actions";

	public static final String PERSISTER_KEY = "persister";

	@Override
	public JsonElement serialize(
			final FragmentSegmentAssignmentOnlyLocal src,
			final Type typeOfSrc,
			final JsonSerializationContext context)
	{
		final List<AssignmentAction> actions = src.events().stream().filter(p -> p.getValue().get()).map(Pair::getKey).collect(Collectors.toList());
		LOG.debug("Serializing actions {}", actions);
		final List<JsonElement> serializedActions = new ArrayList<>();
		for (final AssignmentAction action : actions)
		{
			final JsonObject serializedAction = new JsonObject();
			serializedAction.add(TYPE_KEY, context.serialize(action.getType()));
			serializedAction.add(DATA_KEY, context.serialize(action));
			serializedActions.add(serializedAction);
		}
		LOG.debug("Serialized actions {}", serializedActions);
		final JsonObject map = new JsonObject();
		map.add(ACTIONS_KEY, context.serialize(serializedActions));
		map.add(PERSISTER_KEY, SerializationHelpers.serializeWithClassInfo(src.getPersister(), context));
		return map;
	}

	@Override
	public FragmentSegmentAssignmentOnlyLocal deserialize(JsonElement jsonElement, Type clazz, JsonDeserializationContext context) throws JsonParseException {

		LOG.debug("Deserializing from {}", jsonElement);
		try {

			if (!(jsonElement instanceof JsonObject) || !jsonElement.getAsJsonObject().has(PERSISTER_KEY))
				throw new NoPersisterFound(jsonElement);

			final JsonObject map = jsonElement.getAsJsonObject();
			final FragmentSegmentAssignmentOnlyLocal.Persister persister = SerializationHelpers.deserializeFromClassInfo(map.get(PERSISTER_KEY).getAsJsonObject(), context);
			final FragmentSegmentAssignmentOnlyLocal assignment = new FragmentSegmentAssignmentOnlyLocal(persister);

			if (map.has(ACTIONS_KEY)) {
				final JsonArray serializedActions = map.get(FragmentSegmentAssignmentOnlyLocalSerializer.ACTIONS_KEY).getAsJsonArray();
				final List<AssignmentAction> actions = new ArrayList<>();
				for (int i = 0; i < serializedActions.size(); ++i) {
					final JsonObject entry = serializedActions.get(i).getAsJsonObject();
					final AssignmentAction.Type type = context.deserialize(entry.get(FragmentSegmentAssignmentOnlyLocalSerializer.TYPE_KEY), AssignmentAction.Type.class);
					final AssignmentAction action = context.deserialize(entry.get(FragmentSegmentAssignmentOnlyLocalSerializer.DATA_KEY), type.getClassForType());
					actions.add(action);
				}
				assignment.apply(actions);
			}
			return assignment;
		} catch (ClassNotFoundException e) {
			throw new JsonParseException(e);
		}
	}


	@Override
	public Class<FragmentSegmentAssignmentOnlyLocal> getTargetClass() {
		return FragmentSegmentAssignmentOnlyLocal.class;
	}

	public static class NoPersisterFound extends JsonParseException {

		private final JsonElement el;

		public NoPersisterFound(final JsonElement el) {
			super("No Persister found in JsonElement " + (el == null ? null : el.getAsString()));
			this.el = el;
		}

		public JsonElement getJsonElement() {
			return el == null ? null : el.deepCopy();
		}

	}
}
