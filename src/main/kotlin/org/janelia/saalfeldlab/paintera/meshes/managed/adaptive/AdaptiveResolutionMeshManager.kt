package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import javafx.scene.paint.Color
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager.GetMeshFor
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.IntFunction

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
    private val managers: ExecutorService,
    private val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    private val meshViewUpdateQueue: MeshViewUpdateQueue<ObjectKey>)
    : PainteraMeshManager<ObjectKey> {

    override val meshesGroup = Group()
    override val settings = MeshSettings(source.numMipmapLevels)

    private val meshes = Collections.synchronizedMap(HashMap<ObjectKey, MeshGenerator<ObjectKey>>())
    private val unshiftedWorldTransforms: Array<AffineTransform3D> = DataSource.getUnshiftedWorldTransforms(source, 0)
    private val color: ObjectProperty<Color> = SimpleObjectProperty(Color.WHITE)
    private val areMeshesEnabledProperty: BooleanProperty = SimpleBooleanProperty(true)
    private val showBlockBoundariesProperty: BooleanProperty = SimpleBooleanProperty(false)
    private val rendererBlockSizeProperty: IntegerProperty = SimpleIntegerProperty(Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE)
    private val numElementsPerFrameProperty: IntegerProperty = SimpleIntegerProperty(Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE)
    private val frameDelayMsecProperty: LongProperty = SimpleLongProperty(Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE)
    private val sceneUpdateDelayMsecProperty: LongProperty = SimpleLongProperty(Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE)
    private val sceneUpdateHandler: SceneUpdateHandler
    private val sceneUpdateInvalidationListener: InvalidationListener
    private var rendererGrids: Array<CellGrid>? = RendererBlockSizes.getRendererGrids(source, rendererBlockSizeProperty.get())
    private val sceneUpdateService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-sceneupdate-%d",
            true))
    private val sceneUpdateParametersProperty: ObjectProperty<SceneUpdateParameters?> =
        SimpleObjectProperty()
    private var currentSceneUpdateTask: Future<*>? = null
    private var scheduledSceneUpdateTask: Future<*>? = null

    @Synchronized
    override fun refreshMeshes() {
        assert(Platform.isFxApplicationThread())
        if (rendererGrids == null || !areMeshesEnabledProperty.get()) return
        val sceneUpdateParameters = SceneUpdateParameters(viewFrustum.value, eyeToWorldTransform.value, rendererGrids!!)
        val needToSubmit = sceneUpdateParametersProperty.get() == null
        sceneUpdateParametersProperty.set(sceneUpdateParameters)
        if (needToSubmit && !managers.isShutdown) {
            assert(scheduledSceneUpdateTask == null)
            scheduledSceneUpdateTask = sceneUpdateService.submit(withErrorPrinting { updateScene() })
        }
    }

    @Synchronized
    fun update() {
        val rendererGrids = this.rendererGrids
        if (rendererGrids == null || !areMeshesEnabledProperty.get()) return
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
    override fun removeMeshFor(key: ObjectKey) {
        Optional.ofNullable(meshes.remove(key))
            .ifPresent { mesh: MeshGenerator<ObjectKey> ->
                mesh.state.settings.unbind()
                mesh.interrupt()
                meshesGroup.children.remove(mesh.root)
            }
    }

    @Synchronized
    override fun removeAllMeshes() = allMeshKeys.forEach(Consumer { removeMeshFor(it) })

    @get:Synchronized
    private val allMeshKeys: Collection<ObjectKey>
        get() = meshes.keys.toList()

    fun colorProperty(): ObjectProperty<Color> = color

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
        sceneUpdateInvalidationListener = InvalidationListener { onUpdateScene() }
        viewFrustum.addListener(sceneUpdateInvalidationListener)
        areMeshesEnabledProperty.addListener { _: ObservableValue<out Boolean>?, _: Boolean?, newv: Boolean -> if (newv) refreshMeshes() else removeAllMeshes() }
        rendererBlockSizeProperty.addListener { _: Observable? ->
            synchronized(this) {
                rendererGrids = RendererBlockSizes.getRendererGrids(source, rendererBlockSizeProperty.get())
                refreshMeshes()
            }
        }
        settings.levelOfDetailProperty().addListener(sceneUpdateInvalidationListener)
        settings.coarsestScaleLevelProperty().addListener(sceneUpdateInvalidationListener)
        settings.finestScaleLevelProperty().addListener(sceneUpdateInvalidationListener)

        sceneUpdateHandler = SceneUpdateHandler { { InvokeOnJavaFXApplicationThread.invoke { refreshMeshes() } } }
        sceneUpdateDelayMsecProperty.addListener { _: Observable? ->
            sceneUpdateHandler.update(sceneUpdateDelayMsecProperty.get())
        }
        eyeToWorldTransform.addListener(sceneUpdateHandler)
        val meshViewUpdateQueueListener = InvalidationListener { obs: Observable? ->
            meshViewUpdateQueue.update(
                numElementsPerFrameProperty.get(),
                frameDelayMsecProperty.get()
            )
        }
        numElementsPerFrameProperty.addListener(meshViewUpdateQueueListener)
        frameDelayMsecProperty.addListener(meshViewUpdateQueueListener)
    }

    @Synchronized
    private fun addMesh(key: ObjectKey): MeshGenerator.State? {
        if (!areMeshesEnabledProperty.get() || key in meshes) return meshes[key]?.state
        val meshGenerator: MeshGenerator<ObjectKey> = MeshGenerator<ObjectKey>(
            source.numMipmapLevels,
            key,
            getBlockListFor,
            getMeshFor,
            meshViewUpdateQueue,
            IntFunction { level: Int -> unshiftedWorldTransforms[level] },
            managers,
            workers)
        meshGenerator.state.settings.bindTo(settings)
        meshGenerator.state.showBlockBoundariesProperty().bind(showBlockBoundariesProperty)
        meshGenerator.state.visibleProperty().bind(areMeshesEnabledProperty)
        meshes[key] = meshGenerator
        meshesGroup.children += meshGenerator.root
        return meshGenerator.state
    }

    override fun createMeshFor(key: ObjectKey) = addMesh(key)

    @Synchronized
    fun onUpdateScene() {

        currentSceneUpdateTask?.cancel(true)
        currentSceneUpdateTask = null
        scheduledSceneUpdateTask?.cancel(true)
        sceneUpdateParametersProperty.set(null)
        refreshMeshes()
    }

    @Synchronized
    override fun contains(key: ObjectKey) = key in meshes

    @Synchronized
    override fun getStateFor(key: ObjectKey) = meshes[key]?.state
}
