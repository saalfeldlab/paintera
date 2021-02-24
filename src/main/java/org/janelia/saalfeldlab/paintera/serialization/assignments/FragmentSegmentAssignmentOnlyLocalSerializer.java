package org.janelia.saalfeldlab.paintera.serialization.assignments;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import gnu.trove.map.TLongLongMap;
import javafx.util.Pair;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class FragmentSegmentAssignmentOnlyLocalSerializer implements PainteraSerialization.PainteraAdapter<FragmentSegmentAssignmentOnlyLocal> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String TYPE_KEY = "type";

  public static final String DATA_KEY = "data";

  public static final String ACTIONS_KEY = "actions";

  public static final String PERSISTER_KEY = "persister";

  public static final String INITIAL_LUT_KEY = "initialLut";

  @Override
  public JsonElement serialize(
		  final FragmentSegmentAssignmentOnlyLocal src,
		  final Type typeOfSrc,
		  final JsonSerializationContext context) {

	final List<AssignmentAction> actions = src.events().stream().filter(p -> p.getValue().get()).map(Pair::getKey).collect(Collectors.toList());
	LOG.debug("Serializing actions {}", actions);
	final List<JsonElement> serializedActions = new ArrayList<>();
	for (final AssignmentAction action : actions) {
	  final JsonObject serializedAction = new JsonObject();
	  serializedAction.add(TYPE_KEY, context.serialize(action.getType()));
	  serializedAction.add(DATA_KEY, context.serialize(action));
	  serializedActions.add(serializedAction);
	}
	LOG.debug("Serialized actions {}", serializedActions);
	final JsonObject map = new JsonObject();
	map.add(ACTIONS_KEY, context.serialize(serializedActions));
	map.add(PERSISTER_KEY, SerializationHelpers.serializeWithClassInfo(src.getPersister(), context));
	map.add(INITIAL_LUT_KEY, SerializationHelpers.serializeWithClassInfo(src.getInitialLutSupplier(), context));
	return map;
  }

  @Override
  public FragmentSegmentAssignmentOnlyLocal deserialize(JsonElement jsonElement, Type clazz, JsonDeserializationContext context) throws JsonParseException {

	LOG.debug("Deserializing from {}", jsonElement);
	try {

	  if (!(jsonElement instanceof JsonObject))
		throw new JsonParseException(String.format("Expected instanceof %s but got %s", JsonObject.class, jsonElement));
	  final JsonObject map = jsonElement.getAsJsonObject();

	  if (!map.has(PERSISTER_KEY))
		throw new NoPersisterFound(map);

	  if (!map.has(INITIAL_LUT_KEY))
		throw new NoInitialLutFound(map);

	  final FragmentSegmentAssignmentOnlyLocal.Persister persister = SerializationHelpers
			  .deserializeFromClassInfo(map.get(PERSISTER_KEY).getAsJsonObject(), context);
	  final FragmentSegmentAssignmentOnlyLocal assignment = new FragmentSegmentAssignmentOnlyLocal(
			  tryDeserializeInitialLutSupplier(map.getAsJsonObject(INITIAL_LUT_KEY), context), persister);

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

	private final JsonObject el;

	public NoPersisterFound(final JsonObject el) {

	  super(String.format("No Persister found for key %s in JsonObject %s", PERSISTER_KEY, el));
	  this.el = el;
	}

	public JsonElement getJsonElement() {

	  return el == null ? null : el.deepCopy();
	}

  }

  public static class NoInitialLutFound extends JsonParseException {

	private final JsonObject el;

	public NoInitialLutFound(final JsonObject el) {

	  super(String.format("No initial lut found for key %s in JsonObject %s", INITIAL_LUT_KEY, el));
	  this.el = el;
	}

	public JsonElement getJsonElement() {

	  return el == null ? null : el.deepCopy();
	}

  }

  private static Supplier<TLongLongMap> tryDeserializeInitialLutSupplier(
		  final JsonObject map,
		  final JsonDeserializationContext context) {

	try {
	  return SerializationHelpers.deserializeFromClassInfo(map, context);
	} catch (ClassNotFoundException e) {
	  throw new JsonParseException("Unable to deserialize initial lut supplier", e);
	}
  }
}
