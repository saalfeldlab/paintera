package org.janelia.saalfeldlab.fx.ui

import javafx.event.ActionEvent.ACTION
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.FlowPane
import javafx.util.Subscription
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.ToolBarItem
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.python.antlr.ast.Subscript
import java.util.concurrent.atomic.AtomicBoolean

private data class ToggleActionBarItem(val node: Node, val toggle: Toggle? = null)

class ModeToolActionBar : FlowPane() {

	val modeToolsGroup = ToggleGroup()
	val modeActionsGroup = ToggleGroup()
	val toolActionsGroup = ToggleGroup()

	private val toggleGroups = mutableMapOf<ToggleGroup, List<ToggleActionBarItem>>()

	init {
		addStyleClass("mode-tool-action-bar")
		alignment = Pos.TOP_RIGHT
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
		toggleGroups.remove(group)?.forEach { (_, toggle) -> toggle?.toggleGroup = null }
	}

	fun addActionSets(actionSets: List<ActionSet>, group: ToggleGroup = ToggleGroup()): Subscription {
		return addItems(group, actionSets.actionSetButtons())
	}

	fun addToolBarItems(items: List<ToolBarItem>, group: ToggleGroup = ToggleGroup()): Subscription {
		return addItems(group, items.toolBarButtons())
	}
	private fun addItems(group: ToggleGroup = ToggleGroup(), controls: List<Labeled>): Subscription {
		val toggles = controls.map { ToggleActionBarItem(it, it as? Toggle) }
		toggles.forEach { (_, toggle) -> toggle?.toggleGroup = group }
		toggleGroups.merge(group, toggles) { l, r -> l + r }
		children.addAll(controls)

		/* Subscription must be idempotent*/
		val runOnce  = AtomicBoolean(true)
		fun removeItems() {
			if (runOnce.getAndSet(false)) {
				InvokeOnJavaFXApplicationThread {
					children.removeAll(controls)
					removeToggleGroup(group)
				}
			}
		}
		return Subscription { removeItems() }
	}

	companion object {

		private fun finalizeToolBarItemControl(item : ToolBarItem, control: Labeled) {
			control.id = item.name
			//FIXME Caleb: this is either not necessary, or magic. Regardless, should fix it
			//  why conditionally bind isDisabled only if a graphic?

			val actionIsValid = { item.action?.isValid(null) ?: true }

			/* Listen on disabled when visible*/
			if ("ignore-disable" !in control.styleClass) {
				paintera.baseView.isDisabledProperty.`when`(control.visibleProperty()).subscribe { disabled ->
					control.disableProperty().set(disabled || !actionIsValid())
				}
			} else {
				control.disableProperty().set(!actionIsValid())
			}
			/* set the initial state to */
			control.disableProperty().set(!actionIsValid())

			(control as? Tool)?.isValidProperty?.`when`(control.visibleProperty())?.subscribe { isValid -> control.disableProperty().set(!isValid) }

			control.addStyleClass(Style.TOOLBAR_CONTROL)
			control.tooltip = Tooltip(
				item.keyTrigger?.let { trigger ->
					"${item.name}: ${KeyTracker.keysToString(*trigger.keyCodes.toTypedArray<KeyCode>())}"
				} ?: item.name
			)
		}

		private fun initializeToolBarControl(item: ToolBarItem): Labeled {
			val control = item.newToolBarControl()
			item.action?.let { action ->
				when (control) {
					is ButtonBase -> { control.setOnAction { action() } }
					else -> control.addEventHandler(ACTION, EventHandler { action() })
				}
			}
			finalizeToolBarItemControl(item, control)
			return control.apply {
				item.action ?: let {
					userData = item
				}
				isFocusTraversable = false
			}
		}

		fun List<ToolBarItem>.toolBarButtons() = map {  initializeToolBarControl(it) }

		private fun Iterable<ActionSet>.actionSetButtons() = map { it.toolBarNodes().toSet() }
			.filter { it.isNotEmpty() }
			.fold(setOf<ToolBarItem>()) { l, r -> l + r }
			.map { initializeToolBarControl(it) }

		private fun ActionSet.toolBarNodes(): List<ToolBarItem> {
			return actions.mapNotNull { action ->
				action.name?.let { name ->
					action.createToolNode?.let { create ->
						object : ToolBarItem {
							override val name = name
							override val keyTrigger = null
							override val action = action
							override fun newToolBarControl() = create(super.newToolBarControl())
						}
					}
				}
			}
		}
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