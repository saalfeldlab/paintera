package org.janelia.saalfeldlab.fx.ui

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ButtonBase
import javafx.scene.control.Toggle
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.paintera.control.tools.ToolBarItem

class ActionBar : HBox() {

	var buttons : MutableList<ButtonBase> = mutableListOf()

	var toggleGroup : ToggleGroup? = null
		private set

	init {
		alignment = Pos.CENTER_RIGHT
		isFillHeight = true
	}

	private fun updateToggleGroup() {
		var newGroup : ToggleGroup? = null
		buttons.forEach { node ->
			(node as? Toggle)?.apply {
				toggleGroup = toggleGroup ?: ToggleGroup().also { newGroup = it }
			}
		}
		toggleGroup = newGroup
	}

	fun reset() {
		children.clear()
		buttons.clear()
		updateToggleGroup()
	}

	fun set(vararg actionSets : ActionSet) {
		children.setAll(setButtonsAndGetNodes(actionSets.toList().buttonsForActionSets()))
		updateToggleGroup()
	}

	fun set(vararg items : ToolBarItem) {
		children.setAll(setButtonsAndGetNodes(items.toList().toolBarNodes()))
		updateToggleGroup()
	}

	fun add(vararg actionSets : ActionSet) {
		children.addAll(setButtonsAndGetNodes(actionSets.toList().buttonsForActionSets()))
		updateToggleGroup()
	}

	fun add(vararg items : ToolBarItem) {
		children.addAll(setButtonsAndGetNodes(items.toList().toolBarNodes()))
		updateToggleGroup()

	}

	private fun setButtonsAndGetNodes(buttons : List<ButtonBase>, clear : Boolean = true) : List<Node> {
		if (clear) this.buttons.clear()
		this.buttons.addAll(buttons)
		return buttons.map {  node ->
			node.styleClass += "toolbar-button"
			node.graphic?.also { graphic -> graphic.styleClass += "toolbar-graphic" }
			ScaleView(node).also { it.styleClass += "toolbar-scale-pane" }
		}.toList()
	}

	fun show(show: Boolean = true) {
 		isVisible = show
		isManaged = show
	}

	companion object {

		fun List<ToolBarItem>.toolBarNodes() = map { item ->
			item.toolBarButton.apply {
				this.onAction ?: let {
					userData = item
				}
				isFocusTraversable = false
			}
		}

		private fun ActionSet.toolBarNodes(): List<ToolBarItem> {
			return actions.mapNotNull { action ->
				action.name?.let { name ->
					action.graphic?.let { graphic ->
						object : ToolBarItem {
							override val graphic = graphic
							override val name = name
							override val keyTrigger = null
							override val action = action
						}
					}
				}
			}
		}

		private fun List<ActionSet>.buttonsForActionSets(): List<ButtonBase> = map { it.toolBarNodes().toSet() }
			.filter { it.isNotEmpty() }
			.fold(setOf<ToolBarItem>()) { l, r -> l + r }
			.map { it.toolBarButton }
	}
}