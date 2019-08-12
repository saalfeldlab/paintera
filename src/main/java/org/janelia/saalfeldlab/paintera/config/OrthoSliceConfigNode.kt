package org.janelia.saalfeldlab.paintera.config

import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority

class OrthoSliceConfigNode() {

	constructor(config: OrthoSliceConfig): this() {
		bind(config)
	}

    private val contents: TitledPane

    private val topLeftCheckBox = CheckBox()

    private val topRightCheckBox = CheckBox()

    private val bottomLeftCheckBox = CheckBox()

    private val showOrthoViews = CheckBox()

    init {

        val grid = GridPane()

        val topLeftLabel = Label("top left")
        val topRightLabel = Label("top right")
        val bottomLeftLabel = Label("bottom left")

        grid.add(topLeftLabel, 0, 0)
        grid.add(topRightLabel, 0, 1)
        grid.add(bottomLeftLabel, 0, 2)

        grid.add(topLeftCheckBox, 1, 0)
        grid.add(topRightCheckBox, 1, 1)
        grid.add(bottomLeftCheckBox, 1, 2)

        GridPane.setHgrow(topLeftLabel, Priority.ALWAYS)
        GridPane.setHgrow(topRightLabel, Priority.ALWAYS)
        GridPane.setHgrow(bottomLeftLabel, Priority.ALWAYS)

        contents = TitledPane("Ortho-Views", grid)
        contents.graphic = showOrthoViews
        contents.isExpanded = false
    }

    fun bind(config: OrthoSliceConfig) {
        showOrthoViews.selectedProperty().bindBidirectional(config.enableProperty())
        topLeftCheckBox.selectedProperty().bindBidirectional(config.showTopLeftProperty())
        topRightCheckBox.selectedProperty().bindBidirectional(config.showTopRightProperty())
        bottomLeftCheckBox.selectedProperty().bindBidirectional(config.showBottomLeftProperty())
    }

    fun getContents(): Node {
        return contents
    }

}
