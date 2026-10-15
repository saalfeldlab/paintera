package org.janelia.saalfeldlab.paintera.filters.clahe

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.paintera.filters.ConfigFilterModel

/** @param intensityRange the [Clahe.range] of the filter, followed as it changes */
class ClaheModel(
	config: ClaheConfig,
	private val intensityRange: ObservableValue<ClosedFloatingPointRange<Double>> = SimpleObjectProperty(Clahe().range)
) : ConfigFilterModel<ClaheConfig, Clahe>(config) {

	val radiusProperty = SimpleObjectProperty<Double>().apply { bindBidirectional(config.radiusProperty) }
	val maxSlopeProperty = SimpleDoubleProperty().apply { bindBidirectional(config.maxSlopeProperty) }
	val binsProperty = SimpleObjectProperty<Int>().apply { bindBidirectional(config.binsProperty) }

	init {
		rebuildOn(radiusProperty, maxSlopeProperty, binsProperty, intensityRange)
	}

	override fun reset() {
		val default = Clahe()
		radiusProperty.set(default.radius)
		maxSlopeProperty.set(default.maxSlope)
		binsProperty.set(default.bins)
	}

	override fun build() = Clahe(radiusProperty.get(), maxSlopeProperty.get(), binsProperty.get(), intensityRange.value)
}
