package org.janelia.saalfeldlab.paintera.meshes.ui

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.Property
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Control
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import kotlin.math.max
import kotlin.math.min

class MeshSettingsNode @JvmOverloads constructor(
    val numScaleLevels: Int,
    private val opacity: DoubleProperty,
    private val levelOfDetail: IntegerProperty,
    private val coarsestScaleLevel: IntegerProperty,
    private val finestScaleLevel: IntegerProperty,
    private val smoothingLambda: DoubleProperty,
    private val smoothingIterations: IntegerProperty,
    private val minLabelRatio: DoubleProperty,
    private val inflate: DoubleProperty,
    private val drawMode: Property<DrawMode>,
    private val cullFace: Property<CullFace>,
    private val isVisible: BooleanProperty,
    private val refreshMeshes: Runnable? = null) {

    @JvmOverloads
    constructor(meshSettings: MeshSettings, refreshMeshes: Runnable? = null) : this(
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
        meshSettings.visibleProperty(),
        refreshMeshes)

    fun createContents(
        addMinLabelRatioSlider: Boolean): GridPane {
        val contents = GridPane()
        populateGridWithMeshSettings(
            addMinLabelRatioSlider,
            contents,
            0,
            CheckBox().also { it.selectedProperty().bindBidirectional(isVisible) },
            NumericSliderWithField(0.0, 1.0, opacity.value).also { it.slider.valueProperty().bindBidirectional(opacity) },
            NumericSliderWithField(MeshSettings.Defaults.Values.minLevelOfDetail, MeshSettings.Defaults.Values.maxLevelOfDetail, levelOfDetail.value).also { it.slider.valueProperty().bindBidirectional(levelOfDetail) },
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
        isEnabled: BooleanProperty,
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


        private fun populateGridWithMeshSettings(
            addMinLabelratioSlider: Boolean,
            contents: GridPane,
            initialRow: Int,
            visibleCheckBox: CheckBox,
            opacitySlider: NumericSliderWithField,
            levelOfDetailSlider: NumericSliderWithField,
            coarsestScaleLevelSlider: NumericSliderWithField,
            finestScaleLevelSlider: NumericSliderWithField,
            smoothingLambdaSlider: NumericSliderWithField,
            smoothingIterationsSlider: NumericSliderWithField,
            minLabelRatioSlider: NumericSliderWithField,
            inflateSlider: NumericSliderWithField,
            drawModeChoice: ComboBox<DrawMode>,
            cullFaceChoice: ComboBox<CullFace>): Int {

            setCoarsestAndFinestScaleLevelSliderListeners(
                coarsestScaleLevelSlider.slider,
                finestScaleLevelSlider.slider)

            var row = initialRow

            val textFieldWidth = 48.0
            val choiceWidth = 95.0

            // arrange the grid as 4 columns to fine-tune size and layout of the elements
            for (i in 0..2)
                contents.columnConstraints.add(ColumnConstraints())
            contents.columnConstraints.add(ColumnConstraints(textFieldWidth))

            val setupSlider = { slider: NumericSliderWithField, ttText: String ->
                slider
                    .also { it.slider.isShowTickLabels = true }
                    .also { it.slider.tooltip = Tooltip(ttText) }
                    .also { it.textField.prefWidth = textFieldWidth }
                    .also { it.textField.maxWidth = Control.USE_PREF_SIZE }
                    .also { GridPane.setHgrow(it.slider, Priority.ALWAYS) }
            }

            contents.add(Labels.withTooltip("Visible"), 0, row)
            contents.add(visibleCheckBox, 3, row)
            GridPane.setHalignment(visibleCheckBox, HPos.CENTER)
            ++row

            contents.add(Labels.withTooltip("Opacity"), 0, row)
            contents.add(opacitySlider.slider, 1, row)
            GridPane.setColumnSpan(opacitySlider.slider, 2)
            contents.add(opacitySlider.textField, 3, row)
            setupSlider(opacitySlider, "Mesh Opacity")
            ++row

            contents.add(Labels.withTooltip("Level of detail"), 0, row)
            contents.add(levelOfDetailSlider.slider, 1, row)
            GridPane.setColumnSpan(levelOfDetailSlider.slider, 2)
            contents.add(levelOfDetailSlider.textField, 3, row)
            setupSlider(levelOfDetailSlider, "Level Of Detail")
            ++row

            contents.add(Labels.withTooltip("Coarsest scale"), 0, row)
            contents.add(coarsestScaleLevelSlider.slider, 1, row)
            GridPane.setColumnSpan(coarsestScaleLevelSlider.slider, 2)
            contents.add(coarsestScaleLevelSlider.textField, 3, row)
            setupSlider(coarsestScaleLevelSlider, "Coarsest Scale Level")
            ++row

            contents.add(Labels.withTooltip("Finest scale"), 0, row)
            contents.add(finestScaleLevelSlider.slider, 1, row)
            GridPane.setColumnSpan(finestScaleLevelSlider.slider, 2)
            contents.add(finestScaleLevelSlider.textField, 3, row)
            setupSlider(finestScaleLevelSlider, "Finest Scale Level")
            ++row

            contents.add(Labels.withTooltip("Lambda"), 0, row)
            contents.add(smoothingLambdaSlider.slider, 1, row)
            GridPane.setColumnSpan(smoothingLambdaSlider.slider, 2)
            contents.add(smoothingLambdaSlider.textField, 3, row)
            setupSlider(smoothingLambdaSlider, "Smoothing Lambda")
            ++row

            contents.add(Labels.withTooltip("Iterations"), 0, row)
            contents.add(smoothingIterationsSlider.slider, 1, row)
            GridPane.setColumnSpan(smoothingIterationsSlider.slider, 2)
            contents.add(smoothingIterationsSlider.textField, 3, row)
            setupSlider(smoothingIterationsSlider, "Smoothing Iterations")
            ++row

            // min label ratio slider only makes sense for sources of label multiset type
            if (addMinLabelratioSlider)
            {
                contents.add(Labels.withTooltip("Min label ratio"), 0, row)
                contents.add(minLabelRatioSlider.slider, 1, row)
                GridPane.setColumnSpan(minLabelRatioSlider.slider, 2)
                contents.add(minLabelRatioSlider.textField, 3, row)
                setupSlider(minLabelRatioSlider, "Min label percentage for a pixel to be filled." + System.lineSeparator() +
                    "0.0 means that a pixel will always be filled if it contains the given label.")
                ++row
            }

            contents.add(Labels.withTooltip("Inflate"), 0, row)
            contents.add(inflateSlider.slider, 1, row)
            GridPane.setColumnSpan(inflateSlider.slider, 2)
            contents.add(inflateSlider.textField, 3, row)
            setupSlider(inflateSlider, "Inflate Meshes by Factor")
            ++row

            val drawModeLabel = Labels.withTooltip("Draw Mode")
            contents.add(drawModeLabel, 0, row)
            GridPane.setColumnSpan(drawModeLabel, 2)
            contents.add(drawModeChoice, 2, row)
            GridPane.setColumnSpan(drawModeChoice, 2)
            GridPane.setHalignment(drawModeChoice, HPos.RIGHT)
            drawModeChoice.prefWidth = choiceWidth
            ++row

            val cullFaceLabel = Labels.withTooltip("Cull Face")
            contents.add(cullFaceLabel, 0, row)
            GridPane.setColumnSpan(cullFaceLabel, 2)
            contents.add(cullFaceChoice, 2, row)
            GridPane.setColumnSpan(cullFaceChoice, 2)
            GridPane.setHalignment(cullFaceChoice, HPos.RIGHT)
            cullFaceChoice.prefWidth = choiceWidth
            ++row

            return row
        }

        private fun setCoarsestAndFinestScaleLevelSliderListeners(
            coarsestScaleLevelSlider: Slider,
            finestScaleLevelSlider: Slider) {

            coarsestScaleLevelSlider.valueProperty().addListener { _ ->
                finestScaleLevelSlider.value = min(
                    coarsestScaleLevelSlider.value,
                    finestScaleLevelSlider.value)
            }

            finestScaleLevelSlider.valueProperty().addListener { _ ->
                coarsestScaleLevelSlider.value = max(
                    coarsestScaleLevelSlider.value,
                    finestScaleLevelSlider.value)
            }
        }
    }

}
