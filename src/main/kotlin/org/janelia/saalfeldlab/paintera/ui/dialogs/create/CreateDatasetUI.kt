package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
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
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_ICON
import org.janelia.saalfeldlab.paintera.Style.REMOVE_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import java.io.File

internal const val NAME_WIDTH = 100.0
internal const val MAIN_FIELD_WIDTH = 75.0

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

	private val datasetField = TextField().apply {
		maxWidth = Double.MAX_VALUE
		textProperty().bindBidirectional(model.datasetProperty)
		textProperty().addListener { _, _, newValue: String? ->
			if (!nameField.manuallyNamed && newValue != null)
				nameField.text = newValue.split("/").toTypedArray().last()
		}
	}

	private val dimensionsField = SpatialField.longField(1, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()).boundTo(model.dimensions)
	private val blockSizeField = SpatialField.intField(1, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()).boundTo(model.blockSize)
	private val resolutionField = SpatialField.doubleField(1.0, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()).boundTo(model.resolution)
	private val offsetField = SpatialField.doubleField(0.0, { true }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()).boundTo(model.offset)

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

	/* stable per-axis editors, built once so the fields survive grid rebuilds */
	private val additionalAxisNodes = mutableMapOf<AdditionalAxis, AxisColumnNodes>()

	private class AxisColumnNodes(
		val header: Node, val dimension: Node, val blockSize: Node,
		val resolution: Node, val offset: Node, val unit: Node, val remove: Node?
	)

	private val spatialColumns by lazy {
		listOf(
			AxisColumnNodes(
				header = columnHeader("X"),
				dimension = dimensionsField.x.textField,
				blockSize = blockSizeField.x.textField,
				resolution = resolutionField.x.textField,
				offset = offsetField.x.textField,
				unit = unitField(model.xUnitProperty),
				remove = null
			),
			AxisColumnNodes(
				header = columnHeader("Y"),
				dimension = dimensionsField.y.textField,
				blockSize = blockSizeField.y.textField,
				resolution = resolutionField.y.textField,
				offset = offsetField.y.textField,
				unit = unitField(model.yUnitProperty),
				remove = null
			),
			AxisColumnNodes(
				header = columnHeader("Z"),
				dimension = dimensionsField.z.textField,
				blockSize = blockSizeField.z.textField,
				resolution = resolutionField.z.textField,
				offset = offsetField.z.textField,
				unit = unitField(model.zUnitProperty),
				remove = null
			)
		)
	}

	/* the init block must follow every property declaration; rebuildAxisGrid touches most of them */
	init {
		model.additionalAxes.addListener(ListChangeListener {
			rebuildAxisGrid()
			/* adding or removing a column changes the grid height, so resize the window */
			InvokeOnJavaFXApplicationThread { (scene?.window as? Stage)?.sizeToScene() }
		})
		rebuildAxisGrid()

		children += nameIt("Name", NAME_WIDTH, true, nameField)
		children += nameIt("Container", NAME_WIDTH, true, containerField.asNode())
		children += nameIt("Dataset", NAME_WIDTH, true, datasetField)
		children += axisGrid
		children += scaleLevels
	}

	private fun nodesFor(axisColumn: AdditionalAxis): AxisColumnNodes = additionalAxisNodes.getOrPut(axisColumn) {
		AxisColumnNodes(
			header = typeCombo(axisColumn),
			dimension = boundLongField(axisColumn.sizeProperty) { it > 0 },
			blockSize = boundIntField(axisColumn.blockSizeProperty) { it > 0 },
			resolution = boundDoubleField(axisColumn.resolutionProperty) { it > 0 },
			offset = boundDoubleField(axisColumn.offsetProperty) { true },
			unit = unitField(axisColumn.unitProperty),
			remove = removeColumnButton(axisColumn)
		)
	}

	/* rebuild the axis grid: x, y, z columns from the spatial fields, then a column per additional axis */
	private fun rebuildAxisGrid() {
		axisGrid.children.clear()
		additionalAxisNodes.keys.retainAll(model.additionalAxes.toSet())

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

		val columns = spatialColumns + model.additionalAxes.map { nodesFor(it) }

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

	private fun boundLongField(property: LongProperty, test: (Long) -> Boolean) =
		NumberField.longField(property.value, test, *SubmitOn.entries.toTypedArray()).apply {
			valueProperty().bindBidirectional(property)
			textField.prefWidth = MAIN_FIELD_WIDTH
		}.textField

	private fun boundIntField(property: IntegerProperty, test: (Int) -> Boolean) =
		NumberField.intField(property.value, test, *SubmitOn.entries.toTypedArray()).apply {
			valueProperty().bindBidirectional(property)
			textField.prefWidth = MAIN_FIELD_WIDTH
		}.textField

	private fun boundDoubleField(property: DoubleProperty, test: (Double) -> Boolean) =
		NumberField.doubleField(property.value, test, *SubmitOn.entries.toTypedArray()).apply {
			valueProperty().bindBidirectional(property)
			textField.prefWidth = MAIN_FIELD_WIDTH
		}.textField

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
			disableProperty().bind(this@getDialog.invalidProperty)
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
