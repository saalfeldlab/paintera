package org.janelia.saalfeldlab.paintera.ui.menus

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.MenuItem
import javafx.stage.DirectoryChooser
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.FontIconPatched
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.PainteraMainWindow
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.CurrentSourceVisibilityToggle
import org.janelia.saalfeldlab.paintera.control.actions.ActionType
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType.*
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.dialogs.ExportSourceDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.KeyBindingsDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.ReadMeDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.ReplDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CreateDatasetHandler
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.OpenDialogMenuEntry
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.intersecting.IntersectingSourceStateOpener
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5.OpenSourceDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.thresholded.ThresholdedRawSourceStateOpenerDialog
import org.kordamp.ikonli.fontawesome.FontAwesome
import org.kordamp.ikonli.javafx.FontIcon
import java.util.function.Supplier
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys as PBK

enum class PainteraMenuItems(
	private val text: String,
	private val keys: String? = null,
	private val icon: FontAwesome? = null,
	private val requiredActionTypes: Array<ActionType> = emptyArray()
) {
	NEW_PROJECT("_New Project", requiredActionTypes = arrayOf(OpenProject)),
	OPEN_PROJECT("Open _Project...", icon = FontAwesome.FOLDER_OPEN, requiredActionTypes = arrayOf(OpenProject, LoadProject)),
	OPEN_SOURCE("_Open Source...", PBK.OPEN_SOURCE, FontAwesome.FOLDER_OPEN, arrayOf(AddSource)),
	EXPORT_SOURCE("_Export Source...", PBK.EXPORT_SOURCE, FontAwesome.FLOPPY_O, arrayOf(ExportSource)),
	SAVE("_Save", PBK.SAVE, FontAwesome.FLOPPY_O, arrayOf(SaveProject)),
	SAVE_AS("Save _As...", PBK.SAVE_AS, FontAwesome.FLOPPY_O, arrayOf(SaveProject)),
	QUIT("_Quit", PBK.QUIT, FontAwesome.SIGN_OUT),
	CYCLE_FORWARD("Cycle _Forward", PBK.CYCLE_CURRENT_SOURCE_FORWARD, requiredActionTypes = arrayOf(ChangeActiveSource)),
	CYCLE_BACKWARD("Cycle _Backward", PBK.CYCLE_CURRENT_SOURCE_BACKWARD, requiredActionTypes = arrayOf(ChangeActiveSource)),
	TOGGLE_VISIBILITY("Toggle _Visibility", PBK.TOGGLE_CURRENT_SOURCE_VISIBILITY),
	NEW_LABEL_SOURCE("_Label Source...", PBK.CREATE_NEW_LABEL_DATASET, requiredActionTypes = arrayOf(AddSource)),
	NEW_CONNECTED_COMPONENT_SOURCE("_Fill Connected Components...", PBK.FILL_CONNECTED_COMPONENTS, requiredActionTypes = arrayOf(CreateVirtualSource)),
	NEW_THRESHOLDED_SOURCE("_Threshold...", PBK.THRESHOLDED, requiredActionTypes = arrayOf(CreateVirtualSource)),
	TOGGLE_MENU_BAR_VISIBILITY("Toggle _Visibility", PBK.TOGGLE_MENUBAR_VISIBILITY, requiredActionTypes = arrayOf(ToggleMenuBarVisibility)),
	TOGGLE_MENU_BAR_MODE("Toggle _Mode", PBK.TOGGLE_MENUBAR_MODE, requiredActionTypes = arrayOf(ToggleMenuBarMode)),
	TOGGLE_STATUS_BAR_VISIBILITY("Toggle _Visibility", PBK.TOGGLE_STATUSBAR_VISIBILITY, requiredActionTypes = arrayOf(ToggleStatusBarVisibility)),
	TOGGLE_STATUS_BAR_MODE("Toggle _Mode", PBK.TOGGLE_STATUSBAR_MODE, requiredActionTypes = arrayOf(ToggleStatusBarMode)),
	TOGGLE_SIDE_BAR("Toggle _Visibility", PBK.TOGGLE_SIDE_BAR, requiredActionTypes = arrayOf(ToggleSidePanel)),
	TOGGLE_TOOL_BAR_MODE("Toggle _Mode", PBK.TOGGLE_TOOL_BAR_MODE, requiredActionTypes = arrayOf(ToggleToolBarMode)),
	TOGGLE_TOOL_BAR("Toggle _Visibility", PBK.TOGGLE_TOOL_BAR, requiredActionTypes = arrayOf(ToggleToolBarVisibility)),
	RESET_3D_LOCATION("_Reset 3D Location", PBK.RESET_3D_LOCATION, requiredActionTypes = arrayOf(OrthoslicesContextMenu)),
	CENTER_3D_LOCATION("_Center 3D Location", PBK.CENTER_3D_LOCATION, requiredActionTypes = arrayOf(OrthoslicesContextMenu)),
	SAVE_3D_PNG("Save 3D As _PNG...", PBK.SAVE_3D_PNG, requiredActionTypes = arrayOf(OrthoslicesContextMenu)),
	FULL_SCREEN("Toggle _Fullscreen", PBK.TOGGLE_FULL_SCREEN, requiredActionTypes = arrayOf(ResizeViewers, ResizePanel)),
	SHOW_REPL("Show _REPL...", PBK.SHOW_REPL_TABS),
	RESET_VIEWER_POSITIONS("Reset _Viewer Positions", PBK.RESET_VIEWER_POSITIONS, requiredActionTypes = arrayOf(ResizeViewers, ToggleMaximizeViewer, DetachViewer)),
	SHOW_README("Show _Readme...", PBK.OPEN_README, FontAwesome.QUESTION),
	SHOW_KEY_BINDINGS("Show _Key Bindings...", PBK.OPEN_KEY_BINDINGS, FontAwesome.KEYBOARD_O);

	val menu: MenuItem by LazyForeignValue({ paintera }) { createMenuItem(it, this) }

	companion object {

		private operator fun OpenDialogMenuEntry.invoke(pbv: PainteraBaseView, projectDir : Supplier<String>) {
			onAction().accept(pbv, projectDir)
		}

		private val replDialog = ReplDialog(paintera.gateway.context, "paintera" to this)

		private fun PainteraMainWindow.namedEventHandlers(): Map<PainteraMenuItems, EventHandler<ActionEvent>> {
			val getProjectDirectory = { projectDirectory.actualDirectory.absolutePath }
			return mapOf(
				NEW_PROJECT { Paintera.application.loadProject() },
				OPEN_PROJECT {
					DirectoryChooser().showDialog(paintera.pane.scene.window)?.let { newProject ->
						Paintera.application.loadProject(newProject.path)
					}
				},
				OPEN_SOURCE { OpenSourceDialog.menuEntry(baseView, getProjectDirectory) },
				EXPORT_SOURCE { ExportSourceDialog.askAndExport() },
				SAVE { saveOrSaveAs() },
				SAVE_AS { saveAs() },
				TOGGLE_MENU_BAR_VISIBILITY { properties.menuBarConfig.toggleIsVisible() },
				TOGGLE_MENU_BAR_MODE { properties.menuBarConfig.cycleModes() },
				TOGGLE_STATUS_BAR_VISIBILITY { properties.statusBarConfig.toggleIsVisible() },
				TOGGLE_STATUS_BAR_MODE { properties.statusBarConfig.cycleModes() },
				TOGGLE_SIDE_BAR { properties.sideBarConfig.toggleIsVisible() },
				TOGGLE_TOOL_BAR_MODE { properties.toolBarConfig.cycleModes() },
				TOGGLE_TOOL_BAR { properties.toolBarConfig.toggleIsVisible() },
				QUIT { doSaveAndQuit() },
				CYCLE_FORWARD { baseView.sourceInfo().incrementCurrentSourceIndex() },
				CYCLE_BACKWARD { baseView.sourceInfo().decrementCurrentSourceIndex() },
				TOGGLE_VISIBILITY { CurrentSourceVisibilityToggle(baseView.sourceInfo().currentState()).toggleIsVisible() },
				NEW_LABEL_SOURCE { CreateDatasetHandler.createAndAddNewLabelDataset(baseView, getProjectDirectory) },
				SHOW_REPL { replDialog.show() },
				FULL_SCREEN { properties.windowProperties::isFullScreen.let { it.set(!it.get()) } },
				SHOW_README { ReadMeDialog.showReadme() },
				SHOW_KEY_BINDINGS { KeyBindingsDialog.show() },
				NEW_CONNECTED_COMPONENT_SOURCE { IntersectingSourceStateOpener.createAndAddVirtualIntersectionSource(baseView, getProjectDirectory) },
				NEW_THRESHOLDED_SOURCE { ThresholdedRawSourceStateOpenerDialog.createAndAddNewVirtualThresholdSource(baseView, getProjectDirectory) },
				RESET_VIEWER_POSITIONS { baseView.orthogonalViews().resetPane() },
				RESET_3D_LOCATION { baseView.viewer3D().reset3DAffine() },
				CENTER_3D_LOCATION { baseView.viewer3D().center3DAffine() },
				SAVE_3D_PNG { baseView.viewer3D().saveAsPng() }
			)
		}

		private operator fun PainteraMenuItems.invoke(callback: () -> Unit): Pair<PainteraMenuItems, EventHandler<ActionEvent>> = this to EventHandler { callback() }

		private val namedKeyCombindations by lazy { ControlMode.keyAndMouseBindings.keyCombinations }

		private fun createMenuItem(paintera: PainteraMainWindow, namedEventHandlerMenuItem: PainteraMenuItems): MenuItem {
			return paintera.namedEventHandlers()[namedEventHandlerMenuItem]?.let { handler ->
				namedEventHandlerMenuItem.run {
					MenuItem(text).apply {
						icon?.let { addStyleClass(Style.fontAwesome(it)) }
						onAction = handler
						namedKeyCombindations[keys]?.let { acceleratorProperty().bind(it.primaryCombinationProperty) }
						/* Set up the disabled binding by permission type*/

						val allowedActionsProperty = paintera.baseView.allowedActionsProperty()
						val permissionDeniedBinding = { actionType: ActionType ->
							Bindings.createBooleanBinding({ !allowedActionsProperty.isAllowed(actionType) }, allowedActionsProperty)
						}
						var disabledByPermissionsBinding: BooleanBinding? = if (requiredActionTypes.isEmpty()) null else Bindings.createBooleanBinding({false})
						for (actionType in requiredActionTypes) {
							disabledByPermissionsBinding = disabledByPermissionsBinding!!.or(permissionDeniedBinding(actionType))
						}
						disabledByPermissionsBinding?.let {
							disableProperty().bind(it)
						}
					}
				}
			} ?: error("No namedActions for $namedEventHandlerMenuItem")
		}
	}
}
