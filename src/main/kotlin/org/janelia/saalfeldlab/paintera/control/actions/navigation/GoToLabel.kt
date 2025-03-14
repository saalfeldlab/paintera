package org.janelia.saalfeldlab.paintera.control.actions.navigation

import bdv.viewer.Interpolation
import javafx.event.Event
import javafx.scene.control.ButtonType
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.type.volatiles.VolatileFloatType
import net.imglib2.view.IntervalView
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.onAction
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.convert
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.raster
import org.janelia.saalfeldlab.util.zeroMin


object GoToLabel : MenuAction("Go to _Label...") {

	init {
		verifyPermission(NavigationActionType.Pan, LabelActionType.View)
		onAction<GoToLabelState> { showDialog(it) }
	}

	private fun GoToLabelState.showDialog(event: Event?) {
		initializeWithCurrentLabel()
		PainteraAlerts.confirmation("Go", "Cancel", true).apply {
			Paintera.registerStylesheets(dialogPane)
			title = name?.replace("_", "")
			headerText = "Go to Label"

			dialogPane.content = GoToLabelUI(this@showDialog)
		}.showAndWait().takeIf { it.nullable == ButtonType.OK }?.run {
			val targetLabel = labelProperty.value
			if (activateLabelProperty.value)
				sourceState.selectedIds.activate(targetLabel)
			goToCoordinates(targetLabel)
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

		val sourceData =
			(source as? DataSource<IntegerType<*>, *>)?.getInterpolatedDataSource(0, 0, Interpolation.NEARESTNEIGHBOR)
			?: source.getInterpolatedSource(0, 0, Interpolation.NEARESTNEIGHBOR)
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

		val (x,y,z) = DoubleArray(maxPos.size) { maxPos[it] + .5}
		goToCoordinates(source, viewer, translationController, x, y, z)
	}
}


private fun GoToLabelState.addDistanceMaskAsSource(
	distances: RandomAccessibleInterval<FloatType>,
	min: Double,
	max: Double,
	name: String? = null
): ConnectomicsRawState<FloatType, VolatileFloatType> {

	val maskedSource = source as MaskedSource<*, *>
	val sourceAlignedDistances = distances.zeroMin() // .extendValue(0.0).interval(maskedSource.getSource(0, 0))

	val metadataState = (maskedSource.underlyingSource() as? N5DataSource)?.metadataState!!
	paintera.baseView.apply {
		sourceInfo().trackSources().firstOrNull { it.name == name }?.let { source -> sourceInfo().removeSource(source) }
		return addConnectomicsRawSource<FloatType, VolatileFloatType>(
			sourceAlignedDistances,
			metadataState.resolution,
			DoubleArray(distances.numDimensions()) { metadataState.resolution[it] * distances.min(it) },
			min,
			max,
			name ?: "blockDistForLabel"
		)!!.apply {
			composite = ARGBCompositeAlphaAdd()
		}
	}
}