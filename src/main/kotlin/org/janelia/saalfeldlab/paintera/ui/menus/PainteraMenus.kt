package org.janelia.saalfeldlab.paintera.ui.menus

import javafx.beans.binding.Bindings
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenu
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.actions.ActionMenu
import org.janelia.saalfeldlab.paintera.control.actions.navigation.GoToCoordinate
import org.janelia.saalfeldlab.paintera.control.actions.navigation.GoToLabel
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabel
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems.*
import org.janelia.saalfeldlab.util.PainteraCache
import org.kordamp.ikonli.fontawesome.FontAwesome
import org.kordamp.ikonli.javafx.FontIcon

private val currentSourceName by LazyForeignValue(::paintera) {
	MenuItem(null).apply {
		textProperty().bind(paintera.baseView.sourceInfo().currentState().let { Bindings.createStringBinding({ it.value?.nameProperty()?.value }, it) })
		visibleProperty().bind(textProperty().isNotNull)
		isMnemonicParsing = false
		isDisable = true
	}
}

private val currentSourceMenu by LazyForeignValue(::paintera) {
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

private val showVersion by LazyForeignValue(::paintera) { MenuItem("Show _Version...").apply { onAction = EventHandler { PainteraAlerts.versionDialog().show() } } }

private val recentProjects: ObservableList<String> = FXCollections.observableArrayList()

private val openRecentMenu by LazyForeignValue(::paintera) {
	MatchSelectionMenu(recentProjects, "Open _Recent", 400.0) {
		it?.let { recentProject -> Paintera.application.loadProject(recentProject) }
	}
}

private val fileMenu by LazyForeignValue(::paintera) {
	Menu("_File", null, NEW_PROJECT.menu, OPEN_PROJECT.menu, openRecentMenu, SAVE.menu, SAVE_AS.menu, QUIT.menu).also {
		it.setOnShowing {
			recentProjects.setAll(PainteraCache.RECENT_PROJECTS.readLines().reversed())
		}
	}
}
private val newSourceMenu by LazyForeignValue(::paintera) { Menu("_New", FontIcon(FontAwesome.PLUS), NEW_LABEL_SOURCE.menu, newVirtualSourceMenu).apply { addStyleClass(Style.ADD_ICON) } }
private val newVirtualSourceMenu by LazyForeignValue(::paintera) { Menu("_Virtual", null, NEW_CONNECTED_COMPONENT_SOURCE.menu, NEW_THRESHOLDED_SOURCE.menu) }
private val sourcesMenu by LazyForeignValue(::paintera) { Menu("_Sources", null, currentSourceMenu, OPEN_SOURCE.menu, EXPORT_SOURCE.menu, newSourceMenu) }
private val menuBarMenu by LazyForeignValue(::paintera) { Menu("_Menu Bar", null, TOGGLE_MENU_BAR_VISIBILITY.menu, TOGGLE_MENU_BAR_MODE.menu) }
private val statusBarMenu by LazyForeignValue(::paintera) { Menu("S_tatus Bar", null, TOGGLE_STATUS_BAR_VISIBILITY.menu, TOGGLE_STATUS_BAR_MODE.menu) }
private val sideBarMenu by LazyForeignValue(::paintera) { Menu("_Side Bar", null, TOGGLE_SIDE_BAR.menu) }
private val toolBarMenu by LazyForeignValue(::paintera) { Menu("_Tool Bar", null, TOGGLE_TOOL_BAR.menu, TOGGLE_TOOL_BAR_MODE.menu) }
private val viewer3DMenu by LazyForeignValue(::paintera) { Menu("_3D Viewer", null, RESET_3D_LOCATION.menu, CENTER_3D_LOCATION.menu, SAVE_3D_PNG.menu) }
private val viewMenu by LazyForeignValue(::paintera) {
	Menu(
		"_View",
		null,
		menuBarMenu,
		sideBarMenu,
		statusBarMenu,
		toolBarMenu,
		FULL_SCREEN.menu,
		SHOW_REPL.menu,
		RESET_VIEWER_POSITIONS.menu,
		viewer3DMenu
	)
}
private val actionMenuItems by LazyForeignValue(::paintera) {
	arrayOf(
		SmoothLabel.menuItem,
		ReplaceLabel.replaceMenu().menuItem,
		ReplaceLabel.deleteMenu().menuItem,
		GoToCoordinate.menuItem,
		GoToLabel.menuItem,
	)
}

private val actionMenu by LazyForeignValue(::paintera) { ActionMenu("_Actions", null, *actionMenuItems) }
private val helpMenu by LazyForeignValue(::paintera) { ActionMenu("_Help", null, SHOW_README.menu, SHOW_KEY_BINDINGS.menu, showVersion) }


val menuBar by LazyForeignValue(::paintera) {
	MenuBar(fileMenu, sourcesMenu, actionMenu, viewMenu, helpMenu).apply {
		widthProperty().subscribe { _ -> minWidth = prefWidth(-1.0) }
		padding = Insets.EMPTY
		visibleProperty().bind(paintera.properties.menuBarConfig.isVisibleProperty)
		managedProperty().bind(visibleProperty())
	}
}