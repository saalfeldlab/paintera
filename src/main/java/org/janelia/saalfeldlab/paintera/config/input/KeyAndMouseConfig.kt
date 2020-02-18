package org.janelia.saalfeldlab.paintera.config.input

import javafx.collections.FXCollections
import org.janelia.saalfeldlab.paintera.PainteraMainWindow
import org.janelia.saalfeldlab.paintera.control.Navigation
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class KeyAndMouseConfig {

	val painteraConfig = KeyAndMouseBindings(PainteraMainWindow.namedCombinations)

	val navigationConfig = KeyAndMouseBindings(Navigation.createNamedKeyCombinations())

	private val sourceSpecificConfigs = FXCollections.observableHashMap<Class<out SourceState<*, *>>, KeyAndMouseBindings>()

	private val sourceSpecificConfigsKeys = FXCollections.observableSet<Class<out SourceState<*, *>>>()

	val readOnlySourceSpecificConfigsKeys = FXCollections.unmodifiableObservableSet(sourceSpecificConfigsKeys)

	fun hasConfigFor(clazz: Class<out SourceState<*, *>>) = sourceSpecificConfigs.containsKey(clazz)

	@Synchronized
	fun getConfigFor(clazz: Class<out SourceState<*, *>>) = sourceSpecificConfigs[clazz]

	@Synchronized
	fun getConfigFor(source: SourceState<*, *>): KeyAndMouseBindings {
		LOG.debug("Getting config for {}", source)
		val sourceSpecificBindings = source.createKeyAndMouseBindings()
		val propertiesBindings = sourceSpecificConfigs.computeIfAbsent(source::class.java) {sourceSpecificBindings}
		sourceSpecificBindings.keyCombinations.keys.forEach { it.takeUnless { propertiesBindings.keyCombinations.keys.contains(it) }?.let { propertiesBindings.keyCombinations.addCombination(sourceSpecificBindings.keyCombinations[it]!!) } }
		sourceSpecificBindings.mouseCombinations.keys.forEach { it.takeUnless { propertiesBindings.mouseCombinations.keys.contains(it) }?.let { propertiesBindings.mouseCombinations.addCombination(sourceSpecificBindings.mouseCombinations[it]!!) } }
		return propertiesBindings
	}

	val bindingsAvailableForSourceTypes: Set<Class<out SourceState<*, *>>>
		get() = setOf(*sourceSpecificConfigs.keys.toTypedArray())

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
	}

}
