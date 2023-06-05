package org.janelia.saalfeldlab.paintera.serialization.blocks;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class LabelBlockLookupPainteraAdapter implements PainteraSerialization.PainteraAdapter<LabelBlockLookup> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final LabelBlockLookupAdapter adapter = LabelBlockLookupAdapter.getJsonAdapter();

	@Override
	public LabelBlockLookup deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {

		LOG.debug("Deserializing {} of type {}", jsonElement, type);
		return adapter.deserialize(jsonElement, type, context);
	}

	@Override
	public JsonElement serialize(LabelBlockLookup labelBlockLookup, Type type, JsonSerializationContext context) {

		return adapter.serialize(labelBlockLookup, type, context);
	}

	@Override
	public Class<LabelBlockLookup> getTargetClass() {

		return LabelBlockLookup.class;
	}

	@Override
	public boolean isHierarchyAdapter() {

		return true;
	}
}
