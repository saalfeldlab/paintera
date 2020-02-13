package org.janelia.saalfeldlab.paintera.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import javafx.beans.property.ObjectProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.DoubleField
import org.janelia.saalfeldlab.paintera.state.RawSourceStateConverterNode
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

class LoggingConfigNode {

    val config = LoggingConfig()

    val node: Node
        get() {

            val loggerLevelGrid = GridPane()
            loggerLevelGrid.columnConstraints.setAll(
                ColumnConstraints().also{ it.hgrow = Priority.ALWAYS },
                ColumnConstraints())

            loggerLevelGrid.add(Labels.withTooltip(Logger.ROOT_LOGGER_NAME, "Root logger"), 0, 0)
            loggerLevelGrid.add(logLevelChoiceBox(config.rootLoggerLevelProperty()), 1, 0)

            val contents = VBox(loggerLevelGrid)

            val helpDialog = PainteraAlerts
                .alert(Alert.AlertType.INFORMATION, true)
                .also { it.initModality(Modality.NONE) }
                .also { it.headerText = "Configure Paintera logging." }

            val tpGraphics = HBox(
                Label("Logging"),
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }

            return with (TitledPaneExtensions) {
                TitledPanes.createCollapsed(null, contents)
                    .also { it.graphicsOnly(tpGraphics) }
                    .also { it.alignment = Pos.CENTER_RIGHT }
            }
        }

    fun logLevelChoiceBox(logLevelProperty: ObjectProperty<Level>): ChoiceBox<Level> {
        val choiceBox = ChoiceBox(FXCollections.observableList(LoggingConfig.logBackLevels))
        choiceBox.valueProperty().bindBidirectional(logLevelProperty)
        return choiceBox
    }

}
