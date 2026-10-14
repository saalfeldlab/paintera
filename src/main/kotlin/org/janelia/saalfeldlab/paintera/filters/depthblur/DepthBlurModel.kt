package org.janelia.saalfeldlab.paintera.filters.depthblur

import javafx.beans.property.DoubleProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.paintera.filters.RenderFilterSettings
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlur.Blend

interface DepthBlurModel : RenderFilterSettings {
	val slicesPerSideProperty: ObjectProperty<Int>

	/** unit-less; [physicalPerSpacing] converts it to physical */
	val spacingProperty: ObjectProperty<Double>
	val blendProperty: ObjectProperty<Blend>
	val gaussianCutoffProperty: DoubleProperty

	/** the physical length of a spacing of 1, in [unit] */
	val physicalPerSpacing: Double
	val unit: String

	companion object {
		fun default(): DepthBlurModel = object : DepthBlurModel {
			override val enabledProperty = SimpleBooleanProperty(true)
			override val slicesPerSideProperty = SimpleObjectProperty(4)
			override val spacingProperty = SimpleObjectProperty(2.0)
			override val blendProperty = SimpleObjectProperty(Blend.MEAN)
			override val gaussianCutoffProperty = SimpleDoubleProperty(2.0)
			override val physicalPerSpacing = 4.0
			override val unit = "(physical)"
			override fun reset() {
				slicesPerSideProperty.set(4)
				spacingProperty.set(2.0)
				blendProperty.set(Blend.MEAN)
				gaussianCutoffProperty.set(2.0)
			}
		}
	}
}
