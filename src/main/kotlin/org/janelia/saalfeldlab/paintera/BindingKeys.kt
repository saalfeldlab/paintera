package org.janelia.saalfeldlab.paintera

import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCode.*
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyCombination.*
import org.janelia.saalfeldlab.fx.actions.NamedKeyBinding
import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination

private fun KeyCode.asCombination() = KeyCodeCombination(this)
private fun Modifier.asCombination() = NamedKeyCombination.OnlyModifierKeyCombination(this)

private infix fun String.byKeyCombo(keyCode: KeyCode) = this byKeyCombo keyCode.asCombination()
private infix fun String.byKeyCombo(modifier: Modifier) = this byKeyCombo modifier.asCombination()
private infix fun String.byKeyCombo(combo: KeyCombination) = NamedKeyCombination(this, combo)

private operator fun ArrayList<Modifier>.plus(keyCode: KeyCode) = KeyCodeCombination(keyCode, *this.toTypedArray())
private operator fun ArrayList<Modifier>.plus(modifier: Modifier) = this.apply { add(modifier) }

private operator fun KeyCode.plus(modifiers: ArrayList<Modifier>) = KeyCodeCombination(this, *modifiers.toTypedArray())
private operator fun KeyCode.plus(modifier: Modifier) = KeyCodeCombination(this, modifier)

private operator fun Modifier.plus(keyCode: KeyCode) = KeyCodeCombination(keyCode, this)
private operator fun Modifier.plus(modifier: Modifier) = arrayListOf(this, modifier)


private operator fun Modifier.plus(modifiers: ArrayList<Modifier>) = modifiers.also { it.add(0, this) }

//@formatter:off

object PainteraBaseKeys {
    const val CYCLE_INTERPOLATION_MODES        = "cycle interpolation modes"
    const val CYCLE_CURRENT_SOURCE_FORWARD     = "cycle current source forward"
    const val CYCLE_CURRENT_SOURCE_BACKWARD    = "cycle current source backward"
	const val TOGGLE_CURRENT_SOURCE_VISIBILITY = "toggle current source visibility"
    const val MAXIMIZE_VIEWER                  = "toggle maximize viewer"
    const val DETACH_VIEWER_WINDOW             = "toggle detached viewer window"
    const val RESET_VIEWER_POSITIONS           = "reset viewer positions"
    const val MAXIMIZE_VIEWER_AND_3D           = "toggle maximize viewer and 3D"
    const val CREATE_NEW_LABEL_DATASET         = "create new label dataset"
    const val SHOW_REPL_TABS                   = "open repl"
    const val TOGGLE_FULL_SCREEN               = "toggle full screen"
    const val OPEN_SOURCE                      = "open source"
    const val EXPORT_SOURCE                    = "export source"
    const val SAVE                             = "save"
    const val SAVE_AS                          = "save as"
    const val TOGGLE_MENUBAR_VISIBILITY        = "toggle menubar visibility"
    const val TOGGLE_MENUBAR_MODE              = "toggle menubar mode"
    const val TOGGLE_STATUSBAR_VISIBILITY      = "toggle statusbar visibility"
    const val TOGGLE_STATUSBAR_MODE            = "toggle statusbar mode"
    const val OPEN_README                      = "open readme"
    const val OPEN_KEY_BINDINGS                = "open key bindings"
    const val QUIT                             = "quit"
    const val TOGGLE_SIDE_BAR                  = "toggle side bar"
    const val TOGGLE_TOOL_BAR                  = "toggle tool bar"
    const val FILL_CONNECTED_COMPONENTS        = "fill connected components"
    const val THRESHOLDED                      = "thresholded"
    const val RESET_3D_LOCATION                = "Reset 3D Location"
    const val CENTER_3D_LOCATION               = "Center 3D Location"
    const val SAVE_3D_PNG                      = "Save 3D As PNG"

    val NAMED_COMBINATIONS = NamedKeyCombination.CombinationMap(
        OPEN_SOURCE                                 byKeyCombo CONTROL_DOWN + O,
        EXPORT_SOURCE                               byKeyCombo CONTROL_DOWN + E,
        SAVE                                        byKeyCombo CONTROL_DOWN + S,
        SAVE_AS                                     byKeyCombo CONTROL_DOWN + SHIFT_DOWN + S,
        TOGGLE_MENUBAR_VISIBILITY                   byKeyCombo F2,
        TOGGLE_MENUBAR_MODE                         byKeyCombo SHIFT_DOWN + F2,
        TOGGLE_STATUSBAR_VISIBILITY                 byKeyCombo F3,
        TOGGLE_STATUSBAR_MODE                       byKeyCombo SHIFT_DOWN + F3,
        OPEN_README                                 byKeyCombo F1,
        OPEN_KEY_BINDINGS                           byKeyCombo F4,
        QUIT                                        byKeyCombo CONTROL_DOWN + Q,
        TOGGLE_SIDE_BAR                             byKeyCombo P,
        TOGGLE_TOOL_BAR                             byKeyCombo T,
        CYCLE_CURRENT_SOURCE_FORWARD                byKeyCombo CONTROL_DOWN + TAB,
        CYCLE_CURRENT_SOURCE_BACKWARD               byKeyCombo CONTROL_DOWN + SHIFT_DOWN + TAB,
        TOGGLE_CURRENT_SOURCE_VISIBILITY            byKeyCombo V,
        CYCLE_INTERPOLATION_MODES                   byKeyCombo I,
        MAXIMIZE_VIEWER                             byKeyCombo M,
        MAXIMIZE_VIEWER_AND_3D                      byKeyCombo SHIFT_DOWN + M,
        DETACH_VIEWER_WINDOW                        byKeyCombo SHIFT_DOWN + D,
        RESET_VIEWER_POSITIONS                      byKeyCombo ALT_DOWN + SHIFT_DOWN + D,
        CREATE_NEW_LABEL_DATASET                    byKeyCombo CONTROL_DOWN + SHIFT_DOWN + N,
        SHOW_REPL_TABS                              byKeyCombo SHORTCUT_DOWN + ALT_DOWN + T,
        TOGGLE_FULL_SCREEN                          byKeyCombo F11,
	)

	@JvmStatic
    fun namedCombinationsCopy() = NAMED_COMBINATIONS.deepCopy

}

private class LateInitNamedKeyCombination(keyCombination: KeyCombination, initName : String?) : NamedKeyCombination("LateInitNamedKeyCombo", keyCombination) {
    lateinit var lateName : String
    override val keyBindingName: String
        get() = lateName

    init {
        initName?.let {setName(it)}
    }

    fun setName(name: String) { if (!::lateName.isInitialized) lateName = name}
}

enum class LabelSourceStateKeys(lateInitNamedKeyCombo : LateInitNamedKeyCombination) : NamedKeyBinding by lateInitNamedKeyCombo {
    SELECT_ALL                                           ( CONTROL_DOWN + A),
    SELECT_ALL_IN_CURRENT_VIEW                           ( CONTROL_DOWN + SHIFT_DOWN + A),
    LOCK_SEGMENT                                         ( L),
    NEXT_ID                                              ( N),
    DELETE_ID                                             ( SHIFT_DOWN + BACK_SPACE),
    COMMIT_DIALOG                                        ( C + CONTROL_DOWN),
    MERGE_ALL_SELECTED                                   ( ENTER + CONTROL_DOWN),
    ARGB_STREAM__INCREMENT_SEED                          ( C),
    ARGB_STREAM__DECREMENT_SEED                          ( C + SHIFT_DOWN),
    REFRESH_MESHES                                       ( R),
    CANCEL                                               ( ESCAPE, "cancel tool / exit mode"),
    TOGGLE_NON_SELECTED_LABELS_VISIBILITY                ( V + SHIFT_DOWN, "toggle non-selected labels visibility"),
    SEGMENT_ANYTHING__TOGGLE_MODE                        ( A),
    SEGMENT_ANYTHING__RESET_PROMPT                       ( BACK_SPACE),
    SEGMENT_ANYTHING__ACCEPT_SEGMENTATION                ( ENTER),
    PAINT_BRUSH                                          ( SPACE),
    FILL_2D                                              ( F),
    FILL_3D                                              ( SHIFT_DOWN + F),
    CLEAR_CANVAS                                         ( CONTROL_DOWN + SHIFT_DOWN + C),
    INTERSECT_UNDERLYING_LABEL                           ( SHIFT_DOWN + R),
    SHAPE_INTERPOLATION__TOGGLE_MODE                     ( S),
    SHAPE_INTERPOLATION__TOGGLE_PREVIEW                  ( CONTROL_DOWN + P),
    SHAPE_INTERPOLATION__ACCEPT_INTERPOLATION            ( ENTER),
    SHAPE_INTERPOLATION__SELECT_FIRST_SLICE              ( SHIFT_DOWN + LEFT),
    SHAPE_INTERPOLATION__SELECT_LAST_SLICE               ( SHIFT_DOWN + RIGHT),
    SHAPE_INTERPOLATION__SELECT_PREVIOUS_SLICE           ( LEFT),
    SHAPE_INTERPOLATION__SELECT_NEXT_SLICE               ( RIGHT ),
    SHAPE_INTERPOLATION__REMOVE_SLICE_1                  ( DELETE, "delete current slice "),
    SHAPE_INTERPOLATION__REMOVE_SLICE_2                  ( BACK_SPACE, "delete current slice  "),
    SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_LEFT        ( OPEN_BRACKET, "shape interpolation: auto SAM: new slice left" ),
    SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICES_BISECT     ( QUOTE, "shape interpolation: auto SAM: new slice between closest slices" ),
    SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICES_BISECT_ALL ( SHIFT_DOWN + QUOTE, "shape interpolation: auto SAM: new slice between all slices" ),
    SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_RIGHT       ( CLOSE_BRACKET, "shape interpolation: auto SAM: new slice right"   ),
    SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_HERE        ( SHIFT_DOWN + A, "shape interpolation: auto SAM: new slice at current location"   ),
    ;


    private val formattedName = name.lowercase()
        .replace("__", ": ")
        .replace("_", " ")

    constructor(keys : KeyCombination, name : String? = null) : this(LateInitNamedKeyCombination(keys, name))
    constructor(key : KeyCode, name : String? = null) : this(LateInitNamedKeyCombination(key.asCombination(), name))
    constructor(key : Modifier, name : String? = null) : this(LateInitNamedKeyCombination(key.asCombination(), name))

    init {
        lateInitNamedKeyCombo.setName(formattedName)
    }

    companion object {
        fun namedCombinationsCopy() = NamedKeyCombination.CombinationMap(*entries.map { it.deepCopy }.toTypedArray())
    }
}

enum class RawSourceStateKeys(lateInitNamedKeyCombo : LateInitNamedKeyCombination) : NamedKeyBinding by lateInitNamedKeyCombo {
    RESET_MIN_MAX_INTENSITY_THRESHOLD                 ( SHIFT_DOWN + H, "Reset Min / Max Intensity Threshold"),
    AUTO_MIN_MAX_INTENSITY_THRESHOLD                  ( H, "Auto Min / Max Intensity Threshold"),
    ;


    private val formattedName = name.lowercase()
        .replace("__", ": ")
        .replace("_", " ")

    constructor(keys : KeyCombination, name : String? = null) : this(LateInitNamedKeyCombination(keys, name))
    constructor(key : KeyCode, name : String? = null) : this(LateInitNamedKeyCombination(key.asCombination(), name))
    constructor(key : Modifier, name : String? = null) : this(LateInitNamedKeyCombination(key.asCombination(), name))

    init {
        lateInitNamedKeyCombo.setName(formattedName)
    }

    companion object {
        fun namedCombinationsCopy() = NamedKeyCombination.CombinationMap(*entries.map { it.deepCopy }.toTypedArray())
    }
}

object NavigationKeys {
    const val BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD       = "translate along normal forward"
    const val BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST  = "translate along normal forward fast"
    const val BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW  = "translate along normal forward slow"
    const val BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD      = "translate along normal backward"
	const val BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST = "translate along normal backward fast"
	const val BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW = "translate along normal backward slow"
    const val BUTTON_ZOOM_OUT                             = "zoom out"
    const val BUTTON_ZOOM_OUT2                            = "zoom out (alternative)"
    const val BUTTON_ZOOM_IN                              = "zoom in"
    const val BUTTON_ZOOM_IN2                             = "zoom in (alternative)"
    const val SET_ROTATION_AXIS_X                         = "set rotation axis x"
    const val SET_ROTATION_AXIS_Y                         = "set rotation axis y"
    const val SET_ROTATION_AXIS_Z                         = "set rotation axis z"
    const val KEY_ROTATE_LEFT                             = "rotate left"
    const val KEY_ROTATE_RIGHT                            = "rotate right"
    const val REMOVE_ROTATION                             = "remove rotation"
    const val KEY_MODIFIER_FAST                           = "fast-modifier"
    const val KEY_MODIFIER_SLOW                           = "slow-modifier"

	private val namedComboMap = NamedKeyCombination.CombinationMap(
        BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD      byKeyCombo COMMA,
		BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST byKeyCombo COMMA + SHIFT_DOWN,
		BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW byKeyCombo COMMA + CONTROL_DOWN,
        BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD       byKeyCombo PERIOD,
        BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST  byKeyCombo PERIOD + SHIFT_DOWN,
        BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW  byKeyCombo PERIOD + CONTROL_DOWN,
        BUTTON_ZOOM_OUT                             byKeyCombo MINUS + SHIFT_ANY,
        BUTTON_ZOOM_OUT2                            byKeyCombo DOWN,
        BUTTON_ZOOM_IN                              byKeyCombo EQUALS + SHIFT_ANY,
        BUTTON_ZOOM_IN2                             byKeyCombo UP,
        SET_ROTATION_AXIS_X                         byKeyCombo X,
        SET_ROTATION_AXIS_Y                         byKeyCombo Y,
        SET_ROTATION_AXIS_Z                         byKeyCombo Z,
        KEY_ROTATE_LEFT                             byKeyCombo LEFT,
        KEY_ROTATE_RIGHT                            byKeyCombo RIGHT,
        KEY_MODIFIER_FAST                           byKeyCombo SHIFT_DOWN,
        KEY_MODIFIER_SLOW                           byKeyCombo CONTROL_DOWN,
        REMOVE_ROTATION                             byKeyCombo Z + SHIFT_DOWN
	)

	fun namedCombinationsCopy() = namedComboMap.deepCopy
}
//@formatter:on
