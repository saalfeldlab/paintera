package org.janelia.saalfeldlab.paintera.control.tools

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonBase
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.paintera
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

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
    val keyTrigger: List<KeyCode>?
    val action: Action<*>?
        get() = null

    val toolBarButton: ButtonBase
        get() {
            val button = action?.let { action ->
                Button(null, graphic()).also { btn ->
                    btn.onAction = EventHandler {
                        action(null)
                    }
                }
            } ?: ToggleButton(null, graphic())

            return button.also {
                it.styleClass += "toolbar-button"
                it.tooltip = Tooltip(
                    keyTrigger?.let { keys ->
                        "$name: ${KeyTracker.keysToString(*keys.toTypedArray())}"
                    } ?: name
                )
            }
        }
}

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
            LOG.debug("installing $this")
            installedInto.putIfAbsent(node, mutableListOf())
            actionSets.forEach {
                node.installActionSet(it)
                installedInto[node]?.add(it)
            }
        }
    }

    fun removeFrom(node: Node) {
        installedInto[node]?.let { actions ->
            LOG.debug("removing $this")
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
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}

fun ActionSet.toolBarItemsForActions(): List<ToolBarItem> {
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
