package org.janelia.saalfeldlab.paintera

import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCombination

object BindingKeys {
    const val CYCLE_INTERPOLATION_MODES = "cycle interpolation modes"
    const val CYCLE_CURRENT_SOURCE_FORWARD = "cycle current source forward"
    const val CYCLE_CURRENT_SOURCE_BACKWARD = "cycle current source backward"
    const val TOGGLE_CURRENT_SOURCE_VISIBILITY = "toggle current soruce visibility"
    const val MAXIMIZE_VIEWER = "toggle maximize viewer"
    const val DEDICATED_VIEWER_WINDOW = "toggle dedicated viewer window"
    const val MAXIMIZE_VIEWER_AND_3D = "toggle maximize viewer and 3D"
    const val SHOW_OPEN_DATASET_MENU = "show open dataset menu"
    const val CREATE_NEW_LABEL_DATASET = "create new label dataset"
    const val SHOW_REPL_TABS = "open repl"
    const val TOGGLE_FULL_SCREEN = "toggle full screen"
    const val OPEN_DATA = "open data"
    const val SAVE = "save"
    const val SAVE_AS = "save as"
    const val TOGGLE_MENUBAR_VISIBILITY = "toggle menubar visibility"
    const val TOGGLE_MENUBAR_MODE = "toggle menubar mode"
    const val TOGGLE_STATUSBAR_VISIBILITY = "toggle statusbar visibility"
    const val TOGGLE_STATUSBAR_MODE = "toggle statusbar mode"
    const val OPEN_HELP = "open help"
    const val QUIT = "quit"
    const val TOGGLE_SIDE_BAR = "toggle side bar"
    const val FILL_CONNECTED_COMPONENTS = "fill connected components"
    const val THRESHOLDED = "thresholded"

    val NAMED_COMBINATIONS = with(BindingKeys) {
        NamedKeyCombination.CombinationMap(
            NamedKeyCombination(OPEN_DATA, KeyCode.O, KeyCombination.CONTROL_DOWN),
            NamedKeyCombination(SAVE, KeyCode.S, KeyCombination.CONTROL_DOWN),
            NamedKeyCombination(SAVE_AS, KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            NamedKeyCombination(TOGGLE_MENUBAR_VISIBILITY, KeyCode.F2),
            NamedKeyCombination(TOGGLE_MENUBAR_MODE, KeyCode.F2, KeyCombination.SHIFT_DOWN),
            NamedKeyCombination(TOGGLE_STATUSBAR_VISIBILITY, KeyCode.F3),
            NamedKeyCombination(TOGGLE_STATUSBAR_MODE, KeyCode.F3, KeyCombination.SHIFT_DOWN),
            NamedKeyCombination(OPEN_HELP, KeyCode.F1),
            NamedKeyCombination(QUIT, KeyCode.Q, KeyCombination.CONTROL_DOWN),
            NamedKeyCombination(TOGGLE_SIDE_BAR, KeyCode.P),
            NamedKeyCombination(CYCLE_CURRENT_SOURCE_FORWARD, KeyCode.TAB, KeyCombination.CONTROL_DOWN),
            NamedKeyCombination(CYCLE_CURRENT_SOURCE_BACKWARD, KeyCode.TAB, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            NamedKeyCombination(TOGGLE_CURRENT_SOURCE_VISIBILITY, KeyCode.V),
            NamedKeyCombination(CYCLE_INTERPOLATION_MODES, KeyCode.I),
            NamedKeyCombination(MAXIMIZE_VIEWER, KeyCode.M),
            NamedKeyCombination(MAXIMIZE_VIEWER_AND_3D, KeyCode.M, KeyCombination.SHIFT_DOWN),
            NamedKeyCombination(CREATE_NEW_LABEL_DATASET, KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            NamedKeyCombination(SHOW_REPL_TABS, KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            NamedKeyCombination(TOGGLE_FULL_SCREEN, KeyCode.F11),
        )
    }

    fun namedCombinationsCopy() = NAMED_COMBINATIONS.deepCopy
}
