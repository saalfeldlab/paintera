package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull

class SideBarConfig {

    val isVisibleProperty = SimpleBooleanProperty(true)
    var isVisible: Boolean by isVisibleProperty.nonnull()

    val widthProperty = SimpleDoubleProperty(DEFAULT_WIDTH)
    var width: Double by widthProperty.nonnull()

    fun toggleIsVisible() = this.isVisibleProperty.set(!this.isVisible)

    companion object {
        private const val DEFAULT_WIDTH = 350.0
    }

}
