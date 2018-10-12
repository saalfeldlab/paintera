package org.janelia.saalfeldlab.paintera.cache;

import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.plugin.SciJavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongFunction;

public interface DiscoverableMemoryUsage<V> extends ToLongFunction<V>, SciJavaPlugin {

	Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	List<DiscoverableMemoryUsage> DISCOVERED_MEMORY_USAGE = Collections.unmodifiableList(MakeUnchecked.supplier(DiscoverableMemoryUsage::discoverAll).get());

	boolean isApplicable(Object object);

	static ToLongFunction<?> memoryUsageFromDiscoveredFunctions()
	{
		return object -> {
			for (DiscoverableMemoryUsage memoryUsage : DISCOVERED_MEMORY_USAGE)
				if (memoryUsage.isApplicable(object))
					return memoryUsage.applyAsLong(object);
			throw new RuntimeException("Cannot calculate memory usage for object of class " + object.getClass());
		};
	}

	static List<DiscoverableMemoryUsage> discoverAll() throws InstantiableException {
		List<DiscoverableMemoryUsage> list = new ArrayList<>();
		final Context context = new Context(PluginService.class);

		final PluginService                         pluginService = context.getService(PluginService.class);
		final List<PluginInfo<DiscoverableMemoryUsage>> infos     = pluginService.getPluginsOfType(DiscoverableMemoryUsage.class);
		Collections.sort(infos, (i1, i2) -> -Double.compare(i1.getPriority(), i2.getPriority()));
		for (PluginInfo<DiscoverableMemoryUsage> info : infos)
			list.add(info.createInstance());
		return list;
	}

}
