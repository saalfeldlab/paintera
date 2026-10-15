package org.janelia.saalfeldlab.paintera.filters

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.ui.NamedNode
class RenderFiltersUI(private val model: RenderFiltersModel) : TitledPane() {

	private val order get() = model.config.filterConfigs

	init {
		val entries = VBox(2.0)
		order.subscribe { entries.children.setAll(order.map { entryPane(it) }) }
		entries.children.setAll(order.map { entryPane(it) })
		content = entries
		isExpanded = false
		graphic = HBox(Label("Render Filters"), NamedNode.bufferNode()).apply {
			alignment = Pos.CENTER
			Tooltip.install(this, Tooltip("Applied top to bottom, each to the output of the one above"))
		}
		contentDisplay = ContentDisplay.GRAPHIC_ONLY
		alignment = Pos.CENTER_RIGHT
	}

	private fun entryPane(config: RenderFilterConfig): Node {
		val index = order.indexOf(config)
		val up = Button("▲").apply {
			isDisable = index == 0
			setOnAction { swap(index, index - 1) }
		}
		val down = Button("▼").apply {
			isDisable = index == order.size - 1
			setOnAction { swap(index, index + 1) }
		}
		val enabled = CheckBox().apply {
			selectedProperty().bindBidirectional(config.enabledProperty)
			tooltip = Tooltip("Enable ${config.name}")
		}
		val graphic = HBox(4.0, Label(config.name), NamedNode.bufferNode(), up, down, enabled).apply {
			alignment = Pos.CENTER
			Tooltip.install(this, Tooltip(config.description))
		}
		return TitledPane(null, model.settingsNode(config)).apply {
			isExpanded = false
			this.graphic = graphic
			contentDisplay = ContentDisplay.GRAPHIC_ONLY
			alignment = Pos.CENTER_RIGHT
		}
	}

	private fun swap(from: Int, to: Int) {
		val moved = order.removeAt(from)
		order.add(to, moved)
	}
}
