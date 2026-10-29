package org.janelia.saalfeldlab.paintera.control.actions.navigation

import gnu.trove.set.hash.TLongHashSet
import net.imglib2.Interval
import javafx.event.Event
import javafx.scene.control.ButtonType
import net.imglib2.FinalInterval
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.view.IntervalView
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.data.toXyzBlocks
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraNotifications
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.navigation.GoToLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.util.convert
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.translate
import org.janelia.saalfeldlab.util.zeroMin
import kotlin.jvm.optionals.getOrNull


object GoToLabel : MenuAction("Go to _Label...") {

	init {
		verifyPermission(NavigationActionType.Pan, LabelActionType.View)
		onActionWithState<GoToLabelState> {
			initializeWithCurrentLabel()
			getDialog(text.replace("_", "")).showAndWait()
				?.takeIf { it.getOrNull() == ButtonType.OK }
				?.run {
					val targetLabel = labelProperty.value
					if (activateLabelProperty.value)
						sourceState.selectedIds.activate(targetLabel)
					goToCoordinates(longArrayOf(targetLabel))
				}
		}
	}

	fun isSupported(sourceState: ConnectomicsLabelState<*, *>) = sourceState.labelBlockLookup.let {
		it !is LabelBlockLookupAllBlocks && it !is LabelBlockLookupNoBlocks
	}

	fun goTo(sourceState: ConnectomicsLabelState<*, *>, segmentId: Long) {
		val state = GoToLabelState().apply { this.sourceState = sourceState }
		/* the state's properties are only populated by verifying them */
		val verifyAction = Action<Event>(Event.ANY).also { state.verifyState(it) }
		if (verifyAction.isValid(null))
			state.goToCoordinates(sourceState.fragmentSegmentAssignment.getFragments(segmentId).toArray().takeIf { it.isNotEmpty() } ?: longArrayOf(segmentId))
	}

	/** The block whose center is closest to the mean of all block centers */
	private fun centralBlock(blocks: List<Interval>): Interval? {
		if (blocks.isEmpty())
			return null
		val numDimensions = blocks.first().numDimensions()
		fun Interval.center(dim: Int) = (min(dim) + max(dim)) / 2.0
		val meanCenter = DoubleArray(numDimensions) { dim -> blocks.sumOf { it.center(dim) } / blocks.size }
		return blocks.minBy { block ->
			(0 until numDimensions).sumOf { dim -> (block.center(dim) - meanCenter[dim]).let { it * it } }
		}
	}

	private fun GoToLabelState.goToCoordinates(labelIds: LongArray) {

		/* TODO: Start at lowest res level, move up until we find a block that contains? */
		if (!isSupported(sourceState)) {
			PainteraNotifications.warn("Go To Label Not Supported", "Go To Label requires a label-block-lookup")
			return
		}

		val xyzBlocksWithLabel = labelIds
			.flatMap { sourceState.labelBlockLookup.read(LabelBlockLookupKey(0, it)).asList() }
			.toXyzBlocks(sourceState.dataSource)

		val block = centralBlock(xyzBlocksWithLabel) ?: run {
			PainteraNotifications.warn("Label Not Found", "No blocks contain the label")
			return
		}


		val paddedBlock = let {
			val paddedMin = block.minAsLongArray().also {
				it.forEachIndexed { idx, _ -> it[idx]-- }
			}
			val paddedMax = block.maxAsLongArray().also {
				it.forEachIndexed { idx, _ -> it[idx]++ }
			}
			FinalInterval(paddedMin, paddedMax)
		}

		val labelIdSet = TLongHashSet(labelIds)
		val sourceData = sourceState.dataSource.getDataSource(0, 0)
		val labelBlockMask = sourceData
			.convert(BoolType(false)) { input, output ->
				output.set(!labelIdSet.contains(input.integerLong))
			}
			.interval(block)
			.extendValue(true)
			.interval(paddedBlock)
			.zeroMin()

		var max = -Double.MAX_VALUE
		lateinit var maxPos: LongArray
		lateinit var offsetDistances: IntervalView<FloatType>

		val distances = ArrayImgs.floats(*labelBlockMask.dimensionsAsLongArray())
		DistanceTransform.binaryTransform(labelBlockMask, distances, DistanceTransform.DISTANCE_TYPE.EUCLIDIAN)


		offsetDistances = distances.translate(*LongArray(block.numDimensions()) { block.min(it) - 1 })
		val cursor = offsetDistances.localizingCursor()
		cursor.forEach {
			val value = it.get().toDouble()
			if (value > max) {
				max = value
				maxPos = cursor.positionAsLongArray()
			}
		}

		val (x, y, z) = DoubleArray(maxPos.size) { maxPos[it] + .5 }
		translateToCoordinate(x, y, z)
	}
}