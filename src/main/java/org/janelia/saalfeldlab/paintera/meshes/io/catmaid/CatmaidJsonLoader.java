package org.janelia.saalfeldlab.paintera.meshes.io.catmaid;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.stage.Stage;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.paintera.meshes.Meshes;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshLoader;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CatmaidJsonLoader implements TriangleMeshLoader {

  private static final String MESH_KEY = "mesh";

  private static final String INDEXED_TRIANGLE_SET_KEY = "IndexedTriangleSet";

  private static final String INDEX_ATTRIBUTE_KEY = "index";

  private static final String COORDINATE_KEY = "Coordinate";

  private static final String POINT_ATTRIBUTE_KEY = "point";

  @Override
  public TriangleMesh loadMesh(Path path) throws IOException {

	final Gson gson = new Gson();
	try (final FileReader reader = new FileReader(path.toFile())) {
	  final JsonObject json = gson.fromJson(reader, JsonObject.class);
	  final Document doc = Jsoup.parse(json.get(MESH_KEY).getAsString());
	  final Elements indexedTriangleSet = doc.select(INDEXED_TRIANGLE_SET_KEY);
	  final int[] indices = Stream
			  .of(indexedTriangleSet.attr(INDEX_ATTRIBUTE_KEY).split(" "))
			  .flatMapToInt(s -> IntStream.of(Integer.parseInt(s), 0))
			  .toArray();
	  final Elements coordinate = indexedTriangleSet.select(COORDINATE_KEY);
	  final double[] vertices = Stream
			  .of(coordinate.attr(POINT_ATTRIBUTE_KEY).split(" "))
			  .mapToDouble(Double::parseDouble)
			  .toArray();
	  final float[] texCoordinates = new float[]{0.0f, 0.0f};
	  // VertexFormat.POINT_TEXCOORD
	  // p0, t0, p1, t1, p3, t3
	  final TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_TEXCOORD);
	  mesh.getTexCoords().setAll(texCoordinates);
	  mesh.getPoints().setAll(fromDoubleArray(vertices));
	  mesh.getFaces().setAll(indices);
	  return mesh;
	}
  }

  private static float[] fromDoubleArray(final double[] doubles) {

	final float[] floats = new float[doubles.length];
	for (int d = 0; d < floats.length; ++d) {
	  floats[d] = (float)doubles[d];
	}
	return floats;
  }

  public static void main(String[] args) throws IOException {

	PlatformImpl.startup(() -> {
	});
	final Path path = Paths.get(System.getProperty("user.home"), "Downloads", "catmaid-meshes", "Block3.json");
	final TriangleMesh mesh = new CatmaidJsonLoader().loadMesh(path);
	final double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
	final double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
	for (int i = 0; i < mesh.getPoints().size(); i += 3) {
	  for (int d = 0; d < 3; ++d) {
		min[d] = Math.min(min[d], mesh.getPoints().get(i + d));
		max[d] = Math.max(max[d], mesh.getPoints().get(i + d));
	  }
	}
	System.out.print(Arrays.toString(min) + " " + Arrays.toString(max));
	final Interval interval = Intervals.smallestContainingInterval(new FinalRealInterval(min, max));
	final MeshView mv = new MeshView(mesh);
	mv.setMaterial(Meshes.painteraPhongMaterial(Color.WHITE));
	mv.setDrawMode(DrawMode.FILL);
	mv.setCullFace(CullFace.BACK);
	final Viewer3DFX viewer = new Viewer3DFX(800, 600);
	viewer.meshesEnabledProperty().set(true);
	mv.setOpacity(1.0);
	viewer.setInitialTransformToInterval(interval);
	final MeshView mv2 = new MeshView(mesh);
	mv.setMaterial(Meshes.painteraPhongMaterial());
	mv.setDrawMode(DrawMode.FILL);
	mv.setCullFace(CullFace.BACK);
	mv2.setTranslateX(100);
	viewer.meshesGroup().getChildren().addAll(mv, mv2);
	Platform.setImplicitExit(true);
	Platform.runLater(() -> {
	  final Scene scene = new Scene(viewer);
	  final Stage stage = new Stage();
	  stage.setScene(scene);
	  stage.setWidth(800);
	  stage.setHeight(600);
	  stage.show();
	});
	//		final String mesh = "<IndexedTriangleSet  index='0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35'><Coordinate point='440474 99212 136120 440474 119212 136120 460474 99212 136120 460474 99212 136120 440474 119212 136120 460474 119212 136120 440474 99212 136120 460474 99212 136120 460474 99212 156120 440474 99212 136120 460474 99212 156120 440474 99212 156120 440474 119212 136120 440474 119212 156120 460474 119212 156120 440474 119212 136120 460474 119212 156120 460474 119212 136120 440474 99212 156120 460474 119212 156120 440474 119212 156120 440474 99212 156120 460474 99212 156120 460474 119212 156120 440474 99212 136120 440474 119212 156120 440474 119212 136120 440474 99212 136120 440474 99212 156120 440474 119212 156120 460474 99212 136120 460474 119212 136120 460474 99212 156120 460474 119212 136120 460474 119212 156120 460474 99212 156120'/></IndexedTriangleSet>";
	//		final Document doc = Jsoup.parse(mesh);
	//		System.out.println(doc);
	//		System.out.println(doc.select("IndexedTriangleSet").attr("index"));
  }

}
