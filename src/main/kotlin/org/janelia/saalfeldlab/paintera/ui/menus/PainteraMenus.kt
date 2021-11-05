package org.janelia.saalfeldlab.paintera.ui.menus

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.Bindings
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import org.janelia.saalfeldlab.fx.extensions.createObjectBinding
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraMainWindow
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActionsProperty
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.CYCLE_BACKWARD
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.CYCLE_FORWARD
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.FULL_SCREEN_ITEM
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.NEW_CONNECTED_COMPONENT_SOURCE
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.NEW_LABEL_SOURCE
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.NEW_THRESHOLDED_SOURCE
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.QUIT
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.REPL_ITEM
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.SAVE
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.SAVE_AS
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.SHOW_README
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.TOGGLE_MENU_BAR_MODE
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.TOGGLE_MENU_BAR_VISIBILITY
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.TOGGLE_SIDE_BAR_MENU_ITEM
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.TOGGLE_STATUS_BAR_MODE
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.TOGGLE_STATUS_BAR_VISIBILITY
import org.janelia.saalfeldlab.paintera.ui.menus.NamedActionMenuItems.TOGGLE_VISIBILITY
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val LOG: Logger = LoggerFactory.getLogger("PainteraMenus")
//internal lateinit var paintera: PainteraMainWindow
internal val namedKeyCombinations by lazy { paintera.properties.keyAndMouseConfig.painteraConfig.keyCombinations }
internal val allowedActionsProperty: AllowedActionsProperty by lazy { paintera.baseView.allowedActionsProperty() }

private val openMenu by lazy {
    paintera.gateway.openDialogMenu().getMenu(
        "_Open",
        paintera.baseView,
        { paintera.projectDirectory.actualDirectory.absolutePath },
        {
            LOG.error("Unable to open data", it)
            Exceptions.exceptionAlert(Paintera.Constants.NAME, "Unable to open data", it, owner = paintera.pane.scene?.window).show()
        }).get().apply {
        graphic = FontAwesome[FontAwesomeIcon.FOLDER_OPEN_ALT, 1.5]
        acceleratorProperty().bind(namedKeyCombinations[PainteraMainWindow.BindingKeys.OPEN_DATA]!!.primaryCombinationProperty())
        disableProperty().bind(allowedActionsProperty.createObjectBinding { !it.isAllowed(MenuActionType.AddSource) })
    }
}

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

private val fileMenu by lazy { Menu("_File", null, openMenu, SAVE.menu, SAVE_AS.menu, QUIT.menu) }
private val newSourceMenu by lazy { Menu("_New", null, NEW_LABEL_SOURCE.menu) }
private val newVirtualSourceMenu by lazy { Menu("_Virtual", null, NEW_CONNECTED_COMPONENT_SOURCE.menu, NEW_THRESHOLDED_SOURCE.menu) }
private val sourcesMenu by lazy { Menu("_Sources", null, currentSourceMenu, newSourceMenu, newVirtualSourceMenu) }
private val menuBarMenu by lazy { Menu("_Menu Bar", null, TOGGLE_MENU_BAR_VISIBILITY.menu, TOGGLE_MENU_BAR_MODE.menu) }
private val statusBarMenu by lazy { Menu("S_tatus Bar", null, TOGGLE_STATUS_BAR_VISIBILITY.menu, TOGGLE_STATUS_BAR_MODE.menu) }
private val sideBarMenu by lazy { Menu("_Side Bar", null, TOGGLE_SIDE_BAR_MENU_ITEM.menu) }
private val viewMenu by lazy { Menu("_View", null, menuBarMenu, sideBarMenu, statusBarMenu, FULL_SCREEN_ITEM.menu, REPL_ITEM.menu) }
private val helpMenu by lazy { Menu("_Help", null, SHOW_README.menu, showVersion) }

val MENU_BAR by lazy {
    MenuBar(fileMenu, sourcesMenu, viewMenu, helpMenu).apply {
        padding = Insets.EMPTY
        visibleProperty().bind(paintera.properties.menuBarConfig.isVisibleProperty)
        managedProperty().bind(visibleProperty())
    }
}
