package org.janelia.saalfeldlab.paintera.config

import javafx.beans.InvalidationListener
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.scene.shape.MeshView
import javafx.scene.shape.TriangleMesh
import org.janelia.saalfeldlab.paintera.meshes.Meshes
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormat
import java.nio.file.Path

class ArbitraryMeshConfig {

    private val meshes = FXCollections.observableArrayList<MeshInfo>()

    val unmodifiableMeshes = FXCollections.unmodifiableObservableList(meshes)!!

    val isVisibleProperty: BooleanProperty = SimpleBooleanProperty(true)

    private val lastPath = SimpleObjectProperty<Path>()

    class MeshInfo @JvmOverloads constructor(
            val path: Path,
            val format: TriangleMeshFormat,
            private val mesh: TriangleMesh = format.loader.loadMesh(path)) {

        private val meshView: MeshView = MeshView(this.mesh)

		val isVisibleProperty: BooleanProperty = SimpleBooleanProperty(true)

        private val scale = SimpleDoubleProperty(1.0)

        private val translateX = SimpleDoubleProperty(0.0)

        private val translateY = SimpleDoubleProperty(0.0)

        private val translateZ = SimpleDoubleProperty(0.0)

        private val name = SimpleStringProperty()

        private val color = SimpleObjectProperty(Meshes.DEFAULT_MESH_COLOR)

        private val cullFace = SimpleObjectProperty(CullFace.BACK)

        private val drawMode = SimpleObjectProperty(DrawMode.FILL)

        init {
			val material = Meshes.painteraPhongMaterial(color.get())
            material.diffuseColorProperty().bindBidirectional(color)
            this.meshView.material = material

            this.meshView.visibleProperty().bindBidirectional(this.isVisibleProperty)

            this.meshView.scaleXProperty().bindBidirectional(scale)
            this.meshView.scaleYProperty().bindBidirectional(scale)
            this.meshView.scaleZProperty().bindBidirectional(scale)

            this.meshView.translateXProperty().bindBidirectional(translateX)
            this.meshView.translateYProperty().bindBidirectional(translateY)
            this.meshView.translateZProperty().bindBidirectional(translateZ)

            this.meshView.cullFaceProperty().bindBidirectional(this.cullFace)
            this.meshView.drawModeProperty().bindBidirectional(this.drawMode)

            this.name.set(this.path.fileName.toString())
        }

        fun nameProperty() = name

        fun colorProperty() = color

        fun scaleProperty() = scale

        fun translateXProperty() = translateX

        fun translateYProperty() = translateY

        fun translateZProperty() = translateZ

        fun cullFaceProperty() = cullFace

        fun drawModeProperty() = drawMode

		fun getMeshView(): Node = meshView
    }

    fun addMesh(mesh: MeshInfo) = this.meshes.add(mesh)

    fun removeMesh(mesh: MeshInfo) = this.meshes.remove(mesh)

    fun lastPathProperty() = lastPath

	@Deprecated(message = "Pass config to constructor of ArbitraryMeshConfigNode directly instead")
    fun setTo(that: ArbitraryMeshConfig) {
        this.lastPath.set(that.lastPath.get())
        this.meshes.setAll(that.meshes)
        this.isVisibleProperty.set(that.isVisibleProperty.get())
    }

	@Deprecated(message = "Pass config to constructor of ArbitraryMeshConfigNode directly instead")
    fun bindTo(that: ArbitraryMeshConfig) {
        this.lastPath.bind(that.lastPath)
        this.isVisibleProperty.bind(that.isVisibleProperty)
        that.meshes.addListener(InvalidationListener { this.meshes.setAll(that.meshes) })
    }


}
