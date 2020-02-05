package org.janelia.saalfeldlab.paintera.meshes.managed

import gnu.trove.set.hash.TLongHashSet
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.binding.ObjectBinding
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.Group
import javafx.scene.paint.Color
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.cache.Invalidate
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.labels.blocks.CachedLabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMeshCacheLoader
import org.janelia.saalfeldlab.paintera.meshes.managed.adaptive.AdaptiveResolutionMeshManager
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.HashWrapper
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlin.math.min

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class MeshManagerWithAssignmentForSegments(
    source: DataSource<*, *>,
    val labelBlockLookup: LabelBlockLookup,
    private val getMeshFor: GetMeshFor<TLongHashSet>,
    viewFrustumProperty: ObservableValue<ViewFrustum>,
    eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
    private val selectedSegments: SelectedSegments,
    private val argbStream: AbstractHighlightingARGBStream,
    val managers: ExecutorService,
    val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    val meshViewUpdateQueue: MeshViewUpdateQueue<TLongHashSet>) {

    private class CancelableTask(private val task: (BooleanSupplier) -> Unit) : Runnable {

        private var isCanceled: Boolean = false

        fun cancel() {
            isCanceled = true
        }

        override fun run() = task(BooleanSupplier { isCanceled })

        companion object {

        }

    }

    private val unbindExecutors = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-with-assignment-unbindg-%d",
            true))

    private val setMeshesToSelectionExecutors = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-with-assignment-set-meshes-to-selection-%d",
            true))
    private var currentTask: CancelableTask? = null

    private val getBlockList: GetBlockListFor<TLongHashSet> = object : GetBlockListFor<TLongHashSet> {
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
    private val segmentColorBindingMap =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<Long, ObjectBinding<Color>>())

    private val viewerEnabled: SimpleBooleanProperty = SimpleBooleanProperty(false)
    var isViewerEnabled: Boolean
        get() = viewerEnabled.get()
        set(enabled) = viewerEnabled.set(enabled)
    fun viewerEnabledProperty(): BooleanProperty = viewerEnabled

    private val manager: AdaptiveResolutionMeshManager<TLongHashSet> = AdaptiveResolutionMeshManager(
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

    val managedSettings = ManagedMeshSettings(source.numMipmapLevels)

    val settings: MeshSettings
        get() = managedSettings.globalSettings

    val meshesGroup: Group
        get() = manager.meshesGroup

    private val managerCancelAndUpdate = InvalidationListener { manager.cancelAndUpdate() }

    @Synchronized
    fun setMeshesToSelection() {
        currentTask?.cancel()
        currentTask = null
        val task = CancelableTask { isCanceled ->
            if (isCanceled.asBoolean) return@CancelableTask
            // TODO can this be more efficient if using TLongSets instead?
            val (selection, presentKeys) = synchronized (this) {
                val selection = selectedSegments.selectedIds.activeIds.toHashSet()
                val presentKeys = segmentFragmentMap.keys.toHashSet()
                Pair(selection, presentKeys)
            }
            // TODO remove selected neurons for which the assignment has changed!!!
            val presentButNotSelected = presentKeys.filterNot { it in selection }
            val selectedButNotPresent = selection.filterNot { it in presentKeys }
            // remove meshes that are present but not in selection
            for (id in presentButNotSelected) {
                if (isCanceled.asBoolean) break
                removeMeshFor(id)
            }
            // add meshes for all selected ids that are not present yet
            // removing mesh if is canceled is necessary because could be canceled between call to isCanceled.asBoolean and createaMeshFor
            for (id in selectedButNotPresent) {
                if (isCanceled.asBoolean) break
                createMeshFor(id)
                if (isCanceled.asBoolean) removeMeshFor(id)
            }
            if (!isCanceled.asBoolean)
                manager.cancelAndUpdate()
        }
        currentTask = task
        setMeshesToSelectionExecutors.submit(task)
    }

    @Synchronized
    private fun createMeshFor(key: Long) {
        if (key in segmentFragmentMap) return
        selectedSegments
            .assignment
            .getFragments(key)
            ?.takeUnless { it.isEmpty }
            ?.let { fragments ->
                segmentFragmentMap[key] = fragments
                fragmentSegmentMap[fragments] = key
                manager.createMeshFor(fragments, false)
            }
            ?.also { setupGeneratorState(key, it) }
    }

    @Synchronized
    private fun setupGeneratorState(key: Long, state: MeshGenerator.State) {
        state.settings.levelOfDetailProperty().addListener(managerCancelAndUpdate)
        state.settings.coarsestScaleLevelProperty().addListener(managerCancelAndUpdate)
        state.settings.finestScaleLevelProperty().addListener(managerCancelAndUpdate)
        state.settings.bindTo(managedSettings.getOrAddMesh(key, true))
        state.colorProperty().bind(segmentColorBindingMap.computeIfAbsent(key) {
            Bindings.createObjectBinding(
                Callable { Colors.toColor(argbStream.argb(key) or 0xFF000000.toInt()) },
                argbStream)
        })
    }

    private fun MeshGenerator.State.release() {
        unbindExecutors.submit {
            settings.levelOfDetailProperty().removeListener(managerCancelAndUpdate)
            settings.coarsestScaleLevelProperty().removeListener(managerCancelAndUpdate)
            settings.finestScaleLevelProperty().removeListener(managerCancelAndUpdate)
            settings.unbind()
            colorProperty().unbind()
        }
    }

    @Synchronized
    private fun removeMeshFor(key: Long) {
        segmentFragmentMap.remove(key)?.let {
            fragmentSegmentMap.remove(it)
            manager.removeMeshFor(it)?.release()
        }
        segmentColorBindingMap.remove(key)
    }

    @Synchronized
    fun removeAllMeshes() {
        currentTask?.cancel()
        currentTask = null
        segmentFragmentMap.clear()
        fragmentSegmentMap.clear()
        segmentColorBindingMap.clear()
        manager.removeAllMeshes()
    }

    @Synchronized
    fun refreshMeshes() {
        this.removeAllMeshes()
        if (labelBlockLookup is Invalidate<*>) labelBlockLookup.invalidateAll()
        if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
        this.setMeshesToSelection()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun LabelBlockLookup.read(level: Int, id: Long) = read(LabelBlockLookupKey(level, id))

        @JvmStatic
        fun <D : IntegerType<D>> fromBlockLookup(
            dataSource: DataSource<D, *>,
            selectedSegments: SelectedSegments,
            argbStream: AbstractHighlightingARGBStream,
            viewFrustumProperty: ObservableValue<ViewFrustum>,
            eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
            labelBlockLookup: LabelBlockLookup,
            meshManagerExecutors: ExecutorService,
            meshWorkersExecutors: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>): MeshManagerWithAssignmentForSegments {
            LOG.debug("Data source is type {}", dataSource.javaClass)
            val actualLookup = when (dataSource) {
                is MaskedSource<D, *> -> LabeLBlockLookupWithMaskedSource.create(labelBlockLookup, dataSource)
                else -> labelBlockLookup
            }
            // Set up mesh caches
            val segmentMaskGenerators = Array(dataSource.numMipmapLevels) { SegmentMaskGenerators.create<D, BoolType>(dataSource, it) }
            val loaders = Array(dataSource.numMipmapLevels) {
                SegmentMeshCacheLoader<D>(
                    IntArray(3) { 1 },
                    Supplier { dataSource.getDataSource(0, it) },
                    segmentMaskGenerators[it],
                    dataSource.getSourceTransformCopy(0, it))
            }
            val getMeshFor = GetMeshFor.FromCache.fromPairLoaders(*loaders)

            return MeshManagerWithAssignmentForSegments(
                dataSource,
                actualLookup,
                getMeshFor,
                viewFrustumProperty,
                eyeToWorldTransformProperty,
                selectedSegments,
                argbStream,
                meshManagerExecutors,
                meshWorkersExecutors,
                MeshViewUpdateQueue())
        }
    }

    private class CachedLabeLBlockLookupWithMaskedSource<D: IntegerType<D>>(
        private val delegate: CachedLabelBlockLookup,
        private val maskedSource: MaskedSource<D, *>)
        :
        LabeLBlockLookupWithMaskedSource<D>(delegate, maskedSource),
        Invalidate<LabelBlockLookupKey> by delegate

    private open class LabeLBlockLookupWithMaskedSource<D: IntegerType<D>>(
        private val delegate: LabelBlockLookup,
        private val maskedSource: MaskedSource<D, *>) : LabelBlockLookup by delegate {

        override fun read(key: LabelBlockLookupKey): Array<Interval> {
            val blocks = delegate.read(key).map { HashWrapper.interval(it) }.toMutableSet()
            blocks += affectedBlocksForLabel(maskedSource, key.level, key.id).map { HashWrapper.interval(it) }
            return blocks.map { it.data }.toTypedArray()
        }

        companion object {
            private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

            fun <D: IntegerType<D>> create(delegate: LabelBlockLookup, maskedSource: MaskedSource<D, *>) = when(delegate) {
                is CachedLabelBlockLookup -> CachedLabeLBlockLookupWithMaskedSource(delegate, maskedSource)
                else -> LabeLBlockLookupWithMaskedSource(delegate, maskedSource)
            }

            private fun affectedBlocksForLabel(source: MaskedSource<*, *>, level: Int, id: Long): Array<Interval> {
                val grid = source.getCellGrid(0, level)
                val imgDim = grid.imgDimensions
                val blockSize = IntArray(imgDim.size) { grid.cellDimension(it) }
                LOG.debug("Getting blocks at level={} for id={}", level, id)
                val blockMin = LongArray(grid.numDimensions())
                val blockMax = LongArray(grid.numDimensions())
                val indexedBlocks = source.getModifiedBlocks(level, id)
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
    fun getStateFor(key: Long) = segmentFragmentMap[key] ?.let { manager.getStateFor(it) }

    fun getContainedFragmentsFor(key: Long) = segmentFragmentMap[key]

    val getBlockListForLongKey: GetBlockListFor<Long>
        get() = object : GetBlockListFor<Long> {
            override fun getBlocksFor(level: Int, key: Long): Array<Interval>? = getBlockList.getBlocksFor(level, getContainedFragmentsFor(key) ?: TLongHashSet())
        }

    val getMeshForLongKey: GetMeshFor<Long>
        get() = object : GetMeshFor<Long> {
            override fun getMeshFor(key: ShapeKey<Long>): PainteraTriangleMesh? = getMeshFor.getMeshFor(ShapeKey(
                getContainedFragmentsFor(key.shapeId()) ?: TLongHashSet(),
                key.scaleIndex(),
                key.simplificationIterations(),
                key.smoothingLambda(),
                key.smoothingIterations(),
                key.minLabelRatio(),
                key.min(),
                key.max()))
        }
}
