package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableObjectValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.control.ButtonBase
import javafx.scene.control.Toggle
import javafx.scene.control.ToggleGroup
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.ToolBarItem
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.control.tools.toolBarItemsForActions
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.slf4j.LoggerFactory

interface ControlMode {

    fun enter() {}

    fun exit() {}

    val allowedActions: AllowedActions
    val statusProperty: StringProperty

    companion object {
        internal val keyAndMouseBindings = KeyAndMouseBindings(PainteraBaseKeys.namedCombinationsCopy())
    }

}

interface SourceMode : ControlMode {
    val activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>
    val activeViewerProperty: SimpleObjectProperty<ViewerAndTransforms?>
}

interface ToolMode : SourceMode {

    val tools: ObservableList<Tool>
    val modeActions: List<ActionSet>

    val defaultTool: Tool?
        get() = NavigationTool

    var activeToolProperty: ObjectProperty<Tool?>
    var activeTool: Tool?

    override fun enter() {
        super.enter()
        /* Keep deafult tool selected if nothing else */
        activeToolProperty.addListener { _, _, new ->
            if (new == null && defaultTool != null) switchTool(defaultTool)
        }
    }

    fun switchTool(tool: Tool?) {
        if (activeTool == tool)
            return
        LOG.debug("Switch from $activeTool to $tool")
        (activeTool as? ViewerTool)?.apply {
            activeViewer?.let { removeFrom(it) }
        }
        activeTool?.deactivate()

        tool?.activate()
        activeTool = tool
    }

    fun createToolBar(): GridPane {
        return GridPane().apply {
            val toolToggleBarGroup = ToggleGroup()

            /* When the selected toggle changes, switch to that tool (if we aren't already) or default if unselected only */
            toolToggleBarGroup.selectedToggleProperty().addListener { _, _, selected ->
                selected?.let {
                    val tool = it.userData as Tool
                    if (activeTool != tool) {
                        switchTool(tool)
                    }
                } ?: switchTool(defaultTool)
            }
            val toolButtons = tools.filter { it is ToolBarItem }.map { tool ->
                (tool as ToolBarItem)
                tool.toolBarButton.apply {
                    (this as? Toggle)?.apply {
                        toggleGroup = toolToggleBarGroup
                    }
                    userData = tool
                    isFocusTraversable = false
                }
            }

            val toolbox = HBox().apply {
                alignment = Pos.CENTER_RIGHT
                children += toolButtons
            }

            fun List<ActionSet>.actionButtonsFromActionSets(): List<ButtonBase> = map { it.toolBarItemsForActions().toSet() }
                .filter { it.isNotEmpty() }
                .fold(setOf<ToolBarItem>()) { l, r -> l + r }
                .map { it.toolBarButton }


            val actionbox = HBox().apply { alignment = Pos.CENTER_RIGHT }


            val triggerOnActiveToolChange = { newTool: Tool ->

                toolToggleBarGroup.toggles.firstOrNull { it.userData == newTool }?.also { toggleForTool ->
                    toolToggleBarGroup.selectToggle(toggleForTool)
                }
                val toolActionButtons = newTool.actionSets.actionButtonsFromActionSets()
                actionbox.children.addAll(toolActionButtons)
            }

            /* The first time, we haven't triggered yet, so ensure it does */
            activeTool?.let { triggerOnActiveToolChange(it) }

            /* listen for changes to the activeToolProperty*/
            activeToolProperty.addListener { _, old, new ->
                old?.let { actionbox.children.clear() }
                new?.let { newTool -> triggerOnActiveToolChange(newTool) }
            }

            val modebox = HBox().apply { alignment = Pos.CENTER_RIGHT }
            val modeActionButtons = modeActions.actionButtonsFromActionSets()
            modebox.children += modeActionButtons


            addColumn(2, toolbox)
            addColumn(1, actionbox)
            addColumn(0, modebox)
        }

    }

    /**
     * Prompt the user to select a viewer prior to executing [afterViewerIsSelected].
     * User can abandon the selection with [KeyCode.ESCAPE].
     *
     * @param afterViewerIsSelected will be executed if a viewer is clicked, and [KeyCode.ESCAPE] is not pressed
     */
    fun selectViewerBefore(afterViewerIsSelected: () -> Unit) {
        /* temporarily revoke permissions, so no actions are performed until we select a viewer  */
        paintera.baseView.allowedActionsProperty().suspendPermisssions()
        this.statusProperty.set("Select a Viewer...")


        val cleanup = SimpleBooleanProperty(false)

        paintera.baseView.orthogonalViews().views().forEach { view ->
            with(view) {
                /* defined later, but declared here for usage inside filters */
                lateinit var resetFilterAndPermissions: () -> Unit

                /* store the prev cursor, change to CROSSHAIR  */
                val prevCursor = cursor

                /* select the viewer, and triger the callback */
                val selectViewEvent = EventHandler<MouseEvent> {
                    it.consume()
                    paintera.baseView.currentFocusHolder.get()?.also {
                        /* trigger callback */
                        afterViewerIsSelected()
                        /* then indicate cleanup  */
                        cleanup.set(true)
                    }
                }

                /* In case ESC is pressed to cancel the selection, switch to the default tool  */
                val escapeFilter = EventHandler<KeyEvent> {
                    if (it.code == KeyCode.ESCAPE) {
                        cleanup.set(true)
                        switchTool(defaultTool)
                    }
                }

                resetFilterAndPermissions = {
                    removeEventFilter(MOUSE_CLICKED, selectViewEvent)
                    removeEventFilter(KEY_PRESSED, escapeFilter)
                    paintera.baseView.allowedActionsProperty().restorePermisssions()
                    cursor = prevCursor
                }

                cleanup.addListener { _, _, cleanup ->
                    if (cleanup) resetFilterAndPermissions()
                }

                /* add filters, and update the cursor to indicate selection */
                cursor = Cursor.CROSSHAIR


                /* listen for the selection; block all mouse click events temporarily, so no other filters are called  */
                addEventFilter(MOUSE_CLICKED, selectViewEvent)

                /* listen for ESC if we wish to cancel*/
                addEventFilter(KEY_PRESSED, escapeFilter)
            }

        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ToolMode::class.java.simpleName)
    }
}

abstract class AbstractSourceMode : SourceMode {
    final override val activeSourceStateProperty = SimpleObjectProperty<SourceState<*, *>?>()
    final override val activeViewerProperty = SimpleObjectProperty<ViewerAndTransforms?>()

    private val currentStateObservable: ObservableObjectValue<SourceState<*, *>?> =
        paintera.baseView.sourceInfo().currentState() as ObservableObjectValue<SourceState<*, *>?>
    private val currentViewerObservable = paintera.baseView.currentFocusHolder

    private val keyBindingsProperty = activeSourceStateProperty.createNullableValueBinding {
        it?.let { paintera.baseView.keyAndMouseBindings.getConfigFor(it).keyCombinations }
    }
    protected val keyBindings by keyBindingsProperty.nullableVal()

    /* This will add and remove the state specific actions from:
     *  - the correct viewers when the active viewer changes
     *  - the global action set when the active source changes */
    private val sourceSpecificActionListener = ChangeListener<SourceState<*, *>?> { _, old, new ->
        val viewer = activeViewerProperty.get()?.viewer()
        old?.apply {
            viewer?.let { viewer -> viewerActionSets.forEach { viewer.removeActionSet(it) } }
            paintera.defaultHandlers.globalActionHandlers.removeAll(globalActionSets)
        }
        new?.apply {
            viewer?.let { viewer -> viewerActionSets.forEach { viewer.installActionSet(it) } }
            paintera.defaultHandlers.globalActionHandlers.addAll(globalActionSets)
        }
    }

    /* This will add and remove the state specific actions from the correct viewers when the active viewer changes */
    private val sourceSpecificViewerActionListener = ChangeListener<ViewerAndTransforms?> { _, old, new ->
        activeSourceStateProperty.get()?.let { state ->
            state.viewerActionSets.forEach { actionSet ->
                old?.viewer()?.removeActionSet(actionSet)
                new?.viewer()?.installActionSet(actionSet)
            }
        }
    }

    override fun enter() {
        activeSourceStateProperty.addListener(sourceSpecificActionListener)
        activeSourceStateProperty.bind(currentStateObservable)
        activeViewerProperty.addListener(sourceSpecificViewerActionListener)
        activeViewerProperty.bind(currentViewerObservable)
    }

    override fun exit() {
        activeSourceStateProperty.unbind()
        activeViewerProperty.unbind()
        activeSourceStateProperty.set(null)
        activeViewerProperty.set(null)
        activeViewerProperty.removeListener(sourceSpecificViewerActionListener)
    }
}


/**
 * Abstract tool mode
 *
 * @constructor Create empty Abstract tool mode
 */
abstract class AbstractToolMode : AbstractSourceMode(), ToolMode {

    override val tools: ObservableList<Tool> = FXCollections.observableArrayList()
    final override var activeToolProperty: ObjectProperty<Tool?> = SimpleObjectProperty<Tool?>()
    final override var activeTool by activeToolProperty.nullable()

    override val statusProperty: StringProperty = SimpleStringProperty().apply {
        activeToolProperty.addListener { _, _, new ->
            new?.let {
                bind(it.statusProperty)
            } ?: unbind()
        }
    }

    private val activeViewerToolHandler = ChangeListener<ViewerAndTransforms?> { _, old, new ->
        (activeTool as? ViewerTool)?.let { tool ->
            old?.viewer()?.let { tool.removeFrom(it) }
            new?.viewer()?.let { tool.installInto(it) }
        }
    }

    private val activeToolHandler = ChangeListener<Tool?> { _, old, new ->
        activeViewerProperty.get()?.let { viewer ->
            (old as? ViewerTool)?.removeFrom(viewer.viewer())
            (new as? ViewerTool)?.installInto(viewer.viewer())
        }
    }

    protected fun escapeToDefault() = painteraActionSet("escape to default") {
        KEY_PRESSED(KeyCode.ESCAPE) {
            /* Don't change to default if we are default */
            verify("Default Tool Is Not Already Active") { activeTool != null && activeTool != defaultTool }
            onAction {
                switchTool(defaultTool)
            }
        }
    }

    override fun enter() {
        super<AbstractSourceMode>.enter()
        activeViewerProperty.addListener(activeViewerToolHandler)
        activeToolProperty.addListener(activeToolHandler)
        switchTool(activeTool ?: defaultTool)
    }

    override fun exit() {
        switchTool(null)
        activeToolProperty.removeListener(activeToolHandler)
        activeViewerProperty.removeListener(activeViewerToolHandler)
        super<AbstractSourceMode>.exit()
    }
}


