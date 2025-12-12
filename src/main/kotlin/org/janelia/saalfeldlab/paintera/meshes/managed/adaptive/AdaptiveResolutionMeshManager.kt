package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

import javafx.application.Platform
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import javafx.scene.Node
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.ChannelLoop
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.PainteraDispatchers
import org.janelia.saalfeldlab.paintera.PainteraDispatchers.asExecutorService
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerModel
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import java.util.Collections
import java.util.function.BooleanSupplier

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class AdaptiveResolutionMeshManager<ObjectKey>(
	private val source: DataSource<*, *>,
	private val getBlockListFor: GetBlockListFor<ObjectKey>,
	private val getMeshFor: GetMeshFor<ObjectKey>,
	private val viewFrustum: ObservableValue<ViewFrustum>,
	private val eyeToWorldTransform: ObservableValue<AffineTransform3D>,
	private val viewerEnabled: ObservableBooleanValue,
	private val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
	private val meshViewUpdateQueue: MeshViewUpdateQueue<ObjectKey>,
) {

	// Avoid flooding the FX application thread with thousands of calls to cancelAndUpdate() and freezing the
	// UI for tens of seconds. Really only the latest cancelAndUpdate() call matters and it does not have to
	// happen at high frequency so we can run only on pulses.
	private val cancelAndUpdateService = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

	val meshesGroup = Group()
	val rendererSettings = MeshManagerModel()
	private val meshesAndViewerEnabledBinding = rendererSettings.meshesEnabledProperty.and(viewerEnabled)
	private val isMeshesAndViewerEnabled by meshesAndViewerEnabledBinding.nonnullVal()

	private val meshes = Collections.synchronizedMap(HashMap<ObjectKey, MeshGenerator<ObjectKey>>())
	private val unshiftedWorldTransforms: Array<AffineTransform3D> = DataSource.getUnshiftedWorldTransforms(source, 0)
	private val sceneUpdateHandler: SceneUpdateHandler = SceneUpdateHandler { InvokeOnJavaFXApplicationThread { update() } }
	private var rendererGrids: Array<CellGrid>? = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSize)
	private val sceneUpdateService = ChannelLoop(name = "Scene Update Service", capacity = Channel.CONFLATED)
	private val sceneUpdateParametersProperty: ObjectProperty<SceneUpdateParameters?> = SimpleObjectProperty()
	private var currentSceneUpdateTask: Job? = null
	private var scheduledSceneUpdateTask: Job? = null

	private val meshesAndViewerEnabledListenersInterruptGeneratorMap: MutableMap<MeshGenerator<ObjectKey>, ChangeListener<Boolean>> = mutableMapOf()

	val meshKeys: Collection<ObjectKey>
		get() = meshes.keys.toList()

	init {
		viewFrustum.addListener { _ -> cancelAndUpdate() }
		rendererSettings.blockSizeProperty.addListener { _: Observable? ->
			synchronized(this) {
				rendererGrids = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSizeProperty.get())
				// Whenever the block size changes, all meshes need to be replaced.
				replaceAllMeshes()
			}
		}


		rendererSettings.sceneUpdateDelayMsecProperty.subscribe { navigationUpdateDelay -> sceneUpdateHandler.setNavigationUpdateDelay(navigationUpdateDelay.toLong()) }
		eyeToWorldTransform.addListener(sceneUpdateHandler)
		val meshViewUpdateQueueListener =  { meshViewUpdateQueue.update(rendererSettings.numElementsPerFrame, rendererSettings.frameDelayMsec) }
		rendererSettings.numElementsPerFrameProperty.subscribe(meshViewUpdateQueueListener)
		rendererSettings.frameDelayMsecProperty.subscribe(meshViewUpdateQueueListener)
	}

	private fun replaceMesh(key: ObjectKey, cancelAndUpdate: Boolean) {
		val state = removeMeshFor(key) { _, _ -> }
		state
			?.let { s -> createMeshFor(key, cancelAndUpdate = cancelAndUpdate, state = s, stateSetup = { _, _ -> }) }
			?: createMeshFor(key, cancelAndUpdate = cancelAndUpdate, stateSetup = { _, _ -> })
	}

	private fun replaceAllMeshes() = meshKeys.map { replaceMesh(it, false) }.also { cancelAndUpdate() }

	fun removeMeshFor(key: ObjectKey, releaseState: (ObjectKey, MeshGenerator.State) -> Unit): MeshGenerator.State? {
		return meshes.remove(key)?.let { generator ->
			generator.interrupt()
			generator.unbindFromThis()
			generator.root.visibleProperty().unbind()
			releaseState(key, generator.state)
			InvokeOnJavaFXApplicationThread {
				generator.root.isVisible = false
				meshesGroup.children -= generator.root
			}
			generator.state
		}
	}

	val meshManagerScope = CoroutineScope(PainteraDispatchers.MeshManagerDispatcher + SupervisorJob()) + CoroutineName("MeshManager")
	internal var currentMeshJob: Job? = null

	fun removeMeshesFor(keys: Iterable<ObjectKey>, releaseState: (ObjectKey, MeshGenerator.State) -> Unit) {

		val keysAndGenerators = synchronized(this) {
			keys
				.associateWith { meshes.remove(it) }
				.mapNotNull { (key, generator) -> generator?.let { key to it } }
		}

		val removedRoots = Collections.synchronizedSet(mutableSetOf<Node>())

		currentMeshJob = meshManagerScope.launch {
			supervisorScope {
				keysAndGenerators.map { (key, generator) ->
					launch {
						generator.run {
							interrupt()
							unbindFromThis()
							root.visibleProperty().unbind()
							releaseState(key, state)
							removedRoots += root
						}
					}
				}
			}
		}.also {
			it.invokeOnCompletion {
				InvokeOnJavaFXApplicationThread {
					// The roots list has to be converted to array first and then passed as vararg
					// to use the implementation in ObservableList instead of the Kotlin extension
					// function.
					meshesGroup.children.removeAll(*removedRoots.toTypedArray())
				}
			}
		}
	}

	fun removeAllMeshes(releaseState: (ObjectKey, MeshGenerator.State) -> Unit) = removeMeshesFor(meshKeys, releaseState)

	private val createMeshQueue = Channel<CreateMeshGenerator<ObjectKey>>(capacity = Channel.UNLIMITED)

	@Suppress("unused")
	private val createMeshRunner = meshManagerScope.launch {
		val jobs = mutableListOf<Deferred<Pair<ObjectKey, MeshGenerator<ObjectKey>>>>()
		while (isActive) {
			val createMeshGenerators = generateSequence { createMeshQueue.tryReceive().getOrNull() }

			for ((key, update, generator) in createMeshGenerators) {
				if (key !in meshes)
					jobs += async { key to generator() }
			}

			val keyMeshMap = jobs.awaitAll().toMap()
			if (!keyMeshMap.isEmpty()) {
				meshes.putAll(keyMeshMap)

				InvokeOnJavaFXApplicationThread {
					meshesGroup.children += keyMeshMap.values.map { it.root }
					update()
				}
			}

			jobs.clear()

			delay(100)
		}
	}

	private data class CreateMeshGenerator<ObjectKey>(
		val key: ObjectKey,
		val cancelAndUpdate: Boolean,
		val create: () -> MeshGenerator<ObjectKey>,
	)

	@JvmOverloads
	fun createMeshFor(
		key: ObjectKey,
		cancelAndUpdate: Boolean,
		state: MeshGenerator.State? = MeshGenerator.State(),
		stateSetup: (ObjectKey, MeshGenerator.State) -> Unit,
	) {
		if (state == null) return
		createMeshQueue.trySend(
			CreateMeshGenerator(key, cancelAndUpdate) {
				stateSetup(key, state)
				val meshGenerator = MeshGenerator(
					source.numMipmapLevels,
					key,
					getBlockListFor,
					getMeshFor,
					meshViewUpdateQueue,
					{ level: Int -> unshiftedWorldTransforms[level] },
					PainteraDispatchers.MeshManagerDispatcher.asExecutorService(),
					workers,
					state,
				)
				meshGenerator.bindToThis()

				// If the viewer or the manager are disabled, interrupt the generator right away because
				// it should not add any meshes to the scene. Once viewer and manager are enabled again,
				// interrupted generators will be replaced appropriately.
				if (!isMeshesAndViewerEnabled)
					meshGenerator.interrupt()

				meshGenerator
			}
		)
	}

	fun contains(key: ObjectKey) = key in meshes

	fun getStateFor(key: ObjectKey) = meshes[key]?.state

	fun requestCancelAndUpdate() = cancelAndUpdateService.submit { cancelAndUpdate() }

	@Synchronized
	private fun cancelAndUpdate() {
		currentSceneUpdateTask?.cancel()
		currentSceneUpdateTask = null
		scheduledSceneUpdateTask?.cancel()
		scheduledSceneUpdateTask = null
		sceneUpdateParametersProperty.set(null)
		update()
	}

	@Synchronized
	private fun update() {
		assert(Platform.isFxApplicationThread()) { "update() was called on thread ${Thread.currentThread().name} instead of JavaFX application thread." }
		val rendererGrids = this.rendererGrids
		if (rendererGrids == null || !isMeshesAndViewerEnabled) return
		val sceneUpdateParameters = SceneUpdateParameters(viewFrustum.value, eyeToWorldTransform.value, rendererGrids)

		val needToSubmit = sceneUpdateParametersProperty.get() == null
		sceneUpdateParametersProperty.set(sceneUpdateParameters)
		if (needToSubmit && !meshManagerScope.isActive)
			assert(scheduledSceneUpdateTask == null) { "scheduledSceneUpdateTask must be null but is $scheduledSceneUpdateTask" }
		scheduledSceneUpdateTask = sceneUpdateService.submit { updateScene() }
	}

	private fun updateScene() {
		assert(!Platform.isFxApplicationThread()) { "updateScene() must not be called from JavaFX application thread." }
		try {
			val blockTreeParametersKeysToMeshGenerators =
				mutableMapOf<BlockTreeParametersKey, MutableList<MeshGenerator<ObjectKey>>>()
			val wasInterrupted = BooleanSupplier { Thread.currentThread().isInterrupted }
			val sceneUpdateParameters = synchronized(this) {
				if (wasInterrupted.asBoolean) return

				val sceneUpdateParameters = sceneUpdateParametersProperty.get() ?: return
				sceneUpdateParametersProperty.set(null)

				if (scheduledSceneUpdateTask == null) return

				currentSceneUpdateTask = scheduledSceneUpdateTask
				scheduledSceneUpdateTask = null

				for (meshGenerator in meshes.values) {
					val blockTreeParametersKey = BlockTreeParametersKey(meshGenerator.state.settings)
					blockTreeParametersKeysToMeshGenerators
						.computeIfAbsent(blockTreeParametersKey) { mutableListOf() }
						.add(meshGenerator)
				}
				sceneUpdateParameters
			}
			val sceneBlockTrees =
				mutableMapOf<BlockTreeParametersKey, BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>>?>()
			for (blockTreeParametersKey in blockTreeParametersKeysToMeshGenerators.keys) {
				if (wasInterrupted.asBoolean) return
				sceneBlockTrees[blockTreeParametersKey] = SceneBlockTree.createSceneBlockTree(
					source,
					sceneUpdateParameters.viewFrustum,
					sceneUpdateParameters.eyeToWorldTransform,
					blockTreeParametersKey.levelOfDetail,
					blockTreeParametersKey.coarsestScaleLevel,
					blockTreeParametersKey.finestScaleLevel,
					sceneUpdateParameters.rendererGrids,
					wasInterrupted
				)
			}
			synchronized(this) {
				if (wasInterrupted.asBoolean) return
				for ((blockTreeParametersKey, meshGeneratorList) in blockTreeParametersKeysToMeshGenerators) {
					val sceneBlockTreeForKey = sceneBlockTrees[blockTreeParametersKey]
					for (meshGenerator in meshGeneratorList)
						meshGenerator.updateMeshes(sceneBlockTreeForKey, sceneUpdateParameters.rendererGrids)
				}
			}
		} finally {
			synchronized(this) { currentSceneUpdateTask = null }
		}
	}

	@Synchronized
	private fun MeshGenerator<ObjectKey>.bindToThis() {
		this.state.showBlocksProperty().bind(rendererSettings.showBlockBoundariesProperty)
		// Store the listener in a map so it can be removed when the corresponding MeshGenerator is removed to avoid memory leaks.
		val listener = ChangeListener<Boolean> { _, _, isEnabled -> if (isEnabled) replaceMesh(this.id, true) else this.interrupt() }
		meshesAndViewerEnabledBinding.addListener(listener)
		meshesAndViewerEnabledListenersInterruptGeneratorMap[this] = listener
	}

	@Synchronized
	private fun MeshGenerator<ObjectKey>.unbindFromThis() {
		this.state.showBlocksProperty().unbind()
		meshesAndViewerEnabledListenersInterruptGeneratorMap.remove(this)?.let { meshesAndViewerEnabledBinding.removeListener(it) }
	}
}
