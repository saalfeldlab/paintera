package org.janelia.saalfeldlab.fx

import javafx.beans.binding.DoubleBinding
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableNumberValue
import javafx.scene.control.ContentDisplay
import javafx.scene.control.TitledPane
import javafx.scene.layout.Region

class TitledPaneExtensions {

	companion object {

		fun TitledPane.graphicsOnly(graphic: Region, arrowWidth: Double = 50.0) = graphicsOnly(graphic, SimpleDoubleProperty(arrowWidth))

		fun TitledPane.graphicsOnly(graphic: Region, arrowWidth: ObservableNumberValue): DoubleBinding {
			val regionWidth = this.widthProperty().subtract(arrowWidth)
			graphic.prefWidthProperty().bind(regionWidth)
			this.text = null
			this.graphic = graphic
			this.contentDisplay = ContentDisplay.GRAPHIC_ONLY
			return regionWidth
		}

		fun TitledPane.expandIfEnabled(isEnabled: ObservableBooleanValue) = this
				.also { isEnabled.addListener { _, _, new -> it.expandIfEnabled(new) } }
				.also { it.expandIfEnabled(isEnabled.value) }

		fun TitledPane.expandIfEnabled(isEnabled: Boolean) = if (isEnabled) enableAndExpand() else disableAndCollapse()

		fun TitledPane.enableAndExpand() = this.let {
			it.isCollapsible = true
			it.isExpanded = true
		}

		fun TitledPane.disableAndCollapse() = this.let {
			it.isExpanded = false
			it.isCollapsible = false
		}

	}

}
