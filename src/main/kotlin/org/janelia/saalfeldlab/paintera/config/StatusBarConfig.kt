package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull

class StatusBarConfig {

	enum class Mode {
		OVERLAY,
		BOTTOM;

		fun next() = Mode.entries[(ordinal + 1) % Mode.entries.size]
	}

	val isVisibleProperty = SimpleBooleanProperty(true)
	var isVisible : Boolean by isVisibleProperty.nonnull()

	val modeProperty = SimpleObjectProperty<Mode>(Mode.BOTTOM)
	var mode: Mode by modeProperty.nonnull()

	fun toggleIsVisible() {
		isVisible = !isVisible
	}

	fun cycleModes() {
		mode = mode.next()
	}

}
