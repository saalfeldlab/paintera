package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import net.imglib2.RandomAccessibleInterval
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.RealType
import net.imglib2.util.Intervals
import net.imglib2.util.Util
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ui.ScaleView
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.util.*


object RawSourceMode : AbstractToolMode() {

	override val defaultTool: Tool = NavigationTool

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList()

	override val allowedActions = AllowedActions.NAVIGATION

	private val minMaxIntensityThreshold = painteraActionSet("Min/Max Intensity Threshold") {
		verifyAll(KEY_PRESSED, "Source State is Raw Source State ") { activeSourceStateProperty.get() is ConnectomicsRawState<*, *> }
		KEY_PRESSED(KeyCode.SHIFT, KeyCode.Y) {
			graphic = { ScaleView().apply { styleClass += "intensity-reset-min-max" } }
			onAction {
				val rawSource = activeSourceStateProperty.get() as ConnectomicsRawState<*, *>
				resetIntensityMinMax(rawSource)
			}
		}
		KEY_PRESSED(KeyCode.Y) {
			lateinit var viewer: ViewerPanelFX
			graphic = { ScaleView().apply { styleClass += "intensity-estimate-min-max" } }
			verify("Last focused viewer found") { paintera.baseView.lastFocusHolder.value?.viewer()?.also { viewer = it } != null }
			onAction {
				val rawSource = activeSourceStateProperty.get() as ConnectomicsRawState<*, *>
				estimateIntensityMinMax(rawSource, viewer)
			}
		}
	}

	fun estimateIntensityMinMax(rawSource: ConnectomicsRawState<*, *>, viewer: ViewerPanelFX) {
		val globalToViewerTransform = AffineTransform3D().also { viewer.state.getViewerTransform(it) }
		val viewerInterval = Intervals.createMinSize(0, 0, 0, viewer.width.toLong(), viewer.height.toLong(), 1L)

		val scaleLevel = viewer.state.bestMipMapLevel
		val dataSource = rawSource.getDataSource().getDataSource(0, scaleLevel) as RandomAccessibleInterval<RealType<*>>

		val sourceToGlobalTransform = rawSource.getDataSource().getSourceTransformCopy(0, scaleLevel)


		val extension = Util.getTypeFromInterval(dataSource).createVariable()
		try {
			extension.setReal(Double.NaN)
		} catch (e: Exception) {
			extension.setZero()
		}

		val screenSource = dataSource
			.extendValue(extension)
			.interpolateNearestNeighbor()
			.affineReal(globalToViewerTransform.concatenate(sourceToGlobalTransform))
			.raster()
			.interval(viewerInterval)

		var min = Double.NaN
		var max = Double.NaN
		LoopBuilder.setImages(screenSource).forEachPixel {
			val value = it.realDouble
			if (!value.isNaN()) {
				if (value < min || min.isNaN()) min = value
				if (value > max || max.isNaN()) max = value
			}
		}
		if (!min.isNaN()) rawSource.converter().minProperty().set(min)
		if (!max.isNaN()) rawSource.converter().maxProperty().set(max)
	}

	fun resetIntensityMinMax(rawSource: ConnectomicsRawState<*, *>) {
		val dataSource = rawSource.getDataSource().getDataSource(0, 0) as RandomAccessibleInterval<RealType<*>>
		val extension = Util.getTypeFromInterval(dataSource).createVariable()

		rawSource.converter().minProperty().set(extension.minValue)
		rawSource.converter().maxProperty().set(extension.maxValue)
	}

	override val modeActions: List<ActionSet> = listOf(minMaxIntensityThreshold)

	private val moveToolTriggersToActiveViewer = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
		/* remove the tool triggers from old, add to new */
		modeActions.forEach { actionSet ->
			old?.viewer()?.removeActionSet(actionSet)
			new?.viewer()?.installActionSet(actionSet)
		}

		/* set the currently activeTool for this viewer */
		switchTool(activeTool ?: NavigationTool)
	}

	override fun enter() {
		activeViewerProperty.addListener(moveToolTriggersToActiveViewer)
		super.enter()
	}

	override fun exit() {
		activeViewerProperty.removeListener(moveToolTriggersToActiveViewer)
		super.exit()
	}

}


