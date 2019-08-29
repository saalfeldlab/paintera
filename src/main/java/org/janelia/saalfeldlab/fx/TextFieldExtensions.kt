package org.janelia.saalfeldlab.fx

import javafx.scene.control.TextField

class TextFieldExtensions {
	companion object {

		fun TextField.acceptOnly(regex: Regex) = this.acceptOnly { regex.matches(it) }

		fun TextField.acceptOnly(filter: (String) -> Boolean) = this.textProperty().addListener { _, old, new ->
			new?.let { it.takeIf { s -> filter(s) } ?: this.textProperty().set(old) }
		}
	}
}
