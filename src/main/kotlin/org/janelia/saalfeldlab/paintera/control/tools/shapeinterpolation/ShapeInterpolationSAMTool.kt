package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.ButtonBase
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
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

	override val toolBarButton: ButtonBase
		get() {
			return super.toolBarButton.apply {
				properties[REQUIRES_ACTIVE_VIEWER] = false
			}
		}

	override fun activate() {
		/* If we are requesting a new embedding that isn't already pre-cached,
		 *  then likely the existing requests are no longer needed.
		 *  Cancel any that have not yet returned. */
		shapeInterpolationMode.samSliceCache[controller.currentDepth] ?: let { SamEmbeddingLoaderCache.cancelPendingRequests() }

		val info = shapeInterpolationMode.cacheLoadSamSliceInfo(controller.currentDepth)
		if (!info.preGenerated)
			temporaryPrompt = false
		else
			controller.deleteSliceAt(controller.currentDepth)

		maskedSource?.resetMasks(false)
		viewerMask = controller.getMask()
		super.activate()

		requestPrediction(info.prediction)
	}

	override fun applyPrediction() {
		lastPrediction?.apply {
			/* cache the prediction. lock the cached slice, since this was applied manually */
			super.applyPrediction()
			shapeInterpolationMode.addSelection(maskInterval)?.also {
				it.prediction = predictionRequest
				it.locked = true
			}
			shapeInterpolationMode.run {
				switchTool(defaultTool)
				modeToolsBar.toggleGroup?.selectToggle(null)
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