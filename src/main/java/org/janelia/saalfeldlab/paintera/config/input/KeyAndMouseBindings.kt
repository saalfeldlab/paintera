package org.janelia.saalfeldlab.paintera.config.input

import org.janelia.saalfeldlab.paintera.NamedKeyCombination
import org.janelia.saalfeldlab.paintera.NamedMouseCombination

class KeyAndMouseBindings(
		private val defaultKeyCombinations: NamedKeyCombination.CombinationMap,
		private val defaultMouseCombinations: NamedMouseCombination.CombinationMap) {

	constructor(): this(NamedMouseCombination.CombinationMap())

	constructor(defaultKeyCombinations: NamedKeyCombination.CombinationMap): this(
			defaultKeyCombinations,
			NamedMouseCombination.CombinationMap())

	constructor(defaultMouseCombinations: NamedMouseCombination.CombinationMap): this(
			NamedKeyCombination.CombinationMap(),
			defaultMouseCombinations)

	val keyCombinations = defaultKeyCombinations.deepCopy
	val mouseCombinations = defaultMouseCombinations.deepCopy

}
