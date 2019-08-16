package org.janelia.saalfeldlab.paintera

import org.apache.commons.lang.builder.ToStringBuilder
import org.apache.commons.lang.builder.ToStringStyle
import org.janelia.saalfeldlab.paintera.exception.PainteraException

class NamedAction<T>(
		val name: String,
		val action: T) {

	override fun equals(other: Any?): Boolean {
		if (other is NamedAction<*>)
			return other.name === name
		return false
	}

	override fun hashCode() = name.hashCode()

	override fun toString() = ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("name", name).toString()

	class ActionMap<T>(vararg actions: NamedAction<T>) {

		private val map = mutableMapOf<String, NamedAction<T>>()

		init {
			actions.forEach { this += it }
		}

		class ActionAlreadyInserted(val action: NamedAction<*>): PainteraException("Action with name ${action.name} already present but tried to insert: $action")

		@Throws(ActionAlreadyInserted::class)
		fun addAction(action: NamedAction<T>) {
			if (map.containsKey(action.name))
				throw ActionAlreadyInserted(action)
			map[action.name] = action
		}

		operator fun plusAssign(action: NamedAction<T>) = addAction(action)

		operator fun plus(action: NamedAction<T>) = this.also { it.plusAssign(action) }

		operator fun contains(actionIdentifier: String) = this.map.containsKey(actionIdentifier)

		operator fun contains(keyCombination: NamedKeyCombination) = contains(keyCombination.name)

		operator fun get(name: String) = map[name]
	}

}
