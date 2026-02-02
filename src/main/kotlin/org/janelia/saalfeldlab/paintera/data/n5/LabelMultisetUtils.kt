package org.janelia.saalfeldlab.paintera.data.n5

import io.github.oshai.kotlinlogging.KotlinLogging
import net.imglib2.cache.CacheLoader
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.cache.util.LoaderCacheAsCacheAdapter
import net.imglib2.img.cell.Cell
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.label.*
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.N5Exception
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets
import java.nio.ByteBuffer
import java.util.function.BiFunction

private val LOG = KotlinLogging.logger { }

/**
 * Open an N5 dataset of [LabelMultisetType] as a memory cached
 * [LazyCellImg].
 *
 * @param n5
 * the n5 reader
 * @param dataset
 * the dataset path
 * @param nullReplacement
 * a function returning data for null blocks
 * @param loaderCache
 * the cache
 * @return the LabelMultiset image
 */
@JvmOverloads
fun openLabelMultiset(
	n5: N5Reader,
	dataset: String,
	loaderCache: LoaderCache<Long, Cell<VolatileLabelMultisetArray>> = SoftRefLoaderCache()
): CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray> {
	if (!N5LabelMultisets.isLabelMultisetType(n5, dataset)) throw N5Exception.N5IOException("$dataset is not a label multiset dataset.")

	val attributes = n5.getDatasetAttributes(dataset)
	val grid = CellGrid(attributes.dimensions, attributes.blockSize)

	val loader = LabelMultisetCacheLoader(n5, dataset) as CacheLoader<Long, Cell<VolatileLabelMultisetArray>>
	val wrappedCache = LoaderCacheAsCacheAdapter(loaderCache, loader)

	val emptyAccess = VolatileLabelMultisetArray(1, true, longArrayOf(Label.INVALID))
	LabelMultisetType(emptyAccess).set(0, 0)

	val cachedImg = CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray>(
		grid,
		LabelMultisetType().entitiesPerPixel,
		wrappedCache,
		emptyAccess
	)
	cachedImg.setLinkedType(LabelMultisetType(cachedImg))
	return cachedImg
}

internal fun constantNullReplacementEmptyArgMax(id : Long): BiFunction<CellGrid, LongArray, ByteArray> = BiFunction { cellGrid, cellPos ->

	val cellMin = LongArray(cellPos.size) { d -> cellPos[d] * cellGrid.cellDimension(d) }
	val cellDims = IntArray(cellMin.size) { d -> cellGrid.cellDimension(d) }
	val numElements = cellDims.reduce { d1, d2 -> d1 * d2 }

	val listData = LongMappedAccessData.factory.createStorage(0)
	val list = LabelMultisetEntryList(listData, 0)
	val entry = LabelMultisetEntry(id, 1)
	list.createListAt(listData, 0)
	list.add(entry)
	val listSize = list.sizeInBytes.toInt()

	val bytes = ByteArray(
		Integer.BYTES // for argmax size (always zero)
				+ numElements * Integer.BYTES // for mappings
				+ list.sizeInBytes.toInt() // for actual entries (one single entry)
	)

	val bb = ByteBuffer.wrap(bytes)

	// argmax ;
	//  No longer necessary to serialize, since we can calculate fairly cheaply during deserialization
	//  Indicated by size 0
	bb.putInt(0)

	// offsets
	repeat(numElements) { bb.putInt(0) }

	if (id != 0L) {
		LOG.debug { "Putting id $id" }
		repeat(listSize) { i ->
			bb.put(ByteUtils.getByte(listData.data, i.toLong()))
		}
	}
	LOG.debug { "Returning ${bytes.size} bytes for $numElements elements" }
	bytes
}


class LabelMultisetCacheLoader(private val n5: N5Reader, private val dataset: String) :
    AbstractLabelMultisetLoader(generateCellGrid(n5, dataset)) {

    val datasetAttributes: DatasetAttributes by lazy { n5.getDatasetAttributes(dataset) }

    override fun getData(vararg gridPosition: Long): ByteArray? {
        LOG.trace { "Reading block for position $gridPosition" }
        val block = runCatching {
            n5.readBlock(dataset, datasetAttributes, *gridPosition)
        }.getOrElse { e ->
            LOG.error { "Error reading block (${gridPosition.contentToString()}) for $dataset" }
            LOG.trace(e) {}
            null
        }

        return block?.let {
            LOG.trace { "Read block $it for position $gridPosition" }
            it.data as ByteArray?
        } ?: let {
            LOG.trace { "No block at $gridPosition" }
            null
        }
    }

	override fun get(key: Long): Cell<VolatileLabelMultisetArray> {

		val numDimensions = grid.numDimensions()

		val cellSize = IntArray(numDimensions)
		val cellMin = LongArray(numDimensions)
		val cellDimensions = IntArray(numDimensions).also { grid.cellDimensions(it) }
		grid.getCellDimensions(key, cellMin, cellSize)

		val gridPosition = LongArray(numDimensions) { cellMin[it] / cellDimensions[it] }

        val bytes = this.getData(*gridPosition)
        LOG.trace { "Got $bytes bytes from loader." }

		val n = Intervals.numElements(*cellSize).toInt()
		val access = bytes
			?.let { LabelUtils.fromBytes(bytes, n) }
			?: EMPTY_ACCESS
		return Cell(cellSize, cellMin, access)
	}

	companion object {
		private val EMPTY_ACCESS = VolatileLabelMultisetArray(0, true, longArrayOf(Label.INVALID))

		private fun generateCellGrid(n5: N5Reader, dataset: String): CellGrid {
			val attributes = n5.getDatasetAttributes(dataset)

			val dimensions = attributes.dimensions
			val cellDimensions = attributes.blockSize

			return CellGrid(dimensions, cellDimensions)
		}
	}
}