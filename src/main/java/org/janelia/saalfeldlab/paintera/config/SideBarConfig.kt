package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.SimpleBooleanProperty

class SideBarConfig {

	private val _isVisible = SimpleBooleanProperty(false)

	var isVisible: Boolean
		get() = _isVisible.get()
		set(isVisible) = _isVisible.set(isVisible)

	fun isVisibleProperty() = _isVisible

	fun toggleIsVisible() = this._isVisible.set(!this.isVisible)

}
