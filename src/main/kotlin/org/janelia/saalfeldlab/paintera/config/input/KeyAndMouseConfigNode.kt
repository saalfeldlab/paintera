package org.janelia.saalfeldlab.paintera.config.input

import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.ContentDisplay
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.KeyCombination
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.Modality
import javafx.util.Callback
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import java.util.Locale

class KeyAndMouseConfigNode(
	private val config: KeyAndMouseConfig,
	private val sourceInfo: SourceInfo
) {

	private val sources: ObservableList<SourceState<*, *>> = FXCollections.observableArrayList()

	private val sourcesByClass: ObservableMap<Class<out SourceState<*, *>>, MutableList<SourceState<*, *>>> = FXCollections.observableHashMap()

	private val hasSources = Bindings.createBooleanBinding({ sourceInfo.numSources().get() > 0 }, sourceInfo.numSources())

	init {
		sources.addListener(
			InvalidationListener {
				sourcesByClass.clear()
				sources.forEach {
					sourcesByClass.computeIfAbsent(it::class.java) { mutableListOf() }.add(it)
				}
			}
		)
		sourceInfo.trackSources().addListener(InvalidationListener { sources.setAll(sourceInfo.trackSources().map { sourceInfo.getState(it) }) })
		sources.setAll(sourceInfo.trackSources().map { sourceInfo.getState(it) })
	}

	fun makeNode(): Accordion {
		val painteraPane = KeyAndMouseBindingsNode(
			"Paintera",
			"TODO", /* TODO */
			"TODO", /*TODO */
			ControlMode.keyAndMouseBindings
		).makeNode()

		val navigationPane = KeyAndMouseBindingsNode(
			"Navigation",
			"TODO", /* TODO */
			"TODO", /* TODO */
			NavigationTool.keyAndMouseBindings
		).makeNode()


		val sourceSpecificConfigPanes = Accordion()

		val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
			headerText = "Source-Specific Bindings"
			contentText = "Source states with source-specific functionality provide key bindings as listed below."
		}

		val tpGraphics = HBox(
			Label("Source-Specific Bindings"),
			NamedNode.bufferNode(),
			Button("").apply {
				addStyleClass(Style.HELP_ICON)
				setOnAction { helpDialog.show() }
			}
		).apply { alignment = Pos.CENTER }
		val sourceSpecificBindings = TitledPanes.createCollapsed(null, sourceSpecificConfigPanes).apply {
			graphic = tpGraphics
			contentDisplay = ContentDisplay.GRAPHIC_ONLY
			alignment = Pos.CENTER_RIGHT
		}

		sourceSpecificBindings.visibleProperty().bind(hasSources)
		sourceSpecificBindings.managedProperty().bind(sourceSpecificBindings.visibleProperty())

		sourcesByClass.addListener(InvalidationListener {
			val sortedKeys = sourcesByClass.keys.sortedBy { it.simpleName }
			sourceSpecificConfigPanes.panes.setAll(sortedKeys
				.filter { config.hasConfigFor(it) }
				.mapNotNull { SourceSpecificKeyAndMouseBindingsNode(sourceInfo, it, sourcesByClass[it]!!, config.getConfigFor(it)!!).makeNode() })
		}.apply { invalidated(sourcesByClass) })

		sourceSpecificConfigPanes.panes.firstOrNull()?.let { sourceSpecificConfigPanes.expandedPane = it }

		return Accordion(painteraPane, navigationPane, sourceSpecificBindings).apply {
			expandedPane = painteraPane
		}
	}

	class SourceSpecificKeyAndMouseBindingsNode(
		val sourceInfo: SourceInfo,
		val sourceClass: Class<out SourceState<*, *>>,
		val sources: List<SourceState<*, *>>,
		val bindings: KeyAndMouseBindings
	) {

		val indexColumn = TableColumn<Pair<Int, String>, String>("Index").apply { cellValueFactory = PropertyValueFactory("first") }
		val nameColumn = TableColumn<Pair<Int, String>, String>("Name").apply { cellValueFactory = PropertyValueFactory("second") }

		fun makeNode(): TitledPane? {

			if (bindings.keyCombinations.isEmpty()) return null

			val sortedStates = sources.sortedBy { sourceInfo.indexOf(it.dataSource) }
			val sortedNames = sortedStates.map { it.nameProperty().value }

			val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION).apply {
				initModality(Modality.NONE)
				headerText = "Bindings for sources of type ${sourceClass.simpleName}"
				dialogPane.content = TableView(FXCollections.observableArrayList(sortedNames.mapIndexed { index, s -> Pair(index, s) })).apply {
					columns.add(indexColumn)
					columns.add(nameColumn)
				}
			}


			val tpGraphics = HBox(
				Labels.withTooltip(sourceClass.simpleName, sourceClass.name),
				NamedNode.bufferNode(),
				Button("").apply {
					addStyleClass(Style.HELP_ICON)
					setOnAction { helpDialog.show() }
				}
			).apply { alignment = Pos.CENTER }

			return TitledPane("", KeyBindingsNode(bindings.keyCombinations).node).apply {
				if (bindings.keyCombinations.isEmpty())
					managedProperty().value = false
				maxWidth = Double.MAX_VALUE
				graphic = tpGraphics
				contentDisplay = ContentDisplay.GRAPHIC_ONLY
				alignment = Pos.CENTER_RIGHT
			}
		}
	}

	class KeyAndMouseBindingsNode(
		val title: String,
		val shortDescription: String,
		val description: String,
		val bindings: KeyAndMouseBindings
	) {

		fun makeNode(): TitledPane {

			val tpGraphics: HBox
			val titleLabel = Label(title)
			if (description.isNotEmpty() && description.trim().uppercase(Locale.getDefault()) != "TODO") {

				val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION).apply {
					initModality(Modality.NONE)
					headerText = title
					contentText = description
				}

				val helpButtonIfDescription = Button("").apply {
					addStyleClass(Style.HELP_ICON)
					setOnAction { helpDialog.show() }
				}
				tpGraphics = HBox(titleLabel, NamedNode.bufferNode(), helpButtonIfDescription).apply { alignment = Pos.CENTER }
			} else {
				tpGraphics = HBox(titleLabel).apply { alignment = Pos.CENTER }
			}



			return TitledPane("", KeyBindingsNode(bindings.keyCombinations).node).apply {
				graphic = tpGraphics
				contentDisplay = ContentDisplay.GRAPHIC_ONLY
				alignment = Pos.CENTER_RIGHT
			}
		}

	}

	class KeyBindingsGridNode(val bindings: NamedKeyCombination.CombinationMap) {

		val node: Node
			get() = makeNode()

		private fun makeNode(): Node {
			val grid = GridPane().apply { alignment = Pos.CENTER_LEFT }
			bindings.keys.sorted().forEachIndexed { index, s ->
				val combination = bindings[s]!!
				grid.add(Labels.withTooltip(combination.keyBindingName).also { GridPane.setHgrow(it, Priority.ALWAYS) }, 0, index)
				grid.add(Buttons.withTooltip("${combination.primaryCombination}") {}.also { it.prefWidth = BUTTON_PREF_WIDTH }, 1, index)
			}
			return grid
		}
	}

	class KeyBindingsNode(val bindings: NamedKeyCombination.CombinationMap) {

		val node: Node
			get() = makeNode()

		val nameColumn = TableColumn<String, String>("Name").apply {
			cellValueFactory = Callback { SimpleStringProperty(it.value) }
		}
		val bindingColumn = TableColumn<String, KeyCombination>("Binding").apply {
			cellValueFactory = Callback { bindings[it.value]?.primaryCombinationProperty }
		}

		private fun makeNode(): Node = TableView(FXCollections.observableArrayList(bindings.keys.sorted())).apply {
			columns.clear()
			columns.add(nameColumn)
			columns.add(bindingColumn)
		}
	}

	companion object {
		private const val BUTTON_PREF_WIDTH = 100.0
	}
}


