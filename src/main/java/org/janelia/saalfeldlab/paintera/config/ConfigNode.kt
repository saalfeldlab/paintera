package org.janelia.saalfeldlab.paintera.config

import javafx.scene.Node
import org.scijava.plugin.SciJavaPlugin

interface ConfigNode<CONFIG> : SciJavaPlugin {

	fun getNode(): Node

	fun getTargetClass(): Class<CONFIG>

	fun bindBidirectionalTo(config: CONFIG)

	fun unbindBidrectionalTo(config: CONFIG)
}
