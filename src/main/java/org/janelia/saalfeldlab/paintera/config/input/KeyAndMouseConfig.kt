package org.janelia.saalfeldlab.paintera.config.input

import javafx.collections.FXCollections
import org.janelia.saalfeldlab.paintera.PainteraMainWindow
import org.janelia.saalfeldlab.paintera.state.SourceState

class KeyAndMouseConfig {

	val painteraConfig = KeyAndMouseBindings(PainteraMainWindow.namedCombinations)

	private val sourceSpecificConfigs = FXCollections.observableHashMap<Class<out SourceState<*, *>>, KeyAndMouseBindings>()

	private val sourceSpecificConfigsKeys = FXCollections.observableSet<Class<out SourceState<*, *>>>()

	val readOnlySourceSpecificConfigsKeys = FXCollections.unmodifiableObservableSet(sourceSpecificConfigsKeys)

	fun getConfigFor(source: SourceState<*, *>) = source::class.java
			.also { sourceSpecificConfigs.computeIfAbsent(it) { KeyAndMouseBindings() } }
			.also { sourceSpecificConfigsKeys.add(it) }

	fun getConfigFor(sourceType: Class<out SourceState<*, *>>) = sourceSpecificConfigs[sourceType]

	val bindingsAvailableForSourceTypes: Set<Class<out SourceState<*, *>>>
		get() = setOf(*sourceSpecificConfigs.keys.toTypedArray())

}
