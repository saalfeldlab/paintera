package org.janelia.saalfeldlab.paintera.data.n5

import bdv.viewer.Interpolation
import bdv.viewer.Source
import mpicbg.spim.data.sequence.VoxelDimensions
import net.imglib2.Dimensions
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealInterval
import net.imglib2.RealPoint
import net.imglib2.RealRandomAccess
import net.imglib2.RealRandomAccessible
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.converter.Converter
import net.imglib2.converter.Converters
import net.imglib2.img.list.ListImg
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.view.Views
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.paintera.data.SlicedRenderSource

/**
 * Source which composes a list of channel slice positions as a 3D composite source.
 *
 * @param source the sliced source
 * @param channelAxis the channel axis
 * @param channels the active positions along [channelAxis], in composite order
 */
class ChannelCompositeSource<D, T>(
	val source: N5DataSource<*, *>,
	val channelAxis: Int,
	channels: LongArray
) : Source<VolatileWithSet<RealComposite<T>>>, SlicedRenderSource
		where D : RealType<D>, T : AbstractVolatileRealType<D, T> {

	val channels: LongArray = channels.copyOf()

	val numChannels: Int
		get() = channels.size

	val numDatasetChannels: Int = source.metadataState.datasetAttributes.dimensions[channelAxis].toInt()

	@Suppress("UNCHECKED_CAST")
	private fun composite(t: Int, level: Int) = source.getCompositeSource<T>(t, level, channelAxis) as RandomAccessibleInterval<RealComposite<T>>

	@Suppress("UNCHECKED_CAST")
	private fun compositeData(t: Int, level: Int) = source.getCompositeDataSource<D>(t, level, channelAxis) as RandomAccessibleInterval<RealComposite<D>>

	/* only load the active channels */
	private val viewerConverter = Converter<RealComposite<T>, VolatileWithSet<RealComposite<T>>> { composite, target ->
		target.setT(composite)
		target.isValid = channels.all { composite.get(it).isValid }
	}

	/** View over the collapsed channel dimension */
	fun getDataSource(t: Int, level: Int): RandomAccessibleInterval<RealComposite<D>> = compositeData(t, level)

	override fun getSource(t: Int, level: Int): RandomAccessibleInterval<VolatileWithSet<RealComposite<T>>> =
		Converters.convert(composite(t, level), viewerConverter, VolatileWithSet(null, true))

	override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation): RealRandomAccessible<VolatileWithSet<RealComposite<T>>> {
		val extended = Views.extendValue(composite(t, level), composite(List(numDatasetChannels) { zero() }))
		val interpolated = when (method) {
			Interpolation.NLINEAR -> CompositeInterpolation(extended, channels, numDatasetChannels, zero())
			else -> Views.interpolate(extended, NearestNeighborInterpolatorFactory())
		}
		return Converters.convert(interpolated, viewerConverter, VolatileWithSet(null, true))
	}

	@Suppress("UNCHECKED_CAST")
	private fun zero(): T = (source.type as T).createVariable().apply { setZero(); isValid = true }

	/**
	 * Trilinear interpolation of a composite [source], per channel: every sample reads each neighbor composite once and
	 * weights the active [channels] out of it. A sample is valid where every neighbor it weights is; the other channels
	 * stay zero
	 */
	private class CompositeInterpolation<D : RealType<D>, T : AbstractVolatileRealType<D, T>>(
		private val source: RandomAccessible<RealComposite<T>>,
		private val channels: LongArray,
		private val numChannels: Int,
		private val zero: T
	) : RealRandomAccessible<RealComposite<T>> {

		override fun numDimensions() = 3

		override fun realRandomAccess(): RealRandomAccess<RealComposite<T>> = Access()

		override fun realRandomAccess(interval: RealInterval): RealRandomAccess<RealComposite<T>> = Access()

		override fun getType(): RealComposite<T> = Access().get()

		private inner class Access : RealPoint(3), RealRandomAccess<RealComposite<T>> {

			private val access = source.randomAccess()
			private val values = List(numChannels) { zero.copy() }
			private val result = composite(values)
			private val sums = DoubleArray(channels.size)
			private val floor = LongArray(3)
			private val fraction = DoubleArray(3)

			override fun get(): RealComposite<T> {
				for (d in 0 until 3) {
					val position = getDoublePosition(d)
					floor[d] = kotlin.math.floor(position).toLong()
					fraction[d] = position - floor[d]
				}
				sums.fill(0.0)
				var valid = true
				for (corner in 0 until 8) {
					var weight = 1.0
					for (d in 0 until 3) {
						val high = corner shr d and 1
						access.setPosition(floor[d] + high, d)
						weight *= if (high == 1) fraction[d] else 1.0 - fraction[d]
					}
					/* a neighbor with no weight, e.g. at an integer position, need not be loaded */
					if (weight == 0.0)
						continue
					val neighbor = access.get()
					for (idx in channels.indices) {
						val value = neighbor.get(channels[idx])
						valid = valid && value.isValid
						sums[idx] += weight * value.realDouble
					}
				}
				for (idx in channels.indices)
					values[channels[idx].toInt()].apply {
						setReal(sums[idx])
						isValid = valid
					}
				return result
			}

			override fun copy(): RealRandomAccess<RealComposite<T>> = Access().also { it.setPosition(this) }
		}
	}

	private companion object {

		fun <T : RealType<T>> composite(values: List<T>): RealComposite<T> =
			Views.collapseReal(ListImg(values, 1, values.size.toLong())).randomAccess().get()
	}

	override fun getSourceTransform(t: Int, level: Int, transform: AffineTransform3D) = source.getSourceTransform(t, level, transform)

	override fun getType(): VolatileWithSet<RealComposite<T>> = VolatileWithSet(null, true)

	override fun getName(): String = source.name

	override fun getVoxelDimensions(): VoxelDimensions? = null

	override fun getNumMipmapLevels(): Int = source.numMipmapLevels

	override fun isPresent(t: Int): Boolean = source.isPresent(t)

	override fun isSliced(): Boolean = true

	override fun setSliceCacheHints(level: Int, hints: CacheHints?) = source.setSliceCacheHints(level, hints)

	override fun prefetchSlice(level: Int, sourceToScreen: AffineTransform3D, screenInterval: Dimensions, interpolation: Interpolation, prefetchHints: CacheHints?) =
		source.prefetchSlice(level, sourceToScreen, screenInterval, interpolation, prefetchHints)
}
