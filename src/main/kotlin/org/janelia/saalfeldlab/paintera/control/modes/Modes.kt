package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableObjectValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.extensions.createValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.PainteraDefaultHandlers.Companion.currentFocusHolder
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState

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
    val activeViewerProperty: SimpleObjectProperty<OrthogonalViews.ViewerAndTransforms?>
}

interface ToolMode : SourceMode {

    val toolBarTools: ObservableList<Tool>
    val modeActions: List<ActionSet>

    var activeToolProperty: ObjectProperty<Tool?>
    var activeTool: Tool?

    fun switchTool(tool: Tool?) {
        activeTool?.deactivate()
        activeTool = tool
        activeTool?.activate()
    }
}

abstract class AbstractSourceMode : SourceMode {
    final override val activeSourceStateProperty = SimpleObjectProperty<SourceState<*, *>?>()
    final override val activeViewerProperty = SimpleObjectProperty<OrthogonalViews.ViewerAndTransforms?>()

    protected val currentStateObservable: ObservableObjectValue<SourceState<*, *>?> = paintera.baseView.sourceInfo().currentState() as ObservableObjectValue<SourceState<*, *>?>
    protected val currentViewerObservable = paintera.baseView.orthogonalViews().currentFocusHolder()

    protected val keyBindingsProperty = activeSourceStateProperty.createValueBinding {
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
    private val sourceSpecificViewerActionListener = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
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

    override val toolBarTools: ObservableList<Tool> = FXCollections.observableArrayList()
    final override var activeToolProperty: ObjectProperty<Tool?> = SimpleObjectProperty<Tool?>()
    final override var activeTool by activeToolProperty.nullable()

    override val statusProperty: StringProperty = SimpleStringProperty().apply {
        activeToolProperty.addListener { _, _, new ->
            new?.let {
                bind(it.statusProperty)
            } ?: unbind()
        }
    }

    override fun exit() {
        switchTool(null)
        super<AbstractSourceMode>.exit()
    }
}


