package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.Property
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty

/** The x, y, z properties of a spatial value.
 * Intended as a data-model binding for the saalfx SpatialFields */
class SpatialValues<P : Property<Number>>(
	val xProperty: P,
	val yProperty: P,
	val zProperty: P
) {

	fun setValues(x: Number, y: Number, z: Number) {
		this.xProperty.value = x
		this.yProperty.value = y
		this.zProperty.value = z
	}

	fun asLongArray() = longArrayOf(xProperty.value.toLong(), yProperty.value.toLong(), zProperty.value.toLong())

	fun asIntArray() = intArrayOf(xProperty.value.toInt(), yProperty.value.toInt(), zProperty.value.toInt())

	fun asDoubleArray() = doubleArrayOf(xProperty.value.toDouble(), yProperty.value.toDouble(), zProperty.value.toDouble())

	companion object {

		fun longValues(initialValue: Long): SpatialValues<LongProperty> =
			SpatialValues(SimpleLongProperty(initialValue), SimpleLongProperty(initialValue), SimpleLongProperty(initialValue))

		fun intValues(initialValue: Int): SpatialValues<IntegerProperty> =
			SpatialValues(SimpleIntegerProperty(initialValue), SimpleIntegerProperty(initialValue), SimpleIntegerProperty(initialValue))

		fun doubleValues(initialValue: Double): SpatialValues<DoubleProperty> =
			SpatialValues(SimpleDoubleProperty(initialValue), SimpleDoubleProperty(initialValue), SimpleDoubleProperty(initialValue))
	}
}
