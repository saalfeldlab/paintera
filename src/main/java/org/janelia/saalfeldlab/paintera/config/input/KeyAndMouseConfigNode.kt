package org.janelia.saalfeldlab.paintera.config.input

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.*
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.paintera.NamedKeyCombination
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

class KeyAndMouseConfigNode(private val config: KeyAndMouseConfig) {

	val node: Node
		get() = makeNode()

	private fun makeNode(): Node {
		val painteraPane =
				KeyAndMouseBindingsNode("Paintera", "TODO", /* TODO */"TODO", /* TODO */config.painteraConfig).node
		return VBox(painteraPane)
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

	class KeyBindingsNode(val bindings: NamedKeyCombination.CombinationMap) {

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

	companion object {
		private const val BUTTON_PREF_WIDTH = 100.0
	}
}
