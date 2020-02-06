package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import javafx.scene.paint.Color
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.meshes.MeshViewUpdateQueue
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class MeshManagerWithSingleMesh<Key>(
    source: DataSource<*, *>,
    private val getBlockList: GetBlockListFor<Key>,
    private val getMeshFor: GetMeshFor<Key>,
    viewFrustumProperty: ObservableValue<ViewFrustum>,
    eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
    val managers: ExecutorService,
    val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    val meshViewUpdateQueue: MeshViewUpdateQueue<Key>) {

    private var meshKey: Key? = null

    private val viewerEnabled: BooleanProperty = SimpleBooleanProperty(false)
    var isViewerEnabled: Boolean
        get() = viewerEnabled.get()
        set(enabled) = viewerEnabled.set(enabled)
    fun viewerEnabledProperty() = viewerEnabled

    private val _color: ObjectProperty<Color> = SimpleObjectProperty(Color.WHITE)
    var color: Color
        get() = _color.value
        set(color) = _color.set(color)
    fun colorProperty() = _color

    private val manager: AdaptiveResolutionMeshManager<Key> = AdaptiveResolutionMeshManager(
        source,
        getBlockList,
        getMeshFor,
        viewFrustumProperty,
        eyeToWorldTransformProperty,
        viewerEnabled,
        managers,
        workers,
        meshViewUpdateQueue)


    val rendererSettings get() = manager.rendererSettings

    val settings: MeshSettings = MeshSettings(source.numMipmapLevels)

    val meshesGroup: Group
        get() = manager.meshesGroup

    private val managerCancelAndUpdate = InvalidationListener { Platform.runLater { manager.cancelAndUpdate() } }

    @Synchronized
    fun createMeshFor(key: Key) {
        if (key == meshKey)
            return
        this.removeAllMeshes()
        meshKey = key
        manager.createMeshFor(key, true) { it.setup() }
    }

    @Synchronized
    fun removeAllMeshes() {
        manager.removeAllMeshes { it.release() }
        meshKey = null
    }

    @Synchronized
    fun refreshMeshes() {
        val key = meshKey
        this.removeAllMeshes()
        if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
        key?.let { createMeshFor(it) }
    }

    private fun MeshGenerator.State.setup() = setupGeneratorState(this)

    @Synchronized
    private fun setupGeneratorState(state: MeshGenerator.State) {
        LOG.debug("Setting up state for mesh key {}", meshKey)
        state.colorProperty().bind(_color)
        state.settings.bindTo(settings)
        state.settings.levelOfDetailProperty().addListener(managerCancelAndUpdate)
        state.settings.coarsestScaleLevelProperty().addListener(managerCancelAndUpdate)
        state.settings.finestScaleLevelProperty().addListener(managerCancelAndUpdate)
    }

    @Synchronized
    private fun MeshGenerator.State.release() {
        colorProperty().unbind()
        settings.unbind()
        settings.levelOfDetailProperty().removeListener(managerCancelAndUpdate)
        settings.coarsestScaleLevelProperty().removeListener(managerCancelAndUpdate)
        settings.finestScaleLevelProperty().removeListener(managerCancelAndUpdate)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

}
