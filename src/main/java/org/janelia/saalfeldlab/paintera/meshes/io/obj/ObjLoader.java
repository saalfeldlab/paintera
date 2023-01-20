package org.janelia.saalfeldlab.paintera.meshes.io.obj;

import com.sun.javafx.application.PlatformImpl;
import de.javagl.obj.FloatTuple;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.ObjReader;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ObjLoader implements TriangleMeshLoader {

  @Override
  public TriangleMesh loadMesh(Path path) throws IOException {

	try (final InputStream fis = new FileInputStream(path.toFile())) {

	  final Obj obj = ObjReader.read(fis);

	  final VertexFormat vertexFormat;
	  if (obj.getNumFaces() > 0 && obj.getFace(0).containsNormalIndices())
		vertexFormat = VertexFormat.POINT_NORMAL_TEXCOORD;
	  else
		vertexFormat = VertexFormat.POINT_TEXCOORD;

	  final boolean hasTexCoordinates = obj.getNumTexCoords() > 0;
	  final float[] texCoords = new float[obj.getNumTexCoords() * 2];
	  final float[] vertices = new float[obj.getNumVertices() * 3];
	  final float[] normals = new float[obj.getNumNormals() * 3];
	  final int[] faces = new int[obj.getNumFaces() * vertexFormat.getVertexIndexSize() * 3];

	  // assume 3D (not 4D, or 2D)

	  for (int i = 0, k = -1; i < obj.getNumTexCoords(); ++i) {
		final FloatTuple coord = obj.getTexCoord(i);
		texCoords[++k] = coord.getX();
		texCoords[++k] = coord.getY();
	  }

	  for (int i = 0, k = -1; i < obj.getNumVertices(); ++i) {
		final FloatTuple vertex = obj.getVertex(i);
		vertices[++k] = vertex.getX();
		vertices[++k] = vertex.getY();
		vertices[++k] = vertex.getZ();
	  }

	  for (int i = 0, k = -1; i < obj.getNumNormals(); ++i) {
		final FloatTuple normal = obj.getNormal(i);
		normals[++k] = normal.getX();
		normals[++k] = normal.getY();
		normals[++k] = normal.getZ();
	  }

	  // from TriangleMesh doc:
	  //
	  // VertexFormat.POINT_TEXCOORD
	  // p0, t0, p1, t1, p3, t3
	  //
	  // VertexFormat.POINT_NORMAL_TEXCOORD:
	  // p0, n0, t0, p1, n1, t1, p3, n3, t3, // First triangle of a textured rectangle
	  // p: vertex
	  // n: normal
	  // t: texture
	  // https://docs.oracle.com/javase/8/javafx/api/javafx/scene/shape/TriangleMesh.html
	  if (VertexFormat.POINT_NORMAL_TEXCOORD.equals(vertexFormat)) {
		for (int i = 0, k = -1; i < obj.getNumFaces(); ++i) {
		  final ObjFace face = obj.getFace(i);
		  faces[++k] = face.getVertexIndex(0);
		  faces[++k] = face.getNormalIndex(0);
		  faces[++k] = hasTexCoordinates ? face.getTexCoordIndex(0) : 0;
		  faces[++k] = face.getVertexIndex(1);
		  faces[++k] = face.getNormalIndex(1);
		  faces[++k] = hasTexCoordinates ? face.getTexCoordIndex(1) : 0;
		  faces[++k] = face.getVertexIndex(2);
		  faces[++k] = face.getNormalIndex(2);
		  faces[++k] = hasTexCoordinates ? face.getTexCoordIndex(2) : 0;
		}
	  } else {
		for (int i = 0, k = -1; i < obj.getNumFaces(); ++i) {
		  final ObjFace face = obj.getFace(i);
		  faces[++k] = face.getVertexIndex(0);
		  faces[++k] = hasTexCoordinates ? face.getTexCoordIndex(0) : 0;
		  faces[++k] = face.getVertexIndex(1);
		  faces[++k] = hasTexCoordinates ? face.getTexCoordIndex(1) : 0;
		  faces[++k] = face.getVertexIndex(2);
		  faces[++k] = hasTexCoordinates ? face.getTexCoordIndex(2) : 0;
		}
	  }

	  final TriangleMesh mesh = new TriangleMesh(vertexFormat);
	  mesh.getTexCoords().setAll(hasTexCoordinates ? texCoords : new float[]{0.0f, 0.0f});
	  mesh.getPoints().setAll(vertices);
	  mesh.getFaces().setAll(faces);
	  if (normals.length > 0)
		mesh.getNormals().setAll(normals);
	  return mesh;
	}
  }

  public static void main(String[] args) throws IOException {

	PlatformImpl.startup(() -> {
	});
	// https://people.sc.fsu.edu/~jburkardt/data/obj/obj.html
	//		final String objFile = "al.obj";
	//		final String objFile = "diamond.obj";
	final String objFile = "alfa147.obj";
	final Path path = Paths.get(System.getProperty("user.home"), "Downloads", objFile);
	final TriangleMesh mesh = new ObjLoader().loadMesh(path);
	final double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
	final double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
	for (int i = 0; i < mesh.getPoints().size(); i += 3) {
	  for (int d = 0; d < 3; ++d) {
		min[d] = Math.min(min[d], mesh.getPoints().get(i + d));
		max[d] = Math.max(max[d], mesh.getPoints().get(i + d));
	  }
	}
	final Interval interval = Intervals.smallestContainingInterval(new FinalRealInterval(min, max));
	final MeshView mv = new MeshView(mesh);
	mv.setMaterial(Meshes.painteraPhongMaterial(Color.WHITE));
	mv.setDrawMode(DrawMode.FILL);
	mv.setCullFace(CullFace.BACK);
	final Viewer3DFX viewer = new Viewer3DFX(800, 600);
	viewer.getMeshesEnabled().set(true);
	mv.setOpacity(1.0);
	viewer.setInitialTransformToInterval(interval);
	final MeshView mv2 = new MeshView(mesh);
	mv.setMaterial(Meshes.painteraPhongMaterial());
	mv.setDrawMode(DrawMode.FILL);
	mv.setCullFace(CullFace.BACK);
	mv2.setTranslateX(100);
	viewer.getMeshesGroup().getChildren().addAll(mv, mv2);
	//		final double factor = 1;
	//		final double w = 1*factor, h = 2*factor, d = 3*factor;
	//		final Box box = new Box(w, h, d);
	//		box.setCullFace(CullFace.NONE);
	//		box.setOpacity(1.0);
	//		box.setMaterial(Meshes.painteraPhongMaterial(Color.RED));
	//		viewer.meshesGroup().getChildren().add(box);
	Platform.setImplicitExit(true);
	Platform.runLater(() -> {
	  final Scene scene = new Scene(viewer);
	  final Stage stage = new Stage();
	  stage.setScene(scene);
	  stage.setWidth(800);
	  stage.setHeight(600);
	  stage.show();
	});
  }

}
