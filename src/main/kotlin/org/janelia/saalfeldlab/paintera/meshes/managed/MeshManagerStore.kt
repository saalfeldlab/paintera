package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.scene.Group
import javafx.scene.paint.Color
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction

typealias MeshesBlockTree = BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>>

class MeshManagerStore<K>(
        val settings: MeshSettings,
        private val getBlockListFor: AdaptiveResolutionMeshManager.GetBlockListFor<K>,
        private val getMeshFor: AdaptiveResolutionMeshManager.GetMeshFor<K>,
        private val meshViewUpdateQueue: MeshViewUpdateQueue<K>,
        private val unshiftedWorldTransforms: IntFunction<AffineTransform3D>,
        private val managers: ExecutorService,
        private val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>) : PainteraMeshManager<K> {

    private val sceneBlockTree: MeshesBlockTree? = null

    private val rendererGrids: Array<CellGrid>? = null

    override val meshesGroup = Group()

    private val meshStore = FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<K, MeshGenerator<K>>())

    private val _color = SimpleObjectProperty<Color>(Color.WHITE)
    var color: Color
        get() = _color.value
        set(color) = _color.set(color)
    fun colorProperty(): ObjectProperty<Color> = _color

    val unmodifiableMeshStore = FXCollections.unmodifiableObservableMap(meshStore)

    private var _showBlockBoundaries = SimpleBooleanProperty()
    var isShowBlockBoundaries: Boolean
        get() = _showBlockBoundaries.value
        set(showBlockBoundaries) = _showBlockBoundaries.set(showBlockBoundaries)
    fun showBlockBoundariesProperty(): BooleanProperty = _showBlockBoundaries

    override fun refreshMeshes() {
        synchronized(meshStore) {
            val keys = meshStore.keys.toList()
            removeAllMeshes()
            keys.forEach { addMesh(it) }
        }
    }

    fun createMeshFor(key: K) = addMesh(key)

    fun removeMeshFor(key: K) = removeMeshes(key)

    fun removeAllMeshes() {
        val keys = synchronized(meshStore) {
            meshStore.keys.toList()
        }
        removeMeshes(keys)
    }

    private fun addMesh(key: K): MeshGenerator.State? {
        synchronized(meshStore) {
            if (key !in meshStore)
                meshStore[key] = MeshGenerator(
                    settings.numScaleLevels,
                    key,
                    getBlockListFor,
                    getMeshFor,
                    meshViewUpdateQueue,
                    unshiftedWorldTransforms,
                    managers,
                    workers)
                    .also {
                        it.postAddHook()
                    }
            return meshStore[key]?.state
        }
    }

    private fun removeMeshes(keys: Collection<K>) {
        val managers = synchronized(meshStore) {
            keys.mapNotNull { meshStore.remove(it) }
        }
        managers.forEach { it.postRemoveHook() }

    }

    private fun removeMeshes(vararg keys: K) {
        removeMeshes(listOf(*keys))
    }

    // TODO avoid synchronization on meshesGroup?
    private fun MeshGenerator<K>.postAddHook() {
        synchronized(meshesGroup) {
            meshesGroup.children.add(root)
            state.colorProperty().bind(colorProperty())
            state.showBlockBoundariesProperty().bind(showBlockBoundariesProperty())
        }
        update(
            sceneBlockTree,
            rendererGrids)
    }

    private fun MeshGenerator<K>.postRemoveHook() {
        interrupt()
        synchronized(meshesGroup) {
            meshesGroup.children.remove(root)
        }
    }

    fun contains(key: K) = key in meshStore

    fun getStateFor(key: K) = meshStore[key]?.state
}
