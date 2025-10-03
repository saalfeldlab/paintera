package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull

class MenuBarConfig {

	val isVisibleProperty = SimpleBooleanProperty(true)
	var isVisible: Boolean by isVisibleProperty.nonnull()

	val modeProperty = SimpleObjectProperty<Mode>(Mode.OVERLAY)
	var mode: Mode by modeProperty.nonnull()

	fun toggleIsVisible() {
		isVisible = !isVisible
	}

	fun cycleModes() {
		mode = mode.next()
	}

	enum class Mode {
		OVERLAY,
		TOP;

		fun next() = Mode.entries[(ordinal + 1) % Mode.entries.size]
	}

	companion object {
		private data class Config( val isVisible: Boolean = true, val mode: Mode = Mode.OVERLAY)
		private val Default = Config()

		fun MenuBarConfig.isDefault() = Config(isVisible, mode) == Default
	}
}
