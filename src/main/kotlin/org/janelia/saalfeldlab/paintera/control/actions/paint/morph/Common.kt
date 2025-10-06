package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.paintera.control.actions.state.MaskedSourceActionState
import org.janelia.saalfeldlab.paintera.control.actions.state.PartialDelegationError
import org.janelia.saalfeldlab.paintera.control.actions.state.SourceStateActionState
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.paintera.util.PainteraUtils.viewerIntervalsInSourceSpace
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.isNotEmpty

interface MorphCommonModel {
	val statusProperty: ObjectProperty<OperationStatus>
	val kernelSizeProperty: IntegerProperty
	val progressProperty: DoubleProperty
	val scaleLevel: Int
	val timepoint: Int  //NOTE: this is aspirational; timepoint other than 0 currently not supported
	val canApply: BooleanExpression
	val canClose: BooleanExpression

	fun getLevelResolution(scaleLevel: Int) : DoubleArray

	companion object {
		fun default() = object : MorphCommonModel {
			override val statusProperty = SimpleObjectProperty<OperationStatus>(Status.Empty)
			override val kernelSizeProperty = SimpleIntegerProperty()
			override val canApply: BooleanBinding = statusProperty.isEqualTo(Status.Done)
			override val canClose: BooleanBinding = statusProperty.isNotEqualTo(Status.Applying)
			override val progressProperty = SimpleDoubleProperty(0.0)
			override val scaleLevel: Int = 0
			override val timepoint: Int = 0

			override fun getLevelResolution(scaleLevel: Int) = throw PartialDelegationError()
		}
	}
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
