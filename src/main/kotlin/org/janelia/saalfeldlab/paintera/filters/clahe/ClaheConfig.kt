package org.janelia.saalfeldlab.paintera.filters.clahe

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.paintera.filters.AbstractRenderFilterConfig
import org.janelia.saalfeldlab.paintera.filters.RenderFilterConfigAdapter
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheConfig.Companion.DEFAULT
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin

class ClaheConfig : AbstractRenderFilterConfig() {

	val radiusProperty = SimpleObjectProperty(DEFAULT.radius)
	val maxSlopeProperty = SimpleDoubleProperty(DEFAULT.maxSlope)
	val binsProperty = SimpleObjectProperty(DEFAULT.bins)

	override val settings = listOf(radiusProperty, maxSlopeProperty, binsProperty)

	override val name = "CLAHE"

	override val description = "" +
			"Contrast limited adaptive histogram equalization in the viewer plane: each tile is remapped by " +
			"its clipped histogram over the frame's intensities, blended between neighboring tiles."

	override val typeKey = "clahe"

	companion object {
		internal val DEFAULT = Clahe()
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class ClaheConfigAdapter : RenderFilterConfigAdapter<ClaheConfig>(ClaheConfig::class.java) {

	override fun newConfig() = ClaheConfig()

	override fun serializeSettings(src: ClaheConfig, settings: JsonObject) {
		if (src.radiusProperty.get() != DEFAULT.radius)
			settings[RADIUS_KEY] = src.radiusProperty.get()
		if (src.maxSlopeProperty.get() != DEFAULT.maxSlope)
			settings[MAX_SLOPE_KEY] = src.maxSlopeProperty.get()
		if (src.binsProperty.get() != DEFAULT.bins)
			settings[BINS_KEY] = src.binsProperty.get()
	}

	override fun deserializeSettings(json: JsonElement, config: ClaheConfig) {
		json.get<Double>(RADIUS_KEY) { config.radiusProperty.set(it) }
		json.get<Double>(MAX_SLOPE_KEY) { config.maxSlopeProperty.set(it) }
		json.get<Int>(BINS_KEY) { config.binsProperty.set(it) }
	}

	private companion object {
		const val RADIUS_KEY = "radius"
		const val MAX_SLOPE_KEY = "maxSlope"
		const val BINS_KEY = "bins"
	}
}
