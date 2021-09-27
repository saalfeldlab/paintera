package org.janelia.saalfeldlab.paintera.meshes.ui

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.Property
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
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
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import kotlin.math.max
import kotlin.math.min

class MeshSettingsController @JvmOverloads constructor(
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
    private val refreshMeshes: Runnable? = null
) {

    @JvmOverloads
    constructor(meshSettings: MeshSettings, refreshMeshes: Runnable? = null) : this(
        meshSettings.numScaleLevels,
        meshSettings.opacityProperty,
        meshSettings.levelOfDetailProperty,
        meshSettings.coarsestScaleLevelProperty,
        meshSettings.finestScaleLevelProperty,
        meshSettings.smoothingLambdaProperty,
        meshSettings.smoothingIterationsProperty,
        meshSettings.minLabelRatioProperty,
        meshSettings.inflateProperty,
        meshSettings.drawModeProperty,
        meshSettings.cullFaceProperty,
        meshSettings.isVisibleProperty,
        refreshMeshes
    )

    fun createContents(
        addMinLabelRatioSlider: Boolean
    ): GridPane {
        return GridPane().populateGridWithMeshSettings(
                addMinLabelRatioSlider,
                CheckBox().also { it.selectedProperty().bindBidirectional(isVisible) },
                NumericSliderWithField(0.0, 1.0, opacity.value).also { it.slider.valueProperty().bindBidirectional(opacity) },
                NumericSliderWithField(
                        MeshSettings.Defaults.Values.minLevelOfDetail,
                        MeshSettings.Defaults.Values.maxLevelOfDetail,
                        levelOfDetail.value
                ).also { it.slider.valueProperty().bindBidirectional(levelOfDetail) },
                NumericSliderWithField(0, this.numScaleLevels - 1, coarsestScaleLevel.value).also {
                    it.slider.valueProperty().bindBidirectional(coarsestScaleLevel)
            },
            NumericSliderWithField(0, this.numScaleLevels - 1, finestScaleLevel.value).apply { slider.valueProperty().bindBidirectional(finestScaleLevel) },
            NumericSliderWithField(0.0, 1.00, .05).apply { slider.valueProperty().bindBidirectional(smoothingLambda) },
            NumericSliderWithField(0, 10, 5).apply { slider.valueProperty().bindBidirectional(smoothingIterations) },
            NumericSliderWithField(0.0, 1.0, 0.5).apply { slider.valueProperty().bindBidirectional(minLabelRatio) },
            NumericSliderWithField(0.5, 2.0, inflate.value).apply { slider.valueProperty().bindBidirectional(inflate) },
            ComboBox(FXCollections.observableArrayList(*DrawMode.values())).apply { valueProperty().bindBidirectional(drawMode) },
            ComboBox(FXCollections.observableArrayList(*CullFace.values())).apply { valueProperty().bindBidirectional(cullFace) })
    }


    @JvmOverloads
    fun createTitledPane(
            addMinLabelRatioSlider: Boolean,
            isEnabled: BooleanProperty,
            helpDialogSettings: HelpDialogSettings = HelpDialogSettings(),
            titledPaneGraphicsSettings: TitledPaneGraphicsSettings = TitledPaneGraphicsSettings(),
            withGridPane: GridPane.() -> Unit = {},
    ): TitledPane {

        val contents = createContents(addMinLabelRatioSlider)
        withGridPane.invoke(contents) /* Used to add costume components to the GridPane */

        val helpDialog = PainteraAlerts
                .alert(Alert.AlertType.INFORMATION, true).apply {
                    initModality(Modality.NONE)
                    headerText = helpDialogSettings.headerText
                    contentText = helpDialogSettings.contentText
                }

        val tpGraphics = HBox(
                Label(titledPaneGraphicsSettings.labelText),
            Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.apply { minWidth = 0.0 },
            CheckBox().apply {
                selectedProperty().bindBidirectional(isEnabled)
                tooltip = Tooltip("Toggle meshes on/off")
            },
            Buttons.withTooltip(null, "Refresh Meshes") { refreshMeshes?.run() }.apply {
                graphic = makeReloadSymbol()
                isVisible = refreshMeshes != null
                isManaged = refreshMeshes != null
            },
            Button("?").apply { onAction = EventHandler { helpDialog.show() } }).apply {
            alignment = Pos.CENTER
        }

        return TitledPane("", contents).apply {
            isExpanded = false
            with(TitledPaneExtensions) { graphicsOnly(tpGraphics) }
            alignment = Pos.CENTER_RIGHT
        }
    }

    data class HelpDialogSettings(
        val headerText: String = "Mesh Settings",
        val contentText: String = "TODO"
    )

    data class TitledPaneGraphicsSettings(val labelText: String = "Mesh Settings")

    companion object {

        const val TEXT_FIELD_WIDTH = 48.0
        const val CHOICE_WIDTH = 95.0


        private fun makeReloadSymbol() = RefreshButton
                .createFontAwesome(scale = 2.0)
                .also { it.rotate = 45.0 }


        private fun GridPane.populateGridWithMeshSettings(
                addMinLabelratioSlider: Boolean,
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
                cullFaceChoice: ComboBox<CullFace>,
        ): GridPane {

            setCoarsestAndFinestScaleLevelSliderListeners(
                    coarsestScaleLevelSlider.slider,
                    finestScaleLevelSlider.slider
            )

            val row = rowCount

            // arrange the grid as 4 columns to fine-tune size and layout of the elements
            (0..2).forEach { _ -> columnConstraints.add(ColumnConstraints()) }
            columnConstraints.add(ColumnConstraints(TEXT_FIELD_WIDTH))

            add(Labels.withTooltip("Visible"), 0, row)
            add(visibleCheckBox, 3, row)
            GridPane.setHalignment(visibleCheckBox, HPos.CENTER)

            addGridOption("Opacity", opacitySlider, "Mesh Opacity")
            addGridOption("Level of detail", levelOfDetailSlider, "Level Of Detail")
            addGridOption("Coarsest scale", coarsestScaleLevelSlider, "Coarsest Scale Level")
            addGridOption("Finest scale", finestScaleLevelSlider, "Finest Scale Level")
            addGridOption("Lambda", smoothingLambdaSlider, "Smoothing Lambda")
            addGridOption("Iterations", smoothingIterationsSlider, "Smoothing Iterations")

            // min label ratio slider only makes sense for sources of label multiset type
            if (addMinLabelratioSlider) {
                val tooltipText = "Min label percentage for a pixel to be filled." + System.lineSeparator() +
                        "0.0 means that a pixel will always be filled if it contains the given label."
                addGridOption("Min label ratio", minLabelRatioSlider, tooltipText
                )
            }

            addGridOption("Inflate", inflateSlider, "Inflate Meshes by Factor")
            addGridOption("Draw Mode", drawModeChoice)
            addGridOption("Cull Face", cullFaceChoice)
            return this
        }

        private fun GridPane.addGridOption(text: String, sliderWithField: NumericSliderWithField, tooltip: String?) {
            addGridOption(text, sliderWithField.slider, sliderWithField.textField)
            with(sliderWithField) {
                slider.isShowTickLabels = true
                tooltip?.let { slider.tooltip = Tooltip(it) }
                textField.prefWidth = TEXT_FIELD_WIDTH
                textField.maxWidth = Control.USE_PREF_SIZE
                GridPane.setHgrow(slider, Priority.ALWAYS)
            }
        }

        fun GridPane.addGridOption(text: String, node: Node, secondNode: Node? = null, tooltip: String? = null, width: Double? = null) {
            val label = Labels.withTooltip(text, tooltip ?: text)
            val row = rowCount

            add(label, 0, row)

            secondNode?.let {
                add(node, 1, row)
                add(secondNode, 3, row)
            } ?: let {
                add(node, 2, row)
                GridPane.setColumnSpan(label, 2)
                GridPane.setHalignment(node, HPos.RIGHT)
            }
            GridPane.setColumnSpan(node, 2)
            when (node) {
                is ChoiceBox<*> -> node.prefWidth = width ?: CHOICE_WIDTH
                is Region -> width?.let { node.prefWidth = it }
            }
        }

        private fun setCoarsestAndFinestScaleLevelSliderListeners(
                coarsestScaleLevelSlider: Slider,
                finestScaleLevelSlider: Slider,
        ) {

            coarsestScaleLevelSlider.valueProperty().addListener { _ ->
                finestScaleLevelSlider.value = min(
                    coarsestScaleLevelSlider.value,
                    finestScaleLevelSlider.value
                )
            }

            finestScaleLevelSlider.valueProperty().addListener { _ ->
                coarsestScaleLevelSlider.value = max(
                    coarsestScaleLevelSlider.value,
                    finestScaleLevelSlider.value
                )
            }
        }
    }

}
