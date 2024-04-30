package org.janelia.saalfeldlab.paintera.ui.menus

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.MenuItem
import javafx.stage.DirectoryChooser
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraMainWindow
import org.janelia.saalfeldlab.paintera.control.CurrentSourceVisibilityToggle
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.dialogs.KeyBindingsDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.ReadMeDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.ReplDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CreateDatasetHandler
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.intersecting.IntersectingSourceStateOpener
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5.N5OpenSourceDialog.N5Opener
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.thresholded.ThresholdedRawSourceStateOpenerDialog
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys as PBK

enum class PainteraMenuItems(
	private val text: String,
	private val keys: String? = null,
	private val icon: FontAwesomeIcon? = null,
	private val allowedAction: MenuActionType? = null
) {
	NEW_PROJECT("_New Project", allowedAction = MenuActionType.OpenProject),
	OPEN_PROJECT("Open _Project", icon = FontAwesomeIcon.FOLDER_OPEN, allowedAction = MenuActionType.OpenProject),
	OPEN_SOURCE("_Open Source", PBK.OPEN_SOURCE, FontAwesomeIcon.FOLDER_OPEN, MenuActionType.AddSource),
	SAVE("_Save", PBK.SAVE, FontAwesomeIcon.SAVE, MenuActionType.SaveProject),
	SAVE_AS("Save _As", PBK.SAVE_AS, FontAwesomeIcon.FLOPPY_ALT, MenuActionType.SaveProject),
	QUIT("_Quit", PBK.QUIT, FontAwesomeIcon.SIGN_OUT),

	CYCLE_FORWARD("Cycle _Forward", PBK.CYCLE_CURRENT_SOURCE_FORWARD, allowedAction = MenuActionType.ChangeActiveSource),
	CYCLE_BACKWARD("Cycle _Backward", PBK.CYCLE_CURRENT_SOURCE_BACKWARD, allowedAction = MenuActionType.ChangeActiveSource),
	TOGGLE_VISIBILITY("Toggle _Visibility", PBK.TOGGLE_CURRENT_SOURCE_VISIBILITY),
	NEW_LABEL_SOURCE("_Label Source (N5)", PBK.CREATE_NEW_LABEL_DATASET, allowedAction = MenuActionType.AddSource),
	NEW_CONNECTED_COMPONENT_SOURCE("_Fill Connected Components", PBK.FILL_CONNECTED_COMPONENTS),
	NEW_THRESHOLDED_SOURCE("_Thresholded", PBK.THRESHOLDED),
	TOGGLE_MENU_BAR_VISIBILITY("Toggle _Visibility", PBK.TOGGLE_MENUBAR_VISIBILITY),
	TOGGLE_MENU_BAR_MODE("Toggle _Mode", PBK.TOGGLE_MENUBAR_MODE),
	TOGGLE_STATUS_BAR_VISIBILITY("Toggle _Visibility", PBK.TOGGLE_STATUSBAR_VISIBILITY),
	TOGGLE_STATUS_BAR_MODE("Toggle _Mode", PBK.TOGGLE_STATUSBAR_MODE),
	TOGGLE_SIDE_BAR_MENU_ITEM("Toggle _Visibility", PBK.TOGGLE_SIDE_BAR),
	TOGGLE_TOOL_BAR_MENU_ITEM("Toggle _Visibility", PBK.TOGGLE_TOOL_BAR),
	RESET_3D_LOCATION_MENU_ITEM("_Reset 3D Location", PBK.RESET_3D_LOCATION),
	CENTER_3D_LOCATION_MENU_ITEM("_Center 3D Location", PBK.CENTER_3D_LOCATION),
	SAVE_3D_PNG_MENU_ITEM("Save 3D As _PNG", PBK.SAVE_3D_PNG),
	FULL_SCREEN_ITEM("Toggle _Fullscreen", PBK.TOGGLE_FULL_SCREEN),
	REPL_ITEM("Show _REPL", PBK.SHOW_REPL_TABS),
	RESET_VIEWER_POSITIONS("Reset _Viewer Positions", PBK.RESET_VIEWER_POSITIONS),
	SHOW_README("Show _Readme", PBK.OPEN_README, FontAwesomeIcon.QUESTION),
	SHOW_KEY_BINDINGS("Show _Key Bindings", PBK.OPEN_KEY_BINDINGS, FontAwesomeIcon.KEYBOARD_ALT);

    val menu: MenuItem by LazyForeignValue({ paintera }) { createMenuItem(it, this) }

	companion object {

		private val replDialog = ReplDialog(paintera.gateway.context, { paintera.pane.scene.window }, "paintera" to this)

		//@formatter:off
		private fun PainteraMainWindow.namedEventHandlers():Map<PainteraMenuItems, EventHandler<ActionEvent>> {
			val getProjectDirectory = { projectDirectory.actualDirectory.absolutePath }
			return mapOf(
				NEW_PROJECT to EventHandler<ActionEvent> { Paintera.application.loadProject() },
                OPEN_PROJECT to EventHandler<ActionEvent> {
					DirectoryChooser().showDialog(paintera.pane.scene.window)?.let{ newProject ->
						Paintera.application.loadProject(newProject.path)
					}
				},
				OPEN_SOURCE to EventHandler<ActionEvent> { N5Opener().onAction().accept(baseView, getProjectDirectory) },
				SAVE to EventHandler<ActionEvent> { saveOrSaveAs() },
				SAVE_AS to EventHandler<ActionEvent> { saveAs() },
				TOGGLE_MENU_BAR_VISIBILITY to EventHandler<ActionEvent> { properties.menuBarConfig.toggleIsVisible() },
				TOGGLE_MENU_BAR_MODE to EventHandler<ActionEvent> { properties.menuBarConfig.cycleModes() },
				TOGGLE_STATUS_BAR_VISIBILITY to EventHandler<ActionEvent> { properties.statusBarConfig.toggleIsVisible() },
				TOGGLE_STATUS_BAR_MODE to EventHandler<ActionEvent> { properties.statusBarConfig.cycleModes() },
				TOGGLE_SIDE_BAR_MENU_ITEM to EventHandler<ActionEvent> { properties.sideBarConfig.toggleIsVisible() },
				TOGGLE_TOOL_BAR_MENU_ITEM to EventHandler<ActionEvent> { properties.toolBarConfig.toggleIsVisible() },
				QUIT to EventHandler<ActionEvent> { doSaveAndQuit() },
				CYCLE_FORWARD to EventHandler<ActionEvent> { baseView.sourceInfo().incrementCurrentSourceIndex() },
				CYCLE_BACKWARD to EventHandler<ActionEvent> { baseView.sourceInfo().decrementCurrentSourceIndex() },
				TOGGLE_VISIBILITY to EventHandler<ActionEvent> {
					CurrentSourceVisibilityToggle(
						baseView.sourceInfo().currentState()
					).toggleIsVisible()
				},
				NEW_LABEL_SOURCE to EventHandler<ActionEvent> { CreateDatasetHandler.createAndAddNewLabelDataset(baseView, getProjectDirectory) },
				REPL_ITEM to EventHandler<ActionEvent> { replDialog.show() },
				FULL_SCREEN_ITEM to EventHandler<ActionEvent> { properties.windowProperties::isFullScreen.let { it.set(!it.get()) } },
				SHOW_README to EventHandler<ActionEvent> { ReadMeDialog.showReadme() },
				SHOW_KEY_BINDINGS to EventHandler<ActionEvent> { KeyBindingsDialog.show() },
				NEW_CONNECTED_COMPONENT_SOURCE to EventHandler<ActionEvent> { IntersectingSourceStateOpener.createAndAddVirtualIntersectionSource(baseView, getProjectDirectory ) },
				NEW_THRESHOLDED_SOURCE to EventHandler<ActionEvent> { ThresholdedRawSourceStateOpenerDialog.createAndAddNewVirtualThresholdSource(baseView, getProjectDirectory ) },
				RESET_VIEWER_POSITIONS to EventHandler<ActionEvent> { baseView.orthogonalViews().resetPane() },
				RESET_3D_LOCATION_MENU_ITEM to EventHandler<ActionEvent> { baseView.viewer3D().reset3DAffine() },
				CENTER_3D_LOCATION_MENU_ITEM to EventHandler<ActionEvent> { baseView.viewer3D().center3DAffine() },
				SAVE_3D_PNG_MENU_ITEM to EventHandler<ActionEvent> { baseView.viewer3D().saveAsPng() }
			)
		}
		//@formatter:on

		private val namedKeyCombindations by lazy { ControlMode.keyAndMouseBindings.keyCombinations }

		private fun createMenuItem(paintera : PainteraMainWindow, namedEventHandlerMenuItem: PainteraMenuItems): MenuItem {
			return paintera.namedEventHandlers()[namedEventHandlerMenuItem]?.let { handler ->
				namedEventHandlerMenuItem.run {
					MenuItem(text).apply {
						icon?.let { graphic = FontAwesome[it, 1.5] }
						onAction = handler
						namedKeyCombindations[keys]?.let { acceleratorProperty().bind(it.primaryCombinationProperty) }
						/* Set up the disabled binding*/
						allowedAction?.let {
							disableProperty().bind(paintera.baseView.allowedActionsProperty().allowedActionBinding(allowedAction).not())
						}
					}
				}
			} ?: error("No namedActions for $namedEventHandlerMenuItem")
		}
	}
}
