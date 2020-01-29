package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import net.imglib2.cache.Invalidate
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerSettings
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.function.BooleanSupplier
import java.util.function.IntFunction

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class AdaptiveResolutionMeshManager<ObjectKey> @JvmOverloads constructor(
    private val source: DataSource<*, *>,
    private val getBlockListFor: GetBlockListFor<ObjectKey>,
    private val getMeshFor: GetMeshFor<ObjectKey>,
    private val viewFrustum: ObservableValue<ViewFrustum>,
    private val eyeToWorldTransform: ObservableValue<AffineTransform3D>,
    private val viewerEnabled: ObservableBooleanValue,
    private val managers: ExecutorService,
    private val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    private val meshViewUpdateQueue: MeshViewUpdateQueue<ObjectKey>)
    : PainteraMeshManager<ObjectKey> {

    override val meshesGroup = Group()
    val rendererSettings = MeshManagerSettings()
    private val _meshesAndViewerEnabled = rendererSettings
        .meshesEnabledProperty()
        .and(viewerEnabled)
        .also { it.addListener { _, _, enabled -> if (enabled) interruptAll() else replaceInterruptedGenerators() } }
    private val isMeshesAndViewerEnabled: Boolean
        get() = _meshesAndViewerEnabled.get()

    private val meshes = Collections.synchronizedMap(HashMap<ObjectKey, MeshGenerator<ObjectKey>>())
    private val unshiftedWorldTransforms: Array<AffineTransform3D> = DataSource.getUnshiftedWorldTransforms(source, 0)
    private val sceneUpdateHandler: SceneUpdateHandler = SceneUpdateHandler { InvokeOnJavaFXApplicationThread.invoke { update() } }
    private val cancelUpdateAndStartNewUpdate: InvalidationListener = InvalidationListener { cancelAndUpdate() }
    private var rendererGrids: Array<CellGrid>? = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSize)
    private val sceneUpdateService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-sceneupdate-%d",
            true))
    private val sceneUpdateParametersProperty: ObjectProperty<SceneUpdateParameters?> =
        SimpleObjectProperty()
    private var currentSceneUpdateTask: Future<*>? = null
    private var scheduledSceneUpdateTask: Future<*>? = null

    private val meshesAndViewerEnabledListenersInterruptGeneratorMap = mutableMapOf<MeshGenerator<ObjectKey>, ChangeListener<Boolean>>()

    @Synchronized
    private fun replaceInterruptedGenerators() {
        val interrupted = meshes
            .filterValues { it.isInterrupted }
            .mapValues { (_, g) -> g.state }
        interrupted.keys.forEach { removeMeshFor(it) }
        interrupted.forEach { (k, v) -> createMeshFor(k, v) }
        cancelAndUpdate()
    }

    @Synchronized
    fun replaceMesh(key: ObjectKey) {
        val state = removeMeshFor(key)
        if (state == null)
            createMeshFor(key)
        else
            createMeshFor(key, state)
    }

    @Synchronized
    override fun refreshMeshes() {
        if (!isMeshesAndViewerEnabled) return
        val meshStates = meshes.mapValues { (_, v) -> v.state }
        removeAllMeshes()
        if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
        meshStates.forEach { (k, v) -> createMeshFor(k, v) }
    }

    @Synchronized
    private fun update() {
        val rendererGrids = this.rendererGrids
        if (rendererGrids == null || !isMeshesAndViewerEnabled) return
        val sceneUpdateParameters = SceneUpdateParameters(viewFrustum.value, eyeToWorldTransform.value, rendererGrids)

        val needToSubmit = sceneUpdateParametersProperty.get() == null
        sceneUpdateParametersProperty.set(sceneUpdateParameters)
        if (needToSubmit && !managers.isShutdown)
            scheduledSceneUpdateTask = sceneUpdateService.submit(withErrorPrinting { updateScene() })

    }

    private fun updateScene() {
        assert(!Platform.isFxApplicationThread())
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
    fun removeMeshFor(key: ObjectKey) = meshes.remove(key)?.let { generator ->
        generator.interrupt()
        generator.unbindFromThis()
        meshesGroup.children -= generator.root
        generator.state
    }

    @Synchronized
    fun removeAllMeshes() = allMeshKeys.map { removeMeshFor(it) }

    @Synchronized
    private fun interruptAll() = meshes.values.forEach { it.interrupt() }

    @Synchronized
    private fun replaceOrInterrupt(replace: Boolean) = if (replace) replaceInterruptedGenerators() else interruptAll()

    @get:Synchronized
    private val allMeshKeys: Collection<ObjectKey>
        get() = meshes.keys.toList()

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun withErrorPrinting(func: () -> Unit) = withErrorPrinting(Runnable { func() })

        private fun withErrorPrinting(runnable: Runnable): Runnable {
            return Runnable {
                try {
                    runnable.run()
                } catch (e: RejectedExecutionException) { // this happens when the application is being shut down and is normal, don't do anything
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    init {
        viewFrustum.addListener(cancelUpdateAndStartNewUpdate)
        _meshesAndViewerEnabled.addListener { _, _, newv: Boolean -> replaceOrInterrupt(newv) }
        rendererSettings.blockSizeProperty().addListener { _: Observable? ->
            synchronized(this) {
                rendererGrids = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSizeProperty().get())
                refreshMeshes()
            }
        }

        rendererSettings.sceneUpdateDelayMsecProperty().addListener { _ -> sceneUpdateHandler.update(rendererSettings.sceneUpdateDelayMsec) }
        eyeToWorldTransform.addListener(sceneUpdateHandler)
        val meshViewUpdateQueueListener = InvalidationListener { meshViewUpdateQueue.update(rendererSettings.numElementsPerFrame, rendererSettings.frameDelayMsec) }
        rendererSettings.numElementsPerFrameProperty().addListener(meshViewUpdateQueueListener)
        rendererSettings.frameDelayMsecProperty().addListener(meshViewUpdateQueueListener)
    }

    @Synchronized
    @JvmOverloads
    fun createMeshFor(
        key: ObjectKey,
        state: MeshGenerator.State = MeshGenerator.State()): MeshGenerator.State? {
        if (key in meshes) return meshes[key]?.state
        val meshGenerator: MeshGenerator<ObjectKey> = MeshGenerator<ObjectKey>(
            source.numMipmapLevels,
            key,
            getBlockListFor,
            getMeshFor,
            meshViewUpdateQueue,
            IntFunction { level: Int -> unshiftedWorldTransforms[level] },
            managers,
            workers,
            state)
        meshGenerator.bindToThis()
        meshes[key] = meshGenerator
        meshesGroup.children += meshGenerator.root
        if (!isMeshesAndViewerEnabled)
            meshGenerator.interrupt()
        // TODO is this cancelAndUpdate necessary?
        cancelAndUpdate()
        return meshGenerator.state
    }

    @Synchronized
    fun cancelAndUpdate() {
        currentSceneUpdateTask?.cancel(true)
        currentSceneUpdateTask = null
        scheduledSceneUpdateTask?.cancel(true)
        sceneUpdateParametersProperty.set(null)
        update()
    }

    @Synchronized
    fun contains(key: ObjectKey) = key in meshes

    @Synchronized
    fun getStateFor(key: ObjectKey) = meshes[key]?.state

    @Synchronized
    private fun MeshGenerator<ObjectKey>.bindToThis() {
        this.state.showBlockBoundariesProperty().bind(rendererSettings.showBlockBoundariesProperty())
        // TODO will this binding be garbage collected at some point? Should it be stored in a map?
        val listener = ChangeListener<Boolean> { _, _, isEnabled -> if (isEnabled) replaceMesh(this.id) else this.interrupt() }
        _meshesAndViewerEnabled.addListener(listener)
        meshesAndViewerEnabledListenersInterruptGeneratorMap[this] = listener
    }

    @Synchronized
    private fun MeshGenerator<ObjectKey>.unbindFromThis() {
        this.state.showBlockBoundariesProperty().unbind()
        meshesAndViewerEnabledListenersInterruptGeneratorMap[this]?.let { _meshesAndViewerEnabled.removeListener(it) }
    }


}
