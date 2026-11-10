package org.janelia.saalfeldlab.paintera.filters.depthblur

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.paintera.filters.ConfigFilterModel
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlur.Blend
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.resolution

/** @param metadataState the source's physical resolution and unit; a spacing of 1 is its finest resolution */
class DepthBlurConfigModel(config: DepthBlurConfig, private val metadataState: MetadataState? = null) : ConfigFilterModel<DepthBlurConfig, DepthBlur>(config), DepthBlurModel {

	override val slicesPerSideProperty = SimpleObjectProperty<Int>().apply { bindBidirectional(config.slicesPerSideProperty) }
	override val spacingProperty = SimpleObjectProperty<Double>().apply { bindBidirectional(config.spacingProperty) }
	override val blendProperty = SimpleObjectProperty<Blend>().apply { bindBidirectional(config.blendProperty) }
	override val gaussianCutoffProperty = SimpleDoubleProperty().apply { bindBidirectional(config.gaussianCutoffProperty) }

	override val physicalPerSpacing get() = metadataState?.resolution?.min() ?: 1.0
	override val unit get() = metadataState?.unit.orEmpty()

	init {
		rebuildOn(slicesPerSideProperty, spacingProperty, blendProperty, gaussianCutoffProperty)
	}

	override fun reset() {
		val default = DepthBlur()
		slicesPerSideProperty.set(default.slicesPerSide)
		spacingProperty.set(default.spacing)
		blendProperty.set(default.blend)
		gaussianCutoffProperty.set(default.gaussianCutoff)
	}

	override fun build() = DepthBlur(slicesPerSideProperty.get(), spacingProperty.get() * physicalPerSpacing, blendProperty.get(), gaussianCutoffProperty.get())
}
