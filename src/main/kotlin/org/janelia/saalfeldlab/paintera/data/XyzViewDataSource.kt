package org.janelia.saalfeldlab.paintera.data

import bdv.viewer.Interpolation
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealInterval
import net.imglib2.cache.Invalidate
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.img.cell.CellGrid
import net.imglib2.interpolation.InterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.Type
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.state.cropAtLevel
import org.janelia.saalfeldlab.paintera.state.cropBoundsAtLevel
import java.util.function.Function
import java.util.function.Supplier

/**
 * A [RandomAccessibleIntervalDataSource] presented through [view]: every level is cropped to the view's
 * [XyzView.xyzCrop] as it is at the time of the call, while the grid stays over the uncropped backing
 */
class XyzViewDataSource<D : Type<D>, T : Type<T>>(
	private val uncropped: Array<RandomAccessibleInterval<D>>,
	sources: Array<RandomAccessibleInterval<T>>,
	private val transforms: Supplier<Array<AffineTransform3D>>,
	invalidate: Invalidate<Long>,
	dataInterpolation: Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>>,
	interpolation: Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>,
	name: String,
	private val view: XyzView
) : RandomAccessibleIntervalDataSource<D, T>(uncropped, sources, transforms, invalidate, dataInterpolation, interpolation, name) {

	override fun getXyzView(): XyzView = view

	private fun <A> crop(source: RandomAccessibleInterval<A>, level: Int): RandomAccessibleInterval<A> =
		view.xyzCrop?.let { Views.interval(source, cropAtLevel(it, transforms.get(), level)) } ?: source

	override fun getSource(t: Int, level: Int): RandomAccessibleInterval<T> = crop(super.getSource(t, level), level)

	override fun getDataSource(t: Int, level: Int): RandomAccessibleInterval<D> = crop(super.getDataSource(t, level), level)

	override fun getCropInterval(level: Int): RealInterval? = view.xyzCrop?.let { cropBoundsAtLevel(it, transforms.get(), level) }

	/* the canvas spans the whole source, not the crop */
	override fun getGrid(level: Int): CellGrid {
		val source = uncropped[level]
		return (source as? AbstractCellImg<*, *, *, *>)?.cellGrid
			?: CellGrid(source.dimensionsAsLongArray(), Intervals.dimensionsAsIntArray(source))
	}
}
