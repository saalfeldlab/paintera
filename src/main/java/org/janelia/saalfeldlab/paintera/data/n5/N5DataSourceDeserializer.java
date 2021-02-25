package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.util.volatiles.SharedQueue;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class N5DataSourceDeserializer implements JsonDeserializer<N5DataSource<?, ?>> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String META_CLASS_KEY = "metaType";

  private static final String META_KEY = "meta";

  private static final String TRANSFORM_KEY = "transform";

  private final SharedQueue queue;

  private final int priority;

  public N5DataSourceDeserializer(final SharedQueue queue, final int priority) {

	super();
	this.queue = queue;
	this.priority = priority;
  }

  @Override
  public N5DataSource<?, ?> deserialize(final JsonElement el, final Type type, final JsonDeserializationContext
		  context)
		  throws JsonParseException {

	try {
	  LOG.debug("Deserializing from {}", el);
	  final String clazz = el.getAsJsonObject().get(META_CLASS_KEY).getAsString();
	  final N5Meta meta = context.deserialize(
			  el.getAsJsonObject().get(META_KEY),
			  Class.forName(clazz));
	  final AffineTransform3D transform = context.deserialize(
			  el.getAsJsonObject().get(TRANSFORM_KEY),
			  AffineTransform3D.class);

	  LOG.debug("Deserialized transform: {}", transform);
	  return new N5DataSource<>(meta, transform, "", queue, priority);
	} catch (IOException | ClassNotFoundException e) {
	  throw new JsonParseException(e);
	}

  }

  @Plugin(type = StatefulSerializer.DeserializerFactory.class)
  public static class Factory implements StatefulSerializer.DeserializerFactory<N5DataSource<?, ?>, N5DataSourceDeserializer> {

	@Override
	public N5DataSourceDeserializer createDeserializer(
			final Arguments arguments,
			final Supplier<String> projectDirectory,
			final IntFunction<SourceState<?, ?>> dependencyFromIndex) {

	  return new N5DataSourceDeserializer(arguments.viewer.getQueue(), 0);
	}

	@Override
	public Class<N5DataSource<?, ?>> getTargetClass() {

	  return (Class<N5DataSource<?, ?>>)(Class<?>)N5DataSource.class;
	}
  }

}
