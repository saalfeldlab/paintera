package org.janelia.saalfeldlab.paintera.filters.clahe

import net.imglib2.img.NativeImg
import net.imglib2.img.basictypeaccess.FloatAccess
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess
import net.imglib2.img.basictypeaccess.array.FloatArray as FloatArrayAccess
import net.imglib2.type.AbstractNativeType
import net.imglib2.type.NativeTypeFactory
import net.imglib2.util.Fraction
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The transfer function of a tile: the output from 0 to 1 for each of [bins] input bins, stored as [bins] floats per pixel. */
internal class TransferType(val bins: Int, private val img: NativeImg<*, out FloatAccess>? = null) : AbstractNativeType<TransferType>() {

	private lateinit var dataAccess: FloatAccess

	init {
		if (img == null)
			dataAccess = FloatArrayAccess(bins)
	}

	/* the position of this pixel's first bin in the data */
	private val offset get() = index().get() * bins

	fun at(bin: Int) = dataAccess.getValue(offset + bin)

	/**
	 * Sets this to the clipped, equalized transfer of a tile's [histogram]; the identity if it has no pixels, so that a
	 * neighbor's blend is unaffected.
	 */
	fun set(histogram: ClaheHistogram, maxSlope: Double) {
		val values = (dataAccess as ArrayDataAccess<*>).currentStorageArray as FloatArray
		val pixelCount = histogram.pixelCount
		if (pixelCount == 0) {
			for (bin in 0 until bins)
				values[offset + bin] = bin / (bins - 1).toFloat()
			return
		}
		/* never below the flat histogram, or the redistribution has no free bin to fill */
		val limit = (maxSlope * pixelCount / bins).roundToInt().coerceAtLeast(ceil(pixelCount / bins.toDouble()).toInt())
		Clahe.createTransfer(histogram.counts, limit, values, offset)
	}

	override fun set(c: TransferType) {
		for (bin in 0 until bins)
			dataAccess.setValue(offset + bin, c.at(bin))
	}

	override fun valueEquals(t: TransferType) = bins == t.bins && (0 until bins).all { at(it) == t.at(it) }

	override fun createVariable() = TransferType(bins)

	override fun copy() = createVariable().also { it.set(this) }

	override fun updateContainer(c: Any?) {
		dataAccess = img!!.update(c)
	}

	override fun getEntitiesPerPixel() = Fraction(bins.toLong(), 1L)

	override fun duplicateTypeOnSameNativeImg() = TransferType(bins, img)

	override fun getNativeTypeFactory(): NativeTypeFactory<TransferType, FloatAccess> = NativeTypeFactory.FLOAT { TransferType(bins, it) }
}
