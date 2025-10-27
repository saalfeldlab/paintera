package org.janelia.saalfeldlab.paintera.control.tools.paint

import javafx.beans.property.SimpleObjectProperty
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import net.imglib2.Interval
import net.imglib2.Volatile
import net.imglib2.converter.Converter
import net.imglib2.type.Type
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.midi.MidiActionSet
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.control.actions.ActionType
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments.Companion.read
import org.janelia.saalfeldlab.paintera.state.BrushProperties
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

interface ConfigurableTool {

	fun getConfigurableNodes(): List<Node>
}

abstract class PaintTool(
	protected val activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>,
	mode: ToolMode? = null
) : ViewerTool(mode), ConfigurableTool {

	abstract override val keyTrigger: NamedKeyBinding

	protected val activeState by activeSourceStateProperty.nullableVal()

	private val activePaintContextBinding = activeSourceStateProperty.createNullableValueBinding { createPaintStateContext<Nothing, Nothing>(it) }
	val statePaintContext by activePaintContextBinding.nullableVal()

	val brushPropertiesBinding = activePaintContextBinding.createNullableValueBinding { it?.brushProperties }
	val brushProperties by brushPropertiesBinding.nullableVal()

	var isPainting = false
		protected set

	override val actionSets: MutableList<ActionSet> get() = mutableListOf(*paintToolMidiNavigationActions().toTypedArray())

	internal var enteredWithoutKeyTrigger = false


	override fun activate() {
		/* So we can use Navigation Bindings while paint tool is active . */
		NavigationTool.activeViewerProperty.unbind()
		NavigationTool.activeViewerProperty.bind(activeViewerProperty)

		super.activate()
	}

	override fun deactivate() {

		enteredWithoutKeyTrigger = false
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
			KEY_PRESSED(keyTrigger) {
				name = "switch to ${this@PaintTool.name}"
				consume = false
				verifyPainteraNotDisabled()
				onAction { mode.switchTool(this@PaintTool) }
			}
			KEY_PRESSED(keyTrigger) {
				name = "suppress trigger key for ${this@PaintTool.name} while active"
				/* swallow keyTrigger down events while Filling*/
				filter = true
				consume = true
				verifyPainteraNotDisabled()
				verify { mode.activeTool == this@PaintTool }
			}
			KEY_RELEASED(keyTrigger) {
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
		private fun getValidSourceState(source: SourceState<*, *>?): SourceState<*, *>? = source as? ConnectomicsLabelState<*, *>

		@Suppress("UNCHECKED_CAST")
		internal fun <D, T> createPaintStateContext(source: SourceState<*, *>?): StatePaintContext<D, T>?
				where D : IntegerType<D>, T : Volatile<D>, T : Type<T> {

			return when (source) {
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

/*Phase out in favor of PaintContextActionState */
interface StatePaintContext<D : IntegerType<D>, T : Type<T>> {
	val dataSource: MaskedSource<D, T>
	val assignment: FragmentSegmentAssignment
	val selectedIds: SelectedIds
	val paintSelection: () -> Long?
	val brushProperties: BrushProperties
	val refreshMeshes: () -> Unit

	fun getMaskForLabel(label: Long): Converter<D, BoolType>
	fun getBlocksForLabel(level: Int, label: Long): Array<Interval>
	fun nextId(activate: Boolean): Long
	fun nextId(): Long = nextId(false)
}

/*Phase out in favor of PaintContextActionState */
private data class ConnectomicsLabelStatePaintContext<D, T>(val state: ConnectomicsLabelState<D, T>) : StatePaintContext<D, T>
		where D : IntegerType<D>, T : Volatile<D>, T : Type<T> {

	override val dataSource: MaskedSource<D, T> = state.dataSource as MaskedSource<D, T>
	override val assignment = state.fragmentSegmentAssignment
	override val selectedIds = state.selectedIds
	override val paintSelection = { selectedIds.lastSelection.takeIf { Label.regular(it) } }
	override val brushProperties: BrushProperties = state.brushProperties
	override val refreshMeshes: () -> Unit = state::refreshMeshes

	override fun getMaskForLabel(label: Long): Converter<D, BoolType> = state.maskForLabel.apply(label)
	override fun getBlocksForLabel(level: Int, label: Long): Array<Interval> {
		return state.labelBlockLookup.read(level, label)
	}

	override fun nextId(activate: Boolean) = state.nextId(activate)
}
