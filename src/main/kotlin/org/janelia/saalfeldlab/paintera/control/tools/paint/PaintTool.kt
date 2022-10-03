package org.janelia.saalfeldlab.paintera.control.tools.paint

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import net.imglib2.Volatile
import net.imglib2.converter.Converter
import net.imglib2.type.Type
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.verifyPainteraNotDisabled
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.midi.MidiActionSet
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.control.actions.ActionType
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.tools.ToolBarItem
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.state.BrushProperties
import org.janelia.saalfeldlab.paintera.state.LabelSourceState
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

interface ConfigurableTool {

    fun getConfigurableNodes(): List<Node>
}

abstract class PaintTool(private val activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) : ViewerTool(mode), ConfigurableTool, ToolBarItem {

    abstract override val keyTrigger: List<KeyCode>

    private val activeStateProperty = SimpleObjectProperty<SourceState<*, *>?>()
    protected val activeState by activeStateProperty.nullableVal()

    private val sourceStateBindings = activeSourceStateProperty.createNullableValueBinding { getValidSourceState(it) }
    private val activeSourceToSourceStateContextBinding = activeSourceStateProperty.createNullableValueBinding { createPaintStateContext(it) }

    val statePaintContext by activeSourceToSourceStateContextBinding.nullableVal()

    private val brushPropertiesBinding = activeSourceToSourceStateContextBinding.createNullableValueBinding { it?.brushProperties }
    val brushProperties by brushPropertiesBinding.nullableVal()

    var isPainting = false
        protected set

    override val actionSets: MutableList<ActionSet> get() = mutableListOf(*paintToolMidiNavigationActions().toTypedArray())

    override fun activate() {
        /* So we can use Navigation Bindings while paint tool is active . */
        NavigationTool.activeViewerProperty.unbind()
        NavigationTool.activeViewerProperty.bind(activeViewerProperty)

        super.activate()
        activeStateProperty.bind(sourceStateBindings)


    }

    override fun deactivate() {

        activeStateProperty.unbind()
        activeStateProperty.set(null)
        super.deactivate()

        /* Explicitly remove the NavigationTool from the activeViewer we care about. */
        NavigationTool.activeViewerProperty.unbind()
    }

    override fun getConfigurableNodes(): List<Node> {
        //TODO Caleb:
        return listOf()
    }


    fun changeBrushDepth(sign: Double) {
        brushProperties?.apply {
            val newDepth = brushDepth + if (sign > 0) -1 else 1
            brushDepth = newDepth.coerceIn(1.0, 2.0)
        }
    }

    open fun createTriggers(mode: ToolMode, actionType: ActionType? = null, ignoreDisable: Boolean = true): ActionSet {
        return painteraActionSet("toggle $name", actionType, ignoreDisable) {
            val keys = keyTrigger.toTypedArray()
            KeyEvent.KEY_PRESSED(*keys) {
                name = "switch to ${this@PaintTool.name}"
                consume = false
                verifyPainteraNotDisabled()
                onAction { mode.switchTool(this@PaintTool) }
            }
            KeyEvent.KEY_PRESSED(*keys) {
                name = "suppress trigger key for ${this@PaintTool.name} while active"
                /* swallow keyTrigger down events while Filling*/
                filter = true
                consume = true
                verifyPainteraNotDisabled()
                verify { mode.activeTool == this@PaintTool }
            }

            KeyEvent.KEY_RELEASED(*keys) {
                name = "switch out of ${this@PaintTool.name}"
                verify { mode.activeTool == this@PaintTool }
                onAction { mode.switchTool(mode.defaultTool) }
            }
        }
    }

    private fun paintToolMidiNavigationActions(): List<MidiActionSet> {
        val midiNavActions = NavigationTool.midiNavigationActions()
        midiNavActions.forEach {
            it.verifyAll(Event.ANY, "Not Currently Painting") { !isPainting }
        }
        return midiNavActions
    }

    fun <A : Action<E>, E : Event> A.verifyNotPainting() = verify { !isPainting }

    companion object {
        private fun getValidSourceState(source: SourceState<*, *>?): SourceState<*, *>? = source?.let {
            //TODO Caleb: The current paint handlers allow LabelSourceState,
            // so even though it is marked for deprecation, is still is required here (for now)
            (it as? ConnectomicsLabelState<*, *>) ?: (it as? LabelSourceState<*, *>)
        }

        @Suppress("UNCHECKED_CAST")
        internal fun <D, T> createPaintStateContext(source: SourceState<*, *>?): StatePaintContext<D, T>?
                where D : IntegerType<D>, T : Volatile<D>, T : Type<T> {

            return when (source) {
                is LabelSourceState<*, *> -> LabelSourceStatePaintContext(source) as StatePaintContext<D, T>
                is ConnectomicsLabelState<*, *> -> {
                    (source.dataSource as? MaskedSource<*, *>)?.let {
                        ConnectomicsLabelStatePaintContext(source) as StatePaintContext<D, T>
                    }
                }
                else -> null
            }
        }
    }
}

interface StatePaintContext<D : IntegerType<D>, T : Type<T>> {
    val dataSource: MaskedSource<D, T>
    val assignment: FragmentSegmentAssignment
    val isVisibleProperty: SimpleBooleanProperty
    val selectedIds: SelectedIds
    val idService: IdService
    val paintSelection: () -> Long?
    val brushProperties: BrushProperties

    fun getMaskForLabel(label: Long): Converter<D, BoolType>
    fun nextId(activate: Boolean): Long
    fun nextId(): Long = nextId(false)
}


private data class LabelSourceStatePaintContext<D, T>(val state: LabelSourceState<D, T>) : StatePaintContext<D, T>
    where D : IntegerType<D>, T : Volatile<D>, T : Type<T> {

    override val dataSource = state.dataSource as MaskedSource<D, T>
    override val assignment = state.assignment()!!
    override val isVisibleProperty = SimpleBooleanProperty().apply { bind(state.isVisibleProperty) }
    override val selectedIds = state.selectedIds()!!
    override val idService: IdService = state.idService()
    override val paintSelection = { selectedIds.lastSelection.takeIf { Label.regular(it) } }
    override val brushProperties: BrushProperties = BrushProperties()

    override fun getMaskForLabel(label: Long): Converter<D, BoolType> = state.getMaskForLabel(label)
    override fun nextId(activate: Boolean) = state.nextId(activate)
}

private data class ConnectomicsLabelStatePaintContext<D, T>(val state: ConnectomicsLabelState<D, T>) : StatePaintContext<D, T>
    where D : IntegerType<D>, T : Volatile<D>, T : Type<T> {

    override val dataSource: MaskedSource<D, T> = state.dataSource as MaskedSource<D, T>
    override val assignment = state.fragmentSegmentAssignment
    override val isVisibleProperty = SimpleBooleanProperty().apply { bind(state.isVisibleProperty) }
    override val selectedIds = state.selectedIds
    override val idService = state.idService
    override val paintSelection = { selectedIds.lastSelection.takeIf { Label.regular(it) } }
    override val brushProperties: BrushProperties = state.brushProperties

    override fun getMaskForLabel(label: Long): Converter<D, BoolType> = state.maskForLabel.apply(label)
    override fun nextId(activate: Boolean) = state.nextId(activate)
}
