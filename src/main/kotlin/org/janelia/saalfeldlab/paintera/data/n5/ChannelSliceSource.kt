package org.janelia.saalfeldlab.paintera.data.n5

import bdv.viewer.Interpolation
import bdv.viewer.MaskUtils
import bdv.viewer.Source
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealRandomAccessible
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.type.mask.Masked
import net.imglib2.type.mask.MaskedType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.paintera.data.SlicedRenderSource
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.interpolate

/** Single slice position along the channel axis of [source], as a 3D source */
@Suppress("UNCHECKED_CAST")
class ChannelSliceSource<D : RealType<D>, T : AbstractVolatileRealType<D, T>>(
	val source: N5DataSource<*, *>,
	val channelAxis: Int,
	val channel: Long
) : Source<T> by (source as Source<T>), SlicedRenderSource by source {

	/* the source's current view, so the other slice positions such as time follow it; only the channel is fixed */
	private val mapping
		get() = source.xyzView.spatialMapping().withSlicePosition(channelAxis, channel)

	override fun getSource(t: Int, level: Int): RandomAccessibleInterval<T> = source.getSource(t, level, mapping) as RandomAccessibleInterval<T>
    override fun doBoundingBoxCulling() = source.doBoundingBoxCulling()

    override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation): RealRandomAccessible<T> {
		val factory = if (method == Interpolation.NLINEAR) ClampingNLinearInterpolatorFactory<T>() else NearestNeighborInterpolatorFactory()
		return getSource(t, level).extendValue(zero()).interpolate(factory)
	}

	override fun getInterpolatedMaskedSource(t: Int, level: Int, method: Interpolation): RealRandomAccessible<out Masked<T>> =
		MaskUtils.extendAndInterpolateMasked(Masked.withConstant(getSource(t, level), 1.0) as RandomAccessibleInterval<MaskedType<T>>, method)

	private fun zero(): T = type.createVariable().apply { setZero(); isValid = true }
}
