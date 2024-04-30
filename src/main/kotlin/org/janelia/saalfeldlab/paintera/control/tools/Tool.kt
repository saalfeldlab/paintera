package org.janelia.saalfeldlab.paintera.control.tools

import bdv.fx.viewer.ViewerPanelFX
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonBase
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.NamedKeyBinding
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.paintera

interface Tool {

	fun activate() {}
	fun deactivate() {}

	val statusProperty: StringProperty
	val actionSets: MutableList<ActionSet>
}

interface ToolBarItem {

	val graphic: () -> Node?
		get() = { null }

	val name: String
	val keyTrigger: NamedKeyBinding?
	val action: Action<*>?
		get() = null

	val toolBarButton: ButtonBase
		get() {
			val node = graphic()
			val button = action?.let { action ->
				var toggleGroup : ToggleGroup? = null
				node?.also { graphic ->
					toggleGroup = graphic.properties["TOGGLE_GROUP"] as? ToggleGroup
				}

				val btn = toggleGroup?.let { group ->
					ToggleButton(null, node).also { it.toggleGroup = group }
				} ?: Button(null, node)

				btn.apply {
					onAction = EventHandler {
						action(null)
					}
				}
			} ?: ToggleButton(null, node)

			return button.also { btn ->
				btn.id = name
				btn.graphic?.let {
					if ("ignore-disable" !in it.styleClass) {
						btn.disableProperty().bind(paintera.baseView.isDisabledProperty)
					}
				}
				btn.styleClass += "toolbar-button"
				btn.tooltip = Tooltip(
					keyTrigger?.let { trigger ->
						"$name: ${KeyTracker.keysToString(*trigger.keyCodes.toTypedArray())}"
					} ?: name
				)
			}
		}
}

const val REQUIRES_ACTIVE_VIEWER = "REQUIRES_ACTIVE_VIEWER"

abstract class ViewerTool(protected val mode: ToolMode? = null) : Tool, ToolBarItem {

	private val installedInto: MutableMap<Node, MutableList<ActionSet>> = mutableMapOf()

	override fun activate() {
		activeViewerProperty.bind(mode?.activeViewerProperty ?: paintera.baseView.lastFocusHolder)
	}

	override fun deactivate() {
		activeViewerAndTransforms?.viewer()?.let { removeFrom(it) }
		activeViewerProperty.unbind()
		activeViewerProperty.set(null)
	}

	override val statusProperty = SimpleStringProperty()

	val activeViewerProperty = SimpleObjectProperty<OrthogonalViews.ViewerAndTransforms?>()

	fun installInto(node: Node) {
		if (!installedInto.containsKey(node)) {
			LOG.debug { "installing $this" }
			installedInto.putIfAbsent(node, mutableListOf())
			actionSets.forEach {
				node.installActionSet(it)
				installedInto[node]?.add(it)
			}
		}
	}

	fun removeFrom(node: Node) {
		installedInto[node]?.let { actions ->
			LOG.debug { "removing $this" }
			actions.removeIf { actionSet ->
				node.removeActionSet(actionSet)
				true
			}
			if (actions.isEmpty()) installedInto -= node
		}
	}

	val activeViewerAndTransforms by activeViewerProperty.nullableVal()
	val activeViewer: ViewerPanelFX? by activeViewerProperty.createNullableValueBinding { it?.viewer() }.nullableVal()

	companion object {
		private val LOG = KotlinLogging.logger {  }
	}
}
