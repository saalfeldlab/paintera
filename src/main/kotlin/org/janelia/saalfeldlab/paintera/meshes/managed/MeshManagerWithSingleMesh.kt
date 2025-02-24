package org.janelia.saalfeldlab.paintera.meshes.managed

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import javafx.scene.paint.Color
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.State
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.meshes.MeshViewUpdateQueue
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import java.util.concurrent.ExecutorService

abstract class MeshManager<Key>(
	val source: DataSource<*, *>,
	val getBlockListFor: GetBlockListFor<Key>,
	val getMeshFor: GetMeshFor<Key>,
	viewFrustumProperty: ObservableValue<ViewFrustum>,
	eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
	val managers: ExecutorService,
	val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
	meshViewUpdateQueue: MeshViewUpdateQueue<Key>,
) {

	companion object {
		val LOG = KotlinLogging.logger { }
	}

	val viewerEnabledProperty: BooleanProperty = SimpleBooleanProperty(false)
	var isViewerEnabled: Boolean by viewerEnabledProperty.nonnull()

	protected val manager: AdaptiveResolutionMeshManager<Key> = AdaptiveResolutionMeshManager(
		source,
		getBlockListFor,
		getMeshFor,
		viewFrustumProperty,
		eyeToWorldTransformProperty,
		viewerEnabledProperty,
		managers,
		workers,
		meshViewUpdateQueue
	)

	val rendererSettings = manager.rendererSettings

	val managedSettings = ManagedMeshSettings<Key>(source.numMipmapLevels).apply { rendererSettings.meshesEnabledProperty.bind(meshesEnabledProperty) }

	val globalSettings: MeshSettings = managedSettings.globalSettings

	val meshesGroup: Group = manager.meshesGroup

	// TODO This listener is added to all mesh states. This is a problem if a lot of ids are selected
	//  and all use global mesh settings. Whenever the global mesh settings are changed, the
	//  managerCancelAndUpdate would be notified for each of the meshes, which can temporarily slow down
	//  the UI for quite some time (tens of seconds). A smarter way might be a single thread executor that
	//  executes only the last request and has a delay.
	//  This may be fixed now by using manager.requestCancelAndUpdate(), which submits a task
	//  to a LatestTaskExecutor with a delay of 100ms.
	protected val managerCancelAndUpdate = InvalidationListener { manager.requestCancelAndUpdate() }

	open fun getStateFor(key: Key): State? {
		return manager.getStateFor(key)
	}

	fun getSettings(key: Key): MeshSettings {
		return managedSettings.getMeshSettings(key)
	}

	fun submitMeshJob(key : Key) : Job {
		return runBlocking {
			async { createMeshFor(key) }
		}
	}

	open suspend fun createMeshFor(key: Key) {
		manager.createMeshFor(key, true, stateSetup = ::setupMeshState)
	}

	protected open fun setupMeshState(key: Key, state: State) {
		LOG.debug { "Setting up state for mesh key $key" }
		with(globalSettings) {
			levelOfDetailProperty.addListener(managerCancelAndUpdate)
			coarsestScaleLevelProperty.addListener(managerCancelAndUpdate)
			finestScaleLevelProperty.addListener(managerCancelAndUpdate)
		}
	}

	@Synchronized
	protected open fun releaseMeshState(key: Key, state: State) {
		state.colorProperty().unbind()
		with(globalSettings) {
			unbind()
			levelOfDetailProperty.removeListener(managerCancelAndUpdate)
			coarsestScaleLevelProperty.removeListener(managerCancelAndUpdate)
			finestScaleLevelProperty.removeListener(managerCancelAndUpdate)
		}
	}

	@Synchronized
	open fun removeAllMeshes() {
		manager.removeAllMeshes { key, state -> releaseMeshState(key, state) }
	}

	@Synchronized
	open fun removeMesh(key: Key) {
		manager.removeMeshFor(key) { meshKey, state -> releaseMeshState(meshKey, state) }
	}

	@Synchronized
	open fun removeMeshes(keys: Iterable<Key>) {
		manager.removeMeshesFor(keys) { key, state -> releaseMeshState(key, state) }
	}

	open fun refreshMeshes() {
		val currentMeshKeys = manager.allMeshKeys
		this.removeAllMeshes()
		if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
		runBlocking {
			launch {
				currentMeshKeys.forEach {
					createMeshFor(it)
				}
			}
		}
	}
}


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
	managers: ExecutorService,
	workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
	meshViewUpdateQueue: MeshViewUpdateQueue<Key>
) : MeshManager<Key>(
	source,
	getBlockList,
	getMeshFor,
	viewFrustumProperty,
	eyeToWorldTransformProperty,
	managers,
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
