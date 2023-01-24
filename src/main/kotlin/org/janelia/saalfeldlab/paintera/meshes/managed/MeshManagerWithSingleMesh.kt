package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import javafx.scene.paint.Color
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.ExecutorService

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class MeshManagerWithSingleMesh<Key>(
	source: DataSource<*, *>,
	val getBlockList: GetBlockListFor<Key>,
	val getMeshFor: GetMeshFor<Key>,
	viewFrustumProperty: ObservableValue<ViewFrustum>,
	eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
	val managers: ExecutorService,
	val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
	meshViewUpdateQueue: MeshViewUpdateQueue<Key>,
) {

	var meshKey: Key? = null
		@Synchronized get
		@Synchronized private set

	val viewerEnabledProperty: BooleanProperty = SimpleBooleanProperty(false)
	var isViewerEnabled: Boolean by viewerEnabledProperty.nonnull()

	val colorProperty: ObjectProperty<Color> = SimpleObjectProperty(Color.WHITE)
	var color: Color by colorProperty.nonnull()

	private val manager: AdaptiveResolutionMeshManager<Key> = AdaptiveResolutionMeshManager(
		source,
		getBlockList,
		getMeshFor,
		viewFrustumProperty,
		eyeToWorldTransformProperty,
		viewerEnabledProperty,
		managers,
		workers,
		meshViewUpdateQueue
	)


	val rendererSettings = manager.rendererSettings

	val settings: MeshSettings = MeshSettings(source.numMipmapLevels)

	val managedSettings = ManagedMeshSettings(source.numMipmapLevels).apply { rendererSettings.meshesEnabledProperty.bind(meshesEnabledProperty) }

	val meshesGroup: Group = manager.meshesGroup

	private val managerCancelAndUpdate = InvalidationListener { manager.requestCancelAndUpdate() }

	@Synchronized
	fun createMeshFor(key: Key) {
		if (key == meshKey)
			return
		this.removeAllMeshes()
		meshKey = key
		manager.createMeshFor(key, true) { it.setup() }
	}

	@Synchronized
	fun removeAllMeshes() {
		manager.removeAllMeshes { it.release() }
		meshKey = null
	}

	@Synchronized
	fun refreshMeshes() {
		val key = meshKey
		this.removeAllMeshes()
		if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
		key?.let { createMeshFor(it) }
	}

	private fun MeshGenerator.State.setup() = setupGeneratorState(this)

	@Synchronized
	private fun setupGeneratorState(state: MeshGenerator.State) {
		LOG.debug("Setting up state for mesh key {}", meshKey)
		state.colorProperty().bind(colorProperty)
		state.settings.bindTo(settings)
		state.settings.levelOfDetailProperty.addListener(managerCancelAndUpdate)
		state.settings.coarsestScaleLevelProperty.addListener(managerCancelAndUpdate)
		state.settings.finestScaleLevelProperty.addListener(managerCancelAndUpdate)
	}

	@Synchronized
	private fun MeshGenerator.State.release() {
		colorProperty().unbind()
		settings.unbind()
		settings.levelOfDetailProperty.removeListener(managerCancelAndUpdate)
		settings.coarsestScaleLevelProperty.removeListener(managerCancelAndUpdate)
		settings.finestScaleLevelProperty.removeListener(managerCancelAndUpdate)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
	}

}
