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
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerSettings
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.IntFunction
import java.util.function.Function as JFunction

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class AdaptiveResolutionMeshManager<ObjectKey> constructor(
    private val source: DataSource<*, *>,
    private val getBlockListFor: GetBlockListFor<ObjectKey>,
    private val getMeshFor: GetMeshFor<ObjectKey>,
    private val viewFrustum: ObservableValue<ViewFrustum>,
    private val eyeToWorldTransform: ObservableValue<AffineTransform3D>,
    private val viewerEnabled: ObservableBooleanValue,
    private val managers: ExecutorService,
    private val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    private val meshViewUpdateQueue: MeshViewUpdateQueue<ObjectKey>) {

    private val bindService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "adaptive-resolution-meshmanager-bind-%d",
            true))

    private val unbindService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "adaptive-resolution-meshmanager-unbind-%d",
            true))

    val meshesGroup = Group()
    val rendererSettings = MeshManagerSettings()
    private val _meshesAndViewerEnabled = rendererSettings
        .meshesEnabledProperty()
        .and(viewerEnabled)
    private val isMeshesAndViewerEnabled: Boolean
        get() = _meshesAndViewerEnabled.get()

    private val meshes = Collections.synchronizedMap(HashMap<ObjectKey, MeshGenerator<ObjectKey>>())
    private val unshiftedWorldTransforms: Array<AffineTransform3D> = DataSource.getUnshiftedWorldTransforms(source, 0)
    private val sceneUpdateHandler: SceneUpdateHandler = SceneUpdateHandler { InvokeOnJavaFXApplicationThread.invoke { update() } }
    private var rendererGrids: Array<CellGrid>? = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSize)
    private val sceneUpdateService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-sceneupdate-%d",
            true))
    private val sceneUpdateParametersProperty: ObjectProperty<SceneUpdateParameters?> = SimpleObjectProperty()
    private var currentSceneUpdateTask: Future<*>? = null
    private var scheduledSceneUpdateTask: Future<*>? = null

    private val meshesAndViewerEnabledListenersInterruptGeneratorMap: MutableMap<MeshGenerator<ObjectKey>, ChangeListener<Boolean>> = mutableMapOf()

    @get:Synchronized
    val allMeshKeys: Collection<ObjectKey>
        get() = meshes.keys.toList()

    init {
        viewFrustum.addListener { _ -> cancelAndUpdate() }
        rendererSettings.blockSizeProperty().addListener { _: Observable? ->
            synchronized(this) {
                rendererGrids = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSizeProperty().get())
                // Whenever the block size changes, all meshes need to be replaced.
                replaceAllMeshes()
            }
        }

        rendererSettings.sceneUpdateDelayMsecProperty().addListener { _ -> sceneUpdateHandler.update(rendererSettings.sceneUpdateDelayMsec) }
        eyeToWorldTransform.addListener(sceneUpdateHandler)
        val meshViewUpdateQueueListener = InvalidationListener { meshViewUpdateQueue.update(rendererSettings.numElementsPerFrame, rendererSettings.frameDelayMsec) }
        rendererSettings.numElementsPerFrameProperty().addListener(meshViewUpdateQueueListener)
        rendererSettings.frameDelayMsecProperty().addListener(meshViewUpdateQueueListener)
    }

    @Synchronized
    private fun replaceMesh(key: ObjectKey, cancelAndUpdate: Boolean) {
        val state = removeMeshFor(key) { }
        state
            ?.let { s -> createMeshFor(key, cancelAndUpdate = cancelAndUpdate, state = s, stateSetup = { }) }
            ?: createMeshFor(key, cancelAndUpdate = cancelAndUpdate, stateSetup = { })
    }

    @Synchronized
    private fun replaceAllMeshes() = allMeshKeys.map { replaceMesh(it, false) }.also { cancelAndUpdate() }

    fun removeMeshFor(key: ObjectKey, releaseState: (MeshGenerator.State) -> Unit) = removeMeshFor(key, Consumer { releaseState(it) })

    @Synchronized
    fun removeMeshFor(key: ObjectKey, releaseState: Consumer<MeshGenerator.State>): MeshGenerator.State?  {
        return meshes.remove(key)?.let { generator ->
            unbindService.submit {
                generator.interrupt()
                generator.unbindFromThis()
                generator.root.visibleProperty().unbind()
                releaseState.accept(generator.state)
                Platform.runLater {
                    generator.root.isVisible = false
                    meshesGroup.children -= generator.root
                }
            }
            generator.state
        }
    }

    fun removeMeshesFor(keys: Iterable<ObjectKey>, releaseState: (MeshGenerator.State) -> Unit) = removeMeshesFor(keys, Consumer { releaseState(it) })

    fun removeMeshesFor(keys: Iterable<ObjectKey>, releaseState: Consumer<MeshGenerator.State>) {
        val generators = synchronized(this) { keys.map { meshes.remove(it) } }
        unbindService.submit {
            val roots = generators.mapNotNull { generator ->
                generator?.interrupt()
                generator?.unbindFromThis()
                generator?.root?.visibleProperty()?.unbind()
                generator?.let { releaseState.accept(it.state) }
                generator?.root
            }
            Platform.runLater {
                // The roots list has to be converted to array first and then passed as vararg
                // to use the implementation in ObservableList instead of the Kotlin extension
                // function.
                meshesGroup.children.removeAll(*roots.toTypedArray())
            }
        }
    }

    fun removeAllMeshes(releaseState: (MeshGenerator.State) -> Unit) = removeAllMeshes(Consumer { releaseState(it) })

    fun removeAllMeshes(releaseState: Consumer<MeshGenerator.State>) = removeMeshesFor(allMeshKeys, releaseState)

    fun createMeshFor(
        key: ObjectKey,
        cancelAndUpdate: Boolean,
        state: MeshGenerator.State = MeshGenerator.State(),
        stateSetup: (MeshGenerator.State) -> Unit) = createMeshFor(key, cancelAndUpdate, state, Consumer { stateSetup(it) })

    @JvmOverloads
    fun createMeshFor(
        key: ObjectKey,
        cancelAndUpdate: Boolean,
        state: MeshGenerator.State? = MeshGenerator.State(),
        stateSetup: Consumer<MeshGenerator.State> = Consumer {}): Boolean {
        if (state === null) return false
        val meshGenerator = synchronized(this) {
            if (key in meshes) return false
            MeshGenerator<ObjectKey>(
                source.numMipmapLevels,
                key,
                getBlockListFor,
                getMeshFor,
                meshViewUpdateQueue,
                IntFunction { level: Int -> unshiftedWorldTransforms[level] },
                managers,
                workers,
                state).also { meshes[key] = it }
        }

        // If the viewer or the manager are disabled, interrupt the generator right away because
        // it should not add any meshes to the scene. Once viewer and manager are enabled again,
        // interrupted generators will be replaced appropriately.
        if (!isMeshesAndViewerEnabled)
            meshGenerator.interrupt()
        bindService.submit {
            meshGenerator.bindToThis()
            meshGenerator.state.showBlockBoundariesProperty().bind(rendererSettings.showBlockBoundariesProperty())
            stateSetup.accept(state)
            Platform.runLater {
                meshesGroup.children += meshGenerator.root
                // TODO is this cancelAndUpdate necessary?
                if (cancelAndUpdate)
                    cancelAndUpdate()
            }
        }
        return true
    }

    @Synchronized
    fun contains(key: ObjectKey) = key in meshes

    @Synchronized
    fun getStateFor(key: ObjectKey) = meshes[key]?.state

    @Synchronized
    fun cancelAndUpdate() {
        currentSceneUpdateTask?.cancel(true)
        currentSceneUpdateTask = null
        scheduledSceneUpdateTask?.cancel(true)
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
            assert(scheduledSceneUpdateTask === null) { "scheduledSceneUpdateTask mut be null but is $scheduledSceneUpdateTask" }
            scheduledSceneUpdateTask = sceneUpdateService.submit(withErrorPrinting { updateScene() })
    }

    private fun updateScene() {
        assert(!Platform.isFxApplicationThread()) { "updateScene() must not be called from JavaFX application thread."}
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
                    wasInterrupted)
            }
            synchronized(this) {
                if (wasInterrupted.asBoolean) return
                for ((blockTreeParametersKey, value) in blockTreeParametersKeysToMeshGenerators) {
                    val sceneBlockTreeForKey =
                        sceneBlockTrees[blockTreeParametersKey]
                    for (meshGenerator in value) meshGenerator.update(
                        sceneBlockTreeForKey,
                        sceneUpdateParameters.rendererGrids)
                }
            }
        } finally {
            synchronized(this) { currentSceneUpdateTask = null }
        }
    }

    @Synchronized
    private fun MeshGenerator<ObjectKey>.bindToThis() {
        this.state.showBlockBoundariesProperty().bind(rendererSettings.showBlockBoundariesProperty())
        // TODO will this binding be garbage collected at some point? Should it be stored in a map?
        val listener = ChangeListener<Boolean> { _, _, isEnabled -> if (isEnabled) replaceMesh(this.id, true) else this.interrupt() }
        _meshesAndViewerEnabled.addListener(listener)
        meshesAndViewerEnabledListenersInterruptGeneratorMap[this] = listener
    }

    @Synchronized
    private fun MeshGenerator<ObjectKey>.unbindFromThis() {
        this.state.showBlockBoundariesProperty().unbind()
        meshesAndViewerEnabledListenersInterruptGeneratorMap[this]?.let { _meshesAndViewerEnabled.removeListener(it) }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun withErrorPrinting(func: () -> Unit) = withErrorPrinting(Runnable { func() })

        private fun withErrorPrinting(runnable: Runnable): Runnable {
            return Runnable {
                try {
                    runnable.run()
                } catch (e: RejectedExecutionException) {
                    // this happens when the application is being shut down and is normal, don't do anything
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

}
