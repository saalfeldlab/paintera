package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleStringProperty
import javafx.css.PseudoClass
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.util.Duration
import kotlinx.coroutines.Job
import net.imglib2.RandomAccessibleInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.logic.BoolType
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.StyleGroup
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.ai.SamEncoder
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.actions.*
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.control.modes.ShapeInterpolationMode
import org.janelia.saalfeldlab.paintera.control.modes.getInterpolantPrompt
import org.janelia.saalfeldlab.paintera.control.navigation.TranslationController
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.Fill2DTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.util.convertRAI
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.get

internal class ShapeInterpolationTool(
	private val controller: ShapeInterpolationController<*>,
	private val previousMode: ControlMode,
 	override val mode: ShapeInterpolationMode<*>,
	private var fill2D: ShapeInterpolationFillTool,
) : ViewerTool(mode) {


	override val actionSets: MutableList<ActionSet> by lazy {
		mutableListOf(
			*shapeInterpolationActions().filterNotNull().toTypedArray(),
			cancelShapeInterpolationTask()
		)
	}

	override fun newToolBarControl()  = super.newToolBarControl().also { item ->
		item.addStyleClass(NavigationTool.NAVIGATION_TOOL_STYLE)
	}
	override val name: String = "Shape Interpolation"
	override val keyTrigger = SHAPE_INTERPOLATION__TOGGLE_MODE
	private var currentJob: Job? = null

	override fun activate() {

		super.activate()
		//TODO Caleb: this should probably be in the `enter()` of the mode, and the `disabled translate` logic should also
		mode.disableUnfocusedViewers()
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
        disabledViewerActionsMap.forEach { (vat, actionSets) ->
            actionSets.forEach {
                vat.viewer().removeActionSet(it)
            }
        }
		disabledViewerActionsMap.clear()
		NavigationTool.deactivate()
		super.deactivate()
	}

	override val statusProperty = SimpleStringProperty().apply {

        val statusBinding = controller.controllerStateProperty.createNullableValueBinding(
            controller.currentDepthProperty,
            controller.sliceAtCurrentDepthProperty
        ) {
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

	private fun requestSamPredictionAtViewerPoint(vat: ViewerAndTransforms, runAfter: () -> Unit = {}) {
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
			requestSamPrediction(depth, refresh = true, provideGlobalToViewerTransform = resultActiveGlobalToViewer) {
				requestEagerEmbeddings(sortedSliceDepths)
				runAfter()
			}
		}
	}

	internal fun requestEagerEmbeddings(sliceDepths: List<Double>) {

        val eagerRequests = ArrayDeque((eagerRequestDepths(sliceDepths) ?: listOf(controller.currentDepth)))
		/* first and last are more likely to be used quickly, request them first */
        eagerRequests.removeFirstOrNull()?.let { requestEmbedding(it) }
        eagerRequests.removeLastOrNull()?.let { requestEmbedding(it) }

        while (eagerRequests.isNotEmpty()) {
			/* request the rest */
            eagerRequests.removeFirstOrNull()?.let {
				requestEmbedding(it)
			}
		}
	}


	internal fun requestEmbedding(depth: Double) {
        runCatching {
			mode.cacheLoadSamSliceInfo(depth)
        }.onFailure {
            LOG.warn(it) { "Failed to load and cache SamSliceInfo at depth: $depth " }
        }
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
		afterPrediction: (AffineTransform3D?) -> Unit = {},
	): AffineTransform3D {

		val newPrediction = mode.samSliceCache[depth] == null
		if (newPrediction)
			SamEncoder.cache.embeddingRequester.cancelPendingRequests()

		val samSliceInfo = mode.cacheLoadSamSliceInfo(depth, provideGlobalToViewerTransform = provideGlobalToViewerTransform)

		if (!newPrediction && refresh) {
			controller.getInterpolationImg(samSliceInfo.globalToViewerTransform, closest = true)?.run {
				val prompt = getInterpolantPrompt(mode.samStyleBoxToggle.get(), samSliceInfo.renderState)
				samSliceInfo.updatePrompt(prompt)
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

		if (moveToSlice) {
			mode.apply {
				moveTo(globalTransform)
			}
		}

		samTool.lastPredictionProperty.addListener { _, _, prediction ->
			prediction ?: let {
				afterPrediction(null)
				return@addListener
			}
			mode.addSelection(prediction.maskInterval, viewerMask, globalTransform) ?: let {
				afterPrediction(null)
				return@addListener
			}

            runCatching {
			    afterPrediction(globalTransform)
            }.exceptionOrNull()?.let { e ->
                LOG.warn(e) { "Error processing SAM prediction" }
            }

			samTool.cleanup()
			samTool.activeViewerProperty.unbind()
		}
		samTool.requestPrediction(samSliceInfo.prompt)
		return globalTransform
	}

	private fun shapeInterpolationActions(): Array<ActionSet?> {
		lateinit var autoSamCurrent: Action<*>
		lateinit var autoSamBisectAll: Action<*>
		lateinit var autoSamBisectCurrent: Action<*>
        lateinit var autoSamLeft: Action<*>
		lateinit var autoSamRight: Action<*>
        lateinit var autoSamBisectLeft: Action<*>
        lateinit var autoSamBisectRight: Action<*>
		with(controller) {
			return arrayOf(
				painteraActionSet("shape interpolation", PaintActionType.ShapeInterpolation) {
					verifyAll(KEY_PRESSED) { isControllerActive }
					KEY_PRESSED(SHAPE_INTERPOLATION__ACCEPT_INTERPOLATION) {
						createToolNode = { apply { addStyleClass(ShapeInterpolationStyle.ACCEPT_INTERPOLATION) } }
						onAction { mode.applyShapeInterpolationAndExitMode() }
						handleException {
							LOG.error(it) {}
							paintera.baseView.changeMode(previousMode)
						}
					}

					KEY_PRESSED(SHAPE_INTERPOLATION__TOGGLE_PREVIEW) {
						createToolNode = {
							val previewPseudoClass = PseudoClass.getPseudoClass("preview")
							controller.previewProperty.subscribe { preview ->
								pseudoClassStateChanged(previewPseudoClass, preview)
							}
							apply { addStyleClass(ShapeInterpolationStyle.TOGGLE_PREVIEW) }
						}
						onAction { controller.togglePreviewMode() }
						handleException {
							paintera.baseView.changeMode(previousMode)
						}
					}

					autoSamBisectAll = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICES_BISECT_ALL) {
						createToolNode = { apply { addStyleClass(ShapeInterpolationStyle.SLICE_BISECT)} }
						verify { SamEncoder.isHealthy }
						onAction {
							val bisectDepthIter = sortedSliceDepths
								.zipWithNext { first, second -> (first + second) / 2.0 }
								.iterator()

							while (bisectDepthIter.hasNext()) {
								val depth = bisectDepthIter.next()
								val isLast = !bisectDepthIter.hasNext()
								controller.freezeInterpolation = !isLast
                                val eagerRequestIfLast: (AffineTransform3D?) -> Unit = if (isLast) { _ -> requestEagerEmbeddings(sortedSliceDepths) } else { _ -> }
                                requestSamPrediction( depth, refresh = isLast, afterPrediction = eagerRequestIfLast )
							}
						}
					}

					autoSamBisectCurrent = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICES_BISECT) {
						verify { SamEncoder.isHealthy }
						onAction {
                            val eagerDepths = eagerRequestDepths(sortedSliceDepths) ?: listOf(currentDepth)
                            val requestDepth = when {
                                eagerDepths.size == 1 -> eagerDepths.first()
                                currentDepth < eagerDepths.first() -> eagerDepths.first()
                                currentDepth > eagerDepths.last() -> eagerDepths.last()
                                eagerDepths.size >= 2 -> eagerDepths.zipWithNext()
                                    .firstOrNull() { (prev, next) -> currentDepth in prev..next }
                                    ?.let { (prev, next) ->
                                        val prevIsCloser = currentDepth - prev < next - currentDepth
                                        if (prevIsCloser) prev else next
                                    }

                                else -> null
							} ?: return@onAction

                            requestSamPrediction(requestDepth, refresh = true) {
                                eagerRequestDepths(sortedSliceDepths)
                            }
						}
					}
                    autoSamLeft = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_LEFT) {
                        createToolNode = { apply { addStyleClass(ShapeInterpolationStyle.SLICE_LEFT) } }
                        verify { SamEncoder.isHealthy }
                        onAction { event ->
                            /* if triggered by an event, then check if we need to flip axes;
                            * If so, call the flipped version, WITHOUT an event */
                            if (event != null && mode.depthAxisFlippedRelativeToGlobal()) {
                                autoSamRight(null)
                                return@onAction
                            }

                            val requestDepth = bestEagerRequest(
                                sortedSliceDepths,
                                currentDepth,
                                RequestDistance.FAR,
                                RequestDirection.PREV
                            )

                            /* request the next depth immediately, request the rest when this one finishes */
                            requestSamPrediction(requestDepth, moveToSlice = true) {
                                requestEagerEmbeddings(sortedSliceDepths)
                            }
                        }
                    }
					autoSamRight = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_RIGHT) {
						createToolNode = { apply { addStyleClass(ShapeInterpolationStyle.SLICE_RIGHT)} }
						verify { SamEncoder.isHealthy }
						onAction { event ->
							/* if triggered by an event, then check if we need to flip axes;
							* If so, call the flipped version, WITHOUT an event */
							if (event != null && mode.depthAxisFlippedRelativeToGlobal()) {
								autoSamLeft(null)
								return@onAction
							}

                            val requestDepth = bestEagerRequest(
                                sortedSliceDepths,
                                currentDepth,
                                RequestDistance.FAR,
                                RequestDirection.NEXT
                            )
                            /* request the next depth immediately, request the rest when this one finishes */
                            requestSamPrediction(requestDepth, moveToSlice = true) {
                                requestEagerEmbeddings(sortedSliceDepths)
                            }
                        }
                    }

                    autoSamBisectLeft = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_BISECT_LEFT) {
                        verify { SamEncoder.isHealthy }
                        onAction { event ->
                            /* if triggered by an event, then check if we need to flip axes;
                            * If so, call the flipped version, WITHOUT an event */
                            if (event != null && mode.depthAxisFlippedRelativeToGlobal()) {
                                autoSamBisectRight(null)
                                return@onAction
                            }
                            val requestDepth = bestEagerRequest(
                                sortedSliceDepths,
                                currentDepth,
                                RequestDistance.NEAR,
                                RequestDirection.PREV
                            )
                            /* request the next depth immediately, request the rest when this one finishes */
                            requestSamPrediction(requestDepth, moveToSlice = true) {
                                requestEagerEmbeddings(sortedSliceDepths)
                            }
                        }
                    }
                    autoSamBisectRight = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_BISECT_RIGHT) {
                        verify { SamEncoder.isHealthy }
                        onAction { event ->
                            /* if triggered by an event, then check if we need to flip axes;
                            * If so, call the flipped version, WITHOUT an event */
                            if (event != null && mode.depthAxisFlippedRelativeToGlobal()) {
                                autoSamBisectLeft(null)
                                return@onAction
                            }

                            val requestDepth = bestEagerRequest(
                                sortedSliceDepths,
                                currentDepth,
                                RequestDistance.NEAR,
                                RequestDirection.NEXT
                            )
							/* trigger the prediction and move there when done */
							requestSamPrediction(requestDepth, moveToSlice = true) {
								/* request the next depth immediately, request the rest when this one finishes */
								requestEagerEmbeddings(sortedSliceDepths)
							}
						}
					}
					KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__REFINE_AUTO_SLICES) {
						onAction {
							val refineSliceIter = mode.samSliceCache
                                .filter { (_, info) -> !info.locked && !info.preGenerated}
								.iterator()

							while (refineSliceIter.hasNext()) {
								val depth = refineSliceIter.next().let { (depth, _) -> depth.toDouble()}
								val isLast = !refineSliceIter.hasNext()
								controller.freezeInterpolation = !isLast
								/* remove old slice before refining */
								deleteSliceAt(depth, reinterpolate = false)
								val eagerRequestIfLast: (AffineTransform3D?) -> Unit = if (isLast) { _ -> requestEagerEmbeddings(sortedSliceDepths) } else { _ -> }
								requestSamPrediction( depth, refresh = true, afterPrediction = eagerRequestIfLast )
							}
						}
					}
					autoSamCurrent = KEY_PRESSED(SHAPE_INTERPOLATION__AUTO_SAM__NEW_SLICE_HERE) {
						verify { SamEncoder.isHealthy }
						onAction {
							requestSamPrediction(currentDepth, refresh = true) {
                                requestEagerEmbeddings(sortedSliceDepths)
                            }
						}
					}

					listOf(SHAPE_INTERPOLATION__REMOVE_SLICE_1, SHAPE_INTERPOLATION__REMOVE_SLICE_2).forEach { key ->
						KEY_PRESSED(key) {
							filter = true
							consume = false
							onAction {
								deleteSliceAt(currentDepth)?.also { mode.samSliceCache -= currentDepth }
							}
						}
					}
					MOUSE_CLICKED {
						name = "select object in current slice"
						verifyNoKeysDown()
						verifyEventNotNull()
						verify { !paintera.mouseTracker.isDragging }
						verify { mode.activeTool !is Fill2DTool }
						verify { it!!.button == MouseButton.PRIMARY && !it.isControlDown } // respond to primary click
						verify { controllerState != ShapeInterpolationController.ControllerState.Interpolate } // need to be in the select state
						onActionWithState({ ShapeInterpolationSelectIDToFillState<Nothing, Nothing>() }) { event ->
							event!! /* Safe because verifyNotNull. Would be nice to not need this */

							fun fillFromViewerMask() {
								val prevSlice = controller.sliceAt(currentDepth)!!.also {
									deleteSliceAt(currentDepth, reinterpolate = false)
									source.resetMasks(false)
									/* replace mask with new one after deleting slice */
									mask = getMask()
								}

								val prevMask = prevSlice.mask
								val filter = prevMask.viewerImg.convertRAI(BoolType()) { a, b -> b.set(a.integerLong == interpolationId) }

								/* get value at position */
								currentJob = fillObjectInSlice(event, mask, true, filter)?.apply {
									invokeOnCompletion { cause ->
										prevSlice.maskBoundingBox?.let { interval ->
											cause?.let {
												addSelection(interval, true, prevSlice.globalTransform, prevSlice.mask)
											}
										} ?: requestRepaint(prevSlice.globalBoundingBox)
									}
								}
							}

							fun fillFromSourceMask() {
								val prevSlice = controller.sliceAt(currentDepth)?.also {
									deleteSliceAt(currentDepth, reinterpolate = false)
									source.resetMasks(false)
									/* replace mask with new one after deleting slice */
									mask = getMask()
								}
								/* get value at position */
								currentJob = fillObjectInSlice(event, mask, true)?.apply {
									invokeOnCompletion { cause ->
										prevSlice?.maskBoundingBox?.let { interval ->
											cause?.let {
												addSelection(interval, true, prevSlice.globalTransform, prevSlice.mask)
											}
										} ?: requestRepaint(prevSlice?.globalBoundingBox)
									}
								}
							}
							if (fillFromViewer(event))
								fillFromViewerMask()
							else if (fillFromSource(event))
								fillFromSourceMask()
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
						onActionWithState({ ShapeInterpolationSelectIDToFillState<Nothing, Nothing>() }) { event ->
							currentJob = fillObjectInSlice(event!!, mask)
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

	private fun fillObjectInSlice(
		event: MouseEvent,
		mask: ViewerMask,
		replaceExistingSlice: Boolean = false,
		filter: RandomAccessibleInterval<BoolType>? = null,
	): Job? {
		with(controller) {
			fill2D.fill2D.viewerMask = mask

			/* If a current slice exists, try to preserve it if cancelled */
			currentSliceMaskInterval?.also {
				mask.pushNewImageLayer()
			}

			val pointInMask = mask.displayPointToMask(event.x, event.y, pointInCurrentDisplay = true)
			val pointInSource = pointInMask.positionAsRealPoint().also { mask.initialMaskToSourceTransform.apply(it, it) }
			val info = mask.info
			val sourceLabel = source.getInterpolatedDataSource(info.time, info.level, null).getAt(pointInSource).integerLong
			if (sourceLabel == Label.BACKGROUND || sourceLabel.toULong() > Label.MAX_ID.toULong()) {
				return null
			}

			val maskLabel = mask.viewerImg.extendValue(Label.INVALID)[pointInMask].integerLong
			fill2D.brushProperties?.brushDepth = 1.0
			fill2D.fillLabel = { if (maskLabel == interpolationId) Label.TRANSPARENT else interpolationId }
			return fill2D.executeFill2DAction(event.x, event.y, mask, filter) { fillInterval ->
				mode.addSelection(fillInterval, replaceExistingSlice = replaceExistingSlice)?.also { it.locked = true }
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

		enum class ShapeInterpolationStyle(val style: String, vararg classes: String) : StyleGroup by StyleGroup.of(style, *classes) {
			AUTO_SAM("auto-sam"),
			SLICE_LEFT("slice-left", AUTO_SAM),
			SLICE_RIGHT("slice-right", AUTO_SAM),
			SLICE_BISECT("slice-bisect", AUTO_SAM),
			ACCEPT_INTERPOLATION("accept-shape-interpolation", Style.ACCEPT_ICON),
			TOGGLE_PREVIEW("toggle-preview", Style.FONT_ICON);

			constructor(style: String, vararg styles: StyleGroup) : this(style, *styles.flatMap { it.classes.toList() }.toTypedArray())
			constructor(style: String) : this(style, *emptyArray<StyleGroup>())

        }

        /**
         * Best guess depths to be used to eagerly request image embeddings at.
         *  - if 0 existing slices, no eager depths are calculated (null) `(0 -> null)`
         *  - if 1 existing slice, eager depths are +- fallbackDistance from the existing slice `(1 -> 2)`
         *  - if 2 or more slices, an eager depth is calculated between each pair, and 1 eager depth
         *  	beyond the first and last at the same distance away as the adjacent slice. `(n -> n+2)`
         *
         * @param sliceDepths to get eager depths relative to.
         * @return list of depths, or null if no eager requests could be determined (e.g. no existing slices)
         */
        internal fun eagerRequestDepths(sliceDepths: List<Double>, fallbackDistance: Double = 20.0): List<Double>? {

            val sliceDepths = sliceDepths.sorted()

            if (sliceDepths.isEmpty())
                return null

            if (sliceDepths.size == 1) {
                val sliceDepth = sliceDepths.first()
                return listOf(sliceDepth - fallbackDistance, sliceDepths.first() + fallbackDistance)
            }

            val adjacentSliceDepths = sliceDepths.zipWithNext()
            val eagerRequestDepths = mutableListOf<Double>()

            /* add bisect slices */
            fun bisectSlices(prev: Double, next: Double) = prev + ((next - prev) / 2.0)
            adjacentSliceDepths.forEach { (prev, next) -> eagerRequestDepths += bisectSlices(prev, next) }

            /* should always be at least 1 slice pair, since we early return if only 0 or 1 slices */

            /* add new first depth */
            adjacentSliceDepths.firstOrNull()?.let { (first, second) ->
                val distance = second - first
                val eagerFirstRequest = first - distance
                eagerRequestDepths.add(0, eagerFirstRequest)
            }

            /* add new last depth */
            adjacentSliceDepths.lastOrNull()?.let { (first, second) ->
                val distance = second - first
                val eagerLastRequest = second + distance
                eagerRequestDepths.add(eagerLastRequest)
            }


            return eagerRequestDepths
        }

        internal enum class RequestDistance {
            /** Farthest slice depth in a given direction (most positive or negative) */
            FAR,

            /** Nearest slice depth in a given direction (next positive or negative) */
            NEAR
        }

        internal enum class RequestDirection {
            /** Previous slice depth relative to a given depth (more negative) */
            PREV,

            /** Next slice depth relative to a given depth (more positive) */
            NEXT
        }

        /**
         * Best eager request should return
         *  - the current depth if sliceDepths is empty
         *  - if currentDepth is on a slice, prev -> more negative eager request, next -> more positive eager request
         *  - the eagerRequestDepth on the `currentDepth` side if sliceDepths has 1 depth
         *  - the nearest eagerDepth if between slices
         *
         * @param sliceDepths current existing slice depths
         * @param currentDepth the current depth position
         * @param distance FAR or NEAR
         * @param direction PREV or NEXT
         * @return
         */
        internal fun bestEagerRequest(
            sliceDepths: List<Double>,
            currentDepth: Double,
            distance: RequestDistance,
            direction: RequestDirection
        ): Double {
            val eagerDepths = eagerRequestDepths(sliceDepths) ?: return currentDepth

            /* If we want the farthest in some direction, that's the easy case
            * since it doesn't matter where we currently are */
            if (distance == RequestDistance.FAR) {
                return when (direction) {
                    RequestDirection.PREV -> eagerDepths.first()
                    RequestDirection.NEXT -> eagerDepths.last()
                }
            }

            /* If we are at a slice, we need to know which direction to approach from */
            val currentSliceIdx = sliceDepths.indexOf(currentDepth)
            if (currentSliceIdx != -1) {
                /* at a slice */
                return when (direction) {
                    RequestDirection.PREV -> eagerDepths.last { it < currentDepth }
                    RequestDirection.NEXT -> eagerDepths.first { it > currentDepth }
                }
            }

            /* if we know we are not at a slice, then there should be no ambiguity in the slice ranges.
            * Bookend to handle when we are outside current slices.  */
            val bookendSliceDepths = sliceDepths.toMutableList().also {
                it.addFirst(-Double.MAX_VALUE)
                it.addLast(Double.MAX_VALUE)
            }

            val (prev, next) = bookendSliceDepths.zipWithNext().first { (prev, next) -> currentDepth in prev..next }
            return eagerDepths.firstOrNull { it in prev..next } ?: currentDepth
		}
	}
}