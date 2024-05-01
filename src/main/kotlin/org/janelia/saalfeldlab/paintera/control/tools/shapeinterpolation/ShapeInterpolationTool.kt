package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.util.Duration
import kotlinx.coroutines.*
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.painteraDragActionSet
import org.janelia.saalfeldlab.fx.extensions.addWithListener
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ui.ScaleView
import org.janelia.saalfeldlab.fx.ui.GlyphScaleView
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.*
import org.janelia.saalfeldlab.paintera.control.navigation.TranslationController
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.Fill2DTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.util.*

internal class ShapeInterpolationTool(
	private val controller: ShapeInterpolationController<*>,
	private val previousMode: ControlMode,
	private val shapeInterpolationMode: ShapeInterpolationMode<*>,
	private var fill2D: Fill2DTool
) : ViewerTool(shapeInterpolationMode) {


	override val actionSets: MutableList<ActionSet> = mutableListOf(
		shapeInterpolationActions(),
		cancelShapeInterpolationTask()
	)

	override val graphic = { GlyphScaleView(FontAwesomeIconView().also { it.styleClass += listOf("navigation-tool") }) }
	override val name: String = "Shape Interpolation"
	override val keyTrigger = SHAPE_INTERPOLATION__TOGGLE_MODE
	private var currentTask: UtilityTask<*>? = null

	override fun activate() {

		super.activate()
		shapeInterpolationMode.disableUnfocusedViewers() //TODO Caleb: this should be in the `enter()` of the mode, and the `disabled translate` logic should also
		/* This action set allows us to translate through the unfocused viewers */
		paintera.baseView.orthogonalViews().viewerAndTransforms()
			.filter { !it.viewer().isFocusable }
			.forEach { disabledViewerAndTransform ->
				val disabledTranslationActions = disabledViewerActions.computeIfAbsent(disabledViewerAndTransform, disabledViewerTranslateOnly)
				val disabledViewer = disabledViewerAndTransform.viewer()
				disabledTranslationActions.forEach { disabledViewer.installActionSet(it) }
			}
		/* Activate, but we want to bind it to our activeViewer bindings instead of the default. */
		NavigationTool.activate()
		NavigationTool.activeViewerProperty.unbind()
		NavigationTool.activeViewerProperty.bind(activeViewerProperty)
		NavigationTool.installInto(activeViewer!!)
	}

	override fun deactivate() {
		/* We intentionally unbound the activeViewer for this, to support the button toggle.
		* We now need to explicitly remove the NavigationTool from the activeViewer we care about.
		* Still deactive it first, to handle the rest of the cleanup */
		NavigationTool.removeFrom(activeViewer!!)
		NavigationTool.deactivate()
		disabledViewerActions.forEach { (vat, actionSets) -> actionSets.forEach { vat.viewer().removeActionSet(it) } }
		disabledViewerActions.clear()
		super.deactivate()
	}

	override val statusProperty = SimpleStringProperty().apply {

		val statusBinding = controller.controllerStateProperty.createNullableValueBinding(controller.sliceDepthProperty) {
			controller.getStatusText()
		}
		bind(statusBinding)
	}

	private fun ShapeInterpolationController<*>.getStatusText() =
		when {
			controllerState == ShapeInterpolationController.ControllerState.Interpolate -> "Interpolating..."
			numSlices == 0 -> "Select or Paint ..."
			else -> {
				val sliceIdx = sortedSliceDepths.indexOf(sliceDepthProperty.get())
				"Slice: ${if (sliceIdx == -1) "N/A" else "${sliceIdx + 1}"} / ${numSlices}"
			}
		}

	private val disabledViewerActions = mutableMapOf<OrthogonalViews.ViewerAndTransforms, Array<ActionSet>>()

	private val disabledViewerTranslateOnly = { vat: OrthogonalViews.ViewerAndTransforms ->
		val translator = vat.run {
			val globalTransformManager = paintera.baseView.manager()
			TranslationController(globalTransformManager, globalToViewerTransform)
		}
		arrayOf(
			painteraDragActionSet("disabled_translate_xy", NavigationActionType.Pan) {
				relative = true;
				verify { it.isSecondaryButtonDown }
				verify { controller.controllerState != ShapeInterpolationController.ControllerState.Interpolate }
				onDrag { translator.translate(it.x - startX, it.y - startY) }
			},
			painteraActionSet("disabled_move_to_cursor", NavigationActionType.Pan, ignoreDisable = true) {
				MOUSE_CLICKED(MouseButton.PRIMARY) {
					verify("only double click") { it?.clickCount!! > 1 }
					onAction {
						val viewer = vat.viewer()
						val x = viewer.width / 2 - viewer.mouseXProperty.value
						val y = viewer.height / 2 - viewer.mouseYProperty.value
						translator.translate(x, y, 0.0, Duration.millis(500.0))
					}
				}
			}
		)
	}

	internal fun requestEmbedding(depth: Double) {
		shapeInterpolationMode.cacheLoadSamSliceInfo(depth)
	}


	/**
	 * Request sam prediction at [globalToViewerTransform]
	 *
	 * @param globalToViewerTransform transform from global space to the sam prediction
	 */
	private fun ShapeInterpolationController<*>.requestSamPrediction(
		depth: Double,
		moveToSlice: Boolean = false,
		refresh: Boolean = false,
		afterPrediction: (AffineTransform3D) -> Unit = {}
	): AffineTransform3D {

		val newPrediction = shapeInterpolationMode.samSliceCache[depth] == null
		if (newPrediction)
			SamEmbeddingLoaderCache.cancelPendingRequests()

		val samSliceInfo = shapeInterpolationMode.cacheLoadSamSliceInfo(depth)

		if (!newPrediction && refresh) {
			controller.getInterpolationImg(samSliceInfo.globalToViewerTransform, closest = true)?.getPositionAtMaxDistance()?.let { (x, y) ->
				samSliceInfo.updatePrediction(x, y)
			}
		}

		val viewerMask = samSliceInfo.mask

		val samTool = SamTool(mode!!.activeSourceStateProperty, mode)
		samTool.unwrapResult = false
		samTool.cleanup()
		samTool.enteredWithoutKeyTrigger = true
		samTool.activeViewerProperty.bind(activeViewerProperty)
		samTool.initializeSam(samSliceInfo.renderState)
		samTool.unwrapResult = false
		samTool.currentLabelToPaint = controller.interpolationId
		samTool.viewerMask = viewerMask
		samTool.maskPriority = SamTool.MaskPriority.MASK


		val globalTransform = viewerMask.initialGlobalTransform.copy()

		if (moveToSlice)
			moveTo(globalTransform)

		samTool.lastPredictionProperty.addListener { _, _, prediction ->
			prediction ?: return@addListener
			shapeInterpolationMode.addSelection(prediction.maskInterval, globalTransform, viewerMask) ?: return@addListener

			afterPrediction(globalTransform)

			samTool.cleanup()
			samTool.activeViewerProperty.unbind()
		}
		samTool.requestPrediction(samSliceInfo.prediction)
		return globalTransform
	}

	private data class EdgeDepthsAndSpacing(val firstDepth: Double?, val firstSpacing: Double, val lastDepth: Double?, val lastSpacing: Double)

	private fun edgeDepthsAndSpacing(depths: MutableList<Double>): EdgeDepthsAndSpacing {
		depths.sort()
		val firstSpacing = depths.zipWithNext().firstOrNull()
			?.let { (first, second) -> (second - first) }
			?: 20.0


		val lastSpacing = depths.zipWithNext().lastOrNull()
			?.let { (first, second) -> (second - first) }
			?: 20.0

		/* just a trick to handle the case where no slice exist. */
		val firstDepth = depths.firstOrNull()
		val lastDepth = depths.lastOrNull()

		return EdgeDepthsAndSpacing(firstDepth, firstSpacing, lastDepth, lastSpacing)
	}

	private fun shapeInterpolationActions(): ActionSet {
		return painteraActionSet("shape interpolation", PaintActionType.ShapeInterpolation) {
			with(controller) {
				verifyAll(KEY_PRESSED) { isControllerActive }
				KEY_PRESSED(SHAPE_INTERPOLATION__ACCEPT_INTERPOLATION){
					graphic = { GlyphScaleView(FontAwesomeIconView().apply { styleClass += listOf("accept", "accept-shape-interpolation") }) }
					onAction {
						if (applyMask()) {
							paintera.baseView.changeMode(previousMode)
						}
					}
					handleException {
						it.printStackTrace()
						paintera.baseView.changeMode(previousMode)
					}
				}

				KEY_PRESSED(SHAPE_INTERPOLATION__TOGGLE_PREVIEW) {
					val iconClsBinding = controller.previewProperty.createNonNullValueBinding { if (it) "toggle-on" else "toggle-off" }
					val iconCls by iconClsBinding.nonnullVal()
					graphic = {
						val icon = FontAwesomeIconView().also {
							it.styleClass.addAll(iconCls)
							it.id = iconCls
							iconClsBinding.addListener { _, old, new ->
								it.styleClass.removeAll(old)
								it.styleClass.add(new)
							}
						}
						GlyphScaleView(icon)
					}
					onAction { controller.togglePreviewMode() }
					handleException {
						paintera.baseView.changeMode(previousMode)
					}
				}

				KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_LEFT) {
					graphic = { ScaleView().apply { styleClass += listOf("auto-sam", "slice-left") } }
					onAction {
						val depths = sortedSliceDepths.toMutableList()
						val (firstDepth, firstSpacing, lastDepth, lastSpacing) = edgeDepthsAndSpacing(depths)

						/* trigger the prediction, and move there when done */
						val newFirstDepth = (firstDepth ?: firstSpacing) - firstSpacing

						/* request the next depth immediately, request the rest when this one finishes */
						requestSamPrediction(newFirstDepth, moveToSlice = true) {
							requestEmbedding(newFirstDepth - 2 * firstSpacing)
							/* Request next in the opposite direction */
							requestEmbedding((lastDepth ?: 0.0) + lastSpacing)
							firstDepth?.let {
								/* request the center between the newest prediction and previous */
								requestEmbedding(it - firstSpacing * .5)
							}
						}
						requestEmbedding(newFirstDepth - firstSpacing)
					}
				}
				KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICES_BISECT) {
					graphic = { ScaleView().apply { styleClass += listOf("auto-sam", "slice-bisect") } }
					onAction {

						val depths = sortedSliceDepths.toMutableList()
						val remainingRequest = SimpleIntegerProperty().apply {
							addListener { _, _, remaining ->
								if (remaining == 0) {
									depths.forEach { requestSamPrediction(it, refresh = true) }

									depths.sort()
									/* eagerly request the next embeddings */
									depths.zipWithNext { before, after ->
										val eagerDepth = (before + after) / 2.0
										requestEmbedding(eagerDepth)
									}
								}
							}
						}

						/* Do the prediction */
						sortedSliceDepths.zipWithNext { before, after -> (before + after) / 2.0 }.forEach {
							depths += it
							remainingRequest.value++
							requestSamPrediction(it) {
								remainingRequest.value--
							}
						}
					}
				}
				KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_RIGHT) {
					graphic = { ScaleView().apply { styleClass += listOf("auto-sam", "slice-right") } }
					onAction {
						val depths = sortedSliceDepths.toMutableList()
						val (firstDepth, firstSpacing, lastDepth, lastSpacing) = edgeDepthsAndSpacing(depths)

						val newLastDepth = (lastDepth ?: -lastSpacing) + lastSpacing
						requestSamPrediction(newLastDepth, moveToSlice = true) {
							requestEmbedding(newLastDepth + 2 * lastSpacing)
							/* Request next in the opposite direction */
							requestEmbedding((firstDepth ?: 0.0) - firstSpacing)
							lastDepth?.let {
								/* request the center between the newest prediction and previous */
								requestEmbedding(it + lastSpacing * .5)
							}
						}
						/* Request next two in the same direction */
						requestEmbedding(newLastDepth + lastSpacing)
					}
				}
				KEY_PRESSED(KeyCode.ALT, KeyCode.A) {
					name = "refresh unlocked slice predictions"
					onAction {
						shapeInterpolationMode.samSliceCache
							.filter { (_, info) -> !info.locked }
							.toList()
							.forEach { (depth, _) ->
								requestSamPrediction(depth.toDouble(), refresh = true)
							}
					}
				}
				KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_HERE) {
					onAction {
						requestSamPrediction(currentDepth, refresh = true)
					}
				}

				listOf(SHAPE_INTERPOLATION__REMOVE_SLICE_1, SHAPE_INTERPOLATION__REMOVE_SLICE_2).forEach { key ->
					KEY_PRESSED(key) {
						filter = true
						consume = false
						onAction {
							deleteSliceAt(currentDepth)?.also { shapeInterpolationMode.samSliceCache -= currentDepth }
						}
					}
				}
				MOUSE_CLICKED {
					name = "select object in current slice"

					verifyNoKeysDown()
					verifyEventNotNull()
					verify { !paintera.mouseTracker.isDragging }
					verify { shapeInterpolationMode.activeTool !is Fill2DTool }
					verify { it!!.button == MouseButton.PRIMARY } // respond to primary click
					verify { controllerState != ShapeInterpolationController.ControllerState.Interpolate } // need to be in the select state
					verify("Can't select BACKGROUND or higher MAX_ID ") { event ->

						source.resetMasks(false)
						val mask = getMask()

						fill2D.fill2D.provideMask(mask)
						val pointInMask = mask.displayPointToInitialMaskPoint(event!!.x, event.y)
						val pointInSource = pointInMask.positionAsRealPoint().also { mask.initialMaskToSourceTransform.apply(it, it) }
						val info = mask.info
						val sourceLabel = source.getInterpolatedDataSource(info.time, info.level, null).getAt(pointInSource).integerLong
						return@verify sourceLabel != Label.BACKGROUND && sourceLabel.toULong() <= Label.MAX_ID.toULong()

					}
					onAction { event ->
						/* get value at position */
						deleteSliceOrInterpolant()?.let { prevSliceGlobalInterval ->
							source.resetMasks(true)
							paintera.baseView.orthogonalViews().requestRepaint(Intervals.smallestContainingInterval(prevSliceGlobalInterval))
						}
						currentTask = fillObjectInSlice(event!!)
					}
				}
				MOUSE_CLICKED {
					name = "toggle object in current slice"
					verify { !paintera.mouseTracker.isDragging }
					verify { controllerState != ShapeInterpolationController.ControllerState.Interpolate }
					verifyEventNotNull()
					verify {
						val triggerByRightClick = (it?.button == MouseButton.SECONDARY) && keyTracker()!!.noKeysActive()
						val triggerByCtrlLeftClick = (it?.button == MouseButton.PRIMARY) && keyTracker()!!.areOnlyTheseKeysDown(KeyCode.CONTROL)
						triggerByRightClick || triggerByCtrlLeftClick
					}
					onAction { event ->
						currentTask = fillObjectInSlice(event!!)
					}
				}
			}
		}
	}


	private fun cancelShapeInterpolationTask(): ActionSet {
		return painteraActionSet("cancel shape interpolation task", PaintActionType.ShapeInterpolation, ignoreDisable = true) {
			with(controller) {
				verifyAll(KEY_PRESSED, "Controller is not active") { isControllerActive }
				KEY_PRESSED(CANCEL) {
					name = "cancel current shape interpolation tool task"
					filter = true
					verify("No task to  cancel") { currentTask != null }
					onAction {
						currentTask?.cancel()
						currentTask = null
					}
				}
			}
		}
	}

	private fun fillObjectInSlice(event: MouseEvent): UtilityTask<*>? {
		with(controller) {
			source.resetMasks(false)
			val mask = getMask()

			/* If a current slice exists, try to preserve it if cancelled */
			currentSliceMaskInterval?.also {
				mask.pushNewImageLayer()
				fill2D.fillTaskProperty.addWithListener { obs, _, task ->
					task?.let {
						task.onCancelled(true) { _, _ ->
							mask.popImageLayer()
							mask.requestRepaint()
						}
						task.onEnd(true) { obs?.removeListener(this) }
					} ?: obs?.removeListener(this)
				}
			}

			fill2D.fill2D.provideMask(mask)
			val pointInMask = mask.displayPointToInitialMaskPoint(event.x, event.y)
			val pointInSource = pointInMask.positionAsRealPoint().also { mask.initialMaskToSourceTransform.apply(it, it) }
			val info = mask.info
			val sourceLabel = source.getInterpolatedDataSource(info.time, info.level, null).getAt(pointInSource).integerLong
			if (sourceLabel == Label.BACKGROUND || sourceLabel.toULong() > Label.MAX_ID.toULong()) {
				return null
			}

			val maskLabel = mask.rai.extendValue(Label.INVALID)[pointInMask].get()
			fill2D.brushProperties?.brushDepth = 1.0
			fill2D.fillLabel = { if (maskLabel == interpolationId) Label.TRANSPARENT else interpolationId }
			return fill2D.executeFill2DAction(event.x, event.y) { fillInterval ->
				shapeInterpolationMode.addSelection(fillInterval)?.also { it.locked = true }
				currentTask = null
				fill2D.fill2D.release()
			}
		}
	}
}