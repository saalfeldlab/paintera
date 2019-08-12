package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.paintera.ui.Crosshair
import org.janelia.saalfeldlab.util.Colors

class CrosshairConfig {

    private val onFocusColor = SimpleObjectProperty(DEFAULT_ON_FOCUS_COLOR)

    private val outOfFocusColor = SimpleObjectProperty(DEFAULT_OUT_OF_FOCUS_COLOR)

    private val showCrosshairs = SimpleBooleanProperty(true)

    private val wasChanged = SimpleBooleanProperty(false)

    init {
        onFocusColor.addListener { _, _, _ -> wasChanged.set(true) }
        outOfFocusColor.addListener { _, _, _ -> wasChanged.set(true) }
        showCrosshairs.addListener { _, _, _ -> wasChanged.set(true) }
        wasChanged.addListener { _, _, _ -> wasChanged.set(false) }
    }

    fun getOnFocusColor(): Color {
        return onFocusColor.get()
    }

    fun setOnFocusColor(color: Color) {
        this.onFocusColor.set(color)
    }

    fun onFocusColorProperty(): ObjectProperty<Color> {
        return this.onFocusColor
    }

    fun getOutOfFocusColor(): Color {
        return outOfFocusColor.get()
    }

    fun setOutOfFocusColor(color: Color) {
        this.outOfFocusColor.set(color)
    }

    fun outOfFocusColorProperty(): ObjectProperty<Color> {
        return this.outOfFocusColor
    }

    fun getShowCrosshairs(): Boolean {
        return showCrosshairs.get()
    }

    fun setShowCrosshairs(show: Boolean) {
        this.showCrosshairs.set(show)
    }

    fun showCrosshairsProperty(): BooleanProperty {
        return this.showCrosshairs
    }

    fun wasChanged(): ObservableBooleanValue {
        return this.wasChanged
    }

    fun bindCrosshairsToConfig(crosshairs: Collection<Crosshair>) {
        crosshairs.stream().forEach { this.bindCrosshairToConfig(it) }
	}

    fun bindCrosshairsToConfig(vararg crosshairs: Crosshair) {
        this.bindCrosshairsToConfig(listOf(*crosshairs))
    }

    fun bindCrosshairToConfig(crosshair: Crosshair) {
        crosshair.highlightColorProperty().bind(this.onFocusColor)
        crosshair.regularColorProperty().bind(this.outOfFocusColor)
        crosshair.isVisibleProperty.bind(this.showCrosshairs)
    }

    companion object {

        val DEFAULT_ON_FOCUS_COLOR = Colors.CREMI

        val DEFAULT_OUT_OF_FOCUS_COLOR = Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5)
    }
}
