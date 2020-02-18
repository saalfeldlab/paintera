package org.janelia.saalfeldlab.paintera.meshes.managed

import gnu.trove.set.hash.TLongHashSet
import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
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
import org.janelia.saalfeldlab.fx.ObservableWithListenersList
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

    private class CancelableTask(private val task: (() -> Boolean) -> Unit) : Runnable {

        private var isCanceled: Boolean = false

        fun cancel() {
            isCanceled = true
        }

        override fun run() = task { isCanceled }

    }

    private class RelevantBindingsAndProperties(private val key: Long, private val stream: AbstractHighlightingARGBStream) {
        private val color: ObjectProperty<Color> = SimpleObjectProperty(Color.WHITE)
        private val colorUpdateListener =
            InvalidationListener { color.value = Colors.toColor(stream.argb(key) or 0xFF000000.toInt()) }
                .also { stream.addListener(it) }

        fun release() = stream.removeListener(colorUpdateListener)
        fun colorProperty() = color
    }

    private val updateExecutors = Executors.newSingleThreadExecutor(
        NamedThreadFactory(
            "meshmanager-with-assignment-update-%d",
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

    // setMeshesCompleted is only visible to enclosing manager if
    // _meshUpdateObservable is private
    // https://kotlinlang.org/docs/reference/object-declarations.html
    // That's why we need a private val and a public val that just exposes it
    private val _meshUpdateObservable = object : ObservableWithListenersList() {
        fun meshUpdateCompleted() = stateChanged()
    }
    val meshUpdateObservable = _meshUpdateObservable

    private val segmentFragmentMap =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<Long, TLongHashSet>())
    private val fragmentSegmentMap =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<TLongHashSet, Long>())
    private val relevantBindingsAndPropertiesMap =
        FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<Long, RelevantBindingsAndProperties>())

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
        .also { rendererSettings.meshesEnabledProperty().bind(it.meshesEnabledProperty()) }

    val settings: MeshSettings
        get() = managedSettings.globalSettings

    val meshesGroup: Group
        get() = manager.meshesGroup

    // TODO This listener is added to all mesh states. This is a problem if a lot of ids are selected
    // TODO and all use global mesh settings. Whenever the global mesh settings are changed, the
    // TODO managerCancelAndUpdate would be notified for each of the meshes, which can temporarily slow down
    // TODO the UI for quite some time (tens of seconds). A smarter way might be a single thread executor that
    // TODO executes only the last request and has a delay.
    // TODO
    // TODO This may be fixed now by using manager.requestCancelAndUpdate(), which submits a task
    // TODO to a LatestTaskExecutor with a delay of 100ms.
    private val managerCancelAndUpdate = InvalidationListener { manager.requestCancelAndUpdate() }

    @Synchronized
    fun setMeshesToSelection() {
        currentTask?.cancel()
        currentTask = null
        val task = CancelableTask { setMeshesToSelectionImpl(it) }
        currentTask = task
        updateExecutors.submit(task)
    }

    private fun setMeshesToSelectionImpl(isCanceled: () -> Boolean) {
        if (isCanceled()) return

        val (selection, presentKeys) = synchronized (this) {
            val selection = TLongHashSet(selectedSegments.selectedSegments)
            val presentKeys = TLongHashSet().also { set -> segmentFragmentMap.keys.forEach { set.add(it) } }
            Pair(selection, presentKeys)
        }

        // We need to collect all ids that are selected but not yet present in the 3d viewer and vice versa
        // to generate a diff and only apply the diff to the current mesh selection.
        // Additionally, segments that are inconsistent, i.e. if the set of fragments has changed for a segment
        // we need to replace it as well.
        val presentButNotSelected = TLongHashSet()
        val selectedButNotPresent = TLongHashSet()
        val inconsistentIds = TLongHashSet()

        selection.forEach { id ->
            if (id !in presentKeys)
                selectedButNotPresent.add(id)
            true
        }

        presentKeys.forEach { id ->
            if (id !in selection)
                presentButNotSelected.add(id)
            else if (segmentFragmentMap[id]?.let { selectedSegments.assignment.isSegmentConsistent(id, it) } == false)
                inconsistentIds.add(id)
            true
        }

        presentButNotSelected.addAll(inconsistentIds)
        selectedButNotPresent.addAll(inconsistentIds)

        // remove meshes that are present but not in selection
        removeMeshesFor(presentButNotSelected.toArray().toList())
        // add meshes for all selected ids that are not present yet
        // removing mesh if is canceled is necessary because could be canceled between call to isCanceled.asBoolean and createMeshFor
        // another option would be to synchronize on a lock object but that is not necessary
        for (id in selectedButNotPresent) {
            if (isCanceled()) break
            createMeshFor(id)
            if (isCanceled()) removeMeshFor(id)
        }

        if (!isCanceled())
            manager.requestCancelAndUpdate()

        if (!isCanceled())
            this._meshUpdateObservable.meshUpdateCompleted()
    }

    private fun createMeshFor(key: Long) {
        if (key in segmentFragmentMap) return
        selectedSegments
            .assignment
            .getFragments(key)
            ?.takeUnless { it.isEmpty }
            ?.let { fragments ->
                segmentFragmentMap[key] = fragments
                fragmentSegmentMap[fragments] = key
                manager.createMeshFor(fragments, false) { setupGeneratorState(key, it) }
            }
    }

    private fun setupGeneratorState(key: Long, state: MeshGenerator.State) {
        state.settings.bindTo(managedSettings.getOrAddMesh(key, true))
        val relevantBindingsAndProperties = relevantBindingsAndPropertiesMap.computeIfAbsent(key) {
            RelevantBindingsAndProperties(key, argbStream)
        }
        state.colorProperty().bind(relevantBindingsAndProperties.colorProperty())
        state.settings.levelOfDetailProperty().addListener(managerCancelAndUpdate)
        state.settings.coarsestScaleLevelProperty().addListener(managerCancelAndUpdate)
        state.settings.finestScaleLevelProperty().addListener(managerCancelAndUpdate)
    }

    private fun MeshGenerator.State.release() {
        settings.levelOfDetailProperty().removeListener(managerCancelAndUpdate)
        settings.coarsestScaleLevelProperty().removeListener(managerCancelAndUpdate)
        settings.finestScaleLevelProperty().removeListener(managerCancelAndUpdate)
        settings.unbind()
        colorProperty().unbind()
    }

    private fun removeMeshFor(key: Long) {
        segmentFragmentMap.remove(key)?.let { fragmentSet ->
            fragmentSegmentMap.remove(fragmentSet)
            manager.removeMeshFor(fragmentSet) {
                it.release()
                relevantBindingsAndPropertiesMap.remove(key)?.release()
            }
        }
    }

    private fun removeMeshesFor(keys: Iterable<Long>) {
        val fragmentSetKeys = keys.mapNotNull { key ->
            relevantBindingsAndPropertiesMap.remove(key)?.release()
            segmentFragmentMap.remove(key)?.also { fragmentSegmentMap.remove(it) }
        }
        manager.removeMeshesFor(fragmentSetKeys) { it.release() }
    }

    @Synchronized
    fun removeAllMeshes() {
        currentTask?.cancel()
        currentTask = null
        val task = CancelableTask { removeAllMeshesImpl() }
        updateExecutors.submit(task)
        currentTask = task
    }

    private fun removeAllMeshesImpl() {
        removeMeshesFor(segmentFragmentMap.keys.toList())
        _meshUpdateObservable.meshUpdateCompleted()
    }

    @Synchronized
    fun refreshMeshes() {
        currentTask?.cancel()
        currentTask = null
        val task = CancelableTask { isCanceled ->
            this.removeAllMeshesImpl()
            if (labelBlockLookup is Invalidate<*>) labelBlockLookup.invalidateAll()
            if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
            this.setMeshesToSelectionImpl(isCanceled)
        }
        updateExecutors.submit(task)
        currentTask = task
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
