package org.janelia.saalfeldlab.paintera.control.modes

import javafx.scene.Node

/** Titled section of the mode settings pane, offered by a [ControlMode] */
interface ModeSetting {

	val title: String

	/** Node built once, that follows the active source of the mode */
	val node: Node
}
