package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Dialog
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.Menu
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.util.Callback
import org.janelia.saalfeldlab.fx.SaalFxStyle
import org.janelia.saalfeldlab.fx.extensions.set
import org.janelia.saalfeldlab.fx.ui.DirectoryField
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.bufferNode
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.nameIt
import org.janelia.saalfeldlab.fx.ui.ObjectField.Companion.stringField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_ICON
import org.janelia.saalfeldlab.paintera.Style.REMOVE_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import java.io.File

private val LOG = KotlinLogging.logger { }

/**
 * The content pane for the "Create new Label dataset" dialog.
 */
class CreateDatasetUI(val model: CreateDatasetModel) : VBox() {

    private val nameField = object : TextField() {
        var manuallyNamed = false
        override fun paste() {
            super.paste()
            if (text.isNotEmpty()) manuallyNamed = true
        }
    }.apply {
        maxWidth = Double.MAX_VALUE
        onKeyTyped = EventHandler { if (text.isNotEmpty()) manuallyNamed = true }
        textProperty().addListener { _, _, new -> if (new.isEmpty()) manuallyNamed = false }
        textProperty().bindBidirectional(model.nameProperty)
    }

    private val containerField = DirectoryField(model.container ?: File(System.getProperty("user.home")), MAIN_FIELD_WIDTH).apply {
        model.container = directoryProperty().value
        directoryProperty().subscribe { dir -> model.container = dir }
    }

    private val datasetField = stringField(model.dataset, *SubmitOn.entries.toTypedArray()).apply {
        valueProperty().bindBidirectional(model.datasetProperty)
        textField.textProperty().addListener { _, _, newValue: String? ->
            if (!nameField.manuallyNamed && newValue != null)
                nameField.text = newValue.split("/").toTypedArray().last()
        }
    }

    private val labelMultisetCheckBox = CheckBox().apply { selectedProperty().bindBidirectional(model.labelMultisetProperty) }
    private val labelMultisetBox = HBox(Label("Label Multiset Type  "), labelMultisetCheckBox).apply {
        alignment = Pos.CENTER_RIGHT
        maxWidth = Double.MAX_VALUE
    }

    private val populateFromCurrentSource = MenuItem("_From Current Source").apply {
        onAction = EventHandler {
            it.consume()
            model.populateFrom(model.currentSource)
        }
    }
    private val populateFromSource = Menu("_Select Source").apply {
        isVisible = model.populateSources.isNotEmpty()
        model.populateSources.forEach { source ->
            items += MenuItem(source.nameProperty().get()).apply {
                onAction = EventHandler { model.populateFrom(source.dataSource) }
                isMnemonicParsing = false
            }
        }
    }
    private val setFromButton = MenuButton("_Populate", null, populateFromSource, populateFromCurrentSource)
    private val setFromCurrentBox = HBox(bufferNode(), setFromButton).apply { HBox.setHgrow(children[0], Priority.ALWAYS) }

    /* the axes laid out as columns (x, y, z, then channel/time); rows are dimension, block size, resolution, offset, unit */
    private val axisGrid = GridPane().apply {
        hgap = 6.0
        vgap = 4.0
        alignment = Pos.CENTER_LEFT
        maxWidth = Double.MAX_VALUE
    }

    private val scaleLevelsUI = ScaleLevelsUI(model.scaleLevels, model.labelMultisetProperty)
    private val scaleLevels = TitledPane("Scale Levels", scaleLevelsUI)

    /** True if a required field (name, dataset, container) is missing, or a scale level has a zero dimension; disables the Create button. */
    val hasErrorProperty: BooleanBinding = Bindings.createBooleanBinding(
        { nameField.text.isNullOrBlank() || datasetField.textField.text.isNullOrBlank() || model.container == null || model.scaleLevels.invalidProperty.value },
        nameField.textProperty(), datasetField.textField.textProperty(), model.containerProperty, model.scaleLevels.invalidProperty
    )

    init {
        model.additionalAxes.addListener(ListChangeListener {
            rebuildAxisGrid()
            /* adding or removing a column changes the grid height, so resize the window */
            InvokeOnJavaFXApplicationThread { (scene?.window as? Stage)?.sizeToScene() }
        })
        rebuildAxisGrid()

        children += nameIt("Name", NAME_WIDTH, true, nameField)
        children += nameIt("Container", NAME_WIDTH, true, containerField.asNode())
        children += nameIt("Dataset", NAME_WIDTH, true, datasetField.textField)
        children += axisGrid
        children += scaleLevels
    }

    private class AxisColumnNodes(
        val header: Node, val dimension: Node, val blockSize: Node,
        val resolution: Node, val offset: Node, val unit: Node, val remove: Node?
    )

    /* rebuild the axis grid: x, y, z columns from the spatial fields, then a column per additional axis */
    private fun rebuildAxisGrid() {
        axisGrid.children.clear()

        axisGrid.columnConstraints.setAll(
            /* col 0: row labels */
            ColumnConstraints().apply { minWidth = NAME_WIDTH; halignment = HPos.LEFT },
            /* col 1: a spacer that grows to push the axis columns all the way right */
            ColumnConstraints().apply { hgrow = Priority.ALWAYS }
        )
        /* remaining axis columns: right aligned */
        repeat(4 + model.additionalAxes.size) {
            axisGrid.columnConstraints.add(ColumnConstraints().apply { halignment = HPos.RIGHT })
        }

        listOf(null, "Dimensions", "Block Size", "Resolution", "Offset", "Unit").forEachIndexed { row, text ->
            text?.let { axisGrid.add(Label(it), 0, row) }
        }

        /* one descriptor per axis column: x, y, z then the additional channel/time columns ; remove is null for x, y, z */
        val columns = buildList {
            this += AxisColumnNodes(
                header = columnHeader("X"),
                dimension = model.dimensions.x.textField,
                blockSize = model.blockSize.x.textField,
                resolution = model.resolution.x.textField,
                offset = model.offset.x.textField,
                unit = unitField(model.xUnitProperty),
                remove = null
            )
            this += AxisColumnNodes(
                header = columnHeader("Y"),
                dimension = model.dimensions.y.textField,
                blockSize = model.blockSize.y.textField,
                resolution = model.resolution.y.textField,
                offset = model.offset.y.textField,
                unit = unitField(model.yUnitProperty),
                remove = null
            )
            this += AxisColumnNodes(
                header = columnHeader("Z"),
                dimension = model.dimensions.z.textField,
                blockSize = model.blockSize.z.textField,
                resolution = model.resolution.z.textField,
                offset = model.offset.z.textField,
                unit = unitField(model.zUnitProperty),
                remove = null
            )
            model.additionalAxes.forEach { axisColumn ->
                this +=  AxisColumnNodes(
                    header = typeCombo(axisColumn),
                    dimension = axisColumn.size.textField,
                    blockSize = axisColumn.blockSize.textField,
                    resolution = axisColumn.resolution.textField,
                    offset = axisColumn.offset.textField,
                    unit = axisColumn.unit,
                    remove = removeColumnButton(axisColumn)
                )
            }
        }

        /* add elements in row-major order so tab moves across rows before columns */
        columns.forEachIndexed { i, it -> axisGrid[2 + i, 0] = it.header }
        columns.forEachIndexed { i, it -> axisGrid[2 + i, 1] = it.dimension }
        columns.forEachIndexed { i, it -> axisGrid[2 + i, 2] = it.blockSize }
        columns.forEachIndexed { i, it -> axisGrid[2 + i, 3] = it.resolution }
        columns.forEachIndexed { i, it -> axisGrid[2 + i, 4] = it.offset }
        columns.forEachIndexed { i, it -> axisGrid[2 + i, 5] = it.unit }
        columns.forEachIndexed { i, it -> it.remove?.let { remove -> axisGrid[2 + i, 6] = remove } }

        /* remaining fields span the spacer + all dimension columns, right aligned to the last dimension column
         * This stops them from expanding the axis columns, and leaves the last column free (except the Add Axis button) */
        val lastDimensionColumn = 1 + columns.size
        axisGrid.add(labelMultisetBox, 1, 7, lastDimensionColumn, 1)
        axisGrid.add(setFromCurrentBox, 1, 8, lastDimensionColumn, 1)
        GridPane.setHalignment(labelMultisetBox, HPos.RIGHT)
        GridPane.setHalignment(setFromCurrentBox, HPos.RIGHT)
        /* `+` adds a new dimension column to the right */
        axisGrid.add(addColumnButton(), 2 + columns.size, 0)
    }

    private fun unitField(unitProperty: StringProperty) =
        TextField().apply { prefWidth = MAIN_FIELD_WIDTH; textProperty().bindBidirectional(unitProperty) }

    private fun columnHeader(text: String) = Label(text).apply {
        maxWidth = Double.MAX_VALUE
        alignment = Pos.CENTER
    }

    private fun typeCombo(axisColumn: AdditionalAxis) = ComboBox(FXCollections.observableArrayList(NonSpatialAxisType.entries)).apply {
        prefWidth = MAIN_FIELD_WIDTH
        maxWidth = Double.MAX_VALUE
        value = axisColumn.typeProperty.value
        valueProperty().subscribe { _, value -> value?.let { axisColumn.typeProperty.value = it } }
        /* collapsed header shows just C/T  */
        buttonCell = SingleLetterTypeCell { it.shortLabel }
        cellFactory = Callback { FullNameTypeCell() }
    }

    private fun removeColumnButton(axisColumn: AdditionalAxis) = Button().apply {
        addStyleClass(REMOVE_ICON)
        onAction = EventHandler { model.additionalAxes.remove(axisColumn) }
    }

    private fun addColumnButton() = Button().apply {
        addStyleClass(ADD_ICON)
        onAction = EventHandler {
            /* default the next axis to the first canonical type still missing (channel, then time) */
            val type = when {
                model.additionalAxes.none { it.typeProperty.value == NonSpatialAxisType.CHANNEL } -> NonSpatialAxisType.CHANNEL
                model.additionalAxes.none { it.typeProperty.value == NonSpatialAxisType.TIME } -> NonSpatialAxisType.TIME
                else -> NonSpatialAxisType.CHANNEL
            }
            model.additionalAxes.add(AdditionalAxis(1L, 1, type, ""))
        }
    }
}

/** Collapsed combo header cell: shows just the [shortLabel] of the item, black text without the blue highlighting. */
private class SingleLetterTypeCell<T>(private val shortLabel: (T) -> String?) : ListCell<T>() {
    init {
        alignment = Pos.CENTER
    }

    override fun updateItem(item: T?, empty: Boolean) {
        super.updateItem(item, empty)
        text = item?.takeUnless { empty }?.let(shortLabel)
        style = "-fx-background-color: transparent; -fx-text-fill: black;"
    }
}

private class FullNameTypeCell<T> : ListCell<T>() {
    init {
        alignment = Pos.CENTER
        textFill = Color.BLACK
    }

    override fun updateItem(item: T?, empty: Boolean) {
        super.updateItem(item, empty)
        text = item?.takeUnless { empty }?.toString()
    }
}

fun CreateDatasetModel.getDialog(): Dialog<CreateDatasetModel> {
    return Dialog<CreateDatasetModel>().apply {
        title = Constants.NAME
        headerText = "Create new Label dataset"
        isResizable = true
        dialogPane.buttonTypes += arrayOf(ButtonType.CANCEL, ButtonType.OK)
        (dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel"
        (dialogPane.lookupButton(ButtonType.OK) as Button).text = "C_reate"

        val ui = CreateDatasetUI(this@getDialog)
        Paintera.registerStylesheets(dialogPane)
        dialogPane.content = ui
        (dialogPane.lookupButton(ButtonType.OK) as Button).apply {
            disableProperty().unbind()
            disableProperty().bind(ui.hasErrorProperty)
        }
        resultConverter = Callback { if (it == ButtonType.OK) this@getDialog else null }
        initAppDialog()
        dialogPane.scene?.window?.sizeToScene()
    }
}

fun main() {
    InvokeOnJavaFXApplicationThread {
        val model = CreateDatasetModel.default()
        val stage = Stage()
        stage.scene = Scene(CreateDatasetUI(model)).also { SaalFxStyle.registerStylesheets(it) }
        stage.title = "Create new Label dataset (preview)"
        stage.show()
    }
}
