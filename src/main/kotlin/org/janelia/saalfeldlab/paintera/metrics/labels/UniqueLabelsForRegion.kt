package org.janelia.saalfeldlab.paintera.metrics.labels

import gnu.trove.set.hash.TLongHashSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.type.label.LabelMultisetEntry
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState
import org.janelia.saalfeldlab.util.AdaptiveScaleBlocks
import org.janelia.saalfeldlab.util.MultiscaleBlock
import java.util.concurrent.atomic.LongAdder

/**
 * Finds the fragments in a region of any Paintera label dataset.
 *
 * Only the first scale level's `unique-labels` is exact; a downsampled level can miss a small fragment if it samples
 * the data or limits the entries per pixel. Blocks fully inside the region use the first scale level's `unique-labels`, and the pixels of the
 * region's borders are read, whether they are integers or label multisets.
 */
open class UniqueLabelsForRegion(metadataState: PainteraDataMultiscaleMetadataState) {

	internal val dataLevels = PainteraDataLevels(metadataState)

	/** The blocks of the levels that are visited, highest resolution first */
	internal open val blocks: AdaptiveScaleBlocks<Any> by lazy { AdaptiveScaleBlocks(dataLevels.levels.take(1), dataLevels.highResBounds) }

	@OptIn(ExperimentalCoroutinesApi::class)
	private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors())

	/** The bounding box interval of the first scale level */
	fun firstLevelBounds(): Interval = FinalInterval(dataLevels.highResBounds)

	/**
	 * Accumulate the [LabelMetrics] over [interval].
	 *
	 * @param s0PixelAcc adds the number of first scale level pixels of [interval] accounted for so far. Used for progress reporting
	 */
	suspend fun process(
        interval: Interval,
        metrics: LabelMetrics,
        s0PixelAcc: LongAdder
    ) = withContext(dispatcher) {
		blocks.forEachBlock(interval) { block ->
            visit(block, metrics, s0PixelAcc)
        }
	}

	internal open suspend fun visit(block: MultiscaleBlock<Any>, metrics: LabelMetrics, s0PixelAcc: LongAdder) {
		currentCoroutineContext().ensureActive()
		if (block.isPartial)
			metrics.addIds(readIdsFromPixelData(block))
		else
			metrics.addIds(dataLevels.readUniqueLabels(block))
		s0PixelAcc.add(block.firstLevelSize)
	}

	private fun readIdsFromPixelData(block: MultiscaleBlock<Any>): TLongHashSet {
		val ids = TLongHashSet()
		val entryRef = LabelMultisetEntry()
		block.data.forEach { pixel ->
			when (pixel) {
                is LabelMultisetType -> for (entry in pixel.entrySetWithRef(entryRef))
                    ids.add(entry.element.id())
                is IntegerType<*> -> ids.add(pixel.integerLong)
			}
		}
		return ids
	}

	companion object {


        /**
         * Get a [UniqueLabelsForRegion] over the provdied [metadataState], and a flag whether to [countLabelsSize] or not.
         * Keep in mind, [countLabelsSize]=`true` is only supported for label multiset data.
         *
         * @param metadataState the metadata state
         * @param countLabelsSize whether to count label size or not
         * @return the [UniqueLabelsForRegion]
         */
        fun from(metadataState: PainteraDataMultiscaleMetadataState, countLabelsSize: Boolean): UniqueLabelsForRegion =
			if (metadataState.isLabelMultiset && (countLabelsSize || PainteraDataLevels(metadataState).maxNumEntriesUnbounded))
				MultisetCountsForRegion(metadataState, countLabelsSize)
			else
				UniqueLabelsForRegion(metadataState)
	}
}
