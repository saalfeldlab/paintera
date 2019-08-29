package org.janelia.saalfeldlab.paintera.config

import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.ui.DoubleField

class NavigationConfigNode(
		private val coordinateConfig: CoordinateConfigNode = CoordinateConfigNode(),
		val config: NavigationConfig? = null) {

    private val contents = TitledPane("Navigation", null)

    private val allowRotationsCheckBox = CheckBox()

    private val keyRotationRegular = DoubleField(0.0)

    private val keyRotationFast = DoubleField(0.0)

    private val keyRotationSlow = DoubleField(0.0)

	init {

		config?.let { bind(it) }

		val vbox = VBox()

        vbox.children.add(this.coordinateConfig.getContents())
        vbox.children.add(rotationsConfig())

        contents.content = vbox
        contents.isExpanded = false

    }

    fun bind(config: NavigationConfig) {
        allowRotationsCheckBox.selectedProperty().bindBidirectional(config.allowRotationsProperty())
        keyRotationRegular.valueProperty().bindBidirectional(config.buttonRotationSpeeds().regular)
        keyRotationSlow.valueProperty().bindBidirectional(config.buttonRotationSpeeds().slow)
        keyRotationFast.valueProperty().bindBidirectional(config.buttonRotationSpeeds().fast)
    }

    fun getContents(): Node {
        return contents
    }

    fun coordinateConfigNode(): CoordinateConfigNode {
        return this.coordinateConfig
    }

    private fun rotationsConfig(): Node {
        val contents = VBox()
        val rotations = TitledPane("Rotations", contents)
        rotations.isExpanded = false
        rotations.graphic = allowRotationsCheckBox
        rotations.collapsibleProperty().bind(allowRotationsCheckBox.selectedProperty())

        run {
            val grid = GridPane()
            val keyRotations = TitledPane("Key Rotation Speeds", grid)
            keyRotations.isExpanded = false
            var row = 0
            val doubleFieldWith = 60.0

            run {
                val label = Label("Slow")
                GridPane.setHgrow(label, Priority.ALWAYS)
                keyRotationSlow.textField().maxWidth = doubleFieldWith
                grid.add(label, 0, row)
                grid.add(keyRotationSlow.textField(), 1, row)
                ++row
            }

            run {
                val label = Label("Regular")
                GridPane.setHgrow(label, Priority.ALWAYS)
                keyRotationRegular.textField().maxWidth = doubleFieldWith
                grid.add(label, 0, row)
                grid.add(keyRotationRegular.textField(), 1, row)
                ++row
            }

            run {
                val label = Label("Fast")
                GridPane.setHgrow(label, Priority.ALWAYS)
                keyRotationFast.textField().maxWidth = doubleFieldWith
                grid.add(label, 0, row)
                grid.add(keyRotationFast.textField(), 1, row)
                ++row
            }
            contents.children.add(keyRotations)
        }
        return rotations
    }

}
