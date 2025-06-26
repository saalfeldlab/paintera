package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ShapeInterpolationMode
import org.janelia.saalfeldlab.paintera.control.tools.REQUIRES_ACTIVE_VIEWER
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool
import org.janelia.saalfeldlab.paintera.state.SourceState

internal class ShapeInterpolationSAMTool(private val controller: ShapeInterpolationController<*>, activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, private val shapeInterpolationMode: ShapeInterpolationMode<*>) : SamTool(activeSourceStateProperty, shapeInterpolationMode) {


	init {
		activeViewerProperty.unbind()
		activeViewerProperty.bind(mode!!.activeViewerProperty)
	}

	override fun newToolBarControl()  = super.newToolBarControl().also { item ->
		item.properties[REQUIRES_ACTIVE_VIEWER] = false
	}

	private var replaceExistingSlice = false

	override var currentDisplay: Boolean = true

	override fun activate() {
		/* If we are requesting a new embedding that isn't already pre-cached,
		 *  then likely the existing requests are no longer needed.
		 *  Cancel any that have not yet returned. */
		var drawPrompt = false
		shapeInterpolationMode.samSliceCache[controller.currentDepth]?.let {
			drawPrompt = true
		} ?: let {
			SamEmbeddingLoaderCache.cancelPendingRequests()
		}

		val info = shapeInterpolationMode.cacheLoadSamSliceInfo(controller.currentDepth)
		maskedSource?.resetMasks(false)
		replaceExistingSlice = !info.locked
		viewerMask = controller.getMask(ignoreExisting = replaceExistingSlice)

		super.activate()

		if (drawPrompt)
			info.prediction.drawPrompt()

		requestPrediction(info.prediction)
	}

	override fun deactivate() {
		super.deactivate()
		controller.setMaskOverlay()
	}

	override fun applyPrediction() {
		lastPrediction?.apply {
			/* cache the prediction. lock the cached slice, since this was applied manually */
			super.applyPrediction()
			shapeInterpolationMode.run {
				addSelection(maskInterval, replaceExistingSlice = replaceExistingSlice)?.also {
					it.prediction = predictionRequest
					it.locked = true
				}
				switchTool(defaultTool)?.invokeOnCompletion {
					InvokeOnJavaFXApplicationThread {
						actionBar.modeToolsGroup.selectToggle(null)
					}
				}
			}
		}
	}

	override fun setCurrentLabelToSelection() {
		currentLabelToPaint = controller.interpolationId
	}

	override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
		super.actionSets.also { it += shapeInterpolationMode.extraActions() }
	}


	/**
	 * Additional SAM actions for Shape Interpolation
	 *
	 * @param samTool
	 * @return the additional ActionSet
	 *
	 * */
	private fun ShapeInterpolationMode<*>.extraActions(): ActionSet {
		return painteraActionSet("Shape Interpolation SAM Actions", PaintActionType.ShapeInterpolation) {
			switchAndApplyShapeInterpolationActions(this)
		}
	}
}