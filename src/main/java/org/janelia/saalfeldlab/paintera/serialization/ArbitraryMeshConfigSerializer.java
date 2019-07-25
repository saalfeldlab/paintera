package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import org.janelia.saalfeldlab.paintera.config.ArbitraryMeshConfig;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormat;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.ArrayList;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class ArbitraryMeshConfigSerializer implements PainteraSerialization.PainteraAdapter<ArbitraryMeshConfig> {

	private static final String IS_VISIBLE_KEY = "isVisible";

	private static final String LAST_PATH_KEY = "lastPath";

	private static final String MESH_INFO_LIST_KEY = "meshes";

	@Plugin(type = PainteraSerialization.PainteraAdapter.class)
	public static class MeshInfoSerializer implements PainteraSerialization.PainteraAdapter<ArbitraryMeshConfig.MeshInfo> {

		private static final String PATH_KEY = "path";

		private static final String FORMAT_KEY = "format";

		private static final String NAME_KEY = "name";

		private static final String IS_VISIBLE_KEY = "isVisible";

		private static final String COLOR_KEY = "color";

		private static final String SCALE_KEY = "scale";

		private static final String TRANSLATE_X_KEY = "translateX";

		private static final String TRANSLATE_Y_KEY = "translateY";

		private static final String TRANSLATE_Z_KEY = "translateZ";

		private static final String CULL_FACE_KEY = "cullFace";

		private static final String DRAW_MODE_KEY = "drawMode";

		@Override
		public ArbitraryMeshConfig.MeshInfo deserialize(
				final JsonElement json,
				final Type typeOfT,
				final JsonDeserializationContext context) throws JsonParseException {
			final JsonObject map = json.getAsJsonObject();
			final String path = map.get(PATH_KEY).getAsString();
			final String className = map.get(FORMAT_KEY).getAsString();
			try {
				final ArbitraryMeshConfig.MeshInfo meshInfo = new ArbitraryMeshConfig.MeshInfo(
						Paths.get(path),
						((Class<TriangleMeshFormat>)Class.forName(className)).getConstructor().newInstance());
				if (map.has(NAME_KEY)) meshInfo.nameProperty().set(map.get(NAME_KEY).getAsString());
				if (map.has(IS_VISIBLE_KEY)) meshInfo.isVisibleProperty().set(map.get(IS_VISIBLE_KEY).getAsBoolean());
				if (map.has(COLOR_KEY)) meshInfo.colorProperty().set(Color.web(map.get(COLOR_KEY).getAsString()));
				if (map.has(SCALE_KEY)) meshInfo.scaleProperty().set(map.get(SCALE_KEY).getAsDouble());
				if (map.has(TRANSLATE_X_KEY)) meshInfo.translateXProperty().set(map.get(TRANSLATE_X_KEY).getAsDouble());
				if (map.has(TRANSLATE_Y_KEY)) meshInfo.translateYProperty().set(map.get(TRANSLATE_Y_KEY).getAsDouble());
				if (map.has(TRANSLATE_Z_KEY)) meshInfo.translateZProperty().set(map.get(TRANSLATE_Z_KEY).getAsDouble());
				if (map.has(CULL_FACE_KEY)) meshInfo.cullFaceProperty().set(context.deserialize(map.get(CULL_FACE_KEY), CullFace.class));
				if (map.has(DRAW_MODE_KEY)) meshInfo.drawModeProperty().set(context.deserialize(map.get(DRAW_MODE_KEY), DrawMode.class));
				return meshInfo;
			} catch (Exception e) {
				throw new JsonParseException(e);
			}
		}

		@Override
		public JsonElement serialize(
				final ArbitraryMeshConfig.MeshInfo meshInfo,
				final Type typeOfSrc,
				final JsonSerializationContext context) {
			final JsonObject map = new JsonObject();
			map.addProperty(PATH_KEY, meshInfo.getPath().toAbsolutePath().toString());
			map.addProperty(FORMAT_KEY, meshInfo.getFormat().getClass().getName());
			map.addProperty(NAME_KEY, meshInfo.nameProperty().get());
			map.addProperty(IS_VISIBLE_KEY, meshInfo.isVisibleProperty().get());
			map.addProperty(COLOR_KEY, Colors.toHTML(meshInfo.colorProperty().get()));
			map.addProperty(SCALE_KEY, meshInfo.scaleProperty().get());
			map.addProperty(TRANSLATE_X_KEY, meshInfo.translateXProperty().get());
			map.addProperty(TRANSLATE_Y_KEY, meshInfo.translateYProperty().get());
			map.addProperty(TRANSLATE_Z_KEY, meshInfo.translateZProperty().get());
			map.add(CULL_FACE_KEY, context.serialize(meshInfo.cullFaceProperty().get()));
			map.add(DRAW_MODE_KEY, context.serialize(meshInfo.drawModeProperty().get()));
			return map;
		}

		@Override
		public Class<ArbitraryMeshConfig.MeshInfo> getTargetClass() {
			return ArbitraryMeshConfig.MeshInfo.class;
		}

		@Override
		public boolean isHierarchyAdapter() {
			return false;
		}
	}

	@Override
	public ArbitraryMeshConfig deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context) throws JsonParseException {
		final ArbitraryMeshConfig config = new ArbitraryMeshConfig();
		if (json.isJsonObject()) {
			final JsonObject map = json.getAsJsonObject();
			if (map.has(IS_VISIBLE_KEY)) config.isVisibleProperty().set(map.get(IS_VISIBLE_KEY).getAsBoolean());
			if (map.has(LAST_PATH_KEY)) config.lastPathProperty().set(Paths.get(map.get(LAST_PATH_KEY).getAsString()));
			if (map.has(MESH_INFO_LIST_KEY)) {
				final JsonArray jsonList = map.get(MESH_INFO_LIST_KEY).getAsJsonArray();
				for (int i = 0; i < jsonList.size(); ++i)
					config.addMesh(context.deserialize(jsonList.get(i), ArbitraryMeshConfig.MeshInfo.class));
			}
		}
		return config;
	}

	@Override
	public JsonElement serialize(
			final ArbitraryMeshConfig config,
			final Type typeOfSrc,
			final JsonSerializationContext context) {
		final JsonObject map = new JsonObject();
		map.addProperty(IS_VISIBLE_KEY, config.isVisibleProperty().get());
		if (config.lastPathProperty().get() != null) map.addProperty(LAST_PATH_KEY, config.lastPathProperty().get().toAbsolutePath().toString());
		map.add(MESH_INFO_LIST_KEY, context.serialize(new ArrayList<>(config.getUnmodifiableMeshes())));
		return map;
	}

	@Override
	public Class<ArbitraryMeshConfig> getTargetClass() {
		return ArbitraryMeshConfig.class;
	}

	@Override
	public boolean isHierarchyAdapter() {
		return false;
	}
}
