package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.event.Event
import net.imglib2.Volatile
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.verifiable
import org.janelia.saalfeldlab.paintera.control.actions.ActionType
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType.*
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelState.Mode
import org.janelia.saalfeldlab.paintera.control.actions.state.MaskedSourceActionState
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState


private class PaintContextDelegate(mode: Mode, val getPaintContext: () -> StatePaintContext<*, *>) : ReplaceLabelUI.AbstractModel(mode) {

	private val paintContext: StatePaintContext<*, *>
		get() = getPaintContext()

	private val selectedIds
		get() = paintContext.selectedIds

	private val assignment
		get() = paintContext.assignment

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

	override val canCancel: BooleanExpression
		get() = paintContext.dataSource.let { source ->
			Bindings.createBooleanBinding(
				{ !(source.isApplyingMaskProperty().value || source.isMaskInUseBinding().value || source.isBusyProperty().value) },
				source.isApplyingMaskProperty(), source.isMaskInUseBinding(), source.isBusyProperty()
			)
		}

	override val activateReplacementLabelProperty by lazy {  super.activateReplacementLabelProperty.apply { set(mode == Mode.Replace) } }

	override val replacementLabelProperty by lazy {
		super.replacementLabelProperty.apply {
			value = when (mode) {
				Mode.Delete -> 0L
				else -> paintContext.selectedIds.lastSelection
			}
		}
	}

	override fun fragmentsForSegment(segment: Long): LongArray {
		return assignment.getFragments(segment).toArray()
	}

	override fun nextId() = paintContext.nextId()

}

class ReplaceLabelState<D, T> internal constructor(
	mode: Mode,
	paintContextProperty: Property<StatePaintContext<*, *>> = SimpleObjectProperty(),
) :
	MaskedSourceActionState.ActiveSource<ConnectomicsLabelState<D, T>, D, T>(),
	ReplaceLabelUI.Model by PaintContextDelegate(mode, paintContextProperty::getValue)
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	@Suppress("unused") //Used by reflection for ActionState logic
	constructor() : this(Mode.All, SimpleObjectProperty())

	internal var paintContext by verifiable("Paint Label Mode has StatePaintContext") {
		val ctx = (paintera.currentMode as? PaintLabelMode)?.statePaintContext as? StatePaintContext<*, *>
		paintContextProperty.value = ctx // Needed to inject this to the model delegate
		ctx
	}


	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)
		action.verify("Mask not in use") { !this@ReplaceLabelState.paintContext.dataSource.isMaskInUseBinding().get() }
	}

	enum class Mode(val labelIsValid: (Long?) -> Boolean, vararg val permissions: ActionType) {
		Replace({ it?.let { it > 0 } == true }, Fill),
		Delete({ it?.let { it == 0L } == true }, Erase, Background),
		All({ Replace.labelIsValid(it) || Delete.labelIsValid(it) }, Fill, Erase, Background);
	}
}