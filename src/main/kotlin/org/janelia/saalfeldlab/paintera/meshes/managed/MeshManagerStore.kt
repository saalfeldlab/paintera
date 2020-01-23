package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.beans.binding.Bindings
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
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction

typealias MeshesBlockTree = BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>>

class MeshManagerStore<K>(
    override val settings: MeshSettings,
    private val getBlockListFor: PainteraMeshManager.GetBlockListFor<K>,
    private val getMeshFor: PainteraMeshManager.GetMeshFor<K>,
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

    private val intColor = Bindings.createIntegerBinding(
        // deriveColor(hueShift, saturationFactor, brightnessFactor, opacityFactor)
        Callable { Colors.toARGBType(color.deriveColor(0.0, 1.0, 1.0, settings.opacity)).get() },
        _color)

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

    override fun createMeshFor(key: K) = addMesh(key)

    override fun removeMeshFor(key: K) = removeMeshes(key)

    override fun removeAllMeshes() {
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
                    intColor,
                    unshiftedWorldTransforms,
                    managers,
                    workers,
                    showBlockBoundariesProperty())
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

    override fun contains(key: K) = key in meshStore

    override fun getStateFor(key: K) = meshStore[key]?.state
}
