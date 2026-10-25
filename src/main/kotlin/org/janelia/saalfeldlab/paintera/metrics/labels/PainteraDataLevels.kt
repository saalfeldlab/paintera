package org.janelia.saalfeldlab.paintera.metrics.labels

import gnu.trove.set.hash.TLongHashSet
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.CachedCellImg
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata
import org.janelia.saalfeldlab.paintera.data.n5.openLabelMultiset
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState
import org.janelia.saalfeldlab.util.AdaptiveScaleBlocks
import org.janelia.saalfeldlab.util.MultiscaleBlock
import org.janelia.saalfeldlab.util.MultiscaleLevel
import org.janelia.saalfeldlab.util.n5.N5Helpers
import kotlin.collections.all

/**
 * The scale levels of a Paintera label dataset and its `unique-labels`, as an [AdaptiveScaleBlocks] pyramid.
 * Opening the levels reads no data.
 */
internal class PainteraDataLevels(private val metadataState: PainteraDataMultiscaleMetadataState) {

	private val uniqueLabelsMetadata = checkNotNull(metadataState.uniqueLabelsMetadata) { "${metadataState.dataset} has no unique-labels" }

	private val N5DatasetMetadata.normalizedPath: String
		get() = N5URI.normalizeGroupPath(path)

	val levels: List<MultiscaleLevel<Any>>

	val blocks: AdaptiveScaleBlocks<Any>

	val highResBounds: Interval

    /**
     * If data is [net.imglib2.type.label.LabelMultisetType] and max num entries is unbounded at all scale levels
     */
    val maxNumEntriesUnbounded: Boolean

	init {
		val dataLevels = metadataState.metadata.childrenMetadata
		check(dataLevels.isNotEmpty()) { "No scale levels" }
		levels = List(dataLevels.size) { level ->
			val image = openImage(dataLevels[level])
			val uniqueLabelsAttributes = uniqueLabelsMetadata.childrenMetadata[level].attributes
			check(image.dimensionsAsLongArray().contentEquals(uniqueLabelsAttributes.dimensions) && image.cellGrid.cellDimensions.contentEquals(uniqueLabelsAttributes.blockSize)) {
				"unique-labels at level $level does not match the grid of ${dataLevels[level].path}"
			}
			val scaleFactors = LongArray(image.numDimensions()) { axis ->
				val factor = metadataState.scaleFactors[level][axis]
				check(factor >= 1.0 && factor == Math.rint(factor)) { "Level $level downsamples axis $axis by $factor, which is not a whole factor" }
				factor.toLong()
			}
			@Suppress("UNCHECKED_CAST")
			MultiscaleLevel(level, image as RandomAccessibleInterval<Any>, image.cellGrid, scaleFactors)
		}
		highResBounds = FinalInterval(levels[0].image)
		blocks = AdaptiveScaleBlocks(levels, highResBounds)
		maxNumEntriesUnbounded = dataLevels.all {
            val maxNumEntries : Int = metadataState.reader[it.normalizedPath, N5Helpers.MAX_NUM_ENTRIES_KEY] ?: -1
            maxNumEntries == -1
        }
	}

	fun readUniqueLabels(block: MultiscaleBlock<Any>): TLongHashSet {
		val ids = TLongHashSet()
		val levelMetadata = uniqueLabelsMetadata.childrenMetadata[block.level.index]
		val uniqueLabels = metadataState.reader.readBlock<LongArray>(levelMetadata.normalizedPath, levelMetadata.attributes, *block.gridPosition)
		uniqueLabels?.let { ids.addAll(it.data) }
		return ids
	}

	private fun openImage(levelMetadata: N5DatasetMetadata): CachedCellImg<*, *> =
		if (metadataState.isLabelMultiset)
			openLabelMultiset(metadataState.reader, levelMetadata.normalizedPath)
		else
			N5Utils.open<Nothing>(metadataState.reader, levelMetadata.normalizedPath)
}
