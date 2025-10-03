package org.janelia.saalfeldlab.paintera.serialization

import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull


class WindowProperties {

	companion object {
		private const val DEFAULT_WIDTH = 800
		private const val DEFAULT_HEIGHT = 600
	}

	internal val widthProperty: IntegerProperty = SimpleIntegerProperty(DEFAULT_WIDTH)
	var width by widthProperty.nonnull()

	internal val heightProperty: IntegerProperty = SimpleIntegerProperty(DEFAULT_HEIGHT)
	var height by heightProperty.nonnull()

	internal val fullScreenProperty: BooleanProperty = SimpleBooleanProperty(false)
	var isFullScreen: Boolean by fullScreenProperty.nonnull()

}
