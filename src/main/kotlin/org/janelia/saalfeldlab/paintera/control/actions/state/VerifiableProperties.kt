package org.janelia.saalfeldlab.paintera.control.actions.state


import gnu.trove.set.hash.TLongHashSet
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.Volatile
import net.imglib2.cache.img.DiskCachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.converter.Converter
import net.imglib2.type.Type
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.verifiable
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments.Companion.read
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.interval

/**
 * ActionState to validate and provide [ViewerPanelFX] and [OrthogonalViews.ViewerAndTransforms]
 *
 */
interface ViewerActionState {
	var viewerAndTransforms: OrthogonalViews.ViewerAndTransforms
	var viewer: ViewerPanelFX

	/**
	 * Implementation of ViewerActionState that grabs state properties from the
	 * most recently focused [ViewerPanelFX]
	 *
	 * @constructor Create empty Most recent focus
	 */
	open class MostRecentFocus :
		PainteraActionState(),
		ViewerActionState {

		override var viewerAndTransforms by verifiable("Viewer is Active") {
			val lastFocus = paintera.baseView.mostRecentFocusHolder.value.viewer()!!
			paintera.baseView.orthogonalViews().run {
				when (lastFocus) {
					topLeft.viewer() -> topLeft
					topRight.viewer() -> topRight
					bottomLeft.viewer() -> bottomLeft
					else -> null
				}
			}
		}
		override var viewer by verifiable("Viewer is Active") { viewerAndTransforms.viewer() }
	}
}

/**
 * ActionState to provide valid [SourceState]
 *
 * @param S the source state type
 */
interface SourceStateActionState<S : SourceState<*, *>> {
	var sourceState: S

	/**
	 * SourceStateActionState the provides its [SourceState] based on the current active Source
	 *
	 * @param S
	 * @constructor Create empty From active source
	 */
	open class FromActiveSource<S : SourceState<*, *>> :
		PainteraActionState(),
		SourceStateActionState<S> {
		override var sourceState by verifiable("Source is Active") {
			val currentSource = paintera.baseView.sourceInfo().currentSourceProperty().value
			paintera.baseView.sourceInfo().getState(currentSource) as? S
		}
	}
}

/**
 * ActionState for validating properties of a LabelState. May be read-only
 *
 * @param S type of the label state
 * @param D data type of the label source
 * @param T volatile type of the label source
 */
interface LabelActionState<S, D, T> : SourceStateActionState<S>
		where S : ConnectomicsLabelState<D, T>, D : IntegerType<D>, T : Type<T>, T : Volatile<D> {

	val assignment get() = sourceState.fragmentSegmentAssignment
	val selectedIds get() = sourceState.selectedIds
	val refreshMeshes get() = sourceState::refreshMeshes

	fun activeFragments(): LongArray = selectedIds.activeIdsCopyAsArray
	fun activeSegments(fragments: LongArray): LongArray = activeFragments().fold(TLongHashSet()) { set, it ->
		val segment = assignment.getSegment(it)
		set.add(segment)
		set
	}.toArray()

	fun fragmentsForActiveSegments(fragments: LongArray): LongArray {
		val fragments = activeFragments()
		val segments = activeSegments(fragments)
		val fragmentsForSegments = segments.fold(TLongHashSet()) { set, it ->
			set.addAll(assignment.getFragments(it))
			set
		}
		return fragmentsForSegments.toArray()
	}

	fun getBlocksForLabel(level: Int, label: Long): Array<Interval> = sourceState.labelBlockLookup.read(level, label)
	fun getMaskForLabel(label: Long): Converter<D, BoolType> = sourceState.maskForLabel.apply(label)

	open class FromActiveSource<S : ConnectomicsLabelState<D, T>, D, T> :
		PainteraActionState(),
		LabelActionState<S, D, T>
			where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {
		override var sourceState by verifiable("Source is Active") {
			val currentSource = paintera.baseView.sourceInfo().currentSourceProperty().value
			paintera.baseView.sourceInfo().getState(currentSource) as? S
		}
	}
}

/**
 * ActionState for validating MaskedSource over a writable Label Source
 *
 * @param S type of the label state
 * @param D data type of the label source
 * @param T volatile type of the label source
 */
interface MaskedSourceActionState<S, D, T> : LabelActionState<S, D, T>
		where S : ConnectomicsLabelState<D, T>, D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	var maskedSource: MaskedSource<D, T>

	fun nextId(activate: Boolean = false): Long = sourceState.nextId(activate)

	fun blocksForLabels(scaleLevel: Int, labels: LongArray, mode: BlocksForLabels = BlocksForLabels.SourceAndCanvas): Set<Interval> {
		return with(mode) {
			getBlocks(scaleLevel, labels)
		}
	}

	fun createSourceAndCanvasImage(timepoint: Int, scaleLevel: Int): DiskCachedCellImg<UnsignedLongType, *> {
		val sourceImg = maskedSource.getReadOnlyDataBackground(timepoint, scaleLevel)
		val canvasImg = maskedSource.getReadOnlyDataCanvas(timepoint, scaleLevel)
		return DiskCachedCellImgFactory(UnsignedLongType()).create(sourceImg) { cell ->

			val canvasCursor = canvasImg.interval(cell).cursor()
			val sourceCursor = sourceImg.interval(cell).cursor()
			val cursor = cell.cursor()

			while (canvasCursor.hasNext() && sourceCursor.hasNext() && cursor.hasNext()) {
				val canvasLabel = canvasCursor.next()
				val sourceLabel = sourceCursor.next()
				val label = cursor.next()
				label.set(canvasLabel.get().takeIf { it != net.imglib2.type.label.Label.INVALID } ?: sourceLabel.realDouble.toLong())
			}
		}
	}

	fun resolutionAtLevel(scaleLevel: Int): DoubleArray {
		if (scaleLevel == 0)
			return sourceState.resolution


		val n5Backend = sourceState.backend as? SourceStateBackendN5<*, *>
		val metadataState = n5Backend?.metadataState as? MultiScaleMetadataState
		metadataState?.scaleTransforms?.get(scaleLevel)?.let { metadataScales ->
			return doubleArrayOf(metadataScales[0, 0], metadataScales[1, 1], metadataScales[2, 2])
		}

		val raiBackend = sourceState.backend as? RandomAccessibleIntervalBackend<*, *>
		raiBackend?.resolutions?.get(scaleLevel)?.let { resolution ->
			return resolution
		}

		return doubleArrayOf(1.0, 1.0, 1.0)
	}

	/**
	 * Writable MaskedSourceActionState from the current active source
	 *
	 */
	open class FromActiveSource<S : ConnectomicsLabelState<D, T>, D, T> :
		LabelActionState.FromActiveSource<S, D, T>(),
		MaskedSourceActionState<S, D, T>
			where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {
		override var maskedSource by verifiable("Active Source has Masked Source") {
			sourceState.dataSource as? MaskedSource<D, T>
		}
	}

	companion object {
		enum class BlocksForLabels {
			SourceOnly {
				override fun MaskedSourceActionState<*, *, *>.getBlocks(scaleLevel: Int, labels: LongArray): Set<Interval> {

					val lbl = (sourceState as ConnectomicsLabelState<*, *>).labelBlockLookup.takeIf { it !is LabelBlockLookupNoBlocks } ?: LabelBlockLookupAllBlocks.fromSource(sourceState.dataSource)
					return labels.flatMapTo(mutableSetOf()) { lbl.read(LabelBlockLookupKey(scaleLevel, it)).toSet() }
				}
			},
			CanvasOnly {
				override fun MaskedSourceActionState<*, *, *>.getBlocks(scaleLevel: Int, labels: LongArray): Set<Interval> {
					val cellGrid = maskedSource.getCellGrid(timepoint, scaleLevel)
					val cellIntervals = cellGrid.cellIntervals().randomAccess()
					val cellPos = LongArray(cellGrid.numDimensions())
					return labels.flatMapTo(mutableSetOf()) {
						maskedSource.getModifiedBlocks(scaleLevel, it).toArray().map { block ->
							cellGrid.getCellGridPositionFlat(block, cellPos)
							FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
						}
					}
				}
			},
			SourceAndCanvas {
				override fun MaskedSourceActionState<*, *, *>.getBlocks(scaleLevel: Int, labels: LongArray): Set<Interval> {
					return SourceOnly.run { getBlocks(scaleLevel, labels) } + CanvasOnly.run { getBlocks(scaleLevel, labels) }
				}
			};

			val timepoint = 0 /* aspirational */

			abstract fun MaskedSourceActionState<*, *, *>.getBlocks(scaleLevel: Int, labels: LongArray): Set<Interval>
		}
	}
}

/**
 * ActionState for access to painting into a writable label source state
 *
 * @param S type of the lavel source
 * @param D data type
 * @param T volatile data type
 */
interface PaintContextActionState<S : ConnectomicsLabelState<D, T>, D, T> : MaskedSourceActionState<S, D, T>
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	val paintSelection get() = { selectedIds.lastSelection.takeIf { Label.regular(it) } }
	val brushProperties get() = sourceState.brushProperties

	/**
	 * PaintContextActionSTate from the current active  mode
	 */
	open class FromCurrentMode<S : ConnectomicsLabelState<D, T>, D, T> :
		PainteraActionState(),
		PaintContextActionState<S, D, T>
			where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

		override var sourceState by verifiable("PaintLabelMode is active and has SourceState") {
			(paintera.currentMode as? PaintLabelMode)
				?.activeSourceStateProperty
				?.get() as? S
		}
		override var maskedSource by verifiable("SourceState has MaskedSource") { sourceState.dataSource as? MaskedSource<D, T> }
	}

	/**
	 * PaintContextActionSTate from the provided [sourceState]
	 */
	open class FromState<S : ConnectomicsLabelState<D, T>, D, T>(override var sourceState: S) : PainteraActionState(), PaintContextActionState<S, D, T>
			where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

		override var maskedSource = sourceState.dataSource as MaskedSource<D, T>
	}
}


