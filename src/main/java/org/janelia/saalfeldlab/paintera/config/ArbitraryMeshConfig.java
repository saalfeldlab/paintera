package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.Meshes;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormat;

import java.io.IOException;
import java.nio.file.Path;

public class ArbitraryMeshConfig {

	public static class MeshInfo {

		private final Path path;

		private final TriangleMeshFormat format;

		private final TriangleMesh mesh;

		private final MeshView meshView;

		private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

		private final DoubleProperty scale = new SimpleDoubleProperty(1.0);

		private final DoubleProperty translateX = new SimpleDoubleProperty(0.0);

		private final DoubleProperty translateY = new SimpleDoubleProperty(0.0);

		private final DoubleProperty translateZ = new SimpleDoubleProperty(0.0);

		private final StringProperty name = new SimpleStringProperty();

		private final ObjectProperty<Color> color = new SimpleObjectProperty<>(Meshes.DEFAULT_MESH_COLOR);

		private final ObjectProperty<CullFace> cullFace = new SimpleObjectProperty<>(CullFace.BACK);

		private final ObjectProperty<DrawMode> drawMode = new SimpleObjectProperty<>(DrawMode.FILL);

		public MeshInfo(
				final Path path,
				final TriangleMeshFormat format) throws IOException {
			this(path, format, format.getLoader().loadMesh(path));
		}

		public MeshInfo(
				final Path path,
				final TriangleMeshFormat format,
				final TriangleMesh mesh) {
			this.path = path;
			this.format = format;
			this.mesh = mesh;
			this.meshView = new MeshView(this.mesh);
			final PhongMaterial material = Meshes.painteraPhongMaterial(color.get());
			material.diffuseColorProperty().bindBidirectional(color);
			this.meshView.setMaterial(material);

			this.meshView.visibleProperty().bindBidirectional(this.isVisible);

			this.meshView.scaleXProperty().bindBidirectional(scale);
			this.meshView.scaleYProperty().bindBidirectional(scale);
			this.meshView.scaleZProperty().bindBidirectional(scale);

			this.meshView.translateXProperty().bindBidirectional(translateX);
			this.meshView.translateYProperty().bindBidirectional(translateY);
			this.meshView.translateZProperty().bindBidirectional(translateZ);

			this.meshView.cullFaceProperty().bindBidirectional(this.cullFace);
			this.meshView.drawModeProperty().bindBidirectional(this.drawMode);

			this.name.set(this.path.getFileName().toString());
		}

		public Path getPath() {
			return this.path;
		}

		public TriangleMeshFormat getFormat() {
			return this.format;
		}

		public BooleanProperty isVisibleProperty() {
			return isVisible;
		}

		public Node getMeshView() {
			return this.meshView;
		}

		public StringProperty nameProperty() {
			return this.name;
		}

		public ObjectProperty<Color> colorProperty() {
			return this.color;
		}

		public DoubleProperty scaleProperty() {
			return this.scale;
		}

		public DoubleProperty translateXProperty() {
			return translateX;
		}

		public DoubleProperty translateYProperty() {
			return translateY;
		}

		public DoubleProperty translateZProperty() {
			return translateZ;
		}

		public ObjectProperty<CullFace> cullFaceProperty() {
			return cullFace;
		}

		public ObjectProperty<DrawMode> drawModeProperty() {
			return drawMode;
		}
	}

	private final ObservableList<MeshInfo> meshes = FXCollections.observableArrayList();

	private final ObservableList<MeshInfo> unmodifiableMeshes = FXCollections.unmodifiableObservableList(meshes);

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	private final ObjectProperty<Path> lastPath = new SimpleObjectProperty<>();

	public ObservableList<MeshInfo> getUnmodifiableMeshes() {
		return this.unmodifiableMeshes;
	}

	public BooleanProperty isVisibleProperty() {
		return this.isVisible;
	}

	public void addMesh(final MeshInfo mesh) {
		this.meshes.add(mesh);
	}

	public void removeMesh(final MeshInfo mesh) {
		this.meshes.remove(mesh);
	}

	public ObjectProperty<Path> lastPathProperty() {
		return lastPath;
	}

	public void setTo(ArbitraryMeshConfig that) {
		this.lastPath.set(that.lastPath.get());
		this.meshes.setAll(that.meshes);
		this.isVisible.set(that.isVisible.get());
	}

	public void bindTo(ArbitraryMeshConfig that) {
		this.lastPath.bind(that.lastPath);
		this.isVisible.bind(that.isVisible);
		that.meshes.addListener((InvalidationListener) obs -> this.meshes.setAll(that.meshes));
	}


}
