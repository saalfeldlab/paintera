package org.janelia.saalfeldlab.paintera

import javafx.event.EventType
import javafx.scene.input.MouseEvent
import org.apache.commons.lang.builder.ToStringBuilder
import org.apache.commons.lang.builder.ToStringStyle
import org.janelia.saalfeldlab.fx.event.MouseCombination
import org.janelia.saalfeldlab.paintera.exception.PainteraException

class NamedMouseCombination(
		val name: String,
		val combination: MouseCombination,
		vararg target: EventType<MouseEvent>) {
	val target = listOf(*target)

	override fun toString() = ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
			.append("name", name)
			.append("combination", combination)
			.append("target", target)
			.toString()

	val deepCopy: NamedMouseCombination
		get() = NamedMouseCombination(name, combination.deepCopy, *target.toTypedArray())



	class CombinationMap(vararg combinations: NamedMouseCombination) {

		private val map = mutableMapOf<String, NamedMouseCombination>()

		init {
			combinations.forEach { this += it }
		}

		class NamedMouseCombinationAlreadyInserted(val keyCombination: NamedMouseCombination):
				PainteraException("Action with name ${keyCombination.name} already present but tried to insert: $keyCombination")

		@Throws(NamedMouseCombinationAlreadyInserted::class)
		fun addCombination(keyCombination: NamedMouseCombination) {
			if (map.containsKey(keyCombination.name))
				throw NamedMouseCombinationAlreadyInserted(keyCombination)
			map[keyCombination.name] = keyCombination
		}

		operator fun plusAssign(keyCombination: NamedMouseCombination) = addCombination(keyCombination)

		operator fun plus(keyCombination: NamedMouseCombination) = this.also { it.plusAssign(keyCombination) }

		operator fun contains(actionIdentifier: String) = this.map.containsKey(actionIdentifier)

		operator fun contains(keyCombination: NamedMouseCombination) = contains(keyCombination.name)

		operator fun get(name: String) = map[name]

		val keys: Set<String>
			get() = map.keys

		val deepCopy: CombinationMap
			get() = map.values.map { it.deepCopy }.toTypedArray().let { CombinationMap(*it) }
	}
}
