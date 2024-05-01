package org.janelia.saalfeldlab.paintera.control.modes

import bdv.fx.viewer.render.RenderUnitState
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.Event
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import net.imglib2.Interval
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.img.array.ArrayImgs
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.view.IntervalView
import net.imglib2.view.Views
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.midi.ToggleAction
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ui.GlyphScaleView
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.cache.HashableTransform.Companion.hashable
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController.ControllerState.Moving
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController.EditSelectionChoice
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache.calculateTargetSamScreenScaleFactor
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.createViewerMask
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.paint.*
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationFillTool
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationPaintBrushTool
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationSAMTool
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.util.*
import paintera.net.imglib2.view.BundleView
import kotlin.collections.forEach
import kotlin.collections.set

class ShapeInterpolationMode<D : IntegerType<D>>(val controller: ShapeInterpolationController<D>, private val previousMode: ControlMode) : AbstractToolMode() {

	internal val samSliceCache = SamSliceCache()

	private val paintBrushTool = ShapeInterpolationPaintBrushTool(activeSourceStateProperty, this)
	private val fill2DTool = ShapeInterpolationFillTool(controller, activeSourceStateProperty, this)
	private val samTool = ShapeInterpolationSAMTool(controller, activeSourceStateProperty, this@ShapeInterpolationMode)
	private val shapeInterpolationTool = ShapeInterpolationTool(controller, previousMode, this@ShapeInterpolationMode, this@ShapeInterpolationMode.fill2DTool)

	override val defaultTool: Tool? by lazy { shapeInterpolationTool }
	override val modeActions by lazy { modeActions() }

	override val allowedActions = AllowedActions.AllowedActionsBuilder()
		.add(PaintActionType.ShapeInterpolation, PaintActionType.Paint, PaintActionType.Erase, PaintActionType.SetBrushSize, PaintActionType.Fill)
		.add(MenuActionType.ToggleMaximizeViewer, MenuActionType.DetachViewer)
		.add(NavigationActionType.Pan, NavigationActionType.Slice, NavigationActionType.Zoom)
		.create()

	private val toolTriggerListener = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
		new?.viewer()?.apply { modeActions.forEach { installActionSet(it) } }
		old?.viewer()?.apply { modeActions.forEach { removeActionSet(it) } }
	}

	private val samNavigationRequestListener = ChangeListener { _, _, curViewer ->
		SamEmbeddingLoaderCache.stopNavigationBasedRequests()
		curViewer?.let {
			SamEmbeddingLoaderCache.startNavigationBasedRequests(curViewer)
		}
	}

	override val tools: ObservableList<Tool> by lazy {
		FXCollections.observableArrayList(
			shapeInterpolationTool,
			paintBrushTool,
			fill2DTool,
			samTool
		)
	}

	fun reset(reason: String? = null) {
		reason?.let { LOG.error { it } }
		paintera.baseView.changeMode(previousMode)
	}

	override fun enter() {
		activeViewerProperty.addListener(toolTriggerListener)
		paintera.baseView.disabledPropertyBindings[controller] = controller.isBusyProperty
		super.enter()
		/* unbind the activeViewerProperty, since we disabled other viewers during ShapeInterpolation mode*/
		activeViewerProperty.unbind()
		/* Try to initialize the tool, if state is valid. If not, change back to previous mode. */
		val viewerAndTransforms = activeViewerProperty.get() ?: return reset("No Active Viewer")
		SamEmbeddingLoaderCache.startNavigationBasedRequests(viewerAndTransforms)
		controller.apply {
			if (!isControllerActive && source.currentMask == null && source.isApplyingMaskProperty.not().get()) {
				modifyFragmentAlpha()
				switchTool(shapeInterpolationTool)
				enterShapeInterpolation(viewerAndTransforms.viewer())
				shapeInterpolationTool.apply {
					requestEmbedding(currentDepth)
				}
			}
		}
	}

	override fun exit() {
		super.exit()
		SamEmbeddingLoaderCache.stopNavigationBasedRequests()
		paintera.baseView.disabledPropertyBindings.remove(controller)
		controller.resetFragmentAlpha()
		activeViewerProperty.removeListener(toolTriggerListener)
		activeViewerProperty.removeListener(samNavigationRequestListener)
		synchronized(samSliceCache) {
			samSliceCache.clear()
		}
		enableAllViewers()
	}

	private fun ShapeInterpolationController<*>.modifyFragmentAlpha() {
		/* set the converter fragment alpha to be the same as the segment. We do this so we can use the fragment alpha for the
		* selected objects during shape interpolation flood fill. This needs to be un-done in the #deactivate */
		converter.activeFragmentAlphaProperty().apply {
			activeSelectionAlpha = get().toDouble() / 255.0
			set(converter.activeSegmentAlphaProperty().get())
		}
	}

	private fun ShapeInterpolationController<*>.resetFragmentAlpha() {
		/* Add the activeFragmentAlpha back when we are done */
		apply {
			converter.activeFragmentAlphaProperty().set((activeSelectionAlpha * 255).toInt())
		}
	}

	private fun modeActions(): List<ActionSet> {
		return mutableListOf(
			painteraActionSet(CANCEL) {
				with(controller) {
					verifyAll(KEY_PRESSED, "Shape Interpolation Controller is Active ") { isControllerActive }
					verifyAll(Event.ANY, "Shape Interpolation Tool is Active") { activeTool is ShapeInterpolationTool }
					val exitMode = { _ : Event? ->
						exitShapeInterpolation(false)
						paintera.baseView.changeMode(previousMode)
					}
					KEY_PRESSED(CANCEL) {
						graphic = { GlyphScaleView(FontAwesomeIconView().apply { styleClass += listOf("reject", "reject-shape-interpolation") }) }
						onAction(exitMode)
					}
					KEY_PRESSED(SHAPE_INTERPOLATION__TOGGLE_MODE) {
						onAction(exitMode)
					}
				}
			},
			painteraActionSet("paint during shape interpolation", PaintActionType.Paint) {
				KEY_PRESSED(paintBrushTool.keyTrigger) {
					name = "switch to paint tool"
					verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
					onAction { switchTool(paintBrushTool) }
				}

				KEY_RELEASED(paintBrushTool.keyTrigger) {
					name = "switch back to shape interpolation tool from paint brush"
					filter = true
					verify { activeTool is PaintBrushTool }
					onAction { switchTool(shapeInterpolationTool) }
				}

				KEY_PRESSED(fill2DTool.keyTrigger) {
					name = "switch to fill2d tool"
					verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
					onAction { switchTool(fill2DTool) }
				}
				KEY_RELEASED(fill2DTool.keyTrigger) {
					name = "switch to shape interpolation tool from fill2d"
					filter = true
					verify { activeTool is Fill2DTool }
					onAction {
						switchTool(shapeInterpolationTool)
					}
				}
				KEY_PRESSED(samTool.keyTrigger) {
					name = "toggle SAM tool"
					verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
					onAction {
						val nextTool = if (activeTool != samTool) samTool else shapeInterpolationTool
						switchTool(nextTool)
					}
				}
			},
			painteraActionSet("key slice navigation") {
				keyPressEditSelectionAction(EditSelectionChoice.First, SHAPE_INTERPOLATION__SELECT_FIRST_SLICE)
				keyPressEditSelectionAction(EditSelectionChoice.Last, SHAPE_INTERPOLATION__SELECT_LAST_SLICE)
				keyPressEditSelectionAction(EditSelectionChoice.Previous, SHAPE_INTERPOLATION__SELECT_PREVIOUS_SLICE)
				keyPressEditSelectionAction(EditSelectionChoice.Next, SHAPE_INTERPOLATION__SELECT_NEXT_SLICE)
			},
			DeviceManager.xTouchMini?.let { device ->
				activeViewerProperty.get()?.viewer()?.let { viewer ->
					painteraMidiActionSet("midi paint tool switch actions", device, viewer, PaintActionType.Paint) {
						val toggleToolActionMap = mutableMapOf<Tool, ToggleAction>()
						activeToolProperty.addListener { _, old, new ->
							toggleToolActionMap[old]?.updateControlSilently(MCUButtonControl.TOGGLE_OFF)
							toggleToolActionMap[new]?.updateControlSilently(MCUButtonControl.TOGGLE_ON)
						}
						toggleToolActionMap[shapeInterpolationTool] = MidiToggleEvent.BUTTON_TOGGLE(0) {
							name = "midi switch back to shape interpolation tool"
							filter = true
							onAction {
								InvokeOnJavaFXApplicationThread {
									if (activeTool != shapeInterpolationTool)
										switchTool(shapeInterpolationTool)
									/* If triggered, ensure toggle is on. Only can be off when switching to another tool */
									updateControlSilently(MCUButtonControl.TOGGLE_ON)
								}
							}
						}
						toggleToolActionMap[paintBrushTool] = MidiToggleEvent.BUTTON_TOGGLE(1) {
							name = "midi switch to paint tool"
							verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
							onAction {
								InvokeOnJavaFXApplicationThread {
									if (activeTool == paintBrushTool) {
										switchTool(shapeInterpolationTool)
									} else {
										switchTool(paintBrushTool)
										paintBrushTool.enteredWithoutKeyTrigger = true
									}
								}
							}
						}
						toggleToolActionMap[fill2DTool] = MidiToggleEvent.BUTTON_TOGGLE(2) {
							name = "midi switch to fill2d tool"
							verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
							onAction {
								InvokeOnJavaFXApplicationThread {
									if (activeTool == fill2DTool) {
										switchTool(shapeInterpolationTool)
									} else {
										switchTool(fill2DTool)
										fill2DTool.enteredWithoutKeyTrigger = true
									}
								}
							}
						}
						toggleToolActionMap[samTool] = MidiToggleEvent.BUTTON_TOGGLE(3) {
							name = "midi switch to sam tool"
							verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
							onAction {
								InvokeOnJavaFXApplicationThread {
									if (activeTool == samTool) {
										switchTool(shapeInterpolationTool)
									} else {
										switchTool(samTool)
										samTool.enteredWithoutKeyTrigger = true
									}
								}
							}
						}
						with(controller) {
							MidiButtonEvent.BUTTON_PRESED(9) {
								name = "midi go to first slice"
								verify { controllerState != Moving }
								onAction { editSelection(EditSelectionChoice.First) }

							}
							MidiButtonEvent.BUTTON_PRESED(10) {
								name = "midi go to previous slice"
								verify { controllerState != Moving }
								onAction { editSelection(EditSelectionChoice.Previous) }

							}
							MidiButtonEvent.BUTTON_PRESED(11) {
								name = "midi go to next slice"
								verify { controllerState != Moving }
								onAction { editSelection(EditSelectionChoice.Next) }

							}
							MidiButtonEvent.BUTTON_PRESED(12) {
								name = "midi go to last slice"
								verify { controllerState != Moving }
								onAction { editSelection(EditSelectionChoice.Last) }
							}
						}
					}
				}
			}
		).filterNotNull()
	}


	fun switchAndApplyShapeInterpolationActions(toolActions: ActionSet) {
		with(toolActions) {
			KEY_PRESSED(CANCEL) {
				name = "cancel_to_shape_interpolation_tool"
				onAction {
					switchTool(shapeInterpolationTool)
					controller.setMaskOverlay(replaceExistingInterpolants = true)
				}
				handleException {
					paintera.baseView.changeMode(previousMode)
				}
			}
			KEY_PRESSED(SHAPE_INTERPOLATION__ACCEPT_INTERPOLATION) {
				onAction {
					switchTool(shapeInterpolationTool)
					if (controller.applyMask())
						paintera.baseView.changeMode(previousMode)
				}
				handleException { paintera.baseView.changeMode(previousMode) }
			}
		}
	}

	private fun ActionSet.keyPressEditSelectionAction(choice: EditSelectionChoice, namedKey: NamedKeyBinding) =
		with(controller) {
			KEY_PRESSED ( namedKey) {
				graphic = when (choice) {
					EditSelectionChoice.First -> {
						{ GlyphScaleView(FontAwesomeIconView().also { it.styleClass += "interpolation-first-slice" }) }
					}

					EditSelectionChoice.Previous -> {
						{ GlyphScaleView(FontAwesomeIconView().also { it.styleClass += "interpolation-previous-slice" }) }
					}

					EditSelectionChoice.Next -> {
						{ GlyphScaleView(FontAwesomeIconView().also { it.styleClass += "interpolation-next-slice" }) }
					}

					EditSelectionChoice.Last -> {
						{ GlyphScaleView(FontAwesomeIconView().also { it.styleClass += "interpolation-last-slice" }) }
					}
				}
				verify { controllerState != Moving && activeTool is ShapeInterpolationTool }
				onAction { editSelection(choice) }
				handleException {
					exitShapeInterpolation(false)
					paintera.baseView.changeMode(previousMode)
				}
			}
		}

	internal fun cacheLoadSamSliceInfo(depth: Double, translate: Boolean = depth != controller.currentDepth): SamSliceInfo {
		return samSliceCache[depth] ?: with(controller) {
			val viewerAndTransforms = this@ShapeInterpolationMode.activeViewerProperty.value!!
			val viewer = viewerAndTransforms.viewer()!!
			val width = viewer.width
			val height = viewer.height

			val globalToViewerTransform = if (translate) {
				calculateGlobalToViewerTransformAtDepth(depth)
			} else {
				AffineTransform3D().also { viewerAndTransforms.viewer().state.getViewerTransform(it) }
			}

			val (maxDistanceX, maxDistanceY) = controller.getInterpolationImg(globalToViewerTransform, closest = true)?.let {
				val interpolantInViewer = if (translate) alignTransformAndViewCenter(it, globalToViewerTransform, width, height) else it
				interpolantInViewer.getPositionAtMaxDistance()
			} ?: doubleArrayOf(width / 2.0, height / 2.0, 0.0)


			val maskInfo = MaskInfo(0, currentBestMipMapLevel)
			val mask = source.createViewerMask(maskInfo, viewer, paintDepth = null, setMask = false, initialGlobalToViewerTransform = globalToViewerTransform)

			val activeSource = activeSourceStateProperty.value!!.sourceAndConverter!!.spimSource
			val sources = mask.viewer.state.sources
				.filter { it.spimSource !== activeSource }
				.toList()

			val renderState = RenderUnitState(mask.initialGlobalToViewerTransform.copy(), mask.info.time, sources, width.toLong(), height.toLong())
			val predictionRequest = SamPredictor.SparsePrediction(listOf(renderState.getSamPoint(maxDistanceX, maxDistanceY, SamPredictor.SparseLabel.IN)))

			SamSliceInfo(renderState, mask, predictionRequest, null, false).also {
				SamEmbeddingLoaderCache.load(renderState)
				samSliceCache[depth] = it
			}
		}
	}

	private fun ShapeInterpolationController<*>.calculateGlobalToViewerTransformAtDepth(depth: Double): AffineTransform3D {
		return adjacentSlices(depth).let { (first, second) ->
			when {
				first != null && second != null -> {
					val depthPercent = depth / (depthAt(first.globalTransform) + depthAt(second.globalTransform))
					val firstMaskTransform = first.mask.initialGlobalToViewerTransform
					val secondMaskTransform = second.mask.initialGlobalToViewerTransform
					SimilarityTransformInterpolator(firstMaskTransform, secondMaskTransform).get(depthPercent)
				}

				first != null -> {
					first.mask.initialGlobalToViewerTransform.let {
						val prevDepth = depthAt(first.globalTransform)
						it.copy().apply { translate(0.0, 0.0, prevDepth - depth) }
					}
				}

				second != null -> {
					second.mask.initialGlobalToViewerTransform.let {
						val nextDepth = depthAt(second.globalTransform)
						it.copy().apply { translate(0.0, 0.0, nextDepth - depth) }
					}
				}

				else -> {
					initialGlobalToViewerTransform!!.let {
						it.copy().apply { translate(0.0, 0.0, -depth) }
					}
				}
			}
		}
	}


	/**
	 * Align transform and view center. Translate [globalToViewerTransform] to center on the center of [view].
	 * Return a new [IntervalView] translated by the same amount, so it is in the new [globalToViewerTransform] space
	 * that result from the translation.
	 *
	 * @param T type of the [view]
	 * @param view in initial [globalToViewerTransform] space
	 * @param globalToViewerTransform the transform, modifier by translation
	 * @param width of the Viewer that [globalToViewerTransform] refers to
	 * @param height of the Viewer that [globalToViewerTransform] refers to
	 * @return an IntervalView resulting from translating [view] by the same amount that [globalToViewerTransform] was translated.
	 * */
	private fun <T> alignTransformAndViewCenter(view: IntervalView<T>, globalToViewerTransform: AffineTransform3D, width: Double, height: Double): IntervalView<T> {
		/* center on the interval */
		val (centerX, centerY) = view.center()
		val translation = longArrayOf(width.toLong() / 2 - centerX, height.toLong() / 2 - centerY, 0)
		globalToViewerTransform.translate(*translation.map { it.toDouble() }.toDoubleArray())
		return Views.translate(view, *translation)
	}

	internal fun addSelection(
		selectionIntervalOverMask: Interval,
		globalTransform: AffineTransform3D = paintera.baseView.manager().transform,
		viewerMask: ViewerMask = controller.currentViewerMask!!
	): SamSliceInfo? {
		val globalToViewerTransform = viewerMask.initialGlobalToMaskTransform
		val sliceDepth = controller.depthAt(globalToViewerTransform)

		/* IF slice exists at depth AND transform is different THEN delete until first non-pregenerated slices */
		samSliceCache[sliceDepth]?.sliceInfo?.let {
			if (it.mask.initialGlobalToViewerTransform.hashable() != viewerMask.initialGlobalToViewerTransform.hashable()) {
				val depths = samSliceCache.keys.toList().sorted()
				val depthIdx = depths.indexOf(sliceDepth.toFloat())
				for (idx in depthIdx - 1 downTo 0) {
					val depth = depths[idx]
					if (samSliceCache[depth]?.preGenerated == true)
						synchronized(samSliceCache) {
							samSliceCache.remove(depth)
						}
					else break
				}

				for (idx in depthIdx + 1 until depths.size) {
					val depth = depths[idx]
					if (samSliceCache[depth]?.preGenerated == true)
						synchronized(samSliceCache) {
							samSliceCache.remove(depth)
						}
					else break
				}
			}
		}

		val slice = controller.addSelection(selectionIntervalOverMask, globalTransform = globalTransform, viewerMask = viewerMask) ?: return null
		return cacheLoadSamSliceInfo(sliceDepth).apply {
			sliceInfo = slice
		}
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}

internal fun RenderUnitState.getSamPoint(screenX: Double, screenY: Double, label: SamPredictor.SparseLabel): SamPredictor.SamPoint {
	val screenScaleFactor = calculateTargetSamScreenScaleFactor()
	return SamPredictor.SamPoint(screenX * screenScaleFactor, screenY * screenScaleFactor, label)
}

internal fun IntervalView<UnsignedLongType>.getPositionAtMaxDistance(): DoubleArray {
	/* find the max point to initialize with */
	val distances = ArrayImgs.doubles(*dimensionsAsLongArray())
	val binaryImg = convert(BoolType()) { source, target -> target.set(!(source.get() != Label.INVALID && source.get() != Label.TRANSPARENT)) }.zeroMin()
	DistanceTransform.binaryTransform(binaryImg, distances, DistanceTransform.DISTANCE_TYPE.EUCLIDIAN)
	var maxDistance = -Double.MAX_VALUE
	var maxPos = center()
	BundleView(distances).interval(distances).forEach { access ->
		val distance = access.get().get()
		if (distance > maxDistance) {
			maxDistance = distance
			maxPos = access.positionAsLongArray()
		}
	}
	return maxPos.mapIndexed { idx, value -> value.toDouble() + min(idx) }.toDoubleArray()
}

internal class SamSliceCache : HashMap<Float, SamSliceInfo>() {
	operator fun set(key: Double, value: SamSliceInfo) = put(key.toFloat(), value)
	operator fun get(key: Double) = get(key.toFloat())
	operator fun minusAssign(key: Double) {
		remove(key.toFloat())
	}
	operator fun minusAssign(key: Float) {
		remove(key)
	}
}

internal data class SamSliceInfo(val renderState: RenderUnitState, val mask: ViewerMask, var prediction: SamPredictor.PredictionRequest, var sliceInfo: ShapeInterpolationController.SliceInfo?, var locked: Boolean = false) {
	val preGenerated get() = sliceInfo == null
	val globalToViewerTransform get() = renderState.transform

	fun updatePrediction(viewerX: Double, viewerY: Double, label: SamPredictor.SparseLabel = SamPredictor.SparseLabel.IN) {
		prediction = SamPredictor.SparsePrediction(listOf(renderState.getSamPoint(viewerX, viewerY, label)))
	}
}