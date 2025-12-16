package org.janelia.saalfeldlab.paintera.data.n5

import com.google.common.util.concurrent.AtomicDouble
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import net.imglib2.Dimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.type.numeric.IntegerType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.util.interval

internal object LabelSourceUtils {

	private val LOG = KotlinLogging.logger {  }

	@JvmStatic
	@JvmName("findMaxId")
	fun findMaxIdBlocking(source: DataSource<out IntegerType<*>, *>): Long {
		return runBlocking {
			findMaxId(source)
		}
	}

	@JvmSynthetic
	suspend fun findMaxId(source: DataSource<out IntegerType<*>, *>, progress: AtomicDouble? = null, maxIdTracker : ((Long) -> Unit)? = null): Long {
		val rai = source.getDataSource(0, 0)
		val blockSize = blockSizeFromRai(rai)
		val cells = Grids.collectAllContainedIntervals(Intervals.minAsLongArray(rai), Intervals.maxAsLongArray(rai), blockSize)



		val delta = 1.0 / cells.size
		return coroutineScope {
			cells.map {
				async {
					val max = findMaxId(rai.interval(it))
					progress?.getAndAdd(delta)
					maxIdTracker?.invoke(max)
					max
				}
			}
				.awaitAll()
				.also { progress?.set(1.0) }
				.maxOrNull() ?: Label.Companion.INVALID
		}
	}


	internal fun findMaxId(rai: RandomAccessibleInterval<out IntegerType<*>>): Long {
		var maxId: Long = Label.Companion.INVALID
		for (t in rai) {
			val id = t.integerLong
			if (id > maxId) maxId = id
		}
		return maxId
	}

	@JvmStatic
	@JvmName("blockSizeFromRai")
	internal fun blockSizeFromRai(rai: RandomAccessibleInterval<*>): IntArray {
		if (rai is AbstractCellImg<*, *, *, *>) {
			val cellGrid = rai.cellGrid
			val blockSize = IntArray(cellGrid.numDimensions())
			cellGrid.cellDimensions(blockSize)
			LOG.debug { "$rai is a cell img with block size $blockSize" }
			return blockSize
		}
		val argMaxDim = argMaxDim(rai)
		val blockSize = Intervals.dimensionsAsIntArray(rai)
		blockSize[argMaxDim] = 1
		return blockSize
	}

	private fun argMaxDim(dims: Dimensions): Int {
		var max = -1L
		var argMax = -1
		for (d in 0..<dims.numDimensions()) {
			if (dims.dimension(d) > max) {
				max = dims.dimension(d)
				argMax = d
			}
		}
		return argMax
	}
}