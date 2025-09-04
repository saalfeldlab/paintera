package org.janelia.saalfeldlab.paintera.control.paint

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.ObservableValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.Interval
import net.imglib2.Point
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.fill.FloodFill
import net.imglib2.algorithm.neighborhood.DiamondShape
import net.imglib2.type.label.Label
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.net.imglib2.util.AccessBoxRandomAccessibleOnGet
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.createViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.getSourceDataInInitialMaskSpace
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.util.convertRAI
import org.janelia.saalfeldlab.util.extendValue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class FloodFill2D<T : IntegerType<T>>(
	val activeViewerProperty: ObservableValue<ViewerPanelFX>,
	val source: MaskedSource<T, *>,
	val isVisible: BooleanSupplier
) {
	val fillDepthProperty = SimpleDoubleProperty(2.0)

	internal var viewerMask: ViewerMask? = null

	fun release() {
		viewerMask = null
	}

	internal fun getOrCreateViewerMask(): ViewerMask {
		return viewerMask ?: activeViewerProperty.value.let {
			val level = it.state.bestMipMapLevel
			val time = it.state.timepoint
			val maskInfo = MaskInfo(time, level)
			source.createViewerMask(maskInfo, it, paintDepth = fillDepthProperty.get())
		}
	}

	suspend fun fillViewerAt(
		viewerSeedX: Double,
		viewerSeedY: Double,
		fill: Long,
		assignment: FragmentSegmentAssignment,
		mask: ViewerMask = getOrCreateViewerMask(),
	): Interval {

		val maskPos = mask.displayPointToMask(viewerSeedX, viewerSeedY, true)
		val filter = getBackgroundLabelMaskForAssignment(maskPos, mask, assignment, fill)

		return fillViewerAt(maskPos, fill, filter, mask)
	}

	suspend fun fillViewerAt(
		viewerSeedX: Double,
		viewerSeedY: Double,
		fill: Long,
		filter: RandomAccessibleInterval<BoolType>,
		mask: ViewerMask = getOrCreateViewerMask(),
	): Interval {

		val maskPos = mask.displayPointToMask(viewerSeedX, viewerSeedY, true)
		return fillViewerAt(maskPos, fill, filter, mask)
	}


	suspend fun fillViewerAt(
		maskPos: Point,
		fill: Long,
		filter: RandomAccessibleInterval<BoolType>,
		mask: ViewerMask = getOrCreateViewerMask(),
	): Interval {

		val interval = fillMaskAt(maskPos, mask, fill, filter)
		if (!coroutineContext.isActive) {
			mask.source.resetMasks()
			mask.requestRepaint()
		}
		return interval
	}

	suspend fun fillMaskAt(maskPos: Point, mask: ViewerMask, fill: Long, filter: RandomAccessibleInterval<BoolType>) : Interval {
		if (fill == Label.INVALID) {
			val reason = "Received invalid label -- will not fill"
			LOG.warn { reason }
			throw CancellationException(reason)
		}

		if (!isVisible.asBoolean) {
			val reason = "Selected source is not visible -- will not fill"
			LOG.warn { reason }
			throw CancellationException(reason)
		}

		val screenInterval = mask.getScreenInterval()

		val triggerRefresh = AtomicBoolean(false)
		val writableViewerImg = mask.viewerImg.writableSource!!.extendValue(UnsignedLongType(fill))
		val fillContext = coroutineContext
		val sourceAccessTracker: AccessBoxRandomAccessibleOnGet<UnsignedLongType> =
			object : AccessBoxRandomAccessibleOnGet<UnsignedLongType>(writableViewerImg) {
				val position: Point = Point(sourceAccess.numDimensions())

				override fun get(): UnsignedLongType {
					if (!fillContext.isActive) throw CancellationException("Flood Fill Canceled")
					synchronized(this) {
						updateAccessBox()
					}
					sourceAccess.localize(position)
					if (!triggerRefresh.get() && Intervals.contains(screenInterval, position)) {
						triggerRefresh.set(true)
					}
					return sourceAccess.get()!!
				}
			}
		sourceAccessTracker.initAccessBox()

		launchRepaintRequestUpdater(fillContext, triggerRefresh, mask, sourceAccessTracker::createAccessInterval)

		fillAt(maskPos, sourceAccessTracker, filter, fill)
		return sourceAccessTracker.createAccessInterval()
	}

	private fun launchRepaintRequestUpdater(fillContext: CoroutineContext, triggerRefresh: AtomicBoolean, mask: ViewerMask, interval: () -> Interval) {
		InvokeOnJavaFXApplicationThread {
			delay(250)
			while (fillContext.isActive) {
				if (triggerRefresh.get()) {
					mask.requestRepaint(interval())
					triggerRefresh.set(false)
				}
				awaitPulse()
				awaitPulse()
			}
			mask.requestRepaint(interval())
		}
	}

	companion object {
		private val LOG = KotlinLogging.logger { }

		suspend fun getBackgroundLabelMaskForAssignment(
			initialSeed: Point, mask: ViewerMask,
			assignment: FragmentSegmentAssignment,
			fillValue: Long
		): RandomAccessibleInterval<BoolType> {
			val backgroundViewerRai = mask.getSourceDataInInitialMaskSpace()

			val id = backgroundViewerRai.getAt(initialSeed)!!.realDouble.toLong()
			val seedLabel = assignment.getSegment(id)
			LOG.trace { "Got seed label $seedLabel" }

			if (seedLabel == fillValue) {
				val reason = "seed label and fill label are the same, nothing to fill"
				LOG.warn { reason }
				throw CancellationException(reason)
			}

			return backgroundViewerRai.convertRAI(BoolType()) { src, target ->
				val segmentId = assignment.getSegment(src!!.realDouble.toLong())
				target.set(segmentId == seedLabel)
			}
		}

		fun fillAt(
			initialSeed: Point,
			target: RandomAccessible<UnsignedLongType>,
			filter: RandomAccessibleInterval<BoolType>,
			fillValue: Long
		) {
			val extendedFilter = filter.extendValue(false)

			// fill only within the given slice, run 2D flood-fill
			LOG.trace { "Flood filling" }
			val backgroundSlice = if (extendedFilter.numDimensions() == 3) {
				Views.hyperSlice(extendedFilter, 2, 0)
			} else {
				extendedFilter
			}
			val viewerFillImg: RandomAccessible<UnsignedLongType> = Views.hyperSlice(target, 2, 0)

			FloodFill.fill(
				backgroundSlice,
				viewerFillImg,
				initialSeed,
				UnsignedLongType(fillValue),
				DiamondShape(1)
			)
		}

	}
}