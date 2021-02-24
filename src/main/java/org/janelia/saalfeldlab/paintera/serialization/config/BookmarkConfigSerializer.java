package org.janelia.saalfeldlab.paintera.serialization.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.util.Duration;
import org.janelia.saalfeldlab.paintera.config.BookmarkConfig;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class BookmarkConfigSerializer implements PainteraSerialization.PainteraAdapter<BookmarkConfig> {

  private static final String BOOKMARKS_KEY = "bookmarks";

  private static final String TRANSITION_TIME_KEY = "transitionTime";

  @Override
  public BookmarkConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

	final JsonObject map = json.getAsJsonObject();
	final BookmarkConfig config = new BookmarkConfig();
	if (map.has(BOOKMARKS_KEY)) {
	  final JsonArray bookmarks = map.get(BOOKMARKS_KEY).getAsJsonArray();
	  for (int i = 0; i < bookmarks.size(); ++i) {
		config.addBookmark(context.deserialize(bookmarks.get(i), BookmarkConfig.Bookmark.class));
	  }
	}
	if (map.has(TRANSITION_TIME_KEY))
	  config.setTransitionTime(context.deserialize(map.get(TRANSITION_TIME_KEY), Duration.class));
	return config;
  }

  @Override
  public JsonElement serialize(BookmarkConfig config, Type typeOfSrc, JsonSerializationContext context) {

	final JsonObject map = new JsonObject();
	final List<BookmarkConfig.Bookmark> bookmarks = new ArrayList<>(config.getUnmodifiableBookmarks());
	if (bookmarks.size() > 0)
	  map.add(BOOKMARKS_KEY, context.serialize(bookmarks.toArray()));
	map.add(TRANSITION_TIME_KEY, context.serialize(config.getTransitionTime()));
	return map;
  }

  @Override
  public Class<BookmarkConfig> getTargetClass() {

	return BookmarkConfig.class;
  }

  @Override
  public boolean isHierarchyAdapter() {

	return false;
  }
}
