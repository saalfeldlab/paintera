package org.janelia.saalfeldlab.paintera.meshes.managed

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections.observableMap
import javafx.collections.FXCollections.synchronizedObservableMap
import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap
import javafx.scene.Group
import javafx.util.Subscription
import kotlinx.coroutines.*
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.PainteraDispatchers
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor

abstract class MeshManager<Key>(
	val source: DataSource<*, *>,
	val getBlockListFor: GetBlockListFor<Key>,
	val getMeshFor: GetMeshFor<Key>,
	viewFrustumProperty: ObservableValue<ViewFrustum>,
	eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
	val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
	meshViewUpdateQueue: MeshViewUpdateQueue<Key>,
) {

	companion object {
		val LOG = KotlinLogging.logger { }
	}

	val viewerEnabledProperty: BooleanProperty = SimpleBooleanProperty(false)

	protected val manager: AdaptiveResolutionMeshManager<Key> = AdaptiveResolutionMeshManager(
		source,
		getBlockListFor,
		getMeshFor,
		viewFrustumProperty,
		eyeToWorldTransformProperty,
		viewerEnabledProperty,
		workers,
		meshViewUpdateQueue
	)

	val rendererSettings = manager.rendererSettings

	val managedSettings = ManagedMeshSettings<Key>(source.numMipmapLevels).apply { rendererSettings.meshesEnabledProperty.bind(meshesEnabledProperty) }

	val globalSettings: MeshSettings = managedSettings.globalSettings

	val meshesGroup: Group = manager.meshesGroup

	private val meshStateListeners: ObservableMap<Key, MutableList<(MeshGenerator.State?) -> Unit>?> = synchronizedObservableMap(observableMap(mutableMapOf()))
	private val meshStateChangeListener = MapChangeListener<Key, MeshGenerator.State?> { change ->

		meshStateListeners[change.key]?.forEach { listener ->
			/* change.valueAdded is the value if a value was added, or is `null` if the entry was removed */
			listener(change.valueAdded)
		}
	}
	protected val meshStates: ObservableMap<Key, MeshGenerator.State> = synchronizedObservableMap(observableMap(mutableMapOf()))

	init {
		meshStates.addListener(meshStateChangeListener)
	}

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
		meshStates[key] = state
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

	protected val meshManagerScope = CoroutineScope(PainteraDispatchers.MeshManagerDispatcher + SupervisorJob())

	open fun refreshMeshes() {
		val keys = manager.meshKeys
		this.removeAllMeshes()
		if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
		meshManagerScope.launch {
			keys.forEach {
				createMeshFor(it)
			}
		}
	}

	open fun subscribeToMeshState(key: Key, valueListener: (MeshGenerator.State?) -> Unit): Subscription {
		/* track the listener */
		synchronized(this) {
			meshStateListeners.compute(key) { _, listeners -> (listeners ?: mutableListOf()).also { it += valueListener } }
		}
		/* trigger with current value*/
		valueListener(meshStates[key])
		/* removal Subscription */
		return Subscription {
			synchronized(this) {
				meshStateListeners.compute(key) { _, listeners -> listeners?.also { it -= valueListener }?.takeUnless { it.isEmpty() } }
			}
		}
	}
}