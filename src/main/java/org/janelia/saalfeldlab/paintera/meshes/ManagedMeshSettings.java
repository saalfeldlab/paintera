package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagedMeshSettings
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final MeshSettings globalSettings;

	private final ObservableMap<Long, MeshSettings> settings = FXCollections.observableHashMap();

	private final HashMap<Long, SimpleBooleanProperty> isManagedProperties = new HashMap<>();

	public ManagedMeshSettings(final int numScaleLevels)
	{
		this(new MeshSettings(numScaleLevels));
	}

	public ManagedMeshSettings(final MeshSettings globalSettings)
	{
		this.globalSettings = globalSettings;
		settings.addListener((MapChangeListener<Long, MeshSettings>) change -> {
			if (change.wasAdded())
			{
				LOG.debug("Adding change {}", change);
				this.isManagedProperties.putIfAbsent(change.getKey(), new SimpleBooleanProperty(true));
				LOG.debug("is {} managed? {}", change.getKey(), this.isManagedProperties.get(change.getKey()));
			}
			else if (change.wasRemoved())
			{
				LOG.debug("Removing change {}", change);
				this.isManagedProperties.remove(change.getKey());
			}
		});
	}

	public MeshSettings getOrAddMesh(final Long id)
	{
		return settings.computeIfAbsent(id, key -> globalSettings.copy());
	}

	public MeshSettings getGlobalSettings()
	{
		return globalSettings;
	}

	public boolean isPresent(final Long t)
	{
		return this.settings.containsKey(t);
	}

	public BooleanProperty isManagedProperty(final Long t)
	{
		return this.isManagedProperties.get(t);
	}

	public void clearSettings()
	{
		this.settings.clear();
	}

	public void keepOnlyMatching(final Predicate<Long> filter)
	{
		final Set<Long> toBeRemoved = settings
				.keySet()
				.stream()
				.filter(filter.negate())
				.collect(Collectors.toSet());
		LOG.debug("Removing {}", toBeRemoved);
		toBeRemoved.forEach(this.settings::remove);
	}

	public static Serializer jsonSerializer()
	{
		return new Serializer();
	}

	public void set(final ManagedMeshSettings that)
	{
		clearSettings();
		globalSettings.set(that.globalSettings);
		for (final Entry<Long, MeshSettings> entry : that.settings.entrySet())
		{
			final Long id = entry.getKey();
			this.settings.put(id, entry.getValue().copy());
			this.isManagedProperties.computeIfAbsent(
					id,
					key -> new SimpleBooleanProperty()
			                                        ).set(that.isManagedProperties.get(id).get());
		}
	}

	private static class Serializer implements
	                                JsonSerializer<ManagedMeshSettings>,
	                                JsonDeserializer<ManagedMeshSettings>
	{

		private static final String GLOBAL_SETTINGS_KEY = "globalSettings";

		private static final String MESH_SETTINGS_KEY = "meshSettings";

		private static final String IS_MANAGED_KEY = "isManaged";

		private static final String SETTINGS_KEY = "settings";

		private static final String ID_KEY = "id";

		@Override
		public ManagedMeshSettings deserialize(final JsonElement json, final Type typeOfT, final
		JsonDeserializationContext context)
		throws JsonParseException
		{
			try
			{
				final JsonObject          map             = json.getAsJsonObject();
				final MeshSettings        globalSettings  = context.deserialize(
						map.get(GLOBAL_SETTINGS_KEY),
						MeshSettings.class
				                                                               );
				final ManagedMeshSettings managedSettings = new ManagedMeshSettings(globalSettings.numScaleLevels());
				managedSettings.globalSettings.set(globalSettings);
				final JsonArray meshSettingsList = Optional
						.ofNullable(map.get(MESH_SETTINGS_KEY))
						.map(JsonElement::getAsJsonArray)
						.orElseGet(JsonArray::new);
				for (int i = 0; i < meshSettingsList.size(); ++i)
				{
					final JsonObject settingsMap = meshSettingsList.get(i).getAsJsonObject();
					if (!settingsMap.has(ID_KEY))
					{
						continue;
					}
					final Long id = context.deserialize(settingsMap.get(ID_KEY), Long.class);
					final MeshSettings settings = Optional
							.ofNullable(settingsMap.get(SETTINGS_KEY))
							.map(el -> (MeshSettings) context.deserialize(el, MeshSettings.class))
							.orElseGet(globalSettings::copy);
					final boolean isManaged = Optional
							.ofNullable(settingsMap.get(IS_MANAGED_KEY))
							.map(JsonElement::getAsBoolean)
							.orElse(true);
					LOG.debug("{} is managed? {}", id, isManaged);
					managedSettings.isManagedProperties.computeIfAbsent(id, key -> new SimpleBooleanProperty()).set(
							isManaged);
					managedSettings.settings.put(id, settings.copy());
					if (!isManaged)
					{
						managedSettings.settings.get(id).set(settings);
					}
				}
				return managedSettings;
			} catch (final Exception e)
			{
				throw e instanceof JsonParseException ? (JsonParseException) e : new JsonParseException(e);
			}
		}

		@Override
		public JsonElement serialize(final ManagedMeshSettings src, final Type typeOfSrc, final
		JsonSerializationContext context)
		{
			final JsonObject map = new JsonObject();
			map.add(GLOBAL_SETTINGS_KEY, context.serialize(src.globalSettings));
			final JsonArray meshSettingsList = new JsonArray();
			for (final Entry<Long, MeshSettings> entry : src.settings.entrySet())
			{
				final Long       id          = entry.getKey();
				final JsonObject settingsMap = new JsonObject();
				settingsMap.add(ID_KEY, context.serialize(id));
				final Boolean isManaged = Optional.ofNullable(src.isManagedProperty(id)).map(BooleanProperty::get)
						.orElse(
						true);
				settingsMap.addProperty(IS_MANAGED_KEY, isManaged);
				if (!isManaged)
				{
					settingsMap.add(SETTINGS_KEY, context.serialize(entry.getValue()));
				}
				meshSettingsList.add(settingsMap);
			}
			map.add(MESH_SETTINGS_KEY, meshSettingsList);
			return map;
		}

	}

}
