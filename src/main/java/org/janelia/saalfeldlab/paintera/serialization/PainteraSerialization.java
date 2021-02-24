package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import javafx.util.Pair;
import org.janelia.saalfeldlab.util.SciJavaUtils;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.scijava.plugin.SciJavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// TODO make service for this
public class PainteraSerialization {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static Map<Class<?>, List<Pair<PainteraSerializer, Double>>> SERIALIZERS_SORTED_BY_PRIORITY = null;

  private static Map<Class<?>, List<Pair<PainteraDeserializer, Double>>> DESERIALIZERS_SORTED_BY_PRIORITY = null;

  public interface PainteraAdapter<T> extends PainteraSerializer<T>, PainteraDeserializer<T>, SciJavaUtils.HasTargetClass<T> {

	default boolean isHierarchyAdapter() {

	  return false;
	}

  }

  public interface PainteraDeserializer<T> extends JsonDeserializer<T>, SciJavaPlugin, SciJavaUtils.HasTargetClass<T> {

	default boolean isHierarchyAdapter() {

	  return false;
	}

  }

  public interface PainteraSerializer<T> extends JsonSerializer<T>, SciJavaPlugin, SciJavaUtils.HasTargetClass<T> {

	default boolean isHierarchyAdapter() {

	  return false;
	}

  }

  public static Map<Class<?>, List<Pair<PainteraSerializer, Double>>> getSerializers(final Context context) {

	if (SERIALIZERS_SORTED_BY_PRIORITY == null) {
	  try {
		SERIALIZERS_SORTED_BY_PRIORITY = Collections.unmodifiableMap(SciJavaUtils.byTargetClassSortedByPriorities(PainteraSerializer.class, context));
	  } catch (InstantiableException e) {
		throw new RuntimeException(e);
	  }
	}
	return SERIALIZERS_SORTED_BY_PRIORITY;
  }

  public static Map<Class<?>, List<Pair<PainteraDeserializer, Double>>> getDeserializers(final Context context) {

	if (DESERIALIZERS_SORTED_BY_PRIORITY == null) {
	  try {
		DESERIALIZERS_SORTED_BY_PRIORITY = Collections.unmodifiableMap(SciJavaUtils.byTargetClassSortedByPriorities(PainteraDeserializer.class, context));
	  } catch (InstantiableException e) {
		throw new RuntimeException(e);
	  }
	}
	return DESERIALIZERS_SORTED_BY_PRIORITY;
  }
}
