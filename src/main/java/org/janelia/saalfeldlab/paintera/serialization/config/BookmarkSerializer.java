package org.janelia.saalfeldlab.paintera.serialization.config;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.scene.transform.Affine;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.config.BookmarkConfig;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class BookmarkSerializer implements PainteraSerialization.PainteraAdapter<BookmarkConfig.Bookmark> {

	private static final String GLOBAL_TRANSFORM_KEY = "globalTransform";

	private static final String VIEWER_3D_KEY = "viewer3DTransform";

	private static final String NOTE_KEY = "note";


	@Override
	public BookmarkConfig.Bookmark deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context) throws JsonParseException {
		final JsonObject map = json.getAsJsonObject();
		return new BookmarkConfig.Bookmark(
				context.deserialize(map.get(GLOBAL_TRANSFORM_KEY), AffineTransform3D.class),
				context.deserialize(map.get(VIEWER_3D_KEY), Affine.class),
				map.has(NOTE_KEY) ? map.get(NOTE_KEY).getAsString() : null);
	}

	@Override
	public JsonElement serialize(
			final BookmarkConfig.Bookmark bookmark,
			final Type typeOfSrc,
			final JsonSerializationContext context) {
		final JsonObject map = new JsonObject();
		map.add(GLOBAL_TRANSFORM_KEY, context.serialize(bookmark.getGlobalTransformCopy()));
		map.add(VIEWER_3D_KEY, context.serialize(bookmark.getViewer3DTransformCopy()));
		if (bookmark.getNote() != null)
			map.addProperty(NOTE_KEY, bookmark.getNote());
		return map;
	}

	@Override
	public Class<BookmarkConfig.Bookmark> getTargetClass() {
		return BookmarkConfig.Bookmark.class;
	}

	@Override
	public boolean isHierarchyAdapter() {
		return false;
	}
}
