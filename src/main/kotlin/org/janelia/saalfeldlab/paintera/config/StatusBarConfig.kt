package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty

class StatusBarConfig {

	enum class Mode {
		BOTTOM,
		OVERLAY;

		fun next() = next(this)

		companion object {
			fun next(mode: Mode) = values()[(mode.ordinal + 1) % values().size]
		}
	}

	private val _isVisible = SimpleBooleanProperty(true)

	private val _mode = SimpleObjectProperty(Mode.BOTTOM)

	var isVisible: Boolean
		get() = _isVisible.get()
		set(isVisible) = _isVisible.set(isVisible)

	var mode: Mode
		get() = _mode.get()
		set(mode) = _mode.set(mode)

	fun isVisibleProperty() = _isVisible

	fun modeProperty(): ObjectProperty<Mode> = _mode

	fun toggleIsVisible() = this._isVisible.set(!this.isVisible)

	fun cycleModes() = this._mode.set(this.mode.next())

}
