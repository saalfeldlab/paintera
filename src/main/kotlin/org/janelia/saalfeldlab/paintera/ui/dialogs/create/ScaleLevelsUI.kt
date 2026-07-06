package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ListChangeListener
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.Duration
import org.controlsfx.control.decoration.Decoration
import org.controlsfx.control.decoration.GraphicDecoration
import org.controlsfx.validation.ValidationMessage
import org.controlsfx.validation.ValidationResult
import org.controlsfx.validation.ValidationSupport
import org.controlsfx.validation.Validator
import org.controlsfx.validation.decoration.GraphicValidationDecoration
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_ICON
import org.janelia.saalfeldlab.paintera.Style.REMOVE_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass

/**
 * UI for a [ScaleLevelsModel] as a single shared grid:
 * - one row per scale level
 * - columns for
 *   - relative factors, absolute factors, resolution, dimensions and (when [labelMultisetProperty]) max entries.
 */
class ScaleLevelsUI(
	private val model: ScaleLevelsModel,
	private val labelMultisetProperty: BooleanProperty
) : VBox(5.0) {

	private val grid = GridPane().apply {
		hgap = 10.0
		vgap = 4.0
		alignment = Pos.CENTER_LEFT
	}

	private val addButton = Button().apply {
		addStyleClass(ADD_ICON)
		setOnAction {
			it.consume()
			model.addLevel()
		}
	}

	/* stable per-level cell nodes, reused across rebuilds so the fields and their validation decorations are not reparented */
	private class RowNodes(val relative: Node, val absolute: Node, val resolution: Node, val dimensions: Node, val maxEntries: Node, val remove: Button)

	private val rowNodesByLevel = mutableMapOf<ScaleLevel, RowNodes>()
	private val levelSupports = mutableMapOf<ScaleLevel, ValidationSupport>()

	init {
		padding = Insets(0.0, 5.0, 10.0, 5.0)
		children += HBox(addButton).apply { alignment = Pos.TOP_RIGHT }
		children += grid

		model.levels.forEach { registerLevelDecorations(it) }
		model.levels.addListener(ListChangeListener { change ->
			while (change.next()) {
				if (change.wasAdded()) change.addedSubList.forEach { registerLevelDecorations(it) }
			}
			rebuild()
		})
		/* the Max Entries column only applies to label multiset datasets */
		labelMultisetProperty.subscribe { _, _ -> rebuild() }
		rebuild()
	}

	private fun rowNodesFor(level: ScaleLevel): RowNodes = rowNodesByLevel.getOrPut(level) {
		RowNodes(
			groupNode(level.relativeDownsamplingFactors),
			groupNode(level.absoluteDownsamplingFactors),
			groupNode(level.resolution),
			groupNode(level.dimensions),
			level.maxNumberOfEntries.textField,
			Button().apply {
				addStyleClass(REMOVE_ICON)
				setOnAction {
					it.consume()
					model.removeLevel(level)
				}
				disableProperty().bind(Bindings.size(model.levels).lessThanOrEqualTo(1))
			}
		)
	}

	private fun groupNode(field: SpatialField<*>) = HBox(field.x.textField, field.y.textField, field.z.textField)

	private class Group(val title: String, val cell: (RowNodes) -> Node)

	private fun rebuild() {
		grid.children.clear()
		grid.columnConstraints.clear()
		val showMaxEntries = labelMultisetProperty.value
		val totalRows = 1 + model.levels.size

		/* leading "Scale N:" label column, right aligned */
		model.levels.forEachIndexed { index, level ->
			if (index == 0) level.relativeDownsamplingFactors.editable = false
			val label = Label("Scale $index:").apply { minWidth = Region.USE_PREF_SIZE }
			grid.add(label, 0, index + 1)
			GridPane.setHalignment(label, HPos.RIGHT)
		}

		val groups = buildList {
			add(Group("Relative Factors") { it.relative })
			add(Group("Absolute Factors") { it.absolute })
			add(Group("Resolution") { it.resolution })
			add(Group("Dimensions") { it.dimensions })
			if (showMaxEntries) add(Group("Max Entries") { it.maxEntries })
		}

		var column = 1
		groups.forEachIndexed { index, group ->
			/* a full-height vertical separator between each header category */
			if (index > 0) {
				grid.add(Separator(Orientation.VERTICAL).apply { maxHeight = Double.MAX_VALUE }, column, 0, 1, totalRows)
				column++
			}
			val headerLabel = header(group.title)
			grid.add(headerLabel, column, 0)
			model.levels.forEachIndexed { i, level ->
				val cell = group.cell(rowNodesFor(level))
				grid.add(cell, column, i + 1)
				GridPane.setHalignment(cell, HPos.CENTER)
			}
			column++
		}

		/* trailing remove-button column */
		model.levels.forEachIndexed { i, level -> grid.add(rowNodesFor(level).remove, column, i + 1) }

		rowNodesByLevel.keys.retainAll(model.levels.toSet())
		levelSupports.keys.retainAll(model.levels.toSet())
		InvokeOnJavaFXApplicationThread { (scene?.window as? Stage)?.sizeToScene() }
	}

	private fun header(text: String) = Label(text).apply {
		maxWidth = Double.MAX_VALUE
		minWidth = USE_PREF_SIZE
		alignment = Pos.CENTER
	}

	private class AlignableGraphicValidationDecoration(private val alignment: Pos = Pos.BOTTOM_RIGHT) : GraphicValidationDecoration() {
		override fun createValidationDecorations(message: ValidationMessage?): MutableCollection<Decoration> {
			return mutableListOf(GraphicDecoration(createDecorationNode(message), alignment))
		}

		/* show the validation tooltip instantly on hover instead of after the default ~1s delay */
		override fun createTooltip(message: ValidationMessage?): Tooltip =
			super.createTooltip(message).apply { showDelay = Duration.ZERO }
	}

	private fun registerLevelDecorations(level: ScaleLevel) {
		val support = ValidationSupport()
		(support.validationDecorator as? GraphicValidationDecoration)?.let {
			support.validationDecoratorProperty().set(AlignableGraphicValidationDecoration(Pos.BOTTOM_RIGHT))
		}
		mapOf(
			level.dimensions.x to model.blockSize.x,
			level.dimensions.y to model.blockSize.y,
			level.dimensions.z to model.blockSize.z,
		).forEach { (dimension, blockSize) ->
			support.registerValidator(dimension.textField, false, Validator<String> { _, _ ->
				ValidationResult.fromErrorIf(dimension.textField, "Dimension size is zero. Remove this scale level.", dimension.value.toInt() <= 0)
					?.addWarningIf(dimension.textField, "Dimension is smaller than block size. Consider removing this scale level.", dimension.value.toInt() <= blockSize.value.toInt())
			})
			/* revalidate when the block size changes; ControlsFX only revalidates on the field's own value */
			blockSize.valueProperty().subscribe { _ -> support.revalidate() }
		}
		levelSupports[level] = support
		/* ControlsFX only decorates after the first value edit; enable initial decoration so an already-invalid
		 * level shows its validation without an edit */
		support.initInitialDecoration()
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {
		val ui = ScaleLevelsUI(ScaleLevelsModel.default(), SimpleBooleanProperty(true))
		val stage = Stage()
		stage.scene = Scene(ui).also { Paintera.registerStylesheets(stage.scene) }
		stage.title = "Scale Levels (preview)"
		stage.show()
	}
}
