package org.janelia.saalfeldlab.paintera.control.actions.navigation

import javafx.beans.property.Property
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.scene.control.Alert
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts

interface GoToCoordinateModel {

	/**
	 * List of properties bound to the position of each dimension of the source.
	 */
	val positionProperties : List<PositionProperty<*>>

	val xProperty: DoublePositionProperty?
	val yProperty: DoublePositionProperty?
	val zProperty: DoublePositionProperty?

	fun Action<*>.getDialog(header: String, title : String = name?.replace("_", "") ?: "Go to Coordinate"): Alert {
		return PainteraAlerts.confirmation("Go", "Cancel").apply {
			this.title = title
			headerText = header
			dialogPane.content = GoToCoordinateUI(this@GoToCoordinateModel)
		}
	}
}

interface PositionProperty<T: Property<Number>> {
	val property: T
	val label: String
}

class DoublePositionProperty(override val label: String, initial: Double) : PositionProperty<SimpleDoubleProperty>{
	override val property: SimpleDoubleProperty = SimpleDoubleProperty(initial)
	override fun toString() = "$label:%.2f".format(property.value)
}
class LongPositionProperty(override val label: String, initial: Long) : PositionProperty<SimpleLongProperty>{
	override val property: SimpleLongProperty = SimpleLongProperty(initial)
	override fun toString() = "$label:${property.value}"
}