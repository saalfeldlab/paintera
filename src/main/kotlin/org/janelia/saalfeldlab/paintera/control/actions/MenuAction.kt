package org.janelia.saalfeldlab.paintera.control.actions

import javafx.event.Event
import javafx.event.EventHandler
import javafx.event.EventType
import javafx.scene.Node
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.paintera

open class ActionMenu(text: String? = null, graphic: Node? = null, vararg items: MenuItem) : Menu(text, graphic, *items) {

	init {
		onShowing = EventHandler {
			for (item in items) {
				(item.userData as? Action<*>)?.apply {
					item.isDisable = !isValid(null)
				}
			}
		}
	}
}

open class MenuAction(val text: String, event: EventType<out Event> = Event.ANY) : Action<Event>(event) {


	init {
		keysDown = null
		name = text
	}

	val menuItem by LazyForeignValue(::paintera) {
		MenuItem(text).also { item ->
			item.onAction = EventHandler { this() }
			item.userData = this
		}
	}

}