package org.janelia.saalfeldlab.paintera

import org.apache.commons.lang.builder.ToStringBuilder
import org.apache.commons.lang.builder.ToStringStyle
import org.janelia.saalfeldlab.paintera.exception.PainteraException

class NamedAction(
    val name: String,
    private val action: () -> Unit,
) {

    override fun equals(other: Any?): Boolean {
        if (other is NamedAction)
            return other.name === name
        return false
    }

    override fun hashCode() = name.hashCode()

    override fun toString() = ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("name", name).toString()

    operator fun invoke() = action()

    class ActionMap(vararg actions: NamedAction) {

        private val map = mutableMapOf<String, NamedAction>()

        init {
            actions.forEach { this += it }
        }

        class ActionAlreadyInserted(val action: NamedAction) :
            PainteraException("Action with name ${action.name} already present but tried to insert: $action")

        @Throws(ActionAlreadyInserted::class)
        fun addAction(action: NamedAction) {
            if (map.containsKey(action.name))
                throw ActionAlreadyInserted(action)
            map[action.name] = action
        }

        operator fun plusAssign(action: NamedAction) = addAction(action)

        operator fun plus(action: NamedAction) = this.also { it.plusAssign(action) }

        operator fun contains(actionIdentifier: String) = this.map.containsKey(actionIdentifier)

        operator fun contains(keyCombination: NamedKeyCombination) = contains(keyCombination.name)

        operator fun get(name: String) = map[name]
    }

}
