package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty

class SideBarConfig {

	private val _isVisible = SimpleBooleanProperty(false)

	private val _width = SimpleDoubleProperty(DEFAULT_WIDTH)

	var isVisible: Boolean
		get() = _isVisible.get()
		set(isVisible) = _isVisible.set(isVisible)

	var width: Double
		get() = _width.get()
		set(width) = _width.set(width)

	fun isVisibleProperty(): BooleanProperty = _isVisible

	fun toggleIsVisible() = this._isVisible.set(!this.isVisible)

	fun widthProperty(): DoubleProperty = _width

	companion object {
		private val DEFAULT_WIDTH = 350.0
	}

}
