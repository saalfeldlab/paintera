package org.janelia.saalfeldlab.paintera.control.actions.navigation

import bdv.viewer.Interpolation
import javafx.scene.control.ButtonType
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.view.IntervalView
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.navigation.GoToLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.util.convert
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.raster
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
					goToCoordinates(targetLabel)
				}
		}
	}

	private fun GoToLabelState.goToCoordinates(labelId: Long) {

		/* TODO: Start at lowest res level, move up until we find a block that contains? */
		when (sourceState.labelBlockLookup) {
			is LabelBlockLookupAllBlocks -> TODO("Warning, will be slow")
			is LabelBlockLookupNoBlocks -> TODO("No LabelBlockLookup Present")
		}

		val blocksWithLabel = sourceState.labelBlockLookup.read(LabelBlockLookupKey(0, labelId))
		val block = blocksWithLabel.firstOrNull() ?: return


		val sourceData = sourceState.dataSource.getInterpolatedDataSource(0, 0, Interpolation.NEARESTNEIGHBOR)
		val labelBlockMask = sourceData
			.raster()
			.interval(block)
			.zeroMin()
			.convert(BoolType(false)) { input, output ->
				output.set(input.integerLong != labelId)
			}


		var min = Double.MAX_VALUE
		var max = -Double.MAX_VALUE
		lateinit var minPos: LongArray
		lateinit var maxPos: LongArray
		lateinit var offsetDistances: IntervalView<FloatType>

		val distances = ArrayImgs.floats(*block.dimensionsAsLongArray())
		DistanceTransform.binaryTransform(labelBlockMask, distances, DistanceTransform.DISTANCE_TYPE.EUCLIDIAN)


		offsetDistances = Views.translate(distances, *block.minAsLongArray())
		val cursor = offsetDistances.localizingCursor()
		cursor.forEach {
			val value = it!!.get().toDouble()
			if (value < min) {
				min = value
				minPos = cursor.positionAsLongArray()
			}
			if (value > max) {
				max = value
				maxPos = cursor.positionAsLongArray()
			}
		}

		val (x, y, z) = DoubleArray(maxPos.size) { maxPos[it] + .5 }
		translateToCoordinate(x, y, z)
	}
}