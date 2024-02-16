package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonObject
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.value.ObservableBooleanValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get


class WindowProperties {
	private val initialWidthProperty: IntegerProperty = SimpleIntegerProperty(800)
	private var initialWidth by initialWidthProperty.nonnull()

	private val initialHeightProperty: IntegerProperty = SimpleIntegerProperty(600)
	private var initialHeight by initialHeightProperty.nonnull()

	internal val widthProperty: IntegerProperty = SimpleIntegerProperty(initialWidthProperty.get())
	var width by widthProperty.nonnull()

	internal val heightProperty: IntegerProperty = SimpleIntegerProperty(initialWidthProperty.get())
	var height by heightProperty.nonnull()

	internal val fullScreenProperty: BooleanProperty = SimpleBooleanProperty(false)
	var isFullScreen: Boolean by fullScreenProperty.nonnull()

	var hasChanged: ObservableBooleanValue = widthProperty
		.isNotEqualTo(initialWidthProperty)
		.or(heightProperty.isNotEqualTo(initialHeightProperty))

	fun clean() {
		initialWidth = width
		initialHeight = height
	}

	fun populate(serializedWindowProperties: JsonObject) {
		serializedWindowProperties.get<Int>(WindowPropertiesSerializer.WIDTH_KEY)?.let { width = it }
		serializedWindowProperties.get<Int>(WindowPropertiesSerializer.HEIGHT_KEY)?.let { width = it }
		clean()
	}
}
