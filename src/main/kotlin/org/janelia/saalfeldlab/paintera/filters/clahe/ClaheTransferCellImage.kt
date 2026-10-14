package org.janelia.saalfeldlab.paintera.filters.clahe

import net.imglib2.Point
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.CellLoader
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.mask.Masked
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.util.get
import org.janelia.saalfeldlab.util.interval
import kotlin.math.roundToInt

/**
 * The pixels of a tile counted over [bins] of an intensity [range].
 */
internal class ClaheHistogram(val bins: Int, val range: ClosedFloatingPointRange<Double>) {

	private val rangeWidth = (range.endInclusive - range.start).let { if (it != 0.0) it else 1.0 }
	private val binsPerUnit = (bins - 1) / rangeWidth

	val counts = IntArray(bins)

	/** the number of pixels with data in the tile */
	var pixelCount = 0
		private set

	fun bin(value: Float) = ((value - range.start) * binsPerUnit).roundToInt().coerceIn(0, bins - 1)

	/** the intensity that a transfer function value from 0 to 1 maps to */
	fun intensity(transfer: Double) = range.start + transfer * rangeWidth

	fun emptyCopy() = ClaheHistogram(bins, range)

	fun setData(tile: Iterable<Masked<FloatType>>) {
		counts.fill(0)
		pixelCount = 0
		tile.forEach { pixel ->
			if (pixel.mask() > 0.0) {
				counts[bin(pixel.value().get())]++
				pixelCount++
			}
		}
	}
}

/**
 * The [TransferType] of each tile, as an image of size `columns x rows` with one cell per 4 x 4 tiles.
 * The tiles of a cell are histogrammed from [imgAndMask] the first time any of them is read.
 */
internal data class ClaheTransferCellImage(
	val imgAndMask: RandomAccessibleInterval<out Masked<FloatType>>,
	val img: CachedCellImg<TransferType, *>,
	val histogram: ClaheHistogram,
	val maxSlope: Double,
) {

	companion object {

		/* a pixel can read up to 4 tiles */
		private const val TILES_PER_CELL = 4

		fun createClaheTransferCellImage(
			imgAndMask: RandomAccessibleInterval<out Masked<FloatType>>,
			tileGrid: CellGrid,
			histogram: ClaheHistogram,
			maxSlope: Double,
		): ClaheTransferCellImage {
			val tiles = tileGrid.cellIntervals()
			val loader = CellLoader<TransferType> { cell ->
				val tileHistogram = histogram.emptyCopy()
				val tilesInCell = cell.localizingCursor()
				while (tilesInCell.hasNext()) {
					val transfer = tilesInCell.next()
					val tile = tiles[Point(tilesInCell.getLongPosition(0), tilesInCell.getLongPosition(1))]
					/* skip tiles outside the source img */
					if (!Intervals.contains(imgAndMask, tile))
						continue
					tileHistogram.setData(imgAndMask.interval(tile))
					transfer.set(tileHistogram, maxSlope)
				}
			}
			val img = ReadOnlyCachedCellImgFactory().create(
				longArrayOf(tileGrid.gridDimension(0), tileGrid.gridDimension(1)),
				TransferType(histogram.bins),
				loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions(TILES_PER_CELL, TILES_PER_CELL)
			)
			return ClaheTransferCellImage(imgAndMask, img, histogram, maxSlope)
		}
	}
}
