package org.janelia.saalfeldlab.paintera

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import org.apache.commons.lang.builder.ToStringBuilder
import org.apache.commons.lang.builder.ToStringStyle
import org.janelia.saalfeldlab.fx.extensions.getValue
import org.janelia.saalfeldlab.fx.extensions.setValue
import org.janelia.saalfeldlab.paintera.exception.PainteraException
import kotlin.collections.set

class NamedKeyCombination(val name: String, primaryCombination: KeyCombination) {

    constructor(name: String, keyCode: KeyCode, vararg modifiers: KeyCombination.Modifier) : this(name, KeyCodeCombination(keyCode, *modifiers))

    private val primaryCombinationProperty = SimpleObjectProperty(primaryCombination)
    var primaryCombination: KeyCombination by primaryCombinationProperty

    fun primaryCombinationProperty() = primaryCombinationProperty

    fun matches(event: KeyEvent) = primaryCombination.match(event)

    val deepCopy: NamedKeyCombination
        get() = NamedKeyCombination(name, primaryCombination)

    override fun equals(other: Any?): Boolean {
        if (other is NamedKeyCombination)
            return other.name === name
        return false
    }

    override fun toString() = ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("name", name)
        .append("primaryCombination", primaryCombination)
        .toString()

    override fun hashCode() = name.hashCode()

    class CombinationMap(vararg combinations: NamedKeyCombination) {

        private val map = mutableMapOf<String, NamedKeyCombination>()

        init {
            combinations.forEach { this += it }
        }

        class KeyCombinationAlreadyInserted(val keyCombination: NamedKeyCombination) :
            PainteraException("Action with name ${keyCombination.name} already present but tried to insert: $keyCombination")

        @Throws(KeyCombinationAlreadyInserted::class)
        fun addCombination(keyCombination: NamedKeyCombination) {
            if (map.containsKey(keyCombination.name))
                throw KeyCombinationAlreadyInserted(keyCombination)
            map[keyCombination.name] = keyCombination
        }

        fun matches(name: String, event: KeyEvent) = get(name)!!.matches(event)

        operator fun plusAssign(keyCombination: NamedKeyCombination) = addCombination(keyCombination)

        operator fun plus(keyCombination: NamedKeyCombination) = this.also { it.plusAssign(keyCombination) }

        operator fun contains(actionIdentifier: String) = this.map.containsKey(actionIdentifier)

        operator fun contains(keyCombination: NamedKeyCombination) = contains(keyCombination.name)

        operator fun get(name: String) = map[name]

        val keys: Set<String>
            get() = map.keys

        val deepCopy: CombinationMap
            get() = map.values.map { it.deepCopy }.toTypedArray().let { CombinationMap(*it) }
    }

}
