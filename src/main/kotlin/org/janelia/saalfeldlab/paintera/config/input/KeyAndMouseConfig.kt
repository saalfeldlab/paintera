package org.janelia.saalfeldlab.paintera.config.input

import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import javafx.collections.ObservableSet
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class KeyAndMouseConfig {

    private val sourceSpecificConfigs: ObservableMap<Class<out SourceState<*, *>>, KeyAndMouseBindings> = FXCollections.observableHashMap()

    private val sourceSpecificConfigsKeys: ObservableSet<Class<out SourceState<*, *>>> = FXCollections.observableSet()

    val readOnlySourceSpecificConfigsKeys: ObservableSet<Class<out SourceState<*, *>>> = FXCollections.unmodifiableObservableSet(sourceSpecificConfigsKeys)

    fun hasConfigFor(clazz: Class<out SourceState<*, *>>) = sourceSpecificConfigs.containsKey(clazz)

    @Synchronized
    fun getConfigFor(clazz: Class<out SourceState<*, *>>) = sourceSpecificConfigs[clazz]

    @Synchronized
    fun getConfigFor(source: SourceState<*, *>): KeyAndMouseBindings {
        LOG.debug("Getting config for {}", source)
        val sourceSpecificBindings = source.createKeyAndMouseBindings()
        val propertiesBindings = sourceSpecificConfigs.computeIfAbsent(source::class.java) { sourceSpecificBindings }

        val propertiesKeyCombos = propertiesBindings.keyCombinations
        sourceSpecificBindings.keyCombinations.forEach { (comboName, keyCombo) ->
            if (comboName !in propertiesKeyCombos) {
                propertiesBindings.keyCombinations += keyCombo
            }
        }

        sourceSpecificBindings.mouseCombinations.forEach { (comboName, mouseCombo) ->
            val propertiesMouseCombos = propertiesBindings.mouseCombinations
            if (comboName !in propertiesMouseCombos) {
                propertiesMouseCombos.addCombination(mouseCombo)
            }
        }
        return propertiesBindings
    }

    val bindingsAvailableForSourceTypes: Set<Class<out SourceState<*, *>>>
        get() = setOf(*sourceSpecificConfigs.keys.toTypedArray())

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

}
