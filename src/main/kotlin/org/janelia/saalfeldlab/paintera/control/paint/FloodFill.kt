package org.janelia.saalfeldlab.paintera.control.paint

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.value.ObservableValue
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.*
import net.imglib2.algorithm.fill.FloodFill
import net.imglib2.algorithm.neighborhood.DiamondShape
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.Label
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.util.Util
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.net.imglib2.util.AccessBoxRandomAccessible
import org.janelia.saalfeldlab.paintera.Paintera.Companion.getPaintera
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.getGlobalViewerInterval
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.extendValue
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.stream.Collectors

class FloodFill<T : IntegerType<T>>(
	private val activeViewerProperty: ObservableValue<ViewerPanelFX>,
	val source: MaskedSource<T, *>,
	private val assignment: FragmentSegmentAssignment,
	val requestRepaint: Consumer<Interval?>,
	private val isVisible: BooleanSupplier
) {

	fun fillAt(x: Double, y: Double, fillSupplier: (() -> Long?)?): Job {
		val fill = fillSupplier?.invoke() ?: let {
			return Job().apply {
				val reason = CancellationException("Received invalid label -- will not fill.")
				LOG.debug(reason) { }
				completeExceptionally(reason)
			}
		}
		return fillAt(x, y, fill)
	}

	private fun fillAt(x: Double, y: Double, fill: Long): Job {
		// TODO should this check happen outside?

		if (!isVisible.asBoolean) {
			return Job().apply {
				val reason = CancellationException("Selected source is not visible -- will not fill")
				LOG.debug(reason) { }
				completeExceptionally(reason)
			}
		}

		val level = 0
		val labelTransform = AffineTransform3D()
		// TODO What to do for time series?
		val time = 0
		source.getSourceTransform(time, level, labelTransform)

		val realSourceSeed = viewerToSourceCoordinates(x, y, activeViewerProperty.value, labelTransform)
		val sourceSeed = Point(realSourceSeed.numDimensions())
		for (d in 0 until sourceSeed.numDimensions()) {
			sourceSeed.setPosition(Math.round(realSourceSeed.getDoublePosition(d)), d)
		}

		LOG.debug { "Filling source $source with label $fill at $sourceSeed" }
		try {
			return fill(time, level, fill, sourceSeed, assignment)
		} catch (e: MaskInUse) {
			LOG.error(e) {}
			return Job().apply { completeExceptionally(e) }
		}
	}

	@Throws(MaskInUse::class)
	private fun fill(
		time: Int,
		level: Int,
		fill: Long,
		seed: Localizable,
		assignment: FragmentSegmentAssignment?
	): Job {
		val data = source.getDataSource(time, level)
		val dataAccess = data.randomAccess()
		dataAccess.setPosition(seed)
		val seedValue = dataAccess.get()
		val seedLabel = assignment?.getSegment(seedValue!!.integerLong) ?: seedValue!!.integerLong
		if (!Label.regular(seedLabel)) {
			val reason = CancellationException("Cannot fill at irregular label: $seedLabel (${Point(seed)})")
			LOG.debug(reason) { }
			return Job().apply { completeExceptionally(reason) }
		}

		val maskInfo = MaskInfo(
			time,
			level
		)
		val mask = source.generateMask(maskInfo, MaskedSource.VALID_LABEL_CHECK)
		val globalToSource = source.getSourceTransformForMask(maskInfo).inverse()

		val visibleSourceIntervals = getPaintera().baseView.orthogonalViews().views().stream()
			.filter { it: ViewerPanelFX -> it.isVisible && it.width > 0.0 && it.height > 0.0 }
			.map { it.getGlobalViewerInterval() }
			.map { globalToSource.estimateBounds(it) }
			.map { Intervals.smallestContainingInterval(it) }
			.collect(Collectors.toList<RealInterval>())

		val triggerRefresh = AtomicBoolean(false)
		var floodFillJob: Job? = null
		val accessTracker: AccessBoxRandomAccessible<UnsignedLongType> = object : AccessBoxRandomAccessible<UnsignedLongType>(mask.rai.extendValue(UnsignedLongType(1))) {
			val position: Point = Point(sourceAccess.numDimensions())

			override fun get(): UnsignedLongType {
				if (floodFillJob?.isCancelled == true || Thread.currentThread().isInterrupted)
					throw CancellationException("Flood Fill Canceled")
				synchronized(this) {
					updateAccessBox()
				}
				sourceAccess.localize(position)
				if (!triggerRefresh.get()) {
					for (interval in visibleSourceIntervals) {
						if (Intervals.contains(interval, position)) {
							triggerRefresh.set(true)
							break
						}
					}
				}
				return sourceAccess.get()!!
			}
		}

		floodFillJob = CoroutineScope(Dispatchers.Default).launch {
			val fillContext = coroutineContext
			InvokeOnJavaFXApplicationThread {
				while (fillContext.isActive) {
					awaitPulse()
					if (triggerRefresh.get()) {
						val repaintInterval = globalToSource.inverse().estimateBounds(accessTracker.createAccessInterval())
						requestRepaint.accept(repaintInterval.smallestContainingInterval)
						triggerRefresh.set(false)
					}
					awaitPulse()
				}
			}

			if (seedValue is LabelMultisetType) {
				fillMultisetType(data as RandomAccessibleInterval<LabelMultisetType>, accessTracker, seed, seedLabel, fill, assignment)
			} else {
				fillPrimitiveType(data, accessTracker, seed, seedLabel, fill, assignment)
			}

			val sourceInterval = accessTracker.createAccessInterval()
			val globalInterval = globalToSource.inverse().estimateBounds(sourceInterval).smallestContainingInterval

			LOG.trace { "FloodFill has been completed" }
			LOG.trace {
				"Applying mask for interval ${Intervals.minAsLongArray(sourceInterval).contentToString()} ${Intervals.maxAsLongArray(sourceInterval).contentToString()}"
			}
			requestRepaint.accept(globalInterval)
			source.applyMask(mask, sourceInterval, MaskedSource.VALID_LABEL_CHECK)
		}
		return floodFillJob
	}

	companion object {
		private val LOG = KotlinLogging.logger { }

		private fun viewerToSourceCoordinates(
			x: Double,
			y: Double,
			viewer: ViewerPanelFX,
			labelTransform: AffineTransform3D
		): RealPoint {
			return viewerToSourceCoordinates(x, y, RealPoint(labelTransform.numDimensions()), viewer, labelTransform)
		}

		private fun <P> viewerToSourceCoordinates(
			x: Double,
			y: Double,
			location: P,
			viewer: ViewerPanelFX,
			labelTransform: AffineTransform3D
		): P where P : RealLocalizable, P : RealPositionable {
			location.setPosition(longArrayOf(x.toLong(), y.toLong(), 0))

			viewer.displayToGlobalCoordinates(location)
			labelTransform.applyInverse(location, location)

			return location
		}

		private fun fillMultisetType(
			input: RandomAccessibleInterval<LabelMultisetType>,
			output: RandomAccessible<UnsignedLongType>,
			seed: Localizable,
			seedLabel: Long,
			fillLabel: Long,
			assignment: FragmentSegmentAssignment?
		) {
			val predicate = makePredicate<LabelMultisetType>(seedLabel, assignment)
			FloodFill.fill(
				input.extendValue(LabelMultisetType()),
				output,
				seed,
				UnsignedLongType(fillLabel),
				DiamondShape(1)
			) { source, target: UnsignedLongType -> predicate(source, target) }
		}

		private fun <T : IntegerType<T>> fillPrimitiveType(
			input: RandomAccessibleInterval<T>,
			output: RandomAccessible<UnsignedLongType>,
			seed: Localizable,
			seedLabel: Long,
			fillLabel: Long,
			assignment: FragmentSegmentAssignment?
		) {
			val extension = input.type.createVariable()
			extension!!.setInteger(Label.OUTSIDE)

			val predicate = makePredicate<T>(seedLabel, assignment)
			FloodFill.fill(
				input.extendValue(extension),
				output,
				seed,
				UnsignedLongType(fillLabel),
				DiamondShape(1)
			) { source, target: UnsignedLongType -> predicate(source, target) }
		}

		private fun <T : IntegerType<T>> makePredicate(seedLabel: Long, assignment: FragmentSegmentAssignment?): (T, UnsignedLongType) -> Boolean {
			val (singleFragment, seedFragments) = assignment?.let {
				val fragments = assignment.getFragments(seedLabel)
				val singleFragment = if (fragments.size() == 1) fragments.toArray()[0] else null
				singleFragment to fragments
			} ?: (seedLabel to null)


			return { sourceVal: T, targetVal: UnsignedLongType ->
				/* true if sourceFragment is a seedFragment */
				val sourceFragment = sourceVal.integerLong
				val shouldFill = if (singleFragment != null) singleFragment == sourceFragment else seedFragments!!.contains(sourceFragment)
				shouldFill && targetVal.integer.toLong() == Label.INVALID
			}
		}
	}
}

