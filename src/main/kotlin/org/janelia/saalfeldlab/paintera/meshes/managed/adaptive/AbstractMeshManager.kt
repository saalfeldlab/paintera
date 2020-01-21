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
class AbstractMeshManager<ObjectKey>(
    val source: DataSource<*, *>,
    private val getBlockListFor: GetBlockListFor<ObjectKey>,
    private val getMeshFor: GetMeshFor<ObjectKey>,
    private val viewFrustum: ObservableValue<ViewFrustum>,
    protected val eyeToWorldTransform: ObservableValue<AffineTransform3D>,
    protected val managers: ExecutorService,
    protected val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    private val meshViewUpdateQueue: MeshViewUpdateQueue<ObjectKey>) :
    PainteraMeshManager<ObjectKey> {

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
    private var rendererGrids: Array<CellGrid>? = null
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
            scheduledSceneUpdateTask = sceneUpdateService.submit(
                withErrorPrinting(
                    Runnable { updateScene() }
                )
            )
        }
    }

    private fun updateScene() {
        assert(!Platform.isFxApplicationThread())
        try {
            var sceneUpdateParameters: SceneUpdateParameters?
            val blockTreeParametersKeysToMeshGenerators: MutableMap<BlockTreeParametersKey, MutableList<MeshGenerator<ObjectKey>>?> =
                HashMap()
            val wasInterrupted =
                BooleanSupplier { Thread.currentThread().isInterrupted }
            synchronized(this) {
                assert(currentSceneUpdateTask == null)
                if (wasInterrupted.asBoolean) return
                if (sceneUpdateParametersProperty.get() == null) return
                sceneUpdateParameters = sceneUpdateParametersProperty.get()
                sceneUpdateParametersProperty.set(null)
                if (scheduledSceneUpdateTask == null) return
                currentSceneUpdateTask = scheduledSceneUpdateTask
                scheduledSceneUpdateTask = null
                for (meshGenerator in meshes.values) {
                    val blockTreeParametersKey =
                        BlockTreeParametersKey(
                            meshGenerator.meshSettingsProperty().get().levelOfDetailProperty().get(),
                            meshGenerator.meshSettingsProperty().get().coarsestScaleLevelProperty().get(),
                            meshGenerator.meshSettingsProperty().get().finestScaleLevelProperty().get()
                        )
                    blockTreeParametersKeysToMeshGenerators
                        .computeIfAbsent(blockTreeParametersKey) { mutableListOf() }
                        ?.add(meshGenerator)
                }
            }
            val sceneBlockTrees: MutableMap<BlockTreeParametersKey, BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>>> =
                HashMap()
            for (blockTreeParametersKey in blockTreeParametersKeysToMeshGenerators.keys) {
                if (wasInterrupted.asBoolean) return
                sceneBlockTrees[blockTreeParametersKey] = SceneBlockTree.createSceneBlockTree(
                    source,
                    sceneUpdateParameters!!.viewFrustum,
                    sceneUpdateParameters!!.eyeToWorldTransform,
                    blockTreeParametersKey.levelOfDetail,
                    blockTreeParametersKey.coarsestScaleLevel,
                    blockTreeParametersKey.finestScaleLevel,
                    sceneUpdateParameters!!.rendererGrids,
                    wasInterrupted
                )
            }
            synchronized(this) {
                if (wasInterrupted.asBoolean) return
                for ((blockTreeParametersKey, value) in blockTreeParametersKeysToMeshGenerators) {
                    val sceneBlockTreeForKey =
                        sceneBlockTrees[blockTreeParametersKey]
                    for (meshGenerator in value!!) meshGenerator.update(
                        sceneBlockTreeForKey,
                        sceneUpdateParameters!!.rendererGrids
                    )
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
                mesh.meshSettingsProperty().unbind()
                mesh.meshSettingsProperty().set(null)
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
        private val LOG =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

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
                rendererGrids =
                    RendererBlockSizes.getRendererGrids(source, rendererBlockSizeProperty.get())
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
    private fun addMesh(key: ObjectKey) {
        if (!areMeshesEnabledProperty.get()) return
        if (meshes.containsKey(key)) return
        val color = Bindings.createIntegerBinding(
            Callable {
                Colors.toARGBType(color.get()).get()
            },
            color
        )
        val meshGenerator: MeshGenerator<ObjectKey> = MeshGenerator<ObjectKey>(
            source.numMipmapLevels,
            key,
            getBlockListFor,
            getMeshFor,
            meshViewUpdateQueue,
            color,
            IntFunction { level: Int -> unshiftedWorldTransforms[level] },
            managers,
            workers,
            showBlockBoundariesProperty
        )
        meshGenerator.meshSettingsProperty().set(settings)
        meshes[key] = meshGenerator
        meshesGroup.children += meshGenerator.root
    }

    override fun createMeshFor(key: ObjectKey) = addMesh(key)

    @Synchronized
    private fun onUpdateScene() {
        currentSceneUpdateTask?.cancel(true)
        currentSceneUpdateTask = null
        scheduledSceneUpdateTask?.cancel(true)
        sceneUpdateParametersProperty.set(null)
        refreshMeshes()
    }
}
