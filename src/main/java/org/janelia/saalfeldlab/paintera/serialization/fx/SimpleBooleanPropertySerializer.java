package org.janelia.saalfeldlab.paintera.serialization.fx;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import javafx.beans.property.SimpleBooleanProperty;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;
import java.util.Optional;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class SimpleBooleanPropertySerializer implements PainteraSerialization.PainteraAdapter<SimpleBooleanProperty> {

  @Override
  public SimpleBooleanProperty deserialize(final JsonElement json, final Type typeOfT, final
  JsonDeserializationContext context)
		  throws JsonParseException {

	return new SimpleBooleanProperty(Optional.ofNullable((Boolean)context.deserialize(json, boolean.class)).orElse(false));
  }

  @Override
  public JsonElement serialize(final SimpleBooleanProperty src, final Type typeOfSrc, final JsonSerializationContext context) {

	return new JsonPrimitive(src.get());
  }

  @Override
  public Class<SimpleBooleanProperty> getTargetClass() {

	return SimpleBooleanProperty.class;
  }
}

