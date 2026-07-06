package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.Property
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
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Style.ADD_ICON
import org.janelia.saalfeldlab.paintera.Style.REMOVE_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass

internal const val SCALE_FIELD_WIDTH = 60.0

/** Bind the field's x, y, z bidirectionally to [values]. */
internal fun <P : Property<Number>> SpatialField<P>.boundTo(values: SpatialValues<P>) = apply {
	x.valueProperty().bindBidirectional(values.xProperty)
	y.valueProperty().bindBidirectional(values.yProperty)
	z.valueProperty().bindBidirectional(values.zProperty)
}

/** Display [values] read-only. */
internal fun <P : Property<Number>> SpatialField<P>.displaying(values: SpatialValues<P>) = apply {
	editable = false
	x.valueProperty().bind(values.xProperty)
	y.valueProperty().bind(values.yProperty)
	z.valueProperty().bind(values.zProperty)
}

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

	private inner class RowNodes(level: ScaleLevel) {
		val relativeField: SpatialField<IntegerProperty> = SpatialField.intField(1, { it > 0 }, SCALE_FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
			.boundTo(level.relativeDownsamplingFactors)
		val absoluteField: SpatialField<IntegerProperty> = SpatialField.intField(1, { true }, SCALE_FIELD_WIDTH).displaying(level.absoluteDownsamplingFactors)
		val resolutionField: SpatialField<DoubleProperty> = SpatialField.doubleField(1.0, { true }, SCALE_FIELD_WIDTH).displaying(level.resolution)
		val dimensionsField: SpatialField<LongProperty> = SpatialField.longField(1, { true }, SCALE_FIELD_WIDTH).displaying(level.dimensions)
		val maxEntriesField: NumberField<IntegerProperty> = NumberField.intField(level.maxNumberOfEntries.value, { true }, *SubmitOn.entries.toTypedArray()).apply {
			valueProperty().bindBidirectional(level.maxNumberOfEntries)
			textField.prefWidth = SCALE_FIELD_WIDTH
			textField.minWidth = Region.USE_PREF_SIZE
		}
		val remove = Button().apply {
			addStyleClass(REMOVE_ICON)
			setOnAction {
				it.consume()
				model.removeLevel(level)
			}
			disableProperty().bind(Bindings.size(model.levels).lessThanOrEqualTo(1))
		}
	}

	private val rowNodesByLevel = mutableMapOf<ScaleLevel, RowNodes>()
	private val levelSupports = mutableMapOf<ScaleLevel, ValidationSupport>()

	init {
		padding = Insets(0.0, 5.0, 10.0, 5.0)
		children += HBox(addButton).apply { alignment = Pos.TOP_RIGHT }
		children += grid

		model.levels.addListener(ListChangeListener { rebuild() })
		/* the Max Entries column only applies to label multiset datasets */
		labelMultisetProperty.subscribe { _, _ -> rebuild() }
		rebuild()
	}

	private fun rowNodesFor(level: ScaleLevel): RowNodes = rowNodesByLevel.getOrPut(level) {
		RowNodes(level).also { registerLevelDecorations(level, it.dimensionsField) }
	}

	private fun groupNode(field: SpatialField<*>) = HBox(field.x.textField, field.y.textField, field.z.textField)

	private class Group(val title: String, val cell: (RowNodes) -> Node)

	private fun rebuild() {
		grid.children.clear()
		grid.columnConstraints.clear()
		val showMaxEntries = labelMultisetProperty.value
		val totalRows = 1 + model.levels.size

		/* leading "Scale N:" label column, right aligned; s0's relative factors are fixed at 1 */
		model.levels.forEachIndexed { index, level ->
			rowNodesFor(level).relativeField.editable = index != 0
			val label = Label("Scale $index:").apply { minWidth = Region.USE_PREF_SIZE }
			grid.add(label, 0, index + 1)
			GridPane.setHalignment(label, HPos.RIGHT)
		}

		val groups = buildList {
			add(Group("Relative Factors") { groupNode(it.relativeField) })
			add(Group("Absolute Factors") { groupNode(it.absoluteField) })
			add(Group("Resolution") { groupNode(it.resolutionField) })
			add(Group("Dimensions") { groupNode(it.dimensionsField) })
			if (showMaxEntries) add(Group("Max Entries") { it.maxEntriesField.textField })
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

	private fun registerLevelDecorations(level: ScaleLevel, dimensionsField: SpatialField<LongProperty>) {
		val support = ValidationSupport()
		(support.validationDecorator as? GraphicValidationDecoration)?.let {
			support.validationDecoratorProperty().set(AlignableGraphicValidationDecoration(Pos.BOTTOM_RIGHT))
		}
		listOf(
			Triple(level.dimensions.xProperty, model.blockSize.xProperty, dimensionsField.x),
			Triple(level.dimensions.yProperty, model.blockSize.yProperty, dimensionsField.y),
			Triple(level.dimensions.zProperty, model.blockSize.zProperty, dimensionsField.z),
		).forEach { (dimension, blockSize, field) ->
			support.registerValidator(field.textField, false, Validator<String> { _, _ ->
				ValidationResult.fromErrorIf(field.textField, "Dimension size is zero. Remove this scale level.", dimension.value.toInt() <= 0)
					?.addWarningIf(field.textField, "Dimension is smaller than block size. Consider removing this scale level.", dimension.value.toInt() <= blockSize.value.toInt())
			})
			/* revalidate when the block size changes; ControlsFX only revalidates on the field's own value */
			blockSize.subscribe { _ -> support.revalidate() }
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
