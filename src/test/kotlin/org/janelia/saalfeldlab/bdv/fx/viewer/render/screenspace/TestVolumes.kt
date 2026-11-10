package org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace

import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.view.Views

/** an image over [region] using whose pixels are from [pixelSupplier] */
fun image(region: Interval, pixelSupplier: (x: Long, y: Long) -> Double): RandomAccessibleInterval<FloatType> {
	val image = Views.translate(ArrayImgs.floats(region.dimension(0), region.dimension(1)), region.min(0), region.min(1))
	val cursor = image.localizingCursor()
    cursor.forEach { it ->
        val pixel = pixelSupplier(cursor.getLongPosition(0), cursor.getLongPosition(1))
        it.setReal(pixel)
    }
	return image
}

/** a single channel volume whose value, coverage and completeness are functions of the position */
fun volume(
	coverage: (x: Long, y: Long, z: Double) -> Double = { _, _, _ -> 1.0 },
	complete: (z: Double) -> Boolean = { true },
	value: (x: Long, y: Long, z: Double) -> Double
) = ScreenImageProvider { screenInterval, sliceToScreen ->
	/* the tests sample with translations along z only */
	val z = sliceToScreen.get(2, 3)
	ScreenImage(image(screenInterval) { x, y -> value(x, y, z) }, image(screenInterval) { x, y -> coverage(x, y, z) }, complete(z))
}

fun ScreenImage.value(x: Long, y: Long) = rasterImg.getAt(x, y).get().toDouble()

fun ScreenImage.covered(x: Long, y: Long) = mask.getAt(x, y).get().toDouble()
