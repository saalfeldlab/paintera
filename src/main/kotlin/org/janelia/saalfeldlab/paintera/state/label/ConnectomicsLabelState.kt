package org.janelia.saalfeldlab.paintera.state.label

import bdv.viewer.Interpolation
import javafx.beans.property.*
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.paintera.NamedKeyCombination
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream
import java.util.function.BiFunction

class ConnectomicsLabelState<D, T>(private val backend: ConnectomicsLabelBackend<D, T>): SourceState<D, T> {

	val lockedSegments = backend.lockedSegments

	val fragmentSegmentAssignment = backend.fragmentSegmentAssignment

	val selectedIds = SelectedIds()

	val selectedSegments = SelectedSegments(selectedIds, fragmentSegmentAssignment)

	private val stream = ModalGoldenAngleSaturatedHighlightingARGBStream(selectedSegments, lockedSegments)

	private val converter = HighlightingStreamConverter.forType(stream, dataSource.type)

	override fun getDataSource(): DataSource<D, T> = backend.source

	override fun converter(): HighlightingStreamConverter<T> = converter

	// ARGB composite
	private val _composite: ObjectProperty<Composite<ARGBType, ARGBType>> = SimpleObjectProperty(
		this,
		"composite",
		ARGBCompositeAlphaYCbCr())
	var composite: Composite<ARGBType, ARGBType>
		get() = _composite.get()
		set(composite) = _composite.set(composite)
	override fun compositeProperty(): ObjectProperty<Composite<ARGBType, ARGBType>> = _composite

	// source name
	private val _name = SimpleStringProperty("")
	var name: String
		get() = _name.get()
		set(name) = _name.set(name)
	override fun nameProperty(): StringProperty = _name

	// status text
	private val _statusText = SimpleStringProperty(this, "status text", "")
	override fun statusTextProperty(): StringProperty = _statusText

	// visibility
	private val _isVisible = SimpleBooleanProperty(true)
	var isVisible: Boolean
		get() = _isVisible.get()
		set(visible) = _isVisible.set(visible)
	override fun isVisibleProperty(): BooleanProperty = _isVisible

	// interpolation
	private val _interpolation = SimpleObjectProperty(this, "interpolation", Interpolation.NEARESTNEIGHBOR)
	var interpolation: Interpolation
		get() = _interpolation.get()
		set(interpolation) = _interpolation.set(interpolation)
	override fun interpolationProperty(): ObjectProperty<Interpolation> = _interpolation

	// source dependencies
	override fun dependsOn(): Array<SourceState<*, *>> = arrayOf()

	override fun axisOrderProperty(): ObjectProperty<AxisOrder> = SimpleObjectProperty()

	override fun getDisplayStatus(): Node {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun onShutdown(paintera: PainteraBaseView) {
		CommitHandler.showCommitDialog(
			this,
			paintera.sourceInfo().indexOf(this.dataSource),
			false,
			BiFunction { index, name ->
				"Shutting down Paintera. " +
						"Uncommitted changes to the canvas will be lost for source $index: $name if skipped. " +
						"Uncommitted changes to the fragment-segment-assigment will be stored in the Paintera project (if any) " +
						"but can be committed to the data backend, as well."
			},
			false,
			"_Skip")
	}

	override fun createKeyAndMouseBindings(): KeyAndMouseBindings {
		val bindings = KeyAndMouseBindings()
		return try {
			createKeyAndMouseBindingsImpl(bindings)
		} catch (e: NamedKeyCombination.CombinationMap.KeyCombinationAlreadyInserted) {
			e.printStackTrace()
			bindings
		}
	}

	companion object {
		@Throws(NamedKeyCombination.CombinationMap.KeyCombinationAlreadyInserted::class)
		private fun createKeyAndMouseBindingsImpl(bindings: KeyAndMouseBindings): KeyAndMouseBindings {
			val c = bindings.keyCombinations
			with(BindingKeys) {
				c.addCombination(NamedKeyCombination(SELECT_ALL, KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN)))
				c.addCombination(NamedKeyCombination(SELECT_ALL_IN_CURRENT_VIEW, KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)))
				c.addCombination(NamedKeyCombination(LOCK_SEGEMENT, KeyCodeCombination(KeyCode.L)))
				c.addCombination(NamedKeyCombination(NEXT_ID, KeyCodeCombination(KeyCode.N)))
				c.addCombination(NamedKeyCombination(COMMIT_DIALOG, KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN)))
				c.addCombination(NamedKeyCombination(MERGE_ALL_SELECTED, KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN)))
				c.addCombination(NamedKeyCombination(ENTER_SHAPE_INTERPOLATION_MODE, KeyCodeCombination(KeyCode.S)))
				c.addCombination(NamedKeyCombination(EXIT_SHAPE_INTERPOLATION_MODE, KeyCodeCombination(KeyCode.ESCAPE)))
				c.addCombination(NamedKeyCombination(SHAPE_INTERPOLATION_APPLY_MASK, KeyCodeCombination(KeyCode.ENTER)))
				c.addCombination(NamedKeyCombination(SHAPE_INTERPOLATION_EDIT_SELECTION_1, KeyCodeCombination(KeyCode.DIGIT1)))
				c.addCombination(NamedKeyCombination(SHAPE_INTERPOLATION_EDIT_SELECTION_2, KeyCodeCombination(KeyCode.DIGIT2)))
				c.addCombination(NamedKeyCombination(ARGB_STREAM_INCREMENT_SEED, KeyCodeCombination(KeyCode.C)))
				c.addCombination(NamedKeyCombination(ARGB_STREAM_DECREMENT_SEED, KeyCodeCombination(KeyCode.C, KeyCombination.SHIFT_DOWN)))
				c.addCombination(NamedKeyCombination(REFRESH_MESHES, KeyCodeCombination(KeyCode.R)))
				c.addCombination(NamedKeyCombination(CANCEL_3D_FLOODFILL, KeyCodeCombination(KeyCode.ESCAPE)))
				c.addCombination(NamedKeyCombination(TOGGLE_NON_SELECTED_LABELS_VISIBILITY, KeyCodeCombination(KeyCode.V, KeyCombination.SHIFT_DOWN)))
			}
			return bindings
		}
	}

	class BindingKeys {
		companion object {
			const val SELECT_ALL = "select all"
			const val SELECT_ALL_IN_CURRENT_VIEW = "select all in current view"
			const val LOCK_SEGEMENT = "lock segment"
			const val NEXT_ID = "next id"
			const val COMMIT_DIALOG = "commit dialog"
			const val MERGE_ALL_SELECTED = "merge all selected"
			const val ENTER_SHAPE_INTERPOLATION_MODE = "shape interpolation: enter mode"
			const val EXIT_SHAPE_INTERPOLATION_MODE = "shape interpolation: exit mode"
			const val SHAPE_INTERPOLATION_APPLY_MASK = "shape interpolation: apply mask"
			const val SHAPE_INTERPOLATION_EDIT_SELECTION_1 = "shape interpolation: edit selection 1"
			const val SHAPE_INTERPOLATION_EDIT_SELECTION_2 = "shape interpolation: edit selection 2"
			const val ARGB_STREAM_INCREMENT_SEED = "argb stream: increment seed"
			const val ARGB_STREAM_DECREMENT_SEED = "argb stream: decrement seed"
			const val REFRESH_MESHES = "refresh meshes"
			const val CANCEL_3D_FLOODFILL = "3d floodfill: cancel"
			const val TOGGLE_NON_SELECTED_LABELS_VISIBILITY = "toggle non-selected labels visibility"
		}
	}


}
