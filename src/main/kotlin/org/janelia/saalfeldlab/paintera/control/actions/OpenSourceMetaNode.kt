package org.janelia.saalfeldlab.paintera.control.actions

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.Property
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
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
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState

private val LOG = KotlinLogging.logger {}


class OpenSourceMetaNode(private val model: OpenSourceModel) : TitledPane() {

    private val perDimensionConfigGrid = GridPane().apply {
        columnConstraints += ColumnConstraints().also { it.minWidth = Region.USE_PREF_SIZE }
    }

    private var subscriptions: Subscription? = null

    init {
        text = "Metadata"
        content = ScrollPane().apply {
            isFitToWidth = true
            content = VBox().apply {
                spacing = 2.0
                children += perDimensionConfigGrid
                model.metadataStateBinding.subscribe { metadataState ->
                    InvokeOnJavaFXApplicationThread {
                        subscriptions?.unsubscribe()
                        subscriptions = Subscription.EMPTY
                        perDimensionConfigGrid.children.clear()
                        perDimensionConfigGrid.columnConstraints.setAll(
                            ColumnConstraints().apply { minWidth = USE_PREF_SIZE }
                        )

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


    private fun MetadataState.bindPerDimensionConfig(grid: GridPane) {

        fun axisLabel(text: String, hpos: HPos) = Label(text).apply {
            styleClass += "axis-label"
            GridPane.setHalignment(this, hpos)
        }

        val dimensions = datasetAttributes.dimensions

        /* Index Header Row */
        var col = 1
        var row = 0
        grid[0, row] = axisLabel("Index", HPos.RIGHT)
        for (d in dimensions.indices) {
            grid[col++, row] = axisLabel("$d", HPos.CENTER)
        }

        /* axes header row */
        col = 0
        row++
        grid[col++, row] = axisLabel("Axis", HPos.RIGHT)
        val axisCombos = Array(dimensions.size) { d ->
            ComboBox<Axis>().apply {
                styleClass += "axis-header"
                items.setAll(*axes)
                maxWidth = Double.MAX_VALUE
                val axisCell = {
                    object : ListCell<Axis>() {
                        init { alignment = Pos.CENTER }
                        override fun updateItem(item: Axis?, empty: Boolean) {
                            super.updateItem(item, empty)
                            text = if (empty) null else item?.name?.uppercase()
                        }
                    }
                }
                buttonCell = axisCell()
                setCellFactory { axisCell() }
                value = axes[d]
            }
        }
        for (idx in axisCombos.indices)
            grid[col++, row] = axisCombos[idx]

        /* dimension size row  */
        col = 0
        row++
        grid[col++, row] = axisLabel("Dimensions", HPos.RIGHT)
        for (idx in dimensions.indices) {
            val (dimField, _) = newLongField(dimensions[idx], false) { it >= 0 }
            grid[col++, row] = dimField
        }

        /* get the XYZ idx for a given axis */
        fun Axis.toXyzIdx(): Int? {
            val normalName = takeIf { it.type == Axis.SPACE }?.name?.uppercase()
            return when (normalName) {
                "X" -> 0
                "Y" -> 1
                "Z" -> 2
                else -> null
            }
        }

        /* store initial values from the parsed metadata for reset on axis change */
        val initialResolution = resolution.copyOf()
        val initialTranslation = translation.copyOf()

        /* resolution row  */
        col = 0
        row++
        grid[col++, row] = axisLabel("Resolution", HPos.RIGHT)
        val resFieldsAndProps = Array(dimensions.size) { idx ->
            val initResolution = axes[idx].toXyzIdx()?.let { spatialIdx -> resolution[spatialIdx] } ?: 1.0

            val (field, property) = newDoubleField(initResolution) { it > 0 }
            subscriptions += property.subscribe { _, res ->
                axes[idx].toXyzIdx()?.let { spatialIdx ->
                    resolution[spatialIdx] = res.toDouble()
                    updateTransform(resolution, translation)
                }
            }
            grid[col++, row] = field
            field to property
        }
        val resolutionFields = Array(dimensions.size) { resFieldsAndProps[it].first }

        /* translation row  */
        col = 0
        row++
        grid[col++, row] = axisLabel("Translation (physical)", HPos.RIGHT)
        val transFieldsAndProps = Array(dimensions.size) { idx ->
            val initTranslation = axes[idx].toXyzIdx()?.let { spatialIdx -> translation[spatialIdx] } ?: 0.0

            val (field, property) = newDoubleField(initTranslation)
            subscriptions += property.subscribe { _, offset ->
                axes[idx].toXyzIdx()?.let { spatialIdx ->
                    translation[spatialIdx] = offset.toDouble()
                    updateTransform(resolution, translation)
                }
            }
            grid[col++, row] = field
            field to property
        }
        val translationFields = Array(dimensions.size) { transFieldsAndProps[it].first }

        /* slice index row  */
        col = 0
        row++
        val sliceLabel = axisLabel("Slice Index", HPos.RIGHT)
        grid[col++, row] = sliceLabel
        slicePositions = IntArray(dimensions.size) { d ->
            axes[d].toXyzIdx()?.let { -1 } ?: 0
        }
        val sliceFieldsAndProps = Array(dimensions.size) { d ->
            val initialSlice = slicePositions!![d]
            val (field, property) = newIntField(initialSlice) { it >= -1 }
            subscriptions += property.subscribe { _, idx -> slicePositions?.set(d, idx.toInt()) }
            grid[col++, row] = field
            field to property
        }
        val sliceFields = Array(dimensions.size) { sliceFieldsAndProps[it].first }

        /* toggle field visibility, reset values, and update spatial mapping */
        fun updateFieldVisibility() {
            var anySliceVisible = false
            for (idx in dimensions.indices) {
                val spatialIdx = axes[idx].toXyzIdx()
                val isSpatial = axes[idx].toXyzIdx() != null
                resolutionFields[idx].isVisible = isSpatial
                resolutionFields[idx].isManaged = isSpatial
                translationFields[idx].isVisible = isSpatial
                translationFields[idx].isManaged = isSpatial
                sliceFields[idx].isVisible = !isSpatial
                sliceFields[idx].isManaged = !isSpatial

                /* reinitialize all field values */
                resFieldsAndProps[idx].second.value = spatialIdx?.let { initialResolution[it] } ?: 1.0
                transFieldsAndProps[idx].second.value = spatialIdx?.let { initialTranslation[it] } ?: 0.0
                sliceFieldsAndProps[idx].second.value = spatialIdx?.let { -1 } ?: 0
                if (!isSpatial) anySliceVisible = true
            }
            sliceLabel.isVisible = anySliceVisible
            sliceLabel.isManaged = anySliceVisible
        }
        updateFieldVisibility()

        /* swap axes and update fields  */
        var swapping = false
        for (idx in axisCombos.indices) {
            subscriptions += axisCombos[idx].valueProperty().subscribe { _, newAxis ->
                if (newAxis == null || swapping) return@subscribe
                swapping = true
                val curAxis = axes[idx]
                val otherIdx = axisCombos.indexOfFirst { it !== axisCombos[idx] && it.value == newAxis }
                axes[idx] = newAxis
                if (otherIdx >= 0) {
                    axes[otherIdx] = curAxis
                    axisCombos[otherIdx].value = curAxis
                }

                updateFieldVisibility()
                swapping = false
            }
        }

        /* add hgrow constraints for the data columns */
        val growCol = ColumnConstraints().apply { hgrow = Priority.ALWAYS }
        repeat(dimensions.size) { grid.columnConstraints += growCol }
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

        minProperty.subscribe { _, v -> model.metadataState?.minIntensity = v.toDouble() }
        maxProperty.subscribe { _, v -> model.metadataState?.maxIntensity = v.toDouble() }

        val label = Label("Intensity Range").apply {
            styleClass += "axis-label"
            GridPane.setHalignment(this, HPos.RIGHT)
        }

        model.typeProperty.subscribe { it ->
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

    private fun addTypeBoundNodes(gridPane: GridPane) {

        /* add a separator that spans the grid horizontally */
        val rowSeparator = Separator(Orientation.HORIZONTAL).apply {
            padding = Insets(10.0, 0.0, 10.0, 0.0)
        }

        val newRow = gridPane.rowCount
        gridPane.add(rowSeparator, 0, newRow, GridPane.REMAINING, 1)

        addRawMetaNodes(gridPane)

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

        private fun newIntField(
            initialValue: Int,
            editable: Boolean = true,
            valueTest: (Int) -> Boolean = { true }
        ): Pair<TextField, IntegerProperty> {
            return NumberField.intField(initialValue, valueTest, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST)
                .toCell(editable)
        }
    }
}
