package org.janelia.saalfeldlab.paintera.metrics.labels

import gnu.trove.map.hash.TLongLongHashMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.label.LabelMultisetEntry
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState
import org.janelia.saalfeldlab.util.AdaptiveScaleBlocks
import org.janelia.saalfeldlab.util.MultiscaleBlock
import org.janelia.saalfeldlab.util.interval
import java.util.concurrent.atomic.LongAdder

/**
 * Find the fragments in a region of a Paintera label multiset dataset, exactly, and counts them.
 *
 * Blocks provided by [AdaptiveScaleBlocks]. Per pixel, higher resolution blocks may be requested if:
 * - [countLabels] is `true`, AND
 * - for a given block, the sum of the ID counts for the pixel is less than the number of pixels at the highest resolution level.
 *   - This indicates we have cut-off some entries (due to maxNumEntries configuration) so we need to go deeper.
 *
 * @param countLabels if `true`, count the labels. Otherwise, just track the unique IDs.
 */
class MultisetCountsForRegion(
	metadataState: PainteraDataMultiscaleMetadataState,
	private val countLabels: Boolean,
) : UniqueLabelsForRegion(metadataState) {

	init {
		require(metadataState.isLabelMultiset) { "An exact count needs a label multiset dataset" }
		dataLevels.levels.zipWithNext().forEachIndexed { levelIndex, (finer, coarser) ->
			require(coarser.scaleFactors.indices.all { coarser.scaleFactors[it] % finer.scaleFactors[it] == 0L }) {
				"Level ${levelIndex + 1} is not a whole multiple of level $levelIndex"
			}
		}
	}

	override val blocks: AdaptiveScaleBlocks<Any>
		get() = dataLevels.blocks

	override suspend fun visit(block: MultiscaleBlock<Any>, metrics: LabelMetrics, s0PixelAcc: LongAdder) {
		currentCoroutineContext().ensureActive()
		val idsAreExactWithoutReading = !countLabels && !block.isPartial && (block.level.index == 0 || dataLevels.maxNumEntriesUnbounded)
		if (idsAreExactWithoutReading)
			return super.visit(block, metrics, s0PixelAcc)

		val counts = TLongLongHashMap()
		@Suppress("UNCHECKED_CAST")
		MultisetCounter(counts).count(block.level.index, block.data as RandomAccessibleInterval<LabelMultisetType>)
		metrics.addCounts(counts)
		s0PixelAcc.add(block.firstLevelSize)
	}

	/**
	 * Adds the counts of the multiset pixels of a region to [accCounts]. A multiset pixel is valid if its counts sum to the first scale
	 * level pixels it covers; Otherwise, move to the next finest level and try again, until we are at the finest level.
	 */
	private inner class MultisetCounter(private val accCounts: TLongLongHashMap) {

		private val highResBounds = dataLevels.highResBounds
		private val numDimensions = highResBounds.numDimensions()

        /* Many of these fields are used to avoid allocations in the tight loop. Ensure you are being careful when using them.  */
        private val position = LongArray(numDimensions)
		private val targetMin = LongArray(numDimensions)
		private val targetMax = LongArray(numDimensions)
		private val targetInterval = FinalInterval.wrap(targetMin, targetMax)
		private val entryRef = LabelMultisetEntry()
		private var ids = LongArray(INITIAL_ENTRIES)
		private var entryCounts = LongArray(INITIAL_ENTRIES)

		suspend fun count(levelIndex: Int, region: RandomAccessibleInterval<LabelMultisetType>) {
			currentCoroutineContext().ensureActive()
			val cursor = region.localizingCursor()
			while (cursor.hasNext()) {
				val multiset = cursor.next()
				var sum = 0L
				var size = 0
				for (entry in multiset.entrySetWithRef(entryRef)) {
					if (size == ids.size) {
						ids = ids.copyOf(size * 2)
						entryCounts = entryCounts.copyOf(size * 2)
					}
					val entryCount = entry.count.toLong()
					ids[size] = entry.element.id()
					entryCounts[size] = entryCount
					sum += entryCount
					size++
				}
				cursor.localize(position)
				if (levelIndex == 0 || sum == sizeAtFirstLevel(levelIndex, position))
					for (entryIdx in 0 until size)
						accCounts.adjustOrPutValue(ids[entryIdx], entryCounts[entryIdx], entryCounts[entryIdx])
				else
					count(levelIndex - 1, finerRegion(levelIndex, position))
			}
		}

		/** The pixels of [targetLevel] under the pixel at [position] of [level]; the interval is reused by the next call */
		private fun coveredIntervalAt(level: Int, position: LongArray, targetLevel: Int): Interval {
			val scaleFactors = blocks.levels[level].scaleFactors
			val targetScaleFactors = blocks.levels[targetLevel].scaleFactors
			for (dim in 0 until numDimensions) {
				val highResMin = position[dim] * scaleFactors[dim]
				val highResMax = (highResMin + scaleFactors[dim] - 1).coerceAtMost(highResBounds.max(dim))
				targetMin[dim] = highResMin / targetScaleFactors[dim]
				targetMax[dim] = highResMax / targetScaleFactors[dim]
			}
			return targetInterval
		}

		private fun sizeAtFirstLevel(levelIndex: Int, position: LongArray) = Intervals.numElements(coveredIntervalAt(levelIndex, position, 0))

		private fun finerRegion(levelIndex: Int, position: LongArray): RandomAccessibleInterval<LabelMultisetType> {
            val finerLevel = levelIndex - 1
            @Suppress("UNCHECKED_CAST")
            val lmtImg = blocks.levels[finerLevel].image as RandomAccessibleInterval<LabelMultisetType>
            val intervalAtFinerLevel = coveredIntervalAt(levelIndex, position, finerLevel)
            return lmtImg.interval(intervalAtFinerLevel)
		}
	}

	private companion object {
		const val INITIAL_ENTRIES = 16
	}
}
