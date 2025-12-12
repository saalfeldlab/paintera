package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.paint.Color
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.State
import org.janelia.saalfeldlab.paintera.meshes.MeshViewUpdateQueue
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor


/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class MeshManagerWithSingleMesh<Key>(
	source: DataSource<*, *>,
	getBlockList: GetBlockListFor<Key>,
	getMeshFor: GetMeshFor<Key>,
	viewFrustumProperty: ObservableValue<ViewFrustum>,
	eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
	workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
	meshViewUpdateQueue: MeshViewUpdateQueue<Key>
) : MeshManager<Key>(
	source,
	getBlockList,
	getMeshFor,
	viewFrustumProperty,
	eyeToWorldTransformProperty,
	workers,
	meshViewUpdateQueue
) {

	var meshKey: Key? = null
		@Synchronized get
		@Synchronized private set

	val colorProperty: ObjectProperty<Color> = SimpleObjectProperty(Color.WHITE)
	var color: Color by colorProperty.nonnull()

	override suspend fun createMeshFor(key: Key) {
		if (key == meshKey)
			return
		this.removeAllMeshes()
		meshKey = key
		super.createMeshFor(key)

	}

	override fun setupMeshState(key: Key, state: State) {
		with(state) {
			colorProperty().bind(colorProperty)
			settings.bindTo(this@MeshManagerWithSingleMesh.globalSettings)
		}
		super.setupMeshState(key, state)
	}

	@Synchronized
	override fun removeAllMeshes() {
		meshKey?.let { removeMesh(it) }
	}

	@Synchronized
	override fun removeMesh(key: Key) {
		meshKey = null
		super.removeMesh(key)
	}

}
