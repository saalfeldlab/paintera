package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull

class MenuBarConfig {

    enum class Mode {
        OVERLAY,
        TOP;

        fun next() = next(this)

        companion object {
            fun next(mode: Mode) = values()[(mode.ordinal + 1) % values().size]
        }
    }

    val isVisibleProperty = SimpleBooleanProperty(true)
    var isVisible: Boolean by isVisibleProperty.nonnull()

    val modeProperty = SimpleObjectProperty(Mode.OVERLAY)
    var mode: Mode by modeProperty.nonnull()

    fun toggleIsVisible() = this.isVisibleProperty.set(!this.isVisible)

    fun cycleModes() = this.modeProperty.set(this.mode.next())

}
