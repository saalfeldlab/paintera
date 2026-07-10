package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.imglib2.type.label.Label
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphCommonModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.Status
import org.janelia.saalfeldlab.paintera.control.actions.state.PartialDelegationError

/** which labels a gap fill may overwrite */
enum class ReplaceMode {
	Background,
	Any,
	Specify;
}

interface CloseLabelModel : MorphCommonModel {
	/** the largest gap, in voxels, closed per iteration */
	val gapSizeProperty: IntegerProperty

	/** how many times to repeat the close */
	val iterationsProperty: IntegerProperty

	/** fragments whose gaps close; a segment id lets its fragments bridge each other */
	val labelsToClose: ObservableList<Long>

	val replaceModeProperty: ObjectProperty<ReplaceMode>

	/** labels that may be overwritten when filling a gap; only used with [ReplaceMode.Specify] */
	val labelsToReplace: ObservableList<Long>

	/** when on, Close Gaps with [fillLabelProperty] instead of the flanking target label */
	val specifyFillLabelProperty: BooleanProperty
	val fillLabelProperty: LongProperty

	val activeFragment: Long
	val allActiveFragments: LongArray
	val allActiveSegments: LongArray

	fun fragmentsForSegment(segment: Long): LongArray
	fun segmentForFragment(fragment: Long): Long
	fun nextId(): Long

	companion object {

		fun default(): CloseLabelModel = object : CloseLabelModel,
				MorphCommonModel by MorphCommonModel.default() {
			override val gapSizeProperty = SimpleIntegerProperty(1)
			override val iterationsProperty = SimpleIntegerProperty(1)
			override val labelsToClose: ObservableList<Long> = FXCollections.observableArrayList()
			override val replaceModeProperty = SimpleObjectProperty(ReplaceMode.Background)
			override val labelsToReplace: ObservableList<Long> = FXCollections.observableArrayList()
			override val canApply: BooleanExpression = Bindings.createBooleanBinding(
				{
					val statusReady = statusProperty.get() == Status.Ready || statusProperty.get() == Status.Done
					/* Specify with no listed labels would apply a no-op */
					val canReplace = replaceModeProperty.get() != ReplaceMode.Specify || labelsToReplace.isNotEmpty()
					statusReady && labelsToClose.isNotEmpty() && canReplace
				},
				statusProperty, labelsToClose, replaceModeProperty, labelsToReplace
			)
			override val specifyFillLabelProperty = SimpleBooleanProperty(false)
			override val fillLabelProperty = SimpleLongProperty(Label.BACKGROUND)
			override val activeFragment: Long get() = throw PartialDelegationError()
			override val allActiveFragments: LongArray get() = throw PartialDelegationError()
			override val allActiveSegments: LongArray get() = throw PartialDelegationError()
			override fun fragmentsForSegment(segment: Long): LongArray = throw PartialDelegationError()
			override fun segmentForFragment(fragment: Long): Long = throw PartialDelegationError()
			override fun nextId(): Long = throw PartialDelegationError()
		}
	}
}
