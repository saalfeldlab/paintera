package org.janelia.saalfeldlab.paintera.config.input

import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination
import org.janelia.saalfeldlab.fx.actions.NamedMouseCombination

class KeyAndMouseBindings @JvmOverloads constructor(
	val keyCombinations: NamedKeyCombination.CombinationMap = NamedKeyCombination.CombinationMap(),
	val mouseCombinations: NamedMouseCombination.CombinationMap = NamedMouseCombination.CombinationMap()
)