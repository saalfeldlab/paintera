package org.janelia.saalfeldlab.paintera.config.input

import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination
import org.janelia.saalfeldlab.fx.actions.NamedMouseCombination

class KeyAndMouseBindings(
	val keyCombinations: NamedKeyCombination.CombinationMap,
	val mouseCombinations: NamedMouseCombination.CombinationMap
) {

	constructor() : this(NamedMouseCombination.CombinationMap())

	constructor(defaultKeyCombinations: NamedKeyCombination.CombinationMap) : this(
		defaultKeyCombinations,
		NamedMouseCombination.CombinationMap()
	)

	constructor(defaultMouseCombinations: NamedMouseCombination.CombinationMap) : this(
		NamedKeyCombination.CombinationMap(),
		defaultMouseCombinations
	)
}
