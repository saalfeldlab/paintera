package org.janelia.saalfeldlab.fx.ui

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ButtonBase
import javafx.scene.control.Dialog
import javafx.scene.control.Toggle
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.FlowPane
import javafx.util.Subscription
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.tools.ToolBarItem
import org.janelia.saalfeldlab.paintera.ui.hGrow

private data class ToggleActionBarItem(val node: Node, val toggle: Toggle? = null)

class ModeToolActionBar : FlowPane() {

	val modeToolsGroup = ToggleGroup()
	val modeActionsGroup = ToggleGroup()
	val toolActionsGroup = ToggleGroup()

	private val toggleGroups = mutableMapOf<ToggleGroup, List<ToggleActionBarItem>>()

	init {
		alignment = Pos.TOP_RIGHT
		prefWrapLength = USE_COMPUTED_SIZE
		hGrow()
	}

	fun reset() {
		toggleGroups.clear()
	}

	fun show(show: Boolean = true) {
 		isVisible = show
		isManaged = show
	}

	fun showGroup(group: ToggleGroup, show: Boolean) {
		toggleGroups[group]?.forEach { (node, _) ->
			node.isVisible = show
			node.isManaged = show
		}
	}

	fun removeToggleGroup(group: ToggleGroup) {
		toggleGroups.remove(group)?.forEach { (_, toggle) -> toggle?.toggleGroup = null}
	}

	fun addActionSets(actionSets : List<ActionSet>, group : ToggleGroup = ToggleGroup()) : Subscription {
		return addButtons(group, actionSets.actionSetButtons())
	}

	fun addToolBarItems(items : List<ToolBarItem>, group: ToggleGroup = ToggleGroup()) : Subscription {
		return addButtons(group, items.toolBarButtons())
	}

	private fun addButtons(group: ToggleGroup = ToggleGroup(), buttons: List<ButtonBase>): Subscription {
		val buttonsToNodes = getNodeForButtons(buttons)
		val toggles = buttonsToNodes.map { (k, v) -> ToggleActionBarItem(v, k as? Toggle) }
		toggles.forEach { (_, toggle) -> toggle?.toggleGroup = group }
		toggleGroups.merge(group, toggles) { l, r -> l + r }
		children.addAll(buttonsToNodes.values)
		return Subscription {
			InvokeOnJavaFXApplicationThread {
				children.removeAll(buttonsToNodes.values)
				removeToggleGroup(group)
			}
		}
	}

	private fun getNodeForButtons(buttons : List<ButtonBase>) : Map<ButtonBase, Node> {
		return buttons.associateWith {  node ->
			node.styleClass += "toolbar-button"
			node.graphic?.also { graphic -> graphic.styleClass += "toolbar-graphic" }
			ScaleView(node).also { it.styleClass += "toolbar-scale-pane" }
		}
	}

	companion object {

		fun List<ToolBarItem>.toolBarButtons() = map { item ->
			item.toolBarButton.apply {
				onAction ?: let {
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

		private fun Iterable<ActionSet>.actionSetButtons(): List<ButtonBase> = map { it.toolBarNodes().toSet() }
			.filter { it.isNotEmpty() }
			.fold(setOf<ToolBarItem>()) { l, r -> l + r }
			.map { it.toolBarButton }
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {
		Dialog<Unit>().apply {
			isResizable = true
			showAndWait()
		}
	}
}