package org.janelia.saalfeldlab.paintera.control.actions

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.DoubleProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.Property
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.layout.*
import javafx.util.Subscription
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.fx.extensions.set
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.ui.TranslationSpaceModel
import org.janelia.saalfeldlab.paintera.ui.TranslationSpaceToggle
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta.ChannelInformation

private val LOG = KotlinLogging.logger {}

/* get the XYZ idx for a given axis */
private fun Axis.toXyzIdx(): Int? {
    val normalName = takeIf { it.type == Axis.SPACE }?.name?.uppercase()
    return when (normalName) {
        "X" -> 0
        "Y" -> 1
        "Z" -> 2
        else -> null
    }
}


class OpenSourceMetaNode(private val model: OpenSourceModel) : TitledPane() {

    private val perDimensionConfigGrid = GridPane().apply {
        columnConstraints += ColumnConstraints().also { it.minWidth = Region.USE_PREF_SIZE }
    }

    private var subscriptions: Subscription? = null

    private val translationSpace = TranslationSpaceModel()

    init {
        text = "Metadata"
        content = ScrollPane().apply {
            isFitToWidth = true
            content = VBox().apply {
                spacing = 2.0
                padding = Insets(0.0, 0.0, 0.0, 10.0)
                children += perDimensionConfigGrid
                model.metadataStateBinding.subscribe { metadataState ->
                    InvokeOnJavaFXApplicationThread {
                        subscriptions?.unsubscribe()
                        subscriptions = Subscription.EMPTY
                        perDimensionConfigGrid.children.clear()
                        perDimensionConfigGrid.columnConstraints.clear()

                        metadataState?.let {
                            it.bindPerDimensionConfig(perDimensionConfigGrid)
                            addTypeBoundNodes(perDimensionConfigGrid)
                        }

                        sizeWindowToScene()
                    }
                }
            }
        }
    }


    private fun axisLabel(text: String, hpos: HPos) = Label(text).apply {
        addStyleClass("axis-label")
        GridPane.setHalignment(this, hpos)
    }

    private fun MetadataState.bindPerDimensionConfig(grid: GridPane) {

        val dimensions = datasetAttributes.dimensions
        val translationSpaceToggle = translationSpaceToggle()

        grid.columnConstraints.setAll(
            /* row labels */
            ColumnConstraints().apply { minWidth = USE_PREF_SIZE; halignment = HPos.LEFT },
            /* spacer, at least the width of the translation space toggle */
            ColumnConstraints().apply {
                hgrow = Priority.ALWAYS
                halignment = HPos.RIGHT
                minWidthProperty().bind(translationSpaceToggle.widestLabelWidthProperty)
            }
        )
        repeat(dimensions.size) { grid.columnConstraints += ColumnConstraints().apply { halignment = HPos.RIGHT } }

        /* Index Header Row */
        var col = DATA_COLUMN
        var row = 0
        grid[0, row] = axisLabel("Index", HPos.LEFT)
        for (d in dimensions.indices) {
            grid[col++, row] = axisLabel("$d", HPos.CENTER)
        }

        /* axes header row */
        col = DATA_COLUMN
        row++
        grid[0, row] = axisLabel("Axis", HPos.LEFT)
        for (d in dimensions.indices) {
            grid[col++, row] = axisLabel(axes[d].name.uppercase(), HPos.CENTER)
        }

        /* dimension size row  */
        col = DATA_COLUMN
        row++
        grid[0, row] = axisLabel("Dimensions", HPos.LEFT)
        for (idx in dimensions.indices) {
            val (dimField, _) = newLongField(dimensions[idx], false) { it >= 0 }
            grid[col++, row] = dimField
        }

        /* resolution row  */
        col = DATA_COLUMN
        row++
        grid[0, row] = axisLabel("Resolution", HPos.LEFT)
        val resFieldsAndProps = Array(dimensions.size) { idx ->
            val initResolution = axes[idx].toXyzIdx()?.let { spatialIdx -> resolution[spatialIdx] } ?: 1.0
            newDoubleField(initResolution) { it > 0 }.also { (field, _) -> grid[col++, row] = field }
        }

        /* translation row  */
        col = DATA_COLUMN
        row++
        grid[0, row] = axisLabel("Translation", HPos.LEFT)
        /* the translation space toggle is in the spacer column, right aligned */
        grid[1, row] = translationSpaceToggle
        val transFieldsAndProps = Array(dimensions.size) { _ ->
            newDoubleField(0.0).also { (field, _) -> grid[col++, row] = field }
        }
        bindTranslation(resFieldsAndProps, transFieldsAndProps)

        /* unit row  */
        col = DATA_COLUMN
        row++
        grid[0, row] = axisLabel("Unit", HPos.LEFT)
        for (idx in dimensions.indices) {
            grid[col++, row] = unitField(idx)
        }

        /* only spatial dimensions have resolution and translation */
        for (idx in dimensions.indices) {
            val isSpatial = axes[idx].toXyzIdx() != null
            resFieldsAndProps[idx].first.isVisible = isSpatial
            resFieldsAndProps[idx].first.isManaged = isSpatial
            transFieldsAndProps[idx].first.isVisible = isSpatial
            transFieldsAndProps[idx].first.isManaged = isSpatial
        }
    }

    private fun MetadataState.bindTranslation(
        resolutionCells: Array<Pair<TextField, DoubleProperty>>,
        translationCells: Array<Pair<TextField, DoubleProperty>>
    ) {
        for (idx in resolutionCells.indices) {
            val spatialIdx = axes[idx].toXyzIdx() ?: continue
            val resolutionProperty = resolutionCells[idx].second
            val physicalTranslation = SimpleDoubleProperty(translation[spatialIdx])
            subscriptions += resolutionProperty.subscribe { _, newResolution ->
                resolution[spatialIdx] = newResolution.toDouble()
                updateTransform(resolution, translation)
            }
            subscriptions += physicalTranslation.subscribe { _, newTranslation ->
                translation[spatialIdx] = newTranslation.toDouble()
                updateTransform(resolution, translation)
            }
            subscriptions += translationSpace.bindAxis(translationCells[idx].second, physicalTranslation, resolutionProperty)
        }
    }

    private fun translationSpaceToggle() = TranslationSpaceToggle(translationSpace).apply {
        subscriptions += subscription
        GridPane.setHalignment(this, HPos.RIGHT)
    }

    private fun MetadataState.unitField(idx: Int) = TextField(axes[idx].unit ?: "").apply {
        addStyleClass("unit-cell")
        textProperty().subscribe { _, newUnit ->
            val axis = axes[idx]
            axes[idx] = Axis(axis.type, axis.name, newUnit.ifBlank { null }, false)
            /* the single `unit` mirrors x */
            if (axis.toXyzIdx() == 0)
                unit = newUnit
        }
    }

    private fun addRawMetaNodes(gridPane: GridPane) {

        val (minField, minProperty) = newDoubleField(0.0)
        val (maxField, maxProperty) = newDoubleField(0.0)

        /* initialize from MetadataState and write edits back */
        subscriptions += model.metadataStateBinding.subscribe { it ->
            InvokeOnJavaFXApplicationThread {
                minProperty.set(it?.minIntensity ?: 0.0)
                maxProperty.set(it?.maxIntensity ?: 255.0)
            }
        }

        subscriptions += minProperty.subscribe { _, v -> model.metadataState?.minIntensity = v.toDouble() }
        subscriptions += maxProperty.subscribe { _, v -> model.metadataState?.maxIntensity = v.toDouble() }

        val label = axisLabel("Intensity Range", HPos.LEFT)

        subscriptions += model.typeProperty.subscribe { it ->
            val isRaw = it == SourceType.RAW
            label.isManaged = isRaw
            minField.isManaged = isRaw
            maxField.isManaged = isRaw

            label.isVisible = isRaw
            minField.isVisible = isRaw
            maxField.isVisible = isRaw

            sizeWindowToScene()
        }

        val newRow = gridPane.rowCount
        val numCols = gridPane.columnCount

        gridPane.apply {
            add(label, 0, newRow)
            add(minField, numCols - 2, newRow)
            add(maxField, numCols - 1, newRow)
        }
    }

    private fun addChannelInfoNode(gridPane: GridPane) {

        val metadataState = model.metadataState ?: return
        val dimensions = metadataState.datasetAttributes.dimensions
        if (dimensions.size != 4) return
        val channelIdx = metadataState.axes.indexOfFirst { it.type == Axis.CHANNEL }.takeUnless { it == -1 } ?: 3

        val channelInfo = ChannelInformation().apply {
            numChannelsProperty().set(dimensions[channelIdx].toInt())
            subscriptions += channelSelectionProperty().subscribe { selection -> model.channelSelection = selection }
        }

        val node = channelInfo.node
        subscriptions += model.typeProperty.subscribe { type ->
            val isRaw = type == SourceType.RAW
            node.isVisible = isRaw
            node.isManaged = isRaw
        }

        val newRow = gridPane.rowCount
        gridPane.add(node, 0, newRow, GridPane.REMAINING, 1)
    }

    private fun addTypeBoundNodes(gridPane: GridPane) {

        /* add a separator that spans the grid horizontally */
        val rowSeparator = Separator(Orientation.HORIZONTAL).apply {
            padding = Insets(10.0, 0.0, 10.0, 0.0)
        }

        val newRow = gridPane.rowCount
        gridPane.add(rowSeparator, 0, newRow, GridPane.REMAINING, 1)

        addRawMetaNodes(gridPane)

        addChannelInfoNode(gridPane)

        subscriptions += model.typeProperty.subscribe { _ ->
            sizeWindowToScene()
        }

    }

    private fun sizeWindowToScene() {
        InvokeOnJavaFXApplicationThread {
            scene?.window?.sizeToScene()
        }
    }

    companion object {

        private const val DATA_COLUMN = 2

        private fun <P : Property<Number>> NumberField<P>.toCell(editable: Boolean = true): Pair<TextField, P> {
            textField.apply {
                styleClass += "number-cell"
                userData = this@apply
                isEditable = editable
            }
            return textField to valueProperty()
        }

        private fun newDoubleField(
            initialValue: Double,
            editable: Boolean = true,
            valueTest: (Double) -> Boolean = { true }
        ): Pair<TextField, DoubleProperty> {
            return NumberField.doubleField(initialValue, valueTest, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST)
                .toCell(editable)
        }

        private fun newLongField(
            initialValue: Long,
            editable: Boolean = true,
            valueTest: (Long) -> Boolean = { true }
        ): Pair<TextField, LongProperty> {
            return NumberField.longField(initialValue, valueTest, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST)
                .toCell(editable)
        }

    }
}
