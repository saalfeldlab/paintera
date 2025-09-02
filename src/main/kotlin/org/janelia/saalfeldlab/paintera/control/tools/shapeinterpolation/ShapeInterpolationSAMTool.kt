package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.ButtonBase
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

	override val toolBarButton: ButtonBase
		get() {
			return super.toolBarButton.apply {
				properties[REQUIRES_ACTIVE_VIEWER] = false
			}
		}

	private var replaceExistingSlice = false

	override var currentDisplay: Boolean = true

	override fun activate() {
		val depth = controller.currentDepth

		/* If we are requesting a new embedding that isn't already pre-cached,
		 *  then likely the existing requests are no longer needed.
		 *  Cancel any that have not yet returned. */
		var drawPrompt = false
		shapeInterpolationMode.samSliceCache[depth]?.let {
			drawPrompt = true
		} ?: let {
			SamEmbeddingLoaderCache.cancelPendingRequests()
		}

		val info = shapeInterpolationMode.cacheLoadSamSliceInfo(depth)
		maskedSource?.resetMasks(false)
		/* only replace existing if we are at a slice, and it's not locked.
		 * The cases are:
		 * - At a slice and not locked -> implies an auto-predicted SAM slice, so we replace it
		 * - At a slice and locked -> implies a manual edit previously, don't replace
		 * - Not at a slice -> desired behavior is to ADD to the interpolation at this location, so don't replace it. */
		replaceExistingSlice = info.sliceInfo != null && !info.locked
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
						modeToolsBar.toggleGroup?.selectToggle(null)
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