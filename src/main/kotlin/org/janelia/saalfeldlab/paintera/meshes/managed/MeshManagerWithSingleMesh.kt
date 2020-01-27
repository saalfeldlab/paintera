package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Group
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.meshes.MeshViewUpdateQueue
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager.GetMeshFor
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
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
    val meshViewUpdateQueue: MeshViewUpdateQueue<Key>)
    :
    PainteraMeshManager<Long> {
    private val bindAndUnbindService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-unbind-%d",
            true))

    private var meshKey: Key? = null

    private val viewerEnabled: SimpleBooleanProperty = SimpleBooleanProperty(false)
    var isViewerEnabled: Boolean
        get() = viewerEnabled.get()
        set(enabled) = viewerEnabled.set(enabled)
    fun viewerEnabledProperty(): BooleanProperty = viewerEnabled

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

    override val meshesGroup: Group
        get() = manager.meshesGroup

    @Synchronized
    fun createMeshFor(key: Key) {
        if (key == meshKey)
            return
        manager.removeAllMeshes()
        meshKey = key
        manager.createMeshFor(key)?.settings?.bindTo(settings)
    }

    @Synchronized
    fun removeAllMeshes() {
        manager.removeAllMeshes()
        meshKey = null
    }

    @Synchronized
    override fun refreshMeshes() {
        val key = meshKey
        this.removeAllMeshes()
        if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
        key?.let { createMeshFor(it) }
    }

}
