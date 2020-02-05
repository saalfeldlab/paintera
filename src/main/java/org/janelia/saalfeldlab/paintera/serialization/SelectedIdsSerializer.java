package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.scijava.plugin.Plugin;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class SelectedIdsSerializer implements PainteraSerialization.PainteraAdapter<SelectedIds>
{

	private static final String LAST_SELECTION = "lastSelection";

	private static final String ACTIVE_IDS = "activeIds";

	@Override
	public SelectedIds deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		final JsonObject  obj         = context.deserialize(json, JsonObject.class);
		final SelectedIds selectedIds = new SelectedIds();

		if (obj.has(ACTIVE_IDS))
		{
			selectedIds.activate(context.deserialize(obj.get(ACTIVE_IDS), long[].class));
		}

		if (obj.has(LAST_SELECTION))
		{
			selectedIds.activateAlso(obj.get(LAST_SELECTION).getAsLong());
		}

		return selectedIds;
	}

	@Override
	public JsonElement serialize(final SelectedIds src, final Type typeOfSrc, final JsonSerializationContext context)
	{
		final JsonObject obj = new JsonObject();
		obj.addProperty(LAST_SELECTION, src.getLastSelection());
		obj.add(ACTIVE_IDS, context.serialize(src.getActiveIdsCopyAsArray()));
		return obj;
	}

	@Override
	public Class<SelectedIds> getTargetClass() {
		return SelectedIds.class;
	}
}
