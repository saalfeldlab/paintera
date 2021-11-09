package org.janelia.saalfeldlab.paintera.ui.menus

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.event.EventHandler
import javafx.scene.control.MenuItem
import org.janelia.saalfeldlab.fx.extensions.createObjectBinding
import org.janelia.saalfeldlab.paintera.NamedAction
import org.janelia.saalfeldlab.paintera.PainteraMainWindow.BindingKeys
import org.janelia.saalfeldlab.paintera.control.CurrentSourceVisibilityToggle
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.dialogs.ReadMeDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.ReplDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CreateDatasetHandler
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.intersecting.IntersectingSourceStateOpener
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.thresholded.ThresholdedRawSourceStateOpenerDialog

enum class NamedActionMenuItems(private val text: String, private val keys: String, private val icon: FontAwesomeIcon? = null, private val allowedAction : MenuActionType? = null) {
    SAVE("_Save", BindingKeys.SAVE, FontAwesomeIcon.SAVE, MenuActionType.SaveProject),
    SAVE_AS("Save _As", BindingKeys.SAVE_AS, FontAwesomeIcon.FLOPPY_ALT, MenuActionType.SaveProject),
    QUIT("_Quit", BindingKeys.QUIT, FontAwesomeIcon.SIGN_OUT),

    CYCLE_FORWARD("Cycle _Forward", BindingKeys.CYCLE_CURRENT_SOURCE_FORWARD, allowedAction = MenuActionType.ChangeActiveSource),
    CYCLE_BACKWARD("Cycle _Backward", BindingKeys.CYCLE_CURRENT_SOURCE_BACKWARD, allowedAction = MenuActionType.ChangeActiveSource),
    TOGGLE_VISIBILITY("Toggle _Visibility", BindingKeys.TOGGLE_CURRENT_SOURCE_VISIBILITY),
    NEW_LABEL_SOURCE("_Label Source (N5)", BindingKeys.CREATE_NEW_LABEL_DATASET, allowedAction = MenuActionType.AddSource),
    NEW_CONNECTED_COMPONENT_SOURCE("_Fill Connected Components", BindingKeys.FILL_CONNECTED_COMPONENTS),
    NEW_THRESHOLDED_SOURCE("_Thresholded", BindingKeys.THRESHOLDED),
    TOGGLE_MENU_BAR_VISIBILITY("Toggle _Visibility", BindingKeys.TOGGLE_MENUBAR_VISIBILITY),
    TOGGLE_MENU_BAR_MODE("Toggle _Mode", BindingKeys.TOGGLE_MENUBAR_MODE),
    TOGGLE_STATUS_BAR_VISIBILITY("Toggle _Visibility", BindingKeys.TOGGLE_STATUSBAR_VISIBILITY),
    TOGGLE_STATUS_BAR_MODE("Toggle _Mode", BindingKeys.TOGGLE_STATUSBAR_MODE),
    TOGGLE_SIDE_BAR_MENU_ITEM("Toggle _Visibility", BindingKeys.TOGGLE_SIDE_BAR),
    FULL_SCREEN_ITEM("Toggle _Fullscreen", BindingKeys.TOGGLE_FULL_SCREEN),
    REPL_ITEM("Show _REPL", BindingKeys.SHOW_REPL_TABS),
    SHOW_README("Show _Readme", BindingKeys.OPEN_HELP, FontAwesomeIcon.QUESTION)

    ;


    val menu: MenuItem by lazy { createMenuItem(this) }



    companion object {

        private val replDialog = ReplDialog(paintera.gateway.context, { paintera.pane.scene.window }, Pair("paintera", this))

        private val namedActions = with(BindingKeys) {
            with(paintera) {
                NamedAction.ActionMap(
                    NamedAction(SAVE) { saveOrSaveAs() },
                    NamedAction(SAVE_AS) { saveAs() },
                    NamedAction(TOGGLE_MENUBAR_VISIBILITY) { properties.menuBarConfig.toggleIsVisible() },
                    NamedAction(TOGGLE_MENUBAR_MODE) { properties.menuBarConfig.cycleModes() },
                    NamedAction(TOGGLE_STATUSBAR_VISIBILITY) { properties.statusBarConfig.toggleIsVisible() },
                    NamedAction(TOGGLE_STATUSBAR_MODE) { properties.statusBarConfig.cycleModes() },
                    NamedAction(TOGGLE_SIDE_BAR) { properties.sideBarConfig.toggleIsVisible() },
                    NamedAction(QUIT) { askAndQuit() },
                    NamedAction(CYCLE_CURRENT_SOURCE_FORWARD) { baseView.sourceInfo().incrementCurrentSourceIndex() },
                    NamedAction(CYCLE_CURRENT_SOURCE_BACKWARD) { baseView.sourceInfo().decrementCurrentSourceIndex() },
                    NamedAction(TOGGLE_CURRENT_SOURCE_VISIBILITY) { CurrentSourceVisibilityToggle(baseView.sourceInfo().currentState()).toggleIsVisible() },
                    NamedAction(CREATE_NEW_LABEL_DATASET) { CreateDatasetHandler.createAndAddNewLabelDataset(baseView) { projectDirectory.actualDirectory.absolutePath } },
                    NamedAction(SHOW_REPL_TABS) { replDialog.show() },
                    NamedAction(TOGGLE_FULL_SCREEN) { properties.windowProperties::isFullScreen.let { it.set(!it.get()) } },
                    NamedAction(OPEN_HELP) { ReadMeDialog.show() },
                    NamedAction(FILL_CONNECTED_COMPONENTS) { IntersectingSourceStateOpener.createAndAddVirtualIntersectionSource(baseView) { projectDirectory.actualDirectory.absolutePath } },
                    NamedAction(THRESHOLDED) { ThresholdedRawSourceStateOpenerDialog.createAndAddNewVirtualThresholdSource(baseView) { projectDirectory.actualDirectory.absolutePath } }
                )
            }
        }

        private val namedKeyCombindations by lazy { paintera.properties.keyAndMouseConfig.painteraConfig.keyCombinations }

        private fun createMenuItem(namedActionMenuItem: NamedActionMenuItems): MenuItem {
            return with(namedActionMenuItem) {
                namedActions[keys]?.let { namedAction ->
                    MenuItem(text).apply {
                        icon?.let { graphic = FontAwesome[it, 1.5] }
                        onAction = EventHandler { namedAction() }
                        namedKeyCombindations[keys]?.let { acceleratorProperty().bind(it.primaryCombinationProperty()) }
                        /* Set up the disabled binding*/
                        allowedAction?.let {
                            disableProperty().bind(allowedActionsProperty.createObjectBinding { !it.isAllowed(allowedAction) })
                        }
                    }
                } ?: error("No namedActions for $keys")
            }
        }
    }
}
