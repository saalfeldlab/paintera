package org.janelia.saalfeldlab.paintera.meshes.managed

import gnu.trove.set.hash.TLongHashSet
import javafx.beans.InvalidationListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.Group
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.cache.CacheLoader
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager.GetMeshFor
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.HashWrapper
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min
import net.imglib2.util.Pair as IPair

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class MeshManagerWithAssignmentForSegmentsKotlin(
    source: DataSource<*, *>,
    val labelBlockLookup: LabelBlockLookup,
    private val getMeshFor: GetMeshFor<TLongHashSet>,
    viewFrustumProperty: ObservableValue<ViewFrustum>,
    eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
    private val selectedSegments: SelectedSegments,
    val managers: ExecutorService,
    val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    val meshViewUpdateQueue: MeshViewUpdateQueue<TLongHashSet>)
    :
    PainteraMeshManager<Long> {
    private val bindAndUnbindService = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-unbind-%d",
            true
        )
    )

    private val getBlockList = object : GetBlockListFor<TLongHashSet> {
        override fun getBlocksFor(level: Int, key: TLongHashSet): Array<Interval>? {
            val intervals = mutableSetOf<HashWrapper<Interval>>()
            key.forEach { id ->
                labelBlockLookup.read(level, id).map { HashWrapper.interval(it) }.let { intervals.addAll(it) }
                true
            }
            return intervals.map { it.data }.toTypedArray()
        }
    }

    private val segmentFragmentMap =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<Long, TLongHashSet>())
    private val fragmentSegmentMap =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<TLongHashSet, Long>())

    private val manager: AdaptiveResolutionMeshManager<TLongHashSet> = AdaptiveResolutionMeshManager(
        source,
        getBlockList,
        getMeshFor,
        viewFrustumProperty,
        eyeToWorldTransformProperty,
        managers,
        workers,
        meshViewUpdateQueue
    )

    override val settings: MeshSettings
        get() = manager.settings

    override val meshesGroup: Group
        get() = manager.meshesGroup

    private val sceneUpdateInvalidationListener = InvalidationListener { manager.onUpdateScene() }

    init {
        this.selectedSegments.addListener(sceneUpdateInvalidationListener)
    }

    @Synchronized
    fun setMeshesToSelection() {
        val selection = selectedSegments.selectedIds.activeIds
        println("LOL SETTING TO SELECTION ${Arrays.toString(selection)}")
        removeAllMeshes()
        selection.forEach { createMeshFor(it) }
        manager.update()
    }

    @Synchronized
    override fun createMeshFor(key: Long) = when(key) {
        in segmentFragmentMap -> getStateFor(key)
        else -> selectedSegments
                .assignment
                .getFragments(key)
                ?.takeUnless { it.isEmpty }
                ?.let { fragments ->
                    segmentFragmentMap[key] = fragments
                    fragmentSegmentMap[fragments] = key
                    manager.createMeshFor(fragments)
                }
    }

    @Synchronized
    override fun removeMeshFor(key: Long) {
        if (key in segmentFragmentMap) {
            segmentFragmentMap
                .remove(key)
                ?.also { fragmentSegmentMap.remove(it) }
                ?.also { manager.removeMeshFor(it) }
        }
    }

    @Synchronized
    override fun removeAllMeshes() {
        segmentFragmentMap.clear()
        fragmentSegmentMap.clear()
        manager.removeAllMeshes()
        manager.update()
    }

    @Synchronized
    override fun refreshMeshes() {
        val inconsistentSegments = segmentFragmentMap.filter { (s, f) ->
            f != selectedSegments.assignment.getFragments(s) || !selectedSegments.isSegmentSelected(s)
        }
        inconsistentSegments.values.forEach { fragmentSegmentMap.remove(it) }
        inconsistentSegments.values.forEach { manager.removeMeshFor(it) }
        inconsistentSegments.keys.forEach { segmentFragmentMap.remove(it) }
        val consistentSegments = inconsistentSegments.mapValues { (s, _) ->
            selectedSegments.assignment.getFragments(s)
        }
        consistentSegments.forEach { (s, f) ->
            segmentFragmentMap[s] = f
            fragmentSegmentMap[f] = s
            manager.createMeshFor(f)
        }
        manager.refreshMeshes()
        manager.update()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun LabelBlockLookup.read(level: Int, id: Long) = read(LabelBlockLookupKey(level, id))

        @JvmStatic
        fun <D : IntegerType<D>> fromBlockLookup(
            dataSource: DataSource<D, *>,
            selectedSegments: SelectedSegments,
            viewFrustumProperty: ObservableValue<ViewFrustum>,
            eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
            labelBlockLookup: LabelBlockLookup,
            meshManagerExecutors: ExecutorService,
            meshWorkersExecutors: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>): MeshManagerWithAssignmentForSegmentsKotlin {
            LOG.debug("Data source is type {}", dataSource.javaClass)
            val actualLookup = when (dataSource) {
                is MaskedSource<D, *> -> LabeLBlockLookupWithMaskedSource(labelBlockLookup, dataSource)
                else -> labelBlockLookup
            }
            // Set up mesh caches
            val segmentMaskGenerators = Array(dataSource.numMipmapLevels) { SegmentMaskGenerators.create<D, BoolType>(dataSource, it) }
            val meshCaches = CacheUtils.segmentMeshCacheLoaders(dataSource, segmentMaskGenerators) { loader: CacheLoader<ShapeKey<TLongHashSet>, IPair<FloatArray, FloatArray>>? ->
                    SoftRefLoaderCache<ShapeKey<TLongHashSet>, IPair<FloatArray, FloatArray>>().withLoader(loader)
            }
            val getMeshFor = object : GetMeshFor<TLongHashSet> {
                override fun getMeshFor(key: ShapeKey<TLongHashSet>) = meshCaches[key.scaleIndex()].apply(key)?.let { PainteraTriangleMesh(it.a, it.b) }
            }
            return MeshManagerWithAssignmentForSegmentsKotlin(
                dataSource,
                actualLookup,
                getMeshFor, //getMeshFor,
                viewFrustumProperty,
                eyeToWorldTransformProperty,
                selectedSegments,
                meshManagerExecutors,
                meshWorkersExecutors,
                MeshViewUpdateQueue())
        }
    }

    private class LabeLBlockLookupWithMaskedSource<D: IntegerType<D>>(
        private val delegate: LabelBlockLookup,
        private val maskedSource: MaskedSource<D, *>) : LabelBlockLookup by delegate {

        override fun read(key: LabelBlockLookupKey): Array<Interval> {
            val blocks = delegate.read(key).map { HashWrapper.interval(it) }.toMutableSet()
            blocks += affectedBlocksForLabel(maskedSource, key.level, key.id).map { HashWrapper.interval(it) }
            return blocks.map { it.data }.toTypedArray()
        }

        companion object {
            private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

            private fun affectedBlocksForLabel(source: MaskedSource<*, *>, level: Int, id: Long): Array<Interval> {
                val grid = source.getCellGrid(0, level)
                val imgDim = grid.imgDimensions
                val blockSize = IntArray(imgDim.size) { grid.cellDimension(it) }
                LOG.debug("Getting blocks at level={} for id={}", level, id)
                val blockMin = LongArray(grid.numDimensions())
                val blockMax = LongArray(grid.numDimensions())
                val indexedBlocks = source.getModifiedBlocks(level, id!!)
                LOG.debug("Received modified blocks at level={} for id={}: {}", level, id, indexedBlocks)
                val intervals = mutableListOf<Interval>()
                val blockIt = indexedBlocks.iterator()
                while (blockIt.hasNext()) {
                    val blockIndex = blockIt.next()
                    grid.getCellGridPositionFlat(blockIndex, blockMin)
                    Arrays.setAll(blockMin) { blockMin[it] * blockSize[it] }
                    Arrays.setAll(blockMax) { min(blockMin[it] + blockSize[it], imgDim[it]) - 1 }
                    intervals += FinalInterval(blockMin, blockMax)
                }
                LOG.debug("Returning {} intervals", intervals.size)
                return intervals.toTypedArray()
            }
        }

    }

    @Synchronized
    override fun contains(key: Long) = segmentFragmentMap[key]?.let { it in manager } ?: false

    override fun getStateFor(key: Long) = segmentFragmentMap[key] ?.let { manager.getStateFor(it) }
}
