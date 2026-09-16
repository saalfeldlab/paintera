package org.janelia.saalfeldlab.paintera.control

import javafx.beans.property.DoubleProperty
import net.imglib2.Point
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealPoint
import net.imglib2.Volatile
import net.imglib2.histogram.Histogram1d
import net.imglib2.histogram.Real1dBinMapper
import net.imglib2.img.array.ArrayImgs
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.label.VolatileLabelMultisetType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.SourceStateWithBackend
import org.janelia.saalfeldlab.paintera.state.raw.ChannelComposition
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.util.asIterable
import org.janelia.saalfeldlab.util.convertRAI
import java.util.Random
import kotlin.math.roundToLong

/** The intensity range of a raw source, reset to its default or estimated from what is on screen */
object IntensityThreshold {

	fun autoIntensityMinMax(rawSource: SourceState<*, RealType<*>>, viewer: ViewerPanelFX) {
		(rawSource as? ConnectomicsRawState<*, *>)?.channels?.let { channels ->
			if (channels.globalRange)
				autoGlobalMinMax(channels, viewer)
			else
				/* one threshold per active channel */
				for (channel in channels.activeChannels)
					autoChannelMinMax(channels, channel, viewer)
			return
		}
		val converter = rawSource.converter() as? ARGBColorConverter<*> ?: return
		val level = viewer.state.bestMipMapLevel
		val dataSource = rawSource.getDataSource()
		val samples = sampleScreen(dataSource.getSource(0, level), dataSource.getSourceTransformCopy(0, level), viewer)
		autoMinMax(samples, converter.minProperty(), converter.maxProperty(), defaultRange(rawSource))
	}

	/** One threshold over the active channels' samples, set on every channel */
	private fun autoGlobalMinMax(channels: ChannelComposition<*, *>, viewer: ViewerPanelFX) {
		val level = viewer.state.bestMipMapLevel
		val perChannel = channels.activeChannels.map { channel ->
			@Suppress("UNCHECKED_CAST")
			val channelView = channels.sourceView(channel, level) as RandomAccessibleInterval<RealType<*>>
			sampleScreen(channelView, channels.sourceTransform(level), viewer)
		}
		if (perChannel.isEmpty())
			return
		val pooled = Samples(perChannel.flatMap { it.values.asList() }.toDoubleArray(), perChannel.first().isInteger)
		val converter = channels.converter
		val (newMin, newMax) = estimate(pooled, converter.minProperty(0).get(), converter.maxProperty(0).get(), channels.defaultRange) ?: return
		for (channel in 0 until converter.numChannels()) {
			converter.minProperty(channel).set(newMin)
			converter.maxProperty(channel).set(newMax)
		}
	}

	/** @param channel a dataset channel index */
	fun autoChannelMinMax(channels: ChannelComposition<*, *>, channel: Int, viewer: ViewerPanelFX) {
		val level = viewer.state.bestMipMapLevel
		@Suppress("UNCHECKED_CAST")
		val channelView = channels.sourceView(channel, level) as RandomAccessibleInterval<RealType<*>>
		val samples = sampleScreen(channelView, channels.sourceTransform(level), viewer)
		autoMinMax(samples, channels.converter.minProperty(channel), channels.converter.maxProperty(channel), channels.defaultRange)
	}

	fun resetIntensityMinMax(rawSource: SourceState<*, RealType<*>>) {
		(rawSource as? ConnectomicsRawState<*, *>)?.channels?.let { channels ->
			val toReset = if (channels.globalRange) 0 until channels.numChannels else channels.activeChannels
			for (channel in toReset)
				resetChannelMinMax(channels, channel)
			return
		}
		val converter = rawSource.converter() as? ARGBColorConverter<*> ?: return
		val (min, max) = defaultRange(rawSource)
		converter.min = min
		converter.max = max
	}

	/** @param channel a dataset channel index */
	fun resetChannelMinMax(channels: ChannelComposition<*, *>, channel: Int) {
		val (min, max) = channels.defaultRange
		channels.converter.minProperty(channel).set(min)
		channels.converter.maxProperty(channel).set(max)
	}

	/** The metadata range, or the type's range without metadata */
	private fun defaultRange(rawSource: SourceState<*, RealType<*>>): Pair<Double, Double> {
		((rawSource as? SourceStateWithBackend<*, *>)?.backend as? SourceStateBackendN5<*, *>)?.metadataState?.let {
			return it.minIntensity to it.maxIntensity
		}
		val extension = rawSource.getDataSource().getSource(0, 0).type.createVariable().let {
			when (it) {
				is VolatileLabelMultisetType, is LabelMultisetType -> UnsignedLongType(0)
				else -> it
			}
		}
		return extension.minValue to extension.maxValue
	}

	/**
	 * Up to [NUM_SAMPLES] values of [sourceRai] under the viewer's pixels: a grid over the viewer first, then random
	 * pixels, skipping pixels outside the source and cells that are not loaded yet.
	 */
	private fun sampleScreen(sourceRai: RandomAccessibleInterval<RealType<*>>, sourceToGlobal: AffineTransform3D, viewer: ViewerPanelFX): Samples {
		val globalToViewer = AffineTransform3D().also { viewer.state.getViewerTransform(it) }
		val screenToSource = globalToViewer.concatenate(sourceToGlobal).inverse()
		val width = viewer.width
		val height = viewer.height
		val random = Random()
		val sampleSpace = sequence {
			for (x in 0 until SAMPLE_GRID)
				for (y in 0 until SAMPLE_GRID)
					yield((y + 0.5) * width / SAMPLE_GRID to (x + 0.5) * height / SAMPLE_GRID)
		}
		val randomScreenSpace = generateSequence { random.nextDouble() * width to random.nextDouble() * height }

		val access = sourceRai.randomAccess()
		val position = RealPoint(3)
		val sourcePos = Point(3)
		val values = DoubleArray(NUM_SAMPLES)
		var numValues = 0
		for ((x, y) in (sampleSpace + randomScreenSpace).take(MAX_SAMPLE_ATTEMPTS)) {
			position.setPosition(doubleArrayOf(x, y, 0.0))
			screenToSource.apply(position, position)
			for (d in 0 until 3)
				sourcePos.setPosition(position.getDoublePosition(d).roundToLong(), d)
            /* skip if the position is not in the source */
            if (!Intervals.contains(sourceRai, sourcePos))
				continue
			val sourceVal = access.setPositionAndGet(sourcePos)
            /* if volatile, and invalid, skip */
			if ((sourceVal as? Volatile<*>)?.isValid == false)
				continue
			values[numValues++] = sourceVal.realDouble
			if (numValues == NUM_SAMPLES)
				break
		}
		return Samples(values.copyOf(numValues), sourceRai.type is IntegerType<*>)
	}

	private fun autoMinMax(samples: Samples, min: DoubleProperty, max: DoubleProperty, default: Pair<Double, Double>) {
		val (newMin, newMax) = estimate(samples, min.get(), max.get(), default) ?: return
		min.set(newMin)
		max.set(newMax)
	}

	/** The new range; null leaves the current one, e.g. with nothing loaded yet */
	private fun estimate(samples: Samples, curMin: Double, curMax: Double, default: Pair<Double, Double>): Pair<Double, Double>? {
		if (samples.values.size < 2)
			return null
		if (curMin == curMax)
			return default
		/* a collapsed histogram estimate goes back to the default */
		return when {
			curMin == default.first && curMax == default.second -> samples.values.min() to samples.values.max()
			samples.isInteger -> estimateWithHistogram(IntType(), samples.values, curMin, curMax) ?: default
			else -> estimateWithHistogram(DoubleType(), samples.values, curMin, curMax) ?: default
		}
	}

	//TODO Caleb: Render histogram, let users select based on slider
	private fun <T : RealType<T>> estimateWithHistogram(type: T, values: DoubleArray, curMin: Double, curMax: Double): Pair<Double, Double>? {
		val numSamples = values.size.toLong()
		val numBins = numSamples.coerceIn(100, 1000)
		val binMapper = Real1dBinMapper<T>(curMin, curMax, numBins, false)
		val histogram = Histogram1d(binMapper)
		val img = ArrayImgs.doubles(values, numSamples).convertRAI(type) { src, target -> target.setReal(src.realDouble) }.asIterable()
		histogram.countData(img)

		val counts = histogram.toLongArray()
		var runningSumMin = 0L
		var runningSumMax = 0L
		var minBinIdx = 0
		var maxBinIdx = counts.size - 1
		val threshold = numSamples / 25
		for (i in counts.indices) {
			val count = counts[i]
			runningSumMin += count
			if (runningSumMin >= threshold) {
				minBinIdx = i
				break
			}
		}
		for (i in counts.indices.reversed()) {
			val count = counts[i]
			runningSumMax += count
			if (runningSumMax >= threshold) {
				maxBinIdx = i
				break
			}
		}

		if (minBinIdx >= maxBinIdx)
			return null

		val min = histogram.getLowerBound(minBinIdx.toLong(), type).let { type.realDouble }
		val max = histogram.getUpperBound(maxBinIdx.toLong(), type).let { type.realDouble }
		/* the same estimate twice in a row means the range has converged; reset to default */
		return (min to max).takeUnless { min == curMin && max == curMax }
	}

	/** Sampled values and whether they came from an integer type, which picks the histogram's bin type */
	private class Samples(val values: DoubleArray, val isInteger: Boolean)

	private const val SAMPLE_GRID = 32
	private const val NUM_SAMPLES = SAMPLE_GRID * SAMPLE_GRID
	private const val MAX_SAMPLE_ATTEMPTS = NUM_SAMPLES * 100

}
