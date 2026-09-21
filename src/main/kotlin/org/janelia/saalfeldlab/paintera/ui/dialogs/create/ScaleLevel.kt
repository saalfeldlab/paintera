package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleIntegerProperty

/**
 * A single scale level of the mipmap pyramid.
 */
class ScaleLevel(downsamplingFactor: Int, maxNumEntries: Int) {

	val relativeDownsamplingFactors = SpatialValues.intValues(downsamplingFactor)
	val maxNumberOfEntries: IntegerProperty = SimpleIntegerProperty(maxNumEntries)

	val baseDimensions = SpatialValues.longValues(-1)
	val absoluteDownsamplingFactors = SpatialValues.intValues(1)
	val dimensions = SpatialValues.longValues(1).apply {
		xProperty.bind(baseDimensions.xProperty.divide(absoluteDownsamplingFactors.xProperty))
		yProperty.bind(baseDimensions.yProperty.divide(absoluteDownsamplingFactors.yProperty))
		zProperty.bind(baseDimensions.zProperty.divide(absoluteDownsamplingFactors.zProperty))
	}
	val resolution = SpatialValues.doubleValues(1.0)

	/** Bind the absolute factors, resolution and dimensions to the given base values; pass null to unbind. */
	fun displayAbsoluteValues(
		baseResolution: SpatialValues<DoubleProperty>?,
		absoluteDownsamplingFactors: SpatialValues<IntegerProperty>?,
		baseDimensions: SpatialValues<LongProperty>?
	) {
		listOf(this.baseDimensions, this.absoluteDownsamplingFactors, this.resolution).forEach { values ->
			values.xProperty.unbind()
			values.yProperty.unbind()
			values.zProperty.unbind()
		}

		if (baseResolution == null || absoluteDownsamplingFactors == null || baseDimensions == null)
			return

		this.absoluteDownsamplingFactors.xProperty.bind(absoluteDownsamplingFactors.xProperty)
		this.absoluteDownsamplingFactors.yProperty.bind(absoluteDownsamplingFactors.yProperty)
		this.absoluteDownsamplingFactors.zProperty.bind(absoluteDownsamplingFactors.zProperty)
		this.resolution.xProperty.bind(baseResolution.xProperty.multiply(absoluteDownsamplingFactors.xProperty))
		this.resolution.yProperty.bind(baseResolution.yProperty.multiply(absoluteDownsamplingFactors.yProperty))
		this.resolution.zProperty.bind(baseResolution.zProperty.multiply(absoluteDownsamplingFactors.zProperty))
		this.baseDimensions.xProperty.bind(baseDimensions.xProperty)
		this.baseDimensions.yProperty.bind(baseDimensions.yProperty)
		this.baseDimensions.zProperty.bind(baseDimensions.zProperty)
	}

	fun downsamplingFactors() = relativeDownsamplingFactors.asDoubleArray()

	fun maxNumEntries() = maxNumberOfEntries.value
}
