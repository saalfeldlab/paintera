package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.property.BooleanProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleLongProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.Event
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.paintera.control.actions.ActionState
import org.janelia.saalfeldlab.paintera.control.actions.verify
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

interface ReplaceLabelUIState {

	val activeFragment: Long
	val activeSegment: Long
	val allActiveFragments: LongArray
	val allActiveSegments: LongArray
	val fragmentsForActiveSegment: LongArray
	val fragmentsForAllActiveSegments: LongArray

	val fragmentsToReplace: ObservableList<Long>
	val replacementLabel: LongProperty
	val activeReplacementLabel: BooleanProperty

	fun fragmentsForSegment(segment: Long): LongArray
	fun nextId(): Long

}

class ReplaceLabelState<T>() : ActionState(), ReplaceLabelUIState
		where T : IntegerType<T> {
	internal lateinit var sourceState: ConnectomicsLabelState<*, *>
	internal lateinit var paintContext: StatePaintContext<T, *>

	internal val maskedSource
		get() = paintContext.dataSource

	internal val assignment
		get() = paintContext.assignment

	private val selectedIds
		get() = paintContext.selectedIds

	override val activeFragment
		get() = selectedIds.lastSelection

	override val activeSegment
		get() = assignment.getSegment(activeFragment)

	override val fragmentsForActiveSegment: LongArray
		get() = assignment.getFragments(activeSegment).toArray()

	override val allActiveFragments: LongArray
		get() = selectedIds.activeIds.toArray()

	override val allActiveSegments
		get() = allActiveFragments.asSequence()
			.map { assignment.getSegment(it) }
			.toSet()
			.toLongArray()

	override val fragmentsForAllActiveSegments
		get() = allActiveSegments.asSequence()
			.flatMap { assignment.getFragments(it).toArray().asSequence() }
			.toSet()
			.toLongArray()

	override fun fragmentsForSegment(segment: Long): LongArray {
		return assignment.getFragments(segment).toArray()
	}

	override val fragmentsToReplace: ObservableList<Long> = FXCollections.observableArrayList()
	override val replacementLabel: LongProperty = SimpleLongProperty(0L)
	override val activeReplacementLabel = SimpleBooleanProperty(false)

	override fun nextId() = sourceState.nextId()

	override fun <E : Event> Action<E>.verifyState() {
		verify(::sourceState, "Label Source is Active") { paintera.currentSource as? ConnectomicsLabelState<*, *> }
		verify(::paintContext, "Paint Label Mode has StatePaintContext") { PaintLabelMode.statePaintContext as StatePaintContext<T, *> }

		verify("Paint Label Mode is Active") { paintera.currentMode is PaintLabelMode }
		verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
		verify("Mask not in use") { !paintContext.dataSource.isMaskInUseBinding().get() }
	}
}