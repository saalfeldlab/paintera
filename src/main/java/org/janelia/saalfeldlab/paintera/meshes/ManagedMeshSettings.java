package org.janelia.saalfeldlab.paintera.meshes;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ManagedMeshSettings {

  public static final String MESH_SETTINGS_KEY = "meshSettings";

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final boolean DEFAULT_IS_MESH_LIST_ENABLED = false;

  public static final boolean DEFAULT_ARE_MESHES_ENABLED = true;

  private final MeshSettings globalSettings;

  private final Map<Long, MeshSettings> individualSettings = new HashMap<>();

  private final HashMap<Long, SimpleBooleanProperty> isManagedProperties = new HashMap<>();

  private final SimpleBooleanProperty isMeshListEnabled = new SimpleBooleanProperty(DEFAULT_IS_MESH_LIST_ENABLED);

  private final SimpleBooleanProperty meshesEnabled = new SimpleBooleanProperty(DEFAULT_ARE_MESHES_ENABLED);

  public ManagedMeshSettings(final int numScaleLevels) {

	this(new MeshSettings(numScaleLevels));
  }

  public ManagedMeshSettings(final MeshSettings globalSettings) {

	this.globalSettings = globalSettings;
  }

  public MeshSettings getOrAddMesh(final Long id, final boolean isManaged) {

	if (!isManagedProperties.containsKey(id)) {
	  final SimpleBooleanProperty isManagedProperty = new SimpleBooleanProperty(isManaged);
	  final MeshSettings settings = new MeshSettings(globalSettings.getNumScaleLevels());
	  isManagedProperty.addListener((obs, oldv, newv) -> {
		LOG.info("Managing settings for mesh id {}? {}", id, newv);
		bindBidirectionalToGlobalSettings(settings, newv);
	  });
	  bindBidirectionalToGlobalSettings(settings, isManaged);
	  isManagedProperties.put(id, isManagedProperty);
	  individualSettings.put(id, settings);
	}
	return individualSettings.get(id);
  }

  public MeshSettings getGlobalSettings() {

	return globalSettings;
  }

  public boolean isPresent(final Long t) {

	return this.isManagedProperties.containsKey(t);
  }

  public BooleanProperty isManagedProperty(final Long t) {

	return this.isManagedProperties.get(t);
  }

  public BooleanProperty isMeshListEnabledProperty() {

	return this.isMeshListEnabled;
  }

  public BooleanProperty getMeshesEnabledProperty() {

	return meshesEnabled;
  }

  public void clearSettings() {

	this.isManagedProperties.clear();
	this.individualSettings.clear();
  }

  public void keepOnlyMatching(final Predicate<Long> filter) {

	final Set<Long> toBeRemoved = isManagedProperties
			.keySet()
			.stream()
			.filter(filter.negate())
			.collect(Collectors.toSet());
	LOG.debug("Removing {}", toBeRemoved);
	toBeRemoved.forEach(this.isManagedProperties::remove);
	toBeRemoved.forEach(this.individualSettings::remove);
  }

  public static Serializer jsonSerializer() {

	return new Serializer();
  }

  public void set(final ManagedMeshSettings that) {

	clearSettings();
	globalSettings.setTo(that.globalSettings);
	isMeshListEnabled.set(that.isMeshListEnabled.get());
	meshesEnabled.set(that.meshesEnabled.get());
	for (final Entry<Long, MeshSettings> entry : that.individualSettings.entrySet()) {
	  final Long id = entry.getKey();
	  final boolean isManaged = that.isManagedProperties.get(id).get();
	  this.getOrAddMesh(id, isManaged);
	  if (!isManaged)
		this.individualSettings.get(id).setTo(entry.getValue());
	}
  }

  @Plugin(type = PainteraSerialization.PainteraAdapter.class)
  public static class Serializer implements PainteraSerialization.PainteraAdapter<ManagedMeshSettings> {

	private static final String GLOBAL_SETTINGS_KEY = "globalSettings";

	private static final String IS_MANAGED_KEY = "isManaged";

	private static final String IS_MESH_LIST_ENABLED_KEY = "isMeshListEnabled";

	private static final String SETTINGS_KEY = "settings";

	private static final String ID_KEY = "id";

	private static final String MESHES_ENABLED_KEY = "areMeshesEnabled";

	@Override
	public ManagedMeshSettings deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context)
			throws JsonParseException {

	  try {
		final JsonObject map = json.getAsJsonObject();
		final MeshSettings globalSettings = context.deserialize(
				map.get(GLOBAL_SETTINGS_KEY),
				MeshSettings.class);
		final boolean isMeshListEnabled = Optional
				.ofNullable(map.get(IS_MESH_LIST_ENABLED_KEY))
				.map(JsonElement::getAsBoolean)
				.orElse(DEFAULT_IS_MESH_LIST_ENABLED);
		final boolean areMeshesEnabled = Optional
				.ofNullable(map.get(MESHES_ENABLED_KEY))
				.map(JsonElement::getAsBoolean)
				.orElse(DEFAULT_ARE_MESHES_ENABLED);
		final ManagedMeshSettings managedSettings = new ManagedMeshSettings(globalSettings.getNumScaleLevels());
		managedSettings.globalSettings.setTo(globalSettings);
		managedSettings.isMeshListEnabled.set(isMeshListEnabled);
		managedSettings.meshesEnabled.set(areMeshesEnabled);
		final JsonArray meshSettingsList = Optional
				.ofNullable(map.get(MESH_SETTINGS_KEY))
				.map(JsonElement::getAsJsonArray)
				.orElseGet(JsonArray::new);
		for (int i = 0; i < meshSettingsList.size(); ++i) {
		  final JsonObject settingsMap = meshSettingsList.get(i).getAsJsonObject();
		  if (!settingsMap.has(ID_KEY)) {
			continue;
		  }
		  final Long id = context.deserialize(settingsMap.get(ID_KEY), Long.class);
		  final MeshSettings settings = Optional
				  .ofNullable(settingsMap.get(SETTINGS_KEY))
				  .map(el -> (MeshSettings)context.deserialize(el, MeshSettings.class))
				  .orElseGet(globalSettings::copy);
		  final boolean isManaged = Optional
				  .ofNullable(settingsMap.get(IS_MANAGED_KEY))
				  .map(JsonElement::getAsBoolean)
				  .orElse(true);
		  LOG.debug("{} is managed? {}", id, isManaged);
		  if (!isManaged)
			managedSettings.getOrAddMesh(id, false).setTo(settings);
		  else
			managedSettings.getOrAddMesh(id, true);
		}
		return managedSettings;
	  } catch (final Exception e) {
		throw e instanceof JsonParseException ? (JsonParseException)e : new JsonParseException(e);
	  }
	}

	@Override
	public JsonElement serialize(
			final ManagedMeshSettings src,
			final Type typeOfSrc,
			final JsonSerializationContext context) {

	  final JsonObject map = new JsonObject();
	  map.add(GLOBAL_SETTINGS_KEY, context.serialize(src.globalSettings));

	  if (DEFAULT_IS_MESH_LIST_ENABLED != src.isMeshListEnabledProperty().get())
		map.addProperty(IS_MESH_LIST_ENABLED_KEY, src.isMeshListEnabledProperty().get());

	  if (DEFAULT_ARE_MESHES_ENABLED != src.getMeshesEnabledProperty().get())
		map.addProperty(MESHES_ENABLED_KEY, src.getMeshesEnabledProperty().get());

	  final JsonArray meshSettingsList = new JsonArray();
	  for (final Entry<Long, MeshSettings> entry : src.individualSettings.entrySet()) {
		final Long id = entry.getKey();
		final Boolean isManaged = Optional.ofNullable(src.isManagedProperty(id)).map(BooleanProperty::get).orElse(true);
		if (!isManaged) {
		  final JsonObject settingsMap = new JsonObject();
		  settingsMap.addProperty(IS_MANAGED_KEY, false);
		  settingsMap.add(ID_KEY, context.serialize(id));
		  settingsMap.add(SETTINGS_KEY, context.serialize(entry.getValue()));
		  meshSettingsList.add(settingsMap);
		}
	  }
	  if (meshSettingsList.size() > 0)
		map.add(MESH_SETTINGS_KEY, meshSettingsList);
	  return map;
	}

	@Override
	public Class<ManagedMeshSettings> getTargetClass() {

	  return ManagedMeshSettings.class;
	}
  }

  private void bindBidirectionalToGlobalSettings(final MeshSettings settings, final boolean bind) {

	if (bind)
	  settings.bindBidirectionalTo(globalSettings);
	else
	  settings.unbindBidrectional(globalSettings);
  }

}
