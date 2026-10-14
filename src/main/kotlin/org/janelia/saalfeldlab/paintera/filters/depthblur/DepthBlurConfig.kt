package org.janelia.saalfeldlab.paintera.filters.depthblur

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.paintera.filters.AbstractRenderFilterConfig
import org.janelia.saalfeldlab.paintera.filters.RenderFilterConfigAdapter
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlurConfig.Companion.DEFAULT
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin

class DepthBlurConfig : AbstractRenderFilterConfig() {

	val slicesPerSideProperty = SimpleObjectProperty(DEFAULT.slicesPerSide)
	val spacingProperty = SimpleObjectProperty(DEFAULT.spacing)
	val blendProperty = SimpleObjectProperty(DEFAULT.blend)
	val gaussianCutoffProperty = SimpleDoubleProperty(DEFAULT.gaussianCutoff)

	override val settings = listOf(slicesPerSideProperty, spacingProperty, blendProperty, gaussianCutoffProperty)

	override val name = "Depth Blur"

	override val description = "" +
			"Blur along the viewer normal only: each pixel is the weighted sum of slices " +
			"above and below the viewer plane, so the in-plane resolution is kept."

	override val typeKey = "depthBlur"

	companion object {
		internal val DEFAULT = DepthBlur()
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class DepthBlurConfigAdapter : RenderFilterConfigAdapter<DepthBlurConfig>(DepthBlurConfig::class.java) {

	override fun newConfig() = DepthBlurConfig()

	override fun serializeSettings(src: DepthBlurConfig, settings: JsonObject) {
		if (src.slicesPerSideProperty.get() != DEFAULT.slicesPerSide)
			settings[SLICES_PER_SIDE_KEY] = src.slicesPerSideProperty.get()
		if (src.spacingProperty.get() != DEFAULT.spacing)
			settings[SPACING_KEY] = src.spacingProperty.get()
		if (src.blendProperty.get() != DEFAULT.blend)
			settings[BLEND_KEY] = src.blendProperty.get().name
		if (src.gaussianCutoffProperty.get() != DEFAULT.gaussianCutoff)
			settings[GAUSSIAN_CUTOFF_KEY] = src.gaussianCutoffProperty.get()
	}

	override fun deserializeSettings(json: JsonElement, config: DepthBlurConfig) {
		json.get<Int>(SLICES_PER_SIDE_KEY) { config.slicesPerSideProperty.set(it) }
		json.get<Double>(SPACING_KEY) { config.spacingProperty.set(it) }
		json.get<String>(BLEND_KEY) { name -> DepthBlur.Blend.entries.firstOrNull { it.name == name }?.let { config.blendProperty.set(it) } }
		json.get<Double>(GAUSSIAN_CUTOFF_KEY) { config.gaussianCutoffProperty.set(it) }
	}

	private companion object {
		const val SLICES_PER_SIDE_KEY = "slicesPerSide"
		const val SPACING_KEY = "spacing"
		const val BLEND_KEY = "blend"
		const val GAUSSIAN_CUTOFF_KEY = "gaussianCutoff"
	}
}
