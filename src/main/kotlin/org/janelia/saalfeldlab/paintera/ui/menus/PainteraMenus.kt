package org.janelia.saalfeldlab.paintera.ui.menus

import javafx.beans.binding.Bindings
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems.*

private val currentSourceName by lazy {
	MenuItem(null).apply {
		textProperty().bind(paintera.baseView.sourceInfo().currentState().let { Bindings.createStringBinding({ it.value?.nameProperty()?.value }, it) })
		visibleProperty().bind(textProperty().isNotNull)
		isMnemonicParsing = false
		isDisable = true
	}
}

private val currentSourceMenu by lazy {
	Menu(
		"_Current",
		null,
		currentSourceName,
		SeparatorMenuItem().apply { visibleProperty().bind(currentSourceName.visibleProperty()) },
		CYCLE_FORWARD.menu,
		CYCLE_BACKWARD.menu,
		TOGGLE_VISIBILITY.menu
	)
}

private val showVersion by lazy { MenuItem("Show _Version").apply { onAction = EventHandler { PainteraAlerts.versionDialog().show() } } }

private val fileMenu by lazy { Menu("_File", null, OPEN_SOURCE.menu, SAVE.menu, SAVE_AS.menu, QUIT.menu) }
private val newSourceMenu by lazy { Menu("_New", null, NEW_LABEL_SOURCE.menu) }
private val newVirtualSourceMenu by lazy { Menu("_Virtual", null, NEW_CONNECTED_COMPONENT_SOURCE.menu, NEW_THRESHOLDED_SOURCE.menu) }
private val sourcesMenu by lazy { Menu("_Sources", null, currentSourceMenu, newSourceMenu, newVirtualSourceMenu) }
private val menuBarMenu by lazy { Menu("_Menu Bar", null, TOGGLE_MENU_BAR_VISIBILITY.menu, TOGGLE_MENU_BAR_MODE.menu) }
private val statusBarMenu by lazy { Menu("S_tatus Bar", null, TOGGLE_STATUS_BAR_VISIBILITY.menu, TOGGLE_STATUS_BAR_MODE.menu) }
private val sideBarMenu by lazy { Menu("_Side Bar", null, TOGGLE_SIDE_BAR_MENU_ITEM.menu) }
private val toolBarMenu by lazy { Menu("_Tool Bar", null, TOGGLE_TOOL_BAR_MENU_ITEM.menu) }
private val viewer3DMenu by lazy { Menu("_3D Viewer", null, RESET_3D_LOCATION_MENU_ITEM.menu, CENTER_3D_LOCATION_MENU_ITEM.menu, SAVE_3D_PNG_MENU_ITEM.menu) }
private val viewMenu by lazy {
	Menu(
		"_View",
		null,
		menuBarMenu,
		sideBarMenu,
		statusBarMenu,
		toolBarMenu,
		FULL_SCREEN_ITEM.menu,
		REPL_ITEM.menu,
		RESET_VIEWER_POSITIONS.menu,
		viewer3DMenu
	)
}
//TODO Caleb: Finish WIP SmoothAction
//private val actionMenu by lazy { Menu("_Actions", null, SmoothAction.menuItem) }

private val helpMenu by lazy { Menu("_Help", null, SHOW_README.menu, SHOW_KEY_BINDINGS.menu, showVersion) }

val MENU_BAR by lazy {
	MenuBar(fileMenu, sourcesMenu, viewMenu, helpMenu).apply {
		padding = Insets.EMPTY
		visibleProperty().bind(paintera.properties.menuBarConfig.isVisibleProperty)
		managedProperty().bind(visibleProperty())
	}
}
