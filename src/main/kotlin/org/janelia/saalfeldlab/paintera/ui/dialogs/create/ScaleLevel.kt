package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.scene.layout.Region
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField

/**
 * A single scale level of the mipmap pyramid.
 */
class ScaleLevel private constructor(
	val relativeDownsamplingFactors: SpatialField<IntegerProperty>,
	val maxNumberOfEntries: NumberField<IntegerProperty>,
	fieldWidth: Double
) {

	constructor(downsamplingFactor: Int, maxNumEntries: Int, fieldWidth: Double, vararg submitOn: SubmitOn) : this(
        relativeDownsamplingFactors = SpatialField.intField(downsamplingFactor, { it > 0 }, fieldWidth, *submitOn),
        maxNumberOfEntries = NumberField.intField(maxNumEntries, { true }, *submitOn),
        fieldWidth = fieldWidth
	)

	val baseDimensions = SpatialField.longField(-1, { true }, fieldWidth).apply { editable = false }
	val absoluteDownsamplingFactors = SpatialField.intField(1, { true }, fieldWidth).apply { editable = false }
	val dimensions = SpatialField.longField(1, { true }, fieldWidth).apply {
		editable = false
		x.valueProperty().bind(baseDimensions.x.valueProperty().divide(absoluteDownsamplingFactors.x.valueProperty()))
		y.valueProperty().bind(baseDimensions.y.valueProperty().divide(absoluteDownsamplingFactors.y.valueProperty()))
		z.valueProperty().bind(baseDimensions.z.valueProperty().divide(absoluteDownsamplingFactors.z.valueProperty()))
	}
	val resolution = SpatialField.doubleField(1.0, { true }, fieldWidth).apply { editable = false }

	init {
		maxNumberOfEntries.textField.apply {
			prefWidth = fieldWidth
			minWidth = Region.USE_PREF_SIZE
		}
	}

	/** Bind the absolute factors, resolution and dimensions to the given base values; pass null to unbind. */
	fun displayAbsoluteValues(
		baseResolution: SpatialField<DoubleProperty>?,
		absoluteDownsamplingFactors: SpatialField<IntegerProperty>?,
		baseDimensions: SpatialField<LongProperty>?
	) {
		listOf(this.baseDimensions, this.absoluteDownsamplingFactors, this.resolution).forEach { field ->
			field.x.valueProperty().unbind()
			field.y.valueProperty().unbind()
			field.z.valueProperty().unbind()
		}

		if (baseResolution == null || absoluteDownsamplingFactors == null || baseDimensions == null)
			return

		val absX = absoluteDownsamplingFactors.x.valueProperty()
		val absY = absoluteDownsamplingFactors.y.valueProperty()
		val absZ = absoluteDownsamplingFactors.z.valueProperty()
		this.absoluteDownsamplingFactors.x.valueProperty().bind(absX)
		this.absoluteDownsamplingFactors.y.valueProperty().bind(absY)
		this.absoluteDownsamplingFactors.z.valueProperty().bind(absZ)
		this.resolution.x.valueProperty().bind(baseResolution.x.valueProperty().multiply(absX))
		this.resolution.y.valueProperty().bind(baseResolution.y.valueProperty().multiply(absY))
		this.resolution.z.valueProperty().bind(baseResolution.z.valueProperty().multiply(absZ))
		this.baseDimensions.x.valueProperty().bind(baseDimensions.x.valueProperty())
		this.baseDimensions.y.valueProperty().bind(baseDimensions.y.valueProperty())
		this.baseDimensions.z.valueProperty().bind(baseDimensions.z.valueProperty())
	}

	fun downsamplingFactors() = relativeDownsamplingFactors.asDoubleArray()

	fun maxNumEntries() = maxNumberOfEntries.valueProperty().get()
}
