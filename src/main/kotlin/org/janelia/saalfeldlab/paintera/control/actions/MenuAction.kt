package org.janelia.saalfeldlab.paintera.control.actions

import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.MenuItem
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.paintera

open class MenuAction(val label: String) : Action<Event>(Event.ANY) {


	init {
		keysDown = null
		name = label
	}

	val menuItem by LazyForeignValue(::paintera) {
		MenuItem(label).also { item ->
			item.onAction = EventHandler { this(it) }
			item.isDisable = isValid(null)
		}
	}

}