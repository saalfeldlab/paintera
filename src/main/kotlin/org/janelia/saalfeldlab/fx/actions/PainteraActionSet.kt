package org.janelia.saalfeldlab.fx.actions

import javafx.event.Event
import javafx.scene.Node
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.paintera.control.actions.ActionType
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.paintera
import org.slf4j.LoggerFactory
import java.util.function.Consumer


open class PainteraActionSet(name: String, val actionType: ActionType? = null, apply: (ActionSet.() -> Unit)? = null) : ActionSet(name, keyTracker, apply) {


    @JvmOverloads
    constructor(name: String, actionType: ActionType? = null, apply: Consumer<ActionSet>? = null) : this(name, actionType, { apply?.accept(this) })

    private fun actionTypeAllowed(): Boolean {
        return actionType?.let { allowedActionsProperty.get().isAllowed(it) } ?: true
    }

    private fun painteraIsDisabled(): Boolean {
        val isDisabled = paintera.baseView.isDisabledProperty.get()
        LOG.debug("Action Denied: Paintera is Disabled")
        return isDisabled
    }

    override fun <E : Event> preInvokeCheck(action: Action<E>, event: E): Boolean {
        return (action.triggerIfDisabled || !painteraIsDisabled()) && actionTypeAllowed() && super.preInvokeCheck(action, event)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
        private val allowedActionsProperty = paintera.baseView.allowedActionsProperty()
        private val keyTracker = paintera.keyTracker
    }
}

fun Node.installTool(tool: Tool) {
    tool.actionSets.forEach { installActionSet(it) }
}

fun Node.removeTool(tool: Tool) {
    tool.actionSets.forEach { removeActionSet(it) }
}
