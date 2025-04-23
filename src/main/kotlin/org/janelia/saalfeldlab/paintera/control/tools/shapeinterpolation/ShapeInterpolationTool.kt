package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.util.Duration
import kotlinx.coroutines.Job
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.GlyphScaleView
import org.janelia.saalfeldlab.fx.ui.ScaleView
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.control.modes.ShapeInterpolationMode
import org.janelia.saalfeldlab.paintera.control.modes.getInterpolantPrompt
import org.janelia.saalfeldlab.paintera.control.navigation.TranslationController
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.Fill2DTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.get

internal class ShapeInterpolationTool(
	private val controller: ShapeInterpolationController<*>,
	private val previousMode: ControlMode,
	private val shapeInterpolationMode: ShapeInterpolationMode<*>,
	private var fill2D: ShapeInterpolationFillTool
) : ViewerTool(shapeInterpolationMode) {


	override val actionSets: MutableList<ActionSet> by lazy {
		mutableListOf(
			*shapeInterpolationActions().filterNotNull().toTypedArray(),
			cancelShapeInterpolationTask()
		)
	}

	override val graphic = { GlyphScaleView(FontAwesomeIconView().also { it.styleClass += listOf("navigation-tool") }) }
	override val name: String = "Shape Interpolation"
	override val keyTrigger = SHAPE_INTERPOLATION__TOGGLE_MODE
	private var currentJob: Job? = null

	override fun activate() {

		super.activate()
		shapeInterpolationMode.disableUnfocusedViewers() //TODO Caleb: this should be in the `enter()` of the mode, and the `disabled translate` logic should also
		/* This action set allows us to translate through the unfocused viewers */
		paintera.baseView.orthogonalViews().viewerAndTransforms()
			.filter { !it.viewer().isFocusable }
			.forEach { disabledViewerAndTransform ->
				val disabledTranslationActions = disabledViewerActionsMap.computeIfAbsent(disabledViewerAndTransform, disabledViewerActions)
				val disabledViewer = disabledViewerAndTransform.viewer()
				disabledTranslationActions.forEach { disabledViewer.installActionSet(it) }
			}
		/* We want to bind it to our activeViewer bindings instead of the default. */
		NavigationTool.activate()
	}

	override fun deactivate() {
		disabledViewerActionsMap.forEach { (vat, actionSets) -> actionSets.forEach { vat.viewer().removeActionSet(it) } }
		disabledViewerActionsMap.clear()
		NavigationTool.deactivate()
		super.deactivate()
	}

	override val statusProperty = SimpleStringProperty().apply {

		val statusBinding = controller.controllerStateProperty.createNullableValueBinding(controller.currentDepthProperty, controller.sliceAtCurrentDepthProperty) {
			controller.getStatusText()
		}
		bind(statusBinding)
	}

	private fun ShapeInterpolationController<*>.getStatusText() =
		when {
			controllerState == ShapeInterpolationController.ControllerState.Interpolate -> "Interpolating..."
			numSlices == 0 -> "Select or Paint ..."
			else -> {
				val sliceIdx = sortedSliceDepths.indexOf(currentDepth)
				"Slice: ${if (sliceIdx == -1) "N/A" else "${sliceIdx + 1}"} / ${numSlices}"
			}
		}

	private val disabledViewerActionsMap = mutableMapOf<ViewerAndTransforms, Array<ActionSet>>()

	private val disabledViewerActions = { vat: ViewerAndTransforms ->
		val globalTransformManager = paintera.baseView.manager()
		val translator = TranslationController(globalTransformManager, vat.globalToViewerTransform)
		arrayOf(
			painteraDragActionSet("disabled_view_translate_xy", NavigationActionType.Pan) {
				relative = true
				verify { it.isSecondaryButtonDown }
				verify { controller.controllerState != ShapeInterpolationController.ControllerState.Interpolate }
				onDrag { translator.translate(it.x - startX, it.y - startY) }
			},
			painteraActionSet("disabled_view_move_to_cursor", NavigationActionType.Pan, ignoreDisable = true) {
				MOUSE_CLICKED(MouseButton.PRIMARY) {
					verify("only double click") { it?.clickCount!! > 1 }
					onAction {
						val viewer = vat.viewer()
						val x = viewer.width / 2 - viewer.mouseXProperty.value
						val y = viewer.height / 2 - viewer.mouseYProperty.value
						translator.translate(x, y, 0.0, Duration.millis(500.0))
					}
				}
			},
			painteraActionSet("disabled_view_auto_sam_click", PaintActionType.SegmentAnything, ignoreDisable = true) {
				MOUSE_CLICKED(MouseButton.PRIMARY, withKeysDown = arrayOf(KeyCode.SHIFT)) {
					onAction { requestSamPredictionAtViewerPoint(vat) }
				}
			}
		)
	}

	private fun requestSamPredictionAtViewerPoint(vat: ViewerAndTransforms, requestMidPoint: Boolean = true, runAfter: () -> Unit = {}) {
		with(controller) {
			val viewer = vat.viewer()
			val dX = viewer.width / 2 - viewer.mouseXProperty.value
			val dY = viewer.height / 2 - viewer.mouseYProperty.value
			val delta = doubleArrayOf(dX, dY, 0.0)

			val globalTransform = paintera.baseView.manager().transform
			TranslationController.translateFromViewer(
				globalTransform,
				vat.globalToViewerTransform.transformCopy,
				delta
			)

			val resultActiveGlobalToViewer = activeViewerAndTransforms!!.displayTransform.transformCopy
				.concatenate(activeViewerAndTransforms!!.viewerSpaceToViewerTransform.transformCopy)
				.concatenate(globalTransform)

			val depth = depthAt(resultActiveGlobalToViewer)
			if (!requestMidPoint) {
				requestSamPrediction(depth, refresh = true, provideGlobalToViewerTransform = resultActiveGlobalToViewer) { runAfter() }
			} else {
				requestSamPrediction(depth, refresh = true, provideGlobalToViewerTransform = resultActiveGlobalToViewer) {
					val depths = sortedSliceDepths
					val sliceIdx = depths.indexOf(depth)
					if (sliceIdx - 1 >= 0) {
						val prevHalfDepth = (depths[sliceIdx] + depths[sliceIdx - 1]) / 2.0
						requestEmbedding(prevHalfDepth)
					}
					if (sliceIdx + 1 < depths.size) {
						val nextHalfDepth = (depths[sliceIdx + 1] + depths[sliceIdx]) / 2.0
						requestEmbedding(nextHalfDepth)
					}
					runAfter()
				}
			}

		}
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
		provideGlobalToViewerTransform: AffineTransform3D? = null,
		afterPrediction: (AffineTransform3D) -> Unit = {}
	): AffineTransform3D {

		val newPrediction = shapeInterpolationMode.samSliceCache[depth] == null
		if (newPrediction)
			SamEmbeddingLoaderCache.cancelPendingRequests()

		val samSliceInfo = shapeInterpolationMode.cacheLoadSamSliceInfo(depth, provideGlobalToViewerTransform = provideGlobalToViewerTransform)

		if (!newPrediction && refresh) {
			controller.getInterpolationImg(samSliceInfo.globalToViewerTransform, closest = true)?.run {
				val points = getInterpolantPrompt(shapeInterpolationMode.samStyleBoxToggle.get())
				samSliceInfo.updatePrediction(points)
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
			shapeInterpolationMode.addSelection(prediction.maskInterval, viewerMask, globalTransform) ?: return@addListener

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

	private fun shapeInterpolationActions(): Array<ActionSet?> {
		lateinit var autoSamCurrent: Action<*>
		lateinit var autoSamLeft: Action<*>
		lateinit var autoSamBisectAll: Action<*>
		lateinit var autoSamBisectCurrent: Action<*>
		lateinit var autoSamRight: Action<*>
		with(controller) {
			return arrayOf(
				painteraActionSet("shape interpolation", PaintActionType.ShapeInterpolation) {
					verifyAll(KEY_PRESSED) { isControllerActive }
					KEY_PRESSED(SHAPE_INTERPOLATION__ACCEPT_INTERPOLATION) {
						graphic = { GlyphScaleView(FontAwesomeIconView().apply { styleClass += listOf("accept", "accept-shape-interpolation") }) }
						onAction { shapeInterpolationMode.applyShapeInterpolationAndExitMode() }
						handleException {
							LOG.error(it) {}
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

					autoSamLeft = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_LEFT) {
						graphic = { ScaleView().apply { styleClass += listOf("auto-sam", "slice-left") } }
						verify { SamEmbeddingLoaderCache.canReachServer }
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
					autoSamBisectAll = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICES_BISECT_ALL) {
						graphic = { ScaleView().apply { styleClass += listOf("auto-sam", "slice-bisect") } }
						verify { SamEmbeddingLoaderCache.canReachServer }
						onAction {
							val depths = sortedSliceDepths.toMutableList()
							val remainingRequest = SimpleIntegerProperty().apply {
								addListener { _, _, remaining ->
									if (remaining == 0) {
										controller.freezeInterpolation = false
										controller.setMaskOverlay()

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
								controller.freezeInterpolation = true
								remainingRequest.value++
								requestSamPrediction(it) {
									remainingRequest.value--
								}
							}
						}
					}

					autoSamBisectCurrent = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICES_BISECT) {
						verify { SamEmbeddingLoaderCache.canReachServer }
						onAction {
							val depths = sortedSliceDepths.toMutableList()
							val (left, right) = depths.zipWithNext().firstOrNull { (left, right) ->
								currentDepth in left..right
							} ?: return@onAction
							requestSamPrediction((right + left) * .5, refresh = true)
						}
					}
					autoSamRight = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_RIGHT) {
						graphic = { ScaleView().apply { styleClass += listOf("auto-sam", "slice-right") } }
						verify { SamEmbeddingLoaderCache.canReachServer }
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
					autoSamCurrent = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_HERE) {
						verify { SamEmbeddingLoaderCache.canReachServer }
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

							fill2D.fill2D.viewerMask = mask
							val pointInMask = mask.displayPointToMask(event!!.x, event.y, pointInCurrentDisplay = true)
							val pointInSource = pointInMask.positionAsRealPoint().also { mask.initialMaskToSourceTransform.apply(it, it) }
							val info = mask.info
							val sourceLabel = source.getInterpolatedDataSource(info.time, info.level, null).getAt(pointInSource).integerLong
							return@verify sourceLabel != Label.BACKGROUND && sourceLabel.toULong() <= Label.MAX_ID.toULong()
						}
						onAction { event ->
							val prevSlice = controller.sliceAt(currentDepth)?.also {
								deleteSliceAt(currentDepth, reinterpolate = false)
							}
							/* get value at position */
							currentJob = fillObjectInSlice(event!!, true)?.apply {
								invokeOnCompletion { cause ->
									prevSlice?.maskBoundingBox?.let { interval ->
										cause?.let {
											addSelection(interval, true, prevSlice.globalTransform, prevSlice.mask)
										}
									} ?: requestRepaint(prevSlice?.globalBoundingBox)
								}
							}
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
							currentJob = fillObjectInSlice(event!!)
						}
					}
				},
				DeviceManager.xTouchMini?.let { device ->
					painteraMidiActionSet("AutoSam", device, activeViewer!!) {
						MidiToggleEvent.BUTTON_TOGGLE(4) {
							name = "preview"
							afterRegisterEvent = {
								toggleDisplayProperty.bind(controller.previewProperty)
							}
							onAction { controller.togglePreviewMode() }
						}
						MidiButtonEvent.BUTTON_PRESSED(5) {
							name = "left"
							verifyEventNotNull()
							onAction { autoSamLeft() }
						}
						MidiButtonEvent.BUTTON_PRESSED(6) {
							name = "bisect.all"
							verifyEventNotNull()
							onAction { autoSamBisectAll() }
						}
						MidiButtonEvent.BUTTON_PRESSED(7) {
							name = "right"
							verifyEventNotNull()
							onAction { autoSamRight() }
						}
						MidiButtonEvent.BUTTON_PRESSED(14) {
							name = "bisect.current"
							verifyEventNotNull()
							onAction { autoSamBisectCurrent() }
						}
						MidiButtonEvent.BUTTON_PRESSED(15) {
							name = "current"
							verifyEventNotNull()
							onAction { autoSamCurrent() }
						}
					}
				}
			)
		}
	}


	private fun cancelShapeInterpolationTask(): ActionSet {
		return painteraActionSet("cancel shape interpolation task", PaintActionType.ShapeInterpolation, ignoreDisable = true) {
			with(controller) {
				verifyAll(KEY_PRESSED, "Controller is not active") { isControllerActive }
				KEY_PRESSED(CANCEL) {
					name = "cancel current shape interpolation tool task"
					filter = true
					verify("No task to  cancel") { currentJob != null }
					onAction {
						currentJob?.cancel()
						currentJob = null
					}
				}
			}
		}
	}

	private fun fillObjectInSlice(event: MouseEvent, replaceExistingSlice: Boolean = false): Job? {
		with(controller) {
			source.resetMasks(false)
			val mask = getMask()

			/* If a current slice exists, try to preserve it if cancelled */
			currentSliceMaskInterval?.also {
				mask.pushNewImageLayer()
			}

			fill2D.fill2D.viewerMask = mask
			val pointInMask = mask.displayPointToMask(event.x, event.y, pointInCurrentDisplay = true)
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
				shapeInterpolationMode.addSelection(fillInterval, replaceExistingSlice = replaceExistingSlice)?.also { it.locked = true }
				currentJob = null
				fill2D.fill2D.release()
			}?.also { job ->
				job.invokeOnCompletion { cause ->
					cause?.let {
						mask.popImageLayer()
						controller.setMaskOverlay()
					}
				}
			}
		}
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}