package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.ChannelLoop
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerModel
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Consumer

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
	private val managers: ExecutorService,
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
	private val sceneUpdateHandler: SceneUpdateHandler = SceneUpdateHandler { InvokeOnJavaFXApplicationThread.invoke { update() } }
	private var rendererGrids: Array<CellGrid>? = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSize)
	private val sceneUpdateService = ChannelLoop(name = "Scene Update Service", capacity = Channel.CONFLATED)
	private val sceneUpdateParametersProperty: ObjectProperty<SceneUpdateParameters?> = SimpleObjectProperty()
	private var currentSceneUpdateTask: Job? = null
	private var scheduledSceneUpdateTask: Job? = null

	private val meshesAndViewerEnabledListenersInterruptGeneratorMap: MutableMap<MeshGenerator<ObjectKey>, ChangeListener<Boolean>> = mutableMapOf()

	@get:Synchronized
	val allMeshKeys: Collection<ObjectKey>
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

		rendererSettings.sceneUpdateDelayMsecProperty.addListener { _ -> sceneUpdateHandler.update(rendererSettings.sceneUpdateDelayMsec) }
		eyeToWorldTransform.addListener(sceneUpdateHandler)
		val meshViewUpdateQueueListener =
			InvalidationListener { meshViewUpdateQueue.update(rendererSettings.numElementsPerFrame, rendererSettings.frameDelayMsec) }
		rendererSettings.numElementsPerFrameProperty.addListener(meshViewUpdateQueueListener)
		rendererSettings.frameDelayMsecProperty.addListener(meshViewUpdateQueueListener)
	}

	@Synchronized
	private fun replaceMesh(key: ObjectKey, cancelAndUpdate: Boolean) {
		val state = removeMeshFor(key) { _, _ -> }
		state
			?.let { s -> createMeshFor(key, cancelAndUpdate = cancelAndUpdate, state = s, stateSetup = { _, _ -> }) }
			?: createMeshFor(key, cancelAndUpdate = cancelAndUpdate, stateSetup = { _, _ -> })
	}

	@Synchronized
	private fun replaceAllMeshes() = allMeshKeys.map { replaceMesh(it, false) }.also { cancelAndUpdate() }

	@Synchronized
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

	@Synchronized
	fun removeMeshesFor(keys: Iterable<ObjectKey>, releaseState: (ObjectKey, MeshGenerator.State) -> Unit) {
		val keysAndGenerators = synchronized(this) { keys.map { it to meshes.remove(it) } }
		val roots = keysAndGenerators.mapNotNull { (key, generator) ->
			generator?.run {
				interrupt()
				unbindFromThis()
				root?.visibleProperty()?.unbind()
				releaseState(key, state)
				root
			}
		}
		InvokeOnJavaFXApplicationThread {
			// The roots list has to be converted to array first and then passed as vararg
			// to use the implementation in ObservableList instead of the Kotlin extension
			// function.
			meshesGroup.children.removeAll(*roots.toTypedArray())
		}
	}

	fun removeAllMeshes(releaseState: (ObjectKey, MeshGenerator.State) -> Unit) = removeMeshesFor(allMeshKeys, releaseState)

	fun createMeshFor(
		key: ObjectKey,
		cancelAndUpdate: Boolean,
		state: MeshGenerator.State = MeshGenerator.State(),
		stateSetup: (ObjectKey, MeshGenerator.State) -> Unit,
	): Boolean {
		return createMeshFor(key, cancelAndUpdate, state, Consumer { stateSetup(key, it) })
	}

	@JvmOverloads
	fun createMeshFor(
		key: ObjectKey,
		cancelAndUpdate: Boolean,
		state: MeshGenerator.State? = MeshGenerator.State(),
		stateSetup: Consumer<MeshGenerator.State> = Consumer {},
	): Boolean {
		if (state == null) return false
		val meshGenerator = synchronized(this) {
			if (key in meshes) return false
			stateSetup.accept(state)
			MeshGenerator(
				source.numMipmapLevels,
				key,
				getBlockListFor,
				getMeshFor,
				meshViewUpdateQueue,
				{ level: Int -> unshiftedWorldTransforms[level] },
				managers,
				workers,
				state
			).also { meshes[key] = it }
		}
		meshGenerator.bindToThis()

		// If the viewer or the manager are disabled, interrupt the generator right away because
		// it should not add any meshes to the scene. Once viewer and manager are enabled again,
		// interrupted generators will be replaced appropriately.
		if (!isMeshesAndViewerEnabled)
			meshGenerator.interrupt()

		InvokeOnJavaFXApplicationThread {
			meshesGroup.children += meshGenerator.root
			// TODO is this cancelAndUpdate necessary?
			if (cancelAndUpdate)
				cancelAndUpdate()
		}
		return true
	}

	@Synchronized
	fun contains(key: ObjectKey) = key in meshes

	@Synchronized
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
		if (needToSubmit && !managers.isShutdown)
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
				for ((blockTreeParametersKey, value) in blockTreeParametersKeysToMeshGenerators) {
					val sceneBlockTreeForKey =
						sceneBlockTrees[blockTreeParametersKey]
					for (meshGenerator in value)
						meshGenerator.update(sceneBlockTreeForKey, sceneUpdateParameters.rendererGrids)
				}
			}
		} finally {
			synchronized(this) { currentSceneUpdateTask = null }
		}
	}

	@Synchronized
	private fun MeshGenerator<ObjectKey>.bindToThis() {
		this.state.showBlockBoundariesProperty().bind(rendererSettings.showBlockBoundariesProperty)
		// Store the listener in a map so it can be removed when the corresponding MeshGenerator is removed to avoid memory leaks.
		val listener = ChangeListener<Boolean> { _, _, isEnabled -> if (isEnabled) replaceMesh(this.id, true) else this.interrupt() }
		meshesAndViewerEnabledBinding.addListener(listener)
		meshesAndViewerEnabledListenersInterruptGeneratorMap[this] = listener
	}

	@Synchronized
	private fun MeshGenerator<ObjectKey>.unbindFromThis() {
		this.state.showBlockBoundariesProperty().unbind()
		meshesAndViewerEnabledListenersInterruptGeneratorMap.remove(this)?.let { meshesAndViewerEnabledBinding.removeListener(it) }
	}
}
