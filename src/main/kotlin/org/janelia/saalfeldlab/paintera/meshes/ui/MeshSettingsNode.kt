package org.janelia.saalfeldlab.paintera.meshes.ui

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.Property
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.state.LabelSourceStateMeshPaneNode
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton

class MeshSettingsNode @JvmOverloads constructor(
    val numScaleLevels: Int,
    val opacity: DoubleProperty,
    val levelOfDetail: IntegerProperty,
    val coarsestScaleLevel: IntegerProperty,
    val finestScaleLevel: IntegerProperty,
    val smoothingLambda: DoubleProperty,
    val smoothingIterations: IntegerProperty,
    val minLabelRatio: DoubleProperty,
    val inflate: DoubleProperty,
    val drawMode: Property<DrawMode>,
    val cullFace: Property<CullFace>,
    val isVisible: BooleanProperty,
    val isEnabled: BooleanProperty,
    val refreshMeshes: Runnable? = null) {

    @JvmOverloads
    constructor(meshSettings: MeshSettings, isEnabled: BooleanProperty, refreshMeshes: Runnable? = null) : this(
        meshSettings.numScaleLevels,
        meshSettings.opacityProperty(),
        meshSettings.levelOfDetailProperty(),
        meshSettings.coarsestScaleLevelProperty(),
        meshSettings.finestScaleLevelProperty(),
        meshSettings.smoothingLambdaProperty(),
        meshSettings.smoothingIterationsProperty(),
        meshSettings.minLabelRatioProperty(),
        meshSettings.inflateProperty(),
        meshSettings.drawModeProperty(),
        meshSettings.cullFaceProperty(),
        meshSettings.isVisibleProperty(),
        isEnabled,
        refreshMeshes)

    fun createContents(addMinLabelRatioSlider: Boolean): GridPane {
        val contents = GridPane()
        LabelSourceStateMeshPaneNode.populateGridWithMeshSettings(
            addMinLabelRatioSlider,
            contents,
            0,
            CheckBox().also { it.selectedProperty().bindBidirectional(isVisible) },
            NumericSliderWithField(0.0, 1.0, opacity.value).also { it.slider.valueProperty().bindBidirectional(opacity) },
            NumericSliderWithField(0, this.numScaleLevels - 1, levelOfDetail.value).also { it.slider.valueProperty().bindBidirectional(levelOfDetail) },
            NumericSliderWithField(0, this.numScaleLevels - 1, coarsestScaleLevel.value).also { it.slider.valueProperty().bindBidirectional(coarsestScaleLevel) },
            NumericSliderWithField(0, this.numScaleLevels - 1, finestScaleLevel.value).also { it.slider.valueProperty().bindBidirectional(finestScaleLevel) },
            NumericSliderWithField(0.0, 1.00, .05).also { it.slider.valueProperty().bindBidirectional(smoothingLambda) },
            NumericSliderWithField(0, 10, 5).also { it.slider.valueProperty().bindBidirectional(smoothingIterations) },
            NumericSliderWithField(0.0, 1.0, 0.5).also { it.slider.valueProperty().bindBidirectional(minLabelRatio) },
            NumericSliderWithField(0.5, 2.0, inflate.value).also { it.slider.valueProperty().bindBidirectional(inflate) },
            ComboBox(FXCollections.observableArrayList(*DrawMode.values())).also { it.valueProperty().bindBidirectional(drawMode) },
            ComboBox(FXCollections.observableArrayList(*CullFace.values())).also { it.valueProperty().bindBidirectional(cullFace) })
        return contents
    }


    @JvmOverloads
    fun createTitledPane(
        addMinLabelRatioSlider: Boolean,
        helpDialogSettings: HelpDialogSettings = HelpDialogSettings(),
        titledPaneGraphicsSettings: TitledPaneGraphicsSettings = TitledPaneGraphicsSettings()): TitledPane {

        val contents = createContents(addMinLabelRatioSlider)

        val helpDialog = PainteraAlerts
            .alert(Alert.AlertType.INFORMATION, true)
            .also { it.initModality(Modality.NONE) }
            .also { it.headerText = helpDialogSettings.headerText }
            .also { it.contentText = helpDialogSettings.contentText }

        val tpGraphics = HBox(
            Label(titledPaneGraphicsSettings.labelText),
            Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
            CheckBox()
                .also { it.selectedProperty().bindBidirectional(isEnabled) }
                .also { it.tooltip = Tooltip("Toggle meshes on/off") },
            Buttons.withTooltip(null, "Refresh Meshes") { refreshMeshes?.run() }
                .also { it.graphic = makeReloadSymbol() }
                .also { it.isVisible = refreshMeshes != null }
                .also { it.isManaged = refreshMeshes != null },
            Button("?")
                .also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }

        return TitledPane("", contents)
            .also { it.isExpanded = false }
            .also { with(TitledPaneExtensions) { it.graphicsOnly(tpGraphics)} }
            .also { it.alignment = Pos.CENTER_RIGHT }
    }

    data class HelpDialogSettings(
        val headerText: String = "Mesh Settings",
        val contentText: String = "TODO")

    data class TitledPaneGraphicsSettings(val labelText: String = "Mesh Settings")

    companion object {
        private fun makeReloadSymbol() = RefreshButton
            .createFontAwesome(scale = 2.0)
            .also { it.rotate = 45.0 }
    }

}
