package org.janelia.saalfeldlab.paintera.config.input

import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.KeyCombination
import javafx.scene.layout.*
import javafx.stage.Modality
import javafx.util.Callback
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.paintera.NamedKeyCombination
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import java.util.concurrent.Callable

class KeyAndMouseConfigNode(
		private val config: KeyAndMouseConfig,
		private val sourceInfo: SourceInfo) {

	private val sources: ObservableList<SourceState<*, *>> = FXCollections.observableArrayList()

	private val sourcesByClass: ObservableMap<Class<out SourceState<*, *>>, MutableList<SourceState<*, *>>> = FXCollections.observableHashMap()

	private val hasSources = Bindings.createBooleanBinding(Callable { sourceInfo.numSources().get() > 0 }, sourceInfo.numSources())

	init {
		sources.addListener(InvalidationListener { sourcesByClass.clear(); sources.forEach { sourcesByClass.computeIfAbsent(it::class.java) { mutableListOf() }.add(it) } })
	    sourceInfo.trackSources().addListener(InvalidationListener { sources.setAll(sourceInfo.trackSources().map { sourceInfo.getState(it) }) })
		sources.setAll(sourceInfo.trackSources().map { sourceInfo.getState(it) })
	}

	val node: Node
		get() = makeNode()

	private fun makeNode(): Node {
		val painteraPane = KeyAndMouseBindingsNode(
				"Paintera",
				"TODO", /* TODO */
				"TODO", /*TODO */
				config.painteraConfig).node

		val navigationPane = KeyAndMouseBindingsNode(
				"Navigation",
				"TODO", /* TODO */
				"TODO", /* TODO */
				config.navigationConfig).node


		val sourceSpecificConfigPanes = VBox()

		val helpDialog = PainteraAlerts
				.alert(Alert.AlertType.INFORMATION, true)
				.also { it.initModality(Modality.NONE) }
				.also { it.headerText = "Source-Specific Bindings" }
				.also { it.contentText = "Source states with source-specific functionality provide key bindings as listed below." }

		val tpGraphics = HBox(
				Label("Source-Specific Bindings"),
				Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
				Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
				.also { it.alignment = Pos.CENTER }
		val sourceSpecificBindings = TitledPanes
				.createCollapsed(null, sourceSpecificConfigPanes)
				.also { with (TitledPaneExtensions) { it.graphicsOnly(tpGraphics) } }
				.also { it.alignment = Pos.CENTER_RIGHT }

		sourceSpecificBindings.visibleProperty().bind(hasSources)
		sourceSpecificBindings.managedProperty().bind(sourceSpecificBindings.visibleProperty())

		sourcesByClass.addListener(InvalidationListener {
			val sortedKeys = sourcesByClass.keys.sortedBy { it.simpleName }
			sourceSpecificConfigPanes.children.setAll(sortedKeys
					.filter { config.hasConfigFor(it) }
					.map { SourceSpecificKeyAndMouseBindingsNode(sourceInfo, it, sourcesByClass[it]!!, config.getConfigFor(it)!!).node })
		}.also { it.invalidated(sourcesByClass) })

		return VBox(painteraPane, navigationPane, sourceSpecificBindings)
	}

	class SourceSpecificKeyAndMouseBindingsNode(
			val sourceInfo: SourceInfo,
			val sourceClass: Class<out SourceState<*, *>>,
			val sources: List<SourceState<*, *>>,
			val bindings: KeyAndMouseBindings) {


		val node: Node
			get() = makeNode()

		private fun makeNode(): Node {

			val sortedStates = sources.sortedBy { sourceInfo.indexOf(it.dataSource) }
			val sortedNames = sortedStates.map { it.nameProperty().value }

			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = "Bindings for sources of type ${sourceClass.simpleName}" }
					.also { it.dialogPane.content = TableView(FXCollections.observableArrayList(sortedNames.mapIndexed { index, s -> Pair(index, s) }))
							.also { it.columns.add(TableColumn<Pair<Int, String>, String>("Index").also { it.cellValueFactory = PropertyValueFactory("first") }) }
							.also { it.columns.add(TableColumn<Pair<Int, String>, String>("Name").also { it.cellValueFactory = PropertyValueFactory("second") }) }
					}

			val tpGraphics = HBox(
					Labels.withTooltip(sourceClass.simpleName, sourceClass.name),
					Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER }

			return TitledPane("", KeyBindingsNode(bindings.keyCombinations).node)
					.also { with (TitledPaneExtensions) { it.graphicsOnly(tpGraphics) } }
					.also { it.alignment = Pos.CENTER_RIGHT }
		}

	}

	class KeyAndMouseBindingsNode(
			val title: String,
			val shortDescription: String,
			val description: String,
			val bindings: KeyAndMouseBindings) {


		val node: Node
			get() = makeNode()

		private fun makeNode(): Node {

			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = title }
					.also { it.contentText = description }

			val tpGraphics = HBox(
					Label(title),
					Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER }

			return TitledPane("", KeyBindingsNode(bindings.keyCombinations).node)
					.also { with (TitledPaneExtensions) { it.graphicsOnly(tpGraphics) } }
					.also { it.alignment = Pos.CENTER_RIGHT }
		}

	}

	class KeyBindingsGridNode(val bindings: NamedKeyCombination.CombinationMap) {

		val node: Node
			get() = makeNode()

		private fun makeNode(): Node {
			val grid = GridPane().also { it.alignment = Pos.CENTER_LEFT }
			bindings.keys.sorted().forEachIndexed { index, s ->
				val combination = bindings[s]!!
				grid.add(Labels.withTooltip(combination.name).also { GridPane.setHgrow(it, Priority.ALWAYS) }, 0, index)
				grid.add(Buttons.withTooltip("${combination.primaryCombination}") {}.also { it.prefWidth = BUTTON_PREF_WIDTH }, 1, index)
			}
			return grid
		}
	}

	class KeyBindingsNode(val bindings: NamedKeyCombination.CombinationMap) {

		val node: Node
			get() = makeNode()

		private fun makeNode(): Node {
			val table = TableView(FXCollections.observableArrayList(bindings.keys.sorted()))
			table.columns.clear()
			table.columns.add(TableColumn<String, String>("Name").also { it.cellValueFactory = Callback { SimpleStringProperty(it.value) } })
			table.columns.add(TableColumn<String, KeyCombination>("Binding").also { it.cellValueFactory = Callback { bindings[it.value]?.primaryCombinationProperty() } })
			return table
		}
	}

	companion object {
		private const val BUTTON_PREF_WIDTH = 100.0
	}
}
