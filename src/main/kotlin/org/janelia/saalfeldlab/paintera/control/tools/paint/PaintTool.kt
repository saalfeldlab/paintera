package org.janelia.saalfeldlab.paintera.control.tools.paint

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Node
import net.imglib2.Volatile
import net.imglib2.converter.Converter
import net.imglib2.type.Type
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.extensions.createValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
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

abstract class PaintTool(val activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>) : ViewerTool(), ConfigurableTool {

    val activeStateProperty = SimpleObjectProperty<SourceState<*, *>?>()
    protected val activeState by activeStateProperty.nullableVal()

    private val sourceStateBindings = activeSourceStateProperty.createValueBinding { getValidSourceState(it) }
    val activeSourceToSourceStateContextBinding = activeSourceStateProperty.createValueBinding { binding -> createPaintStateContext(binding) }

    val statePaintContext by activeSourceToSourceStateContextBinding.nullableVal()

    val brushPropertiesBinding = activeSourceToSourceStateContextBinding.createValueBinding { it?.brushProperties }
    val brushProperties by brushPropertiesBinding.nullableVal()

    override fun activate() {
        super.activate()
        activeStateProperty.bind(sourceStateBindings)
    }

    override fun deactivate() {
        activeStateProperty.unbind()
        activeStateProperty.set(null)
        super.deactivate()
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

    companion object {
        private fun getValidSourceState(source: SourceState<*, *>?) = source?.let {
            //TODO Caleb: The current paint handlers allow LabelSourceState,
            // so even though it is marked for deprecation, is still is required here (for now)
            (it as? ConnectomicsLabelState<*, *>) ?: (it as? LabelSourceState<*, *>)
        }

        internal fun createPaintStateContext(source: SourceState<*, *>?) = when (source) {
            is LabelSourceState<*, *> -> LabelSourceStatePaintContext(source)
            is ConnectomicsLabelState<*, *> -> {
                (source.dataSource as? MaskedSource<*, *>)?.let {
                    ConnectomicsLabelStatePaintContext(source)
                }
            }
            else -> null
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
