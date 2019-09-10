package org.janelia.saalfeldlab.paintera.config

import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.util.Colors

class CrosshairConfigNode() {

	constructor(config: CrosshairConfig): this() {
		this.bind(config)
	}

    private val contents: TitledPane

    private val showCrosshairs = CheckBox()

    private val onFocusColorPicker = ColorPicker()

    private val outOfFocusColorPicker = ColorPicker()

    init {

        val grid = GridPane()

        onFocusColorPicker.maxWidth = 40.0
        onFocusColorPicker.customColors.addAll(Colors.cremi(1.0), Colors.cremi(0.5))

        outOfFocusColorPicker.maxWidth = 40.0
        outOfFocusColorPicker.customColors.addAll(Colors.cremi(1.0), Colors.cremi(0.5))

        val onFocusLabel = Label("on focus")
        val offFocusLabel = Label("off focus")
        grid.add(onFocusLabel, 0, 1)
        grid.add(offFocusLabel, 0, 2)

        grid.add(onFocusColorPicker, 1, 1)
        grid.add(outOfFocusColorPicker, 1, 2)

        GridPane.setHgrow(onFocusLabel, Priority.ALWAYS)
        GridPane.setHgrow(offFocusLabel, Priority.ALWAYS)

        contents = TitledPane("Crosshair", grid)
        contents.graphic = showCrosshairs
        contents.isExpanded = false

    }

    fun bind(config: CrosshairConfig) {
        showCrosshairs.selectedProperty().bindBidirectional(config.showCrosshairsProperty())
        onFocusColorPicker.valueProperty().bindBidirectional(config.onFocusColorProperty())
        outOfFocusColorPicker.valueProperty().bindBidirectional(config.outOfFocusColorProperty())
    }

    fun getContents(): Node {
        return contents
    }

}
