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
import java.util.concurrent.ConcurrentHashMap;

public class ManagedMeshSettings<K> {

	public static final String MESH_SETTINGS_KEY = "meshSettings";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final boolean DEFAULT_ARE_MESHES_ENABLED = true;

	private final MeshSettings globalSettings;

	private final Map<K, MeshSettings> individualSettings = new HashMap<>();

	private final ConcurrentHashMap<K, SimpleBooleanProperty> managedPropertyMap = new ConcurrentHashMap<>();

	private final SimpleBooleanProperty meshesEnabled = new SimpleBooleanProperty(DEFAULT_ARE_MESHES_ENABLED);

	public ManagedMeshSettings(final int numScaleLevels) {

		this(new MeshSettings(numScaleLevels));
	}

	public ManagedMeshSettings(final MeshSettings globalSettings) {

		this.globalSettings = globalSettings;
	}

	/**
	 * Retrieves a MeshSettings object for the given id.
	 * By default, the mesh settings are bound to the global settings.
	 * If `manageIndividually` is true, the mesh settings are unbound.
	 * <p>
	 * The resulting MeshSettings will be added to the `individualSettings` for this `id`, either `manageIndividually` or not.
	 *
	 * @param id        The id of the mesh to retrieve the settings for.
	 * @param manageIndividually True if the individual mesh settings should be used.
	 * @return The mesh settings for the given id, or a new MeshSettings object if the id is not found.
	 */
	public MeshSettings getMeshSettings(final K id, final boolean manageIndividually) {

		managedPropertyMap.compute(id, (k,v) -> {
			if (v != null)
				return v;

			final MeshSettings settings = new MeshSettings(globalSettings.getNumScaleLevels());
			individualSettings.put(id, settings);

			final SimpleBooleanProperty manageIndividuallyProp = new SimpleBooleanProperty(manageIndividually);
			manageIndividuallyProp.subscribe(useIndividualSettings -> {
				LOG.debug("Managing settings for mesh id {}? {}", id, useIndividualSettings);
				if (useIndividualSettings)
					settings.unbind();
				else
					settings.bind(globalSettings);
			});

			return manageIndividuallyProp;
		});

		return individualSettings.get(id);
	}

	/**
	 * Retrieves a MeshSettings for the given key.
	 * If the mesh settings are managed, then the managed settings will be returned.
	 * Otherwise, a new MeshSettings object will be created based on global settings.
	 *
	 * @param meshKey the key of the mesh to retrieve the settings for
	 * @return the mesh settings for the given mesh key, or a new MeshSettings object if the key is not found
	 */
	public MeshSettings getMeshSettings(final K meshKey) {

		final var isManagedProperty = managedPropertyMap.get(meshKey);
		final boolean manageIndividually = isManagedProperty != null && isManagedProperty.get();
		return getMeshSettings(meshKey, manageIndividually);
	}

	public MeshSettings getGlobalSettings() {

		return globalSettings;
	}

	public BooleanProperty managedIndividuallyProperty(final K t) {

		return this.managedPropertyMap.get(t);
	}

	public BooleanProperty getMeshesEnabledProperty() {

		return meshesEnabled;
	}

	public void clearSettings() {

		this.managedPropertyMap.clear();
		this.individualSettings.clear();
	}

	public void set(final ManagedMeshSettings<K> that) {

		clearSettings();
		globalSettings.setTo(that.globalSettings);
		meshesEnabled.set(that.meshesEnabled.get());
		for (final Entry<K, MeshSettings> entry : that.individualSettings.entrySet()) {
			final K id = entry.getKey();
			final boolean managedIndividually = that.managedPropertyMap.get(id).get();
			this.getMeshSettings(id, managedIndividually);
			if (managedIndividually)
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
				final boolean areMeshesEnabled = Optional
						.ofNullable(map.get(MESHES_ENABLED_KEY))
						.map(JsonElement::getAsBoolean)
						.orElse(DEFAULT_ARE_MESHES_ENABLED);
				final ManagedMeshSettings managedSettings = new ManagedMeshSettings(globalSettings.getNumScaleLevels());
				managedSettings.globalSettings.setTo(globalSettings);
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
					//TODO Caleb: This needs to store and deserialize based on generic key type, not just Long
					//  Necessary to share logic for meshes across segment and virtual sources
					final Long id = context.deserialize(settingsMap.get(ID_KEY), Long.class);
					final MeshSettings settings = Optional
							.ofNullable(settingsMap.get(SETTINGS_KEY))
							.map(el -> (MeshSettings) context.deserialize(el, MeshSettings.class))
							.orElseGet(globalSettings::copy);
					JsonElement isManaged = settingsMap.get(IS_MANAGED_KEY);
					final boolean manageMeshIndividually = Optional
							.ofNullable(isManaged)
							.map(JsonElement::getAsBoolean)
							.orElse(true);
					LOG.debug("{} is managed? {}", id, manageMeshIndividually);
					var meshSettings = managedSettings.getMeshSettings(id, manageMeshIndividually);
					if (manageMeshIndividually)
						meshSettings.setTo(settings);
				}
				return managedSettings;
			} catch (final Exception e) {
				throw e instanceof JsonParseException ? (JsonParseException) e : new JsonParseException(e);
			}
		}

		@Override
		public JsonElement serialize(
				final ManagedMeshSettings src,
				final Type typeOfSrc,
				final JsonSerializationContext context) {

			//TODO Caleb: This also needs to serialize the generic type, instead of casting to Object
			ManagedMeshSettings<Object> managedMeshSettings = (ManagedMeshSettings<Object>) src;

			final JsonObject map = new JsonObject();
			map.add(GLOBAL_SETTINGS_KEY, context.serialize(managedMeshSettings.globalSettings));

			if (DEFAULT_ARE_MESHES_ENABLED != managedMeshSettings.getMeshesEnabledProperty().get())
				map.addProperty(MESHES_ENABLED_KEY, managedMeshSettings.getMeshesEnabledProperty().get());

			final JsonArray meshSettingsList = new JsonArray();
			for (final Entry<?, MeshSettings> entry : managedMeshSettings.individualSettings.entrySet()) {
				final Object id = entry.getKey();
				final Boolean isManaged = Optional.ofNullable(managedMeshSettings.managedIndividuallyProperty(id)).map(BooleanProperty::get).orElse(true);
				if (isManaged) {
					final JsonObject settingsMap = new JsonObject();
					settingsMap.addProperty(IS_MANAGED_KEY, true);
					settingsMap.add(ID_KEY, context.serialize(id));
					settingsMap.add(SETTINGS_KEY, context.serialize(entry.getValue()));
					meshSettingsList.add(settingsMap);
				}
			}
			if (!meshSettingsList.isEmpty())
				map.add(MESH_SETTINGS_KEY, meshSettingsList);
			return map;
		}

		@Override
		public Class<ManagedMeshSettings> getTargetClass() {

			return ManagedMeshSettings.class;
		}
	}
}
