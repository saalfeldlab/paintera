package org.janelia.saalfeldlab.util;

import javafx.util.Pair;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.plugin.SciJavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

// TODO make service for this
public class SciJavaUtils {

  public interface HasTargetClass<T> {

	Class<T> getTargetClass();
  }

  public static <T> void sortByPriority(final List<T> list, ToDoubleFunction<T> getPriority) {

	list.sort((v1, v2) -> Double.compare(getPriority.applyAsDouble(v2), getPriority.applyAsDouble(v1)));
  }

  public static void sortByPriority(final List<? extends Pair<?, Double>> list) {

	sortByPriority(list, Pair::getValue);
  }

  public static <T extends SciJavaPlugin & HasTargetClass<?>> Map<Class<?>, List<Pair<T, Double>>> byTargetClassSortedByPriorities(
		  final Class<T> clazz,
		  final Context context
  ) throws InstantiableException {

	return byTargetClassSortedByPriorities(getAllMatchingPluginInfos(clazz, context));
  }

  public static <T extends SciJavaPlugin & HasTargetClass<?>> Map<Class<?>, List<Pair<T, Double>>> byTargetClassSortedByPriorities(
		  final List<? extends PluginInfo<T>> pluginInfos
  ) throws InstantiableException {

	final Map<Class<?>, List<Pair<T, Double>>> byClassWithPriorities = byTargetClassWithPriorities(pluginInfos);
	byClassWithPriorities.values().forEach(SciJavaUtils::sortByPriority);
	return byClassWithPriorities;
  }

  public static <T extends SciJavaPlugin & HasTargetClass<?>> Map<Class<?>, List<Pair<T, Double>>> byTargetClassWithPriorities(
		  final List<? extends PluginInfo<T>> pluginInfos
  ) throws InstantiableException {

	final Map<Class<?>, List<Pair<T, Double>>> byClassWithPriorities = new HashMap<>();
	for (final PluginInfo<T> pluginInfo : pluginInfos) {
	  final T instance = pluginInfo.createInstance();
	  byClassWithPriorities
			  .computeIfAbsent(instance.getTargetClass(), k -> new ArrayList<>())
			  .add(new Pair<>(instance, pluginInfo.getPriority()));
	}
	return byClassWithPriorities;
  }

  public static <T extends SciJavaPlugin> List<? extends PluginInfo<T>> getAllMatchingPluginInfos(
		  final Class<T> clazz,
		  final Context context) {

	return getAllMatchingPluginInfos(clazz, context.getService(PluginService.class));
  }

  public static <T extends SciJavaPlugin> List<? extends PluginInfo<T>> getAllMatchingPluginInfos(
		  final Class<T> clazz,
		  final PluginService pluginService) {

	return pluginService.getPluginsOfType(clazz);
  }
}
