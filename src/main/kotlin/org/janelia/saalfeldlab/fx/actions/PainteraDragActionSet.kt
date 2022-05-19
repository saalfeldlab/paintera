package org.janelia.saalfeldlab.fx.actions

import javafx.event.Event
import org.janelia.saalfeldlab.paintera.control.actions.ActionType
import org.janelia.saalfeldlab.paintera.paintera

open class PainteraDragActionSet @JvmOverloads constructor(val actionType: ActionType?, name: String, filter: Boolean = true, apply: (DragActionSet.() -> Unit)? = null) : DragActionSet(name, paintera.keyTracker, filter, apply) {

    override fun <E : Event> preInvokeCheck(action: Action<E>, event: E): Boolean {
        val actionAllowed = actionType?.let { paintera.baseView.allowedActionsProperty().get().isAllowed(actionType) } ?: true
        return actionAllowed && super.preInvokeCheck(action, event)
    }
}
