package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.paintera.control.actions.state.MaskedSourceActionState
import org.janelia.saalfeldlab.paintera.control.actions.state.PartialDelegationError
import org.janelia.saalfeldlab.paintera.control.actions.state.SourceStateActionState
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.paintera.util.PainteraUtils.viewerIntervalsInSourceSpace
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.isNotEmpty

interface MorphCommonModel {
	/** status of the morphological operation, used for flow control. */
	val statusProperty: ObjectProperty<OperationStatus>
	/** kernel size in physical units */
	val kernelSizeProperty: DoubleProperty
	/** progress to indicate to the end user */
	val progressProperty: DoubleProperty
	/** whether the live result is rendered over the canvas */
	val previewProperty: BooleanProperty
	/** target level to smooth at */
	val scaleLevel: Int
	/** NOTE: this is aspirational; timepoint other than 0 currently not supported */
	val timepoint: Int
	/** boolean conditional whether valid to apply to canvas*/
	val canApply: BooleanExpression
	/** boolean conditional whether valid to finished operation (abort, cancel, or done) */
	val canClose: BooleanExpression

	fun getLevelResolution(scaleLevel: Int) : DoubleArray

	companion object {
		fun default() = object : MorphCommonModel {
			override val statusProperty = SimpleObjectProperty<OperationStatus>(Status.Empty)
			override val kernelSizeProperty = SimpleDoubleProperty()
			override val previewProperty = SimpleBooleanProperty(true)
			override val canApply: BooleanBinding = statusProperty.isEqualTo(Status.Ready).or(statusProperty.isEqualTo(Status.Done))
			override val canClose: BooleanBinding = statusProperty.isNotEqualTo(Status.Applying)
			override val progressProperty = SimpleDoubleProperty(0.0)
			override val scaleLevel: Int = 0
			override val timepoint: Int = 0

			override fun getLevelResolution(scaleLevel: Int) = throw PartialDelegationError()
		}
	}
}

/** operation status must be set on the JavaFX thread. */
fun MorphCommonModel.setStatus(status: OperationStatus) {
	InvokeOnJavaFXApplicationThread { statusProperty.value = status }
}

fun <T> T.viewerIntervalsInSourceSpace(intersectFilters: Collection<Interval> = emptySet()): Set<Interval>
where T : MorphCommonModel, T : SourceStateActionState<*> {
	val intersectFilter = { sourceInterval: RealInterval ->
		sourceInterval.takeIf {
			intersectFilters.isEmpty() || intersectFilters.any { filter ->
				filter.intersect(sourceInterval).isNotEmpty()
			}
		}
	}
	/* get viewer screen intervals for each orthogonal view in the source space*/
	return this.sourceState
		.viewerIntervalsInSourceSpace(timepoint, scaleLevel, intersectFilter)
		.mapNotNullTo(mutableSetOf()) { it?.smallestContainingInterval }
}

fun <T> T.newSourceMask(maskInfo: MaskInfo = MaskInfo(timepoint, scaleLevel)): SourceMask
where T : MorphCommonModel, T : MaskedSourceActionState<*, *, *> {

	val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(maskInfo.level)
	return SourceMask(maskInfo, store, volatileStore.rai, store.cache, volatileStore.invalidate) { store.shutdown() }
}

fun <T> T.requestRepaintOverIntervals(intervals: List<Interval>? = null)
where T : MorphCommonModel, T : MaskedSourceActionState<*, *, *> {
	if (intervals.isNullOrEmpty()) {
		paintera.baseView.orthogonalViews().requestRepaint()
		return
	}
	val dilatedInterval = intervals.reduce(Intervals::union)
	val globalDilatedInterval = maskedSource.getSourceTransformForMask(MaskInfo(timepoint, scaleLevel)).estimateBounds(dilatedInterval)
	paintera.baseView.orthogonalViews().requestRepaint(globalDilatedInterval)
}

/** upper bound on a preview compute-cell edge, to keep per-cell memory bounded for oblique views */
internal const val PREVIEW_MAX_CELL_SIZE = 96

/** Cell dimensions for a preview computation, sized to the visible region instead of to the kernel. */
internal fun previewCellDimensions(intervals: Collection<Interval>, fallback: IntArray?): IntArray? {
	if (intervals.isEmpty()) return fallback
	val bounds = intervals.reduce { a, b -> Intervals.union(a, b) }
	return IntArray(bounds.numDimensions()) { d ->
		bounds.dimension(d).toInt().coerceIn(1, PREVIEW_MAX_CELL_SIZE)
	}
}
