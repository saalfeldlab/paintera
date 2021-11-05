package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import org.janelia.saalfeldlab.fx.extensions.getValue
import org.janelia.saalfeldlab.fx.extensions.setValue

class SideBarConfig {

    val isVisibleProperty = SimpleBooleanProperty(false)
    var isVisible: Boolean by isVisibleProperty

    val widthProperty = SimpleDoubleProperty(DEFAULT_WIDTH)
    var width: Double by widthProperty

    fun toggleIsVisible() = this.isVisibleProperty.set(!this.isVisible)

    companion object {
        private const val DEFAULT_WIDTH = 350.0
    }

}
