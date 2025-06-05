package org.janelia.saalfeldlab.paintera.meshes.managed

import com.google.common.collect.HashBiMap
import gnu.trove.set.hash.TLongHashSet
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.paint.Color
import kotlinx.coroutines.*
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
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMeshCacheLoader
import org.janelia.saalfeldlab.paintera.state.label.FragmentLabelMeshCacheKey
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.HashWrapper
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Arrays
import java.util.concurrent.ExecutorService
import kotlin.coroutines.coroutineContext
import kotlin.math.min

private typealias Segments = TLongHashSet
private typealias Fragments = TLongHashSet

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
class MeshManagerWithAssignmentForSegments(
	source: DataSource<*, *>,
	getMeshFor: GetMeshFor<Long>,
	viewFrustumProperty: ObservableValue<ViewFrustum>,
	eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
	managers: ExecutorService,
	workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
	meshViewUpdateQueue: MeshViewUpdateQueue<Long>,
	val labelBlockLookup: LabelBlockLookup,
	val selectedSegments: SelectedSegments,
	val argbStream: AbstractHighlightingARGBStream,
) : MeshManager<Long>(
	source,
	GetBlockListFor { level, segment ->
		val intervals = mutableSetOf<HashWrapper<Interval>>()
		val fragments = selectedSegments.assignment.getFragments(segment)
		fragments.forEach { id ->
			labelBlockLookup.read(level, id).map { HashWrapper.interval(it) }.let { intervals.addAll(it) }
			true
		}
		intervals.map { it.data }.toTypedArray()
	},
	getMeshFor,
	viewFrustumProperty,
	eyeToWorldTransformProperty,
	managers,
	workers,
	meshViewUpdateQueue
) {


	private class RelevantBindingsAndProperties(private val key: Long, private val stream: AbstractHighlightingARGBStream) {
		val colorProperty = SimpleObjectProperty(calculateColor())
		private val colorUpdater = stream.subscribe { colorProperty.value = calculateColor() }

		private fun calculateColor(): Color = Colors.toColor(stream.argb(key) or 0xFF000000.toInt())
		fun release() = colorUpdater.unsubscribe()
	}

	private val updateExecutors = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private var currentTask: Job? = null

	// setMeshesCompleted is only visible to enclosing manager if
	// _meshUpdateObservable is private
	// https://kotlinlang.org/docs/reference/object-declarations.html
	// That's why we need a private val and a public val that just exposes it
	private val meshUpdateObservable = object : ObservableWithListenersList() {
		fun meshUpdateCompleted() = stateChanged()
	}

	private val segmentFragmentBiMap = HashBiMap.create<Long, Fragments>()
	private val relevantBindingsAndPropertiesMap = FXCollections.synchronizedObservableMap(FXCollections.observableHashMap<Long, RelevantBindingsAndProperties>())

	@Synchronized
	override fun releaseMeshState(key: Long, state: MeshGenerator.State) {
		segmentFragmentBiMap.remove(key)
		relevantBindingsAndPropertiesMap.remove(key)?.release()
		super.releaseMeshState(key, state)
	}

	fun setMeshesToSelection() {

		currentTask?.cancel()
		currentTask = updateExecutors.launch {
			setMeshesToSelectionImpl()
		}
	}

	private suspend fun setMeshesToSelectionImpl() {
		coroutineContext.ensureActive()
		val (selectedSegments, presentSegments) = synchronized(this) {
			val selectedSegments = Segments(selectedSegments.segments)
			val presentSegments = Segments().also { set -> segmentFragmentBiMap.keys.forEach { set.add(it) } }
			selectedSegments to presentSegments
		}

		// We need to collect all ids that are selected but not yet present in the 3d viewer and vice versa
		// to generate a diff and only apply the diff to the current mesh selection.
		// Additionally, segments that are inconsistent, i.e. if the set of fragments has changed for a segment
		// we need to replace it as well.
		val segmentsToRemove = Segments()
		val segmentsToAdd = Segments()

		val inconsistentIds = Segments()

		selectedSegments.forEach { segment ->
			if (segment !in presentSegments && !IdService.isTemporary(segment))
				segmentsToAdd.add(segment)
			true
		}

		synchronized(this) {
			presentSegments.forEach { segment ->
				if (segment !in selectedSegments)
					segmentsToRemove.add(segment)
				else if (segmentFragmentBiMap[segment]?.let { this.selectedSegments.assignment.isSegmentConsistent(segment, it) } == false)
					inconsistentIds.add(segment)
				true
			}
		}


		segmentsToRemove.addAll(inconsistentIds)
		segmentsToAdd.addAll(inconsistentIds)

		// remove meshes that are present but not in selection
		if (!segmentsToRemove.isEmpty)
			removeMeshes(segmentsToRemove.toArray().toList())
		// add meshes for all selected ids that are not present yet
		// removing mesh if is canceled is necessary because could be canceled between call to isCanceled.asBoolean and createMeshFor
		// another option would be to synchronize on a lock object but that is not necessary
		if (!segmentsToAdd.isEmpty)
			createMeshes(segmentsToAdd)

		if (!segmentsToAdd.isEmpty || !segmentsToRemove.isEmpty)
			manager.requestCancelAndUpdate()

		this.meshUpdateObservable.meshUpdateCompleted()
	}

	private suspend fun createMeshes(segments: Segments) {
		coroutineContext.ensureActive()

		for (segment in segments) {
			if (segment in segmentFragmentBiMap) return
			selectedSegments.assignment.getFragments(segment)
				?.takeUnless { it.isEmpty }
				?.let { fragments ->
					segmentFragmentBiMap[segment] = fragments
					createMeshFor(segment)
				}
		}
	}

	override suspend fun createMeshFor(key: Long) {
		super.createMeshFor(key)
		if (!coroutineContext.isActive) {
			removeMesh(key)
		}
	}

	override fun setupMeshState(key: Long, state: MeshGenerator.State) {
		state.settings.bindTo(managedSettings.getMeshSettings(key, true))
		val relevantBindingsAndProperties = relevantBindingsAndPropertiesMap.computeIfAbsent(key) {
			RelevantBindingsAndProperties(key, argbStream)
		}
		state.colorProperty().bind(relevantBindingsAndProperties.colorProperty)
		super.setupMeshState(key, state)

	}

	@Synchronized
	override fun removeMesh(key: Long) {
		super.removeMesh(key)
		meshUpdateObservable.meshUpdateCompleted()
	}

	@Synchronized
	override fun removeMeshes(keys: Iterable<Long>) {
		super.removeMeshes(keys)
		meshUpdateObservable.meshUpdateCompleted()
	}

	@Synchronized
	override fun removeAllMeshes() {
		super.removeAllMeshes()
		meshUpdateObservable.meshUpdateCompleted()
	}

	override fun refreshMeshes() {
		super.removeAllMeshes()
		if (labelBlockLookup is Invalidate<*>) labelBlockLookup.invalidateAll()
		if (getMeshFor is Invalidate<*>) getMeshFor.invalidateAll()
		setMeshesToSelection()
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		fun LabelBlockLookup.read(level: Int, fragmentId: Long) = read(LabelBlockLookupKey(level, fragmentId))

		@JvmStatic
		fun <D : IntegerType<D>> fromBlockLookup(
			dataSource: DataSource<D, *>,
			selectedSegments: SelectedSegments,
			argbStream: AbstractHighlightingARGBStream,
			viewFrustumProperty: ObservableValue<ViewFrustum>,
			eyeToWorldTransformProperty: ObservableValue<AffineTransform3D>,
			labelBlockLookup: LabelBlockLookup,
			meshManagerExecutors: ExecutorService,
			meshWorkersExecutors: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
		): MeshManagerWithAssignmentForSegments {
			LOG.debug("Data source is type {}", dataSource.javaClass)
			val actualLookup = (dataSource as? MaskedSource<D, *>)
				?.let { LabelBlockLookupWithMaskedSource.create(labelBlockLookup, it) }
				?: labelBlockLookup

			// Set up mesh caches
			val segmentMaskGenerators = Array(dataSource.numMipmapLevels) { SegmentMaskGenerators.create<D, BoolType>(dataSource, it) { segment -> selectedSegments.assignment.getFragments(segment) } }
			val loaders = Array(dataSource.numMipmapLevels) {
				SegmentMeshCacheLoader(
					{ dataSource.getDataSource(0, it) },
					segmentMaskGenerators[it],
					dataSource.getSourceTransformCopy(0, it)
				)
			}
			val getMeshFor = GetMeshFor.FromCache.fromLoaders(*loaders)

			return MeshManagerWithAssignmentForSegments(
				dataSource,
				getMeshFor,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				meshManagerExecutors,
				meshWorkersExecutors,
				MeshViewUpdateQueue(),
				actualLookup,
				selectedSegments,
				argbStream
			)
		}
	}

	private class CachedLabelBlockLookupWithMaskedSource<D : IntegerType<D>>(
		private val delegate: CachedLabelBlockLookup,
		private val maskedSource: MaskedSource<D, *>,
	) :
		LabelBlockLookupWithMaskedSource<D>(delegate, maskedSource),
		Invalidate<LabelBlockLookupKey> by delegate

	private open class LabelBlockLookupWithMaskedSource<D : IntegerType<D>>(
		private val delegate: LabelBlockLookup,
		private val maskedSource: MaskedSource<D, *>,
	) : LabelBlockLookup by delegate {

		override fun read(key: LabelBlockLookupKey): Array<Interval> {
			val blocks = delegate.read(key).map { HashWrapper.interval(it) }.toMutableSet()
			blocks += affectedBlocksForLabel(maskedSource, key.level, key.id).map { HashWrapper.interval(it) }
			return blocks.map { it.data }.toTypedArray()
		}

		companion object {
			private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

			fun <D : IntegerType<D>> create(delegate: LabelBlockLookup, maskedSource: MaskedSource<D, *>) = when (delegate) {
				is CachedLabelBlockLookup -> CachedLabelBlockLookupWithMaskedSource(delegate, maskedSource)
				else -> LabelBlockLookupWithMaskedSource(delegate, maskedSource)
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

	fun getFragmentsFor(segment: Long): Fragments = segmentFragmentBiMap[segment] ?: selectedSegments.assignment.getFragments(segment)

	val getBlockListForSegment: GetBlockListFor<Long>
		get() = GetBlockListFor { level, segment ->
			getBlockListFor.getBlocksFor(level, segment)
		}

	val getBlockListForFragments: GetBlockListFor<FragmentLabelMeshCacheKey> = GetBlockListFor { level, key ->
		val intervals = mutableSetOf<HashWrapper<Interval>>()
		val fragments = key.fragments
		fragments.forEach { id ->
			labelBlockLookup.read(level, id).map { HashWrapper.interval(it) }.let { intervals.addAll(it) }
			true
		}
		intervals.map { it.data }.toTypedArray()
	}

	val getMeshForLongKey: GetMeshFor<Long>
		get() = object : GetMeshFor<Long> {
			override fun getMeshFor(key: ShapeKey<Long>): PainteraTriangleMesh? = getMeshFor.getMeshFor(
				ShapeKey(
					key.shapeId(),
					key.scaleIndex(),
					key.simplificationIterations(),
					key.smoothingLambda(),
					key.smoothingIterations(),
					key.minLabelRatio(),
					key.overlap(),
					key.min(),
					key.max()
				)
			)
		}
}
