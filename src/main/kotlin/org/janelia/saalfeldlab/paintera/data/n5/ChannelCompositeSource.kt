package org.janelia.saalfeldlab.paintera.data.n5

import bdv.viewer.Interpolation
import bdv.viewer.Source
import mpicbg.spim.data.sequence.VoxelDimensions
import net.imglib2.Dimensions
import net.imglib2.RandomAccessibleInterval
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
 * The channels of a sliced nD raw source as one composite: every selected channel is the source's own 3D view,
 * sliced at the source's current position of every other non-spatial axis, stacked and collapsed. Stepping the
 * source's view re-slices every channel.
 *
 * @param source the sliced source
 * @param channelAxis the source axis the channels lie along
 * @param channels the positions along [channelAxis], in composite order
 */
class ChannelCompositeSource<D, T>(
	val source: N5DataSource<*, *>,
	val channelAxis: Int,
	channels: LongArray
) : Source<VolatileWithSet<RealComposite<T>>>, SlicedRenderSource
		where D : RealType<D>, T : AbstractVolatileRealType<D, T> {

	/* the source's NativeType bounds are not part of a raw state's generics, so it is star-projected and cast here */
	@Suppress("UNCHECKED_CAST")
	private fun channelSource(t: Int, level: Int, channel: Long) = source.getSource(t, level, channelMapping(channel)) as RandomAccessibleInterval<T>

	@Suppress("UNCHECKED_CAST")
	private fun channelDataSource(t: Int, level: Int, channel: Long) = source.getDataSource(t, level, channelMapping(channel)) as RandomAccessibleInterval<D>

	private fun channelMapping(channel: Long) = source.metadataState.xyzView.spatialMapping().withSlicePosition(channelAxis, channel)

	val channels: LongArray = channels.copyOf()

	val numChannels: Int
		get() = channels.size

	private val viewerConverter = Converter<RealComposite<T>, VolatileWithSet<RealComposite<T>>> { composite, target ->
		target.setT(composite)
		target.isValid = (0 until numChannels).all { composite.get(it.toLong()).isValid }
	}

	/** The channel views stacked and collapsed */
	private fun <V : RealType<V>> composite(channelView: (Long) -> RandomAccessibleInterval<V>): RandomAccessibleInterval<RealComposite<V>> =
		Views.collapseReal(Views.stack(channels.map(channelView)))

	fun getDataSource(t: Int, level: Int): RandomAccessibleInterval<RealComposite<D>> = composite { channelDataSource(t, level, it) }

	override fun getSource(t: Int, level: Int): RandomAccessibleInterval<VolatileWithSet<RealComposite<T>>> =
		Converters.convert(composite { channelSource(t, level, it) }, viewerConverter, VolatileWithSet(null, true))

	override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation): RealRandomAccessible<VolatileWithSet<RealComposite<T>>> {
		/* a composite has no linear interpolation; the extension is a valid, zero composite */
		val extended = Views.extendValue(composite { channelSource(t, level, it) }, extension())
		return Converters.convert(Views.interpolate(extended, NearestNeighborInterpolatorFactory()), viewerConverter, VolatileWithSet(null, true))
	}

	/* an ArrayImg cannot back a volatile type, so the zero composite is built over a list */
	private fun extension(): RealComposite<T> {
		@Suppress("UNCHECKED_CAST")
		val zero = (source.type as T).createVariable().apply { setZero(); isValid = true }
		val zeros = ListImg(List(numChannels) { zero.copy() }, 1, numChannels.toLong())
		return Views.collapseReal(zeros).randomAccess().get()
	}

	override fun getSourceTransform(t: Int, level: Int, transform: AffineTransform3D) = source.getSourceTransform(t, level, transform)

	override fun getType(): VolatileWithSet<RealComposite<T>> = VolatileWithSet(null, true)

	override fun getName(): String = source.name

	override fun getVoxelDimensions(): VoxelDimensions? = null

	override fun getNumMipmapLevels(): Int = source.numMipmapLevels

	override fun isPresent(t: Int): Boolean = source.isPresent(t)

	override fun isSliced(): Boolean = true

	/* the renderer passes null hints before the first frame */
	override fun setSliceCacheHints(level: Int, hints: CacheHints?) = source.setSliceCacheHints(level, hints)

	/* prefetches the channel the source's view is sliced at; the other channels come in on demand */
	override fun prefetchSlice(level: Int, sourceToScreen: AffineTransform3D, screenInterval: Dimensions, interpolation: Interpolation, prefetchHints: CacheHints?) =
		source.prefetchSlice(level, sourceToScreen, screenInterval, interpolation, prefetchHints)
}
