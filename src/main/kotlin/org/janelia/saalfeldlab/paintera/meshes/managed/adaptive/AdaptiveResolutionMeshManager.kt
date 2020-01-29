package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import net.imglib2.Interval
import net.imglib2.cache.Cache
import net.imglib2.cache.CacheLoader
import net.imglib2.cache.Invalidate
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.util.Pair
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
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

    class Settings {

        private val _meshesEnabled: BooleanProperty = SimpleBooleanProperty(true)
        var isMeshesEnabled: Boolean
            get() = _meshesEnabled.get()
            set(meshesEnabled) = _meshesEnabled.set(meshesEnabled)
        fun meshesEnabledProperty() = _meshesEnabled

        private val _showBlockBoundaries: BooleanProperty = SimpleBooleanProperty(false)
        var isShowBlockBounadries: Boolean
            get() = _showBlockBoundaries.get()
            set(showBlockBoundaries) = _showBlockBoundaries.set(showBlockBoundaries)
        fun showBlockBoundariesProperty() = _showBlockBoundaries

        private val _blockSize: IntegerProperty = SimpleIntegerProperty(Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE)
        var blockSize: Int
            get() = _blockSize.value
            set(blockSize) = _blockSize.set(blockSize)
        fun blockSizeProperty() = _blockSize

        private val _numElementsPerFrame: IntegerProperty = SimpleIntegerProperty(Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE)
        var numElementsPerFrame: Int
            get() = _numElementsPerFrame.get()
            set(numElementsPerFrame) = _numElementsPerFrame.set(numElementsPerFrame)
        fun numElementsPerFrameProperty() = _numElementsPerFrame

        private val _frameDelayMsec: LongProperty = SimpleLongProperty(Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE)
        var frameDelayMsec: Long
            get() = _frameDelayMsec.get()
            set(delayMsec) = _frameDelayMsec.set(delayMsec)
        fun frameDelayMsecProperty() = _frameDelayMsec

        private val _sceneUpdateDelayMsec: LongProperty = SimpleLongProperty(Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE)
        var sceneUpdateDelayMsec: Long
            get() = _sceneUpdateDelayMsec.get()
            set(delayMsec) = _sceneUpdateDelayMsec.set(delayMsec)
        fun sceneUpdateDelayMsecProperty() = _sceneUpdateDelayMsec
    }

    override val meshesGroup = Group()
    val rendererSettings = Settings()
    private val _meshesAndViewerEnabled = rendererSettings
        .meshesEnabledProperty()
        .and(viewerEnabled)
        .also { it.addListener { _, _, enabled -> if (enabled) interruptAll() else replaceInterruptedGenerators() } }
    private val isMeshesAndViewerEnabled: Boolean
        get() = _meshesAndViewerEnabled.get()

    private val meshes = Collections.synchronizedMap(HashMap<ObjectKey, MeshGenerator<ObjectKey>>())
    private val unshiftedWorldTransforms: Array<AffineTransform3D> = DataSource.getUnshiftedWorldTransforms(source, 0)
    private val sceneUpdateHandler: SceneUpdateHandler
    private val cancelUpdateAndStartNewUpdate: InvalidationListener
    private var rendererGrids: Array<CellGrid>? = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSize)
    private val sceneUpdateService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-sceneupdate-%d",
            true))
    private val sceneUpdateParametersProperty: ObjectProperty<SceneUpdateParameters?> =
        SimpleObjectProperty()
    private var currentSceneUpdateTask: Future<*>? = null
    private var scheduledSceneUpdateTask: Future<*>? = null

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
    fun refreshMesh(key: ObjectKey) {
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
        meshesGroup.children -= generator.root
        generator.state
    }

    @Synchronized
    fun removeAllMeshes() = allMeshKeys.map { removeMeshFor(it) }

    @Synchronized
    private fun interruptAll() = meshes.values.forEach { it.interrupt() }

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
        cancelUpdateAndStartNewUpdate = InvalidationListener { cancelAndUpdate() }
        viewFrustum.addListener(cancelUpdateAndStartNewUpdate)
        // TODO what to do about refreshMeshes? What if it should not called from within here but by class holding this as member?
        _meshesAndViewerEnabled.addListener { _: ObservableValue<out Boolean>?, _: Boolean?, newv: Boolean -> if (newv) refreshMeshes() else interruptAll() }
        rendererSettings.blockSizeProperty().addListener { _: Observable? ->
            synchronized(this) {
                rendererGrids = RendererBlockSizes.getRendererGrids(source, rendererSettings.blockSizeProperty().get())
                refreshMeshes()
            }
        }

        sceneUpdateHandler = SceneUpdateHandler { InvokeOnJavaFXApplicationThread.invoke { update() } }
        rendererSettings.sceneUpdateDelayMsecProperty().addListener { _ -> sceneUpdateHandler.update(rendererSettings.sceneUpdateDelayMsec) }
        eyeToWorldTransform.addListener(sceneUpdateHandler)
        val meshViewUpdateQueueListener = InvalidationListener { meshViewUpdateQueue.update(rendererSettings.numElementsPerFrame, rendererSettings.frameDelayMsec) }
        rendererSettings.numElementsPerFrameProperty().addListener(meshViewUpdateQueueListener)
        rendererSettings.frameDelayMsecProperty().addListener(meshViewUpdateQueueListener)
    }

    @Synchronized
    private fun addMesh(
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
        // TODO should settings and rendererSettings even be part of this class? Or should enclosing classes take care of this?
        // TODO for example, MeshManagerWithAssignmentForSegmentsKotlin.setupGeneratorState
        meshGenerator.state.showBlockBoundariesProperty().bind(rendererSettings.showBlockBoundariesProperty())
        // TODO will this binding be garbage collected at some point? Should it be stored in a map?
        Bindings.createObjectBinding(
            Callable {
                if (meshGenerator.state.settings.isVisible && isMeshesAndViewerEnabled) {
                    refreshMesh(key)
                } else {
                    meshGenerator.interrupt()
                }
                null as Any?
            },
            meshGenerator.state.settings.visibleProperty(),
            _meshesAndViewerEnabled)
        meshes[key] = meshGenerator
        meshesGroup.children += meshGenerator.root
        if (!isMeshesAndViewerEnabled)
            meshGenerator.interrupt()
        cancelAndUpdate()
        return meshGenerator.state
    }

    @JvmOverloads
    fun createMeshFor(
        key: ObjectKey,
        state: MeshGenerator.State = MeshGenerator.State()) = addMesh(key, state)

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

    interface GetBlockListFor<Key> {
        fun getBlocksFor(level: Int, key: Key): Array<Interval>?
    }

    interface GetMeshFor<Key> {
        fun getMeshFor(key: ShapeKey<Key>): PainteraTriangleMesh?

        class FromCache<Key>(private val cache: Cache<ShapeKey<Key>?, PainteraTriangleMesh?>)
            : GetMeshFor<Key>, Invalidate<ShapeKey<Key>?> by cache {
            override fun getMeshFor(key: ShapeKey<Key>) = cache[key]

            companion object {
                @JvmStatic
                fun <Key> from(cache: Cache<ShapeKey<Key>?, PainteraTriangleMesh?>) = FromCache(cache)

                @JvmStatic
                @JvmOverloads
                fun <Key> fromLoader(
                    loader: CacheLoader<ShapeKey<Key>?, PainteraTriangleMesh?>,
                    cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = from(cache.withLoader(loader))

                @JvmStatic
                @JvmOverloads
                fun <Key> fromLoaders(
                    vararg loader: CacheLoader<ShapeKey<Key>?, PainteraTriangleMesh?>,
                    cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = fromLoader(
                    CacheLoader { key: ShapeKey<Key>? -> key?.let { loader[it.scaleIndex()][it] } },
                    cache)

                @JvmStatic
                @JvmOverloads
                fun <Key> fromPairLoader(
                    loader: CacheLoader<ShapeKey<Key>?, Pair<FloatArray, FloatArray>?>,
                    cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = from(cache.withLoader(loader.asPainteraTriangleMeshLoader()))

                @JvmStatic
                @JvmOverloads
                fun <Key> fromPairLoaders(
                    vararg loader: CacheLoader<ShapeKey<Key>?, Pair<FloatArray, FloatArray>?>,
                    cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = fromPairLoader(
                    CacheLoader { key: ShapeKey<Key>? -> key?.let { loader[it.scaleIndex()][it] } },
                    cache)

                private fun <Key> CacheLoader<ShapeKey<Key>?, Pair<FloatArray, FloatArray>?>.asPainteraTriangleMeshLoader() = CacheLoader { key: ShapeKey<Key>? ->
                    key?.let { k -> this[k]?.let { PainteraTriangleMesh(it.a, it.b) } }
                }
            }
        }
    }

}
