package org.janelia.saalfeldlab.paintera.meshes.io;

import javafx.scene.shape.TriangleMesh;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.scijava.plugin.DefaultPluginService;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.plugin.SciJavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TriangleMeshFormat extends SciJavaPlugin {

	class FormatFinder {

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private static List<TriangleMeshFormat> meshFormats = new ArrayList<>();

		private static Map<String, List<TriangleMeshFormat>> extensionToFormatMapping = new HashMap<>();

		static {
			final Context context = new Context();
			final PluginService pluginService = context.getService(PluginService.class);
			final List<PluginInfo<TriangleMeshFormat>> pluginInfos = pluginService.getPluginsOfType(TriangleMeshFormat.class);
			for (PluginInfo<TriangleMeshFormat> pluginInfo : pluginInfos) {
				try {
					final TriangleMeshFormat instance = pluginInfo.createInstance();
					meshFormats.add(instance);
					for (final String ext : instance.knownExtensions())
						extensionToFormatMapping.computeIfAbsent(ext, k -> new ArrayList<>()).add(instance);
				} catch (InstantiableException e) {
					LOG.error("Unable to instantiate plugin {}", pluginInfo, e);
				}
			}
		}

	}

	String formatName();

	Set<String> knownExtensions();

	TriangleMeshWriter getWriter();

	TriangleMeshLoader getLoader();

	static List<TriangleMeshFormat> availableFormats() {
		return Collections.unmodifiableList(FormatFinder.meshFormats);
	}

	static Map<String, List<TriangleMeshFormat>> extensionsToFormatMapping() {
		return Collections.unmodifiableMap(FormatFinder.extensionToFormatMapping);
	}
}
