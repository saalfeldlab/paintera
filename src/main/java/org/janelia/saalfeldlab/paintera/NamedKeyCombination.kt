package org.janelia.saalfeldlab.paintera

import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.scene.input.KeyCombination
import org.apache.commons.lang.builder.ToStringBuilder
import org.apache.commons.lang.builder.ToStringStyle
import org.janelia.saalfeldlab.paintera.exception.PainteraException

class NamedKeyCombination(
		val name: String,
		primaryCombination: KeyCombination,
		vararg additionalCombinations: KeyCombination) {

	private val _primaryCombination = SimpleObjectProperty(primaryCombination)

	private val additionalCombinations = FXCollections.observableArrayList(*additionalCombinations)

	var primaryCombination: KeyCombination
		get() = _primaryCombination.get()
		set(primaryCombination) = _primaryCombination.set(primaryCombination)

	fun primaryCombinationProperty() = _primaryCombination

	override fun equals(other: Any?): Boolean {
		if (other is NamedKeyCombination)
			return other.name === name
		return false
	}

	override fun toString() = ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
			.append("name", name)
			.append("primaryCombination", primaryCombination)
			.append("additionalCombinations", additionalCombinations)
			.toString()

	override fun hashCode() = name.hashCode()

	class CombinationMap(vararg combinations: NamedKeyCombination) {

		private val map = mutableMapOf<String, NamedKeyCombination>()

		init {
		    combinations.forEach { this += it }
		}

		class KeyCombinationAlreadyInserted(val keyCombination: NamedKeyCombination): PainteraException("Action with name ${keyCombination.name} already present but tried to insert: $keyCombination")

		@Throws(KeyCombinationAlreadyInserted::class)
		fun addCombination(keyCombination: NamedKeyCombination) {
			if (map.containsKey(keyCombination.name))
				throw KeyCombinationAlreadyInserted(keyCombination)
			map[keyCombination.name] = keyCombination
		}

		operator fun plusAssign(keyCombination: NamedKeyCombination) = addCombination(keyCombination)

		operator fun plus(keyCombination: NamedKeyCombination) = this.also { it.plusAssign(keyCombination) }

		operator fun contains(actionIdentifier: String) = this.map.containsKey(actionIdentifier)

		operator fun contains(keyCombination: NamedKeyCombination) = contains(keyCombination.name)

		operator fun get(name: String) = map[name]
	}

}