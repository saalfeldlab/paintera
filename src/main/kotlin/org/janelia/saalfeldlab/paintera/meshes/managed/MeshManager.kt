package org.janelia.saalfeldlab.paintera.meshes.managed

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections.*
import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap
import javafx.scene.Group
import javafx.util.Subscription
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
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

	protected val meshStates: ObservableMap<Key, MeshGenerator.State> = synchronizedObservableMap(observableMap(mutableMapOf()))

	// TODO This listener is added to all mesh states. This is a problem if a lot of ids are selected
	//  and all use global mesh settings. Whenever the global mesh settings are changed, the
	//  managerCancelAndUpdate would be notified for each of the meshes, which can temporarily slow down
	//  the UI for quite some time (tens of seconds). A smarter way might be a single thread executor that
	//  executes only the last request and has a delay.
	//  This may be fixed now by using manager.requestCancelAndUpdate(), which submits a task
	//  to a LatestTaskExecutor with a delay of 100ms.
	protected val managerCancelAndUpdate = InvalidationListener { manager.requestCancelAndUpdate() }

	open fun getStateFor(key: Key) = manager.getStateFor(key)

	fun getSettings(key: Key): MeshSettings = managedSettings.getMeshSettings(key)

	fun submitMeshJob(key: Key) = runBlocking { async { createMeshFor(key) } }

	open suspend fun createMeshFor(key: Key) {
		manager.createMeshFor(key, true, stateSetup = ::setupMeshState)
	}

	protected open fun setupMeshState(key: Key, state: MeshGenerator.State) {
		LOG.debug { "Setting up state for mesh key $key" }
		with(globalSettings) {
			levelOfDetailProperty.addListener(managerCancelAndUpdate)
			coarsestScaleLevelProperty.addListener(managerCancelAndUpdate)
			finestScaleLevelProperty.addListener(managerCancelAndUpdate)
		}
		meshStates += key to state
	}

	@Synchronized
	protected open fun releaseMeshState(key: Key, state: MeshGenerator.State) {
		state.colorProperty().unbind()
		with(globalSettings) {
			unbind()
			levelOfDetailProperty.removeListener(managerCancelAndUpdate)
			coarsestScaleLevelProperty.removeListener(managerCancelAndUpdate)
			finestScaleLevelProperty.removeListener(managerCancelAndUpdate)
		}
		meshStates -= key
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

	open fun subscribeToMeshState(key: Key, valueListener: (MeshGenerator.State?) -> Unit): Subscription {
		val listener = MapChangeListener<Key, MeshGenerator.State> { change ->
			when {
				change.key != key -> Unit //Nothing if not the key we care about
				change.wasAdded() -> valueListener(change.valueAdded)
				change.wasRemoved() -> valueListener(null)
			}
		}
		meshStates.addListener(listener)
		valueListener(meshStates[key])
		return Subscription { meshStates.removeListener(listener) }
	}
}