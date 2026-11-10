package org.janelia.saalfeldlab.paintera.filters.clahe

import net.imglib2.Dimensions
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.Point
import net.imglib2.RandomAccess
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealLocalizable
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.CellLoader
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions
import net.imglib2.img.cell.CellGrid
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform2D
import net.imglib2.realtransform.RealTransform
import net.imglib2.type.mask.Masked
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.ConstantUtils
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheTransferCellImage.Companion.createClaheTransferCellImage
import org.janelia.saalfeldlab.util.convert
import org.janelia.saalfeldlab.util.convertWith
import org.janelia.saalfeldlab.util.get
import org.janelia.saalfeldlab.util.union
import java.util.function.BiConsumer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * [imgAndMask] equalized over the frame, with one cell per tile. A pixel blends the transfer functions of the tiles around
 * it, so a cell is computed from its own tile and the next ones in [transfers], the first time it is read; 0 is not in mask.
 */
internal data class ClaheCellImage(
	val imgAndMask: RandomAccessibleInterval<out Masked<FloatType>>,
	val transfers: ClaheTransferCellImage,
	val img: CachedCellImg<FloatType, *>,
) {

	companion object {

		/** the tiles of a [frame], about [tileSides] pixels wide; the last row and column can be short */
		fun createTileGrid(frame: Dimensions, tileSides: DoubleArray): CellGrid {
			val cellDimensions = IntArray(2) { dim ->
				val tileCount = (frame.dimension(dim) / tileSides[dim]).roundToInt().coerceAtLeast(1)
				ceil(frame.dimension(dim).toDouble() / tileCount).toInt()
			}
			return CellGrid(frame.dimensionsAsLongArray(), cellDimensions)
		}

		/**
		 * The tiles to read for [screenInterval], as an interval of the frame: the tiles it touches and their neighbors, since
		 * a cell is computed whole and each of its pixels blends the tiles next to it.
		 */
		fun blendInterval(tileGrid: CellGrid, screenInterval: Interval): Interval {
			val lowerTiles = LongArray(2) { ((screenInterval.min(it) / tileGrid.cellDimension(it)) - 1).coerceAtLeast(0) }
			val upperTiles = LongArray(2) { ((screenInterval.max(it) / tileGrid.cellDimension(it)) + 1).coerceAtMost(tileGrid.gridDimension(it) - 1) }
			val tiles = tileGrid.cellIntervals()
			return tiles[Point(*lowerTiles)] union tiles[Point(*upperTiles)]
		}

		/* tile `i` is centered at `i`; the last tile is centered as if it were whole */
		fun screenToTile(tileGrid: CellGrid): AffineTransform2D {
			val tileToScreen = AffineTransform2D()
			tileToScreen.scale(tileGrid.cellDimension(0).toDouble(), tileGrid.cellDimension(1).toDouble())
			tileToScreen.translate((tileGrid.cellDimension(0) - 1) / 2.0, (tileGrid.cellDimension(1) - 1) / 2.0)
			return tileToScreen.inverse()
		}

		/**
		 * @param imgAndMask a masked img over the [blendInterval] of whatever will be read
		 * @param tileGrid from [createTileGrid]; its cells are the cells of the image
		 */
		fun createClaheCellImage(
            imgAndMask: RandomAccessibleInterval<out Masked<FloatType>>,
            tileGrid: CellGrid,
            histogram: ClaheHistogram,
            maxSlope: Double,
		): ClaheCellImage {
			val transfers = createClaheTransferCellImage(imgAndMask, tileGrid, histogram, maxSlope)
			val frame = FinalInterval(*tileGrid.imgDimensions)
			val blends = Views.interval(tileBlends(transfers.img, tileGrid), frame)
			val equalizedView = blends.convertWith(imgAndMask, FloatType()) { blend, pixel, equalizedPixel ->
				if (pixel.mask() == 0.0)
					equalizedPixel.set(0f)
				else
					equalizedPixel.set(histogram.intensity(blend.transfer(histogram.bin(pixel.value().get()))).toFloat())
			}
			val loader = CellLoader<FloatType> { cell ->
				LoopBuilder.setImages(Views.interval(equalizedView, cell), cell).forEachPixel(BiConsumer { value, target -> target.set(value) })
			}
			val img = ReadOnlyCachedCellImgFactory().create(
				tileGrid.imgDimensions,
				FloatType(),
				loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions(*tileGrid.cellDimensions)
			)
			return ClaheCellImage(imgAndMask, transfers, img)
		}

		private fun tileBlends(transfers: RandomAccessibleInterval<TransferType>, tileGrid: CellGrid): RandomAccessible<TileBlend> {
			val positions: RandomAccessible<RandomAccess<FloatType>> = Views.bundle(ConstantUtils.constantRandomAccessible(FloatType(), 2))
			val screenToTile = screenToTile(tileGrid)
			val tileCounts = IntArray(2) { tileGrid.gridDimension(it).toInt() }
			return positions.convert({ TileBlend(transfers.randomAccess(), screenToTile, tileCounts) }) { position, blend -> blend.moveTo(position) }
		}
	}
}

/** the two tiles around a position along each axis and their weights; [transfer] blends their transfer functions at a bin */
private class TileBlend(
	private val transfers: RandomAccess<TransferType>,
	private val screenToTile: RealTransform,
	private val tileCounts: IntArray,
) {

	private val screen = DoubleArray(2)
	private val tile = DoubleArray(2)
	private val columns = IntArray(2)
	private val rows = IntArray(2)
	private val columnWeights = DoubleArray(2)
	private val rowWeights = DoubleArray(2)

	fun moveTo(position: RealLocalizable) {
		position.localize(screen)
		screenToTile.apply(screen, tile)
		setAxis(tile[0], tileCounts[0], columns, columnWeights)
		setAxis(tile[1], tileCounts[1], rows, rowWeights)
	}

	/* the first and last tiles repeat beyond their centers */
	private fun setAxis(tileCoordinate: Double, count: Int, tileAxis: IntArray, weights: DoubleArray) {
		val clamped = tileCoordinate.coerceIn(0.0, count - 1.0)
		val lower = floor(clamped).toInt()
		tileAxis[0] = lower
		tileAxis[1] = (lower + 1).coerceAtMost(count - 1)
		weights[1] = clamped - lower
		weights[0] = 1 - weights[1]
	}

	fun transfer(bin: Int): Double {
		var blended = 0.0
		for (row in 0..1)
			for (column in 0..1) {
				val weight = rowWeights[row] * columnWeights[column]
				if (weight == 0.0)
					continue
				transfers.setPosition(columns[column], 0)
				transfers.setPosition(rows[row], 1)
				blended += weight * transfers.get().at(bin)
			}
		return blended
	}
}
