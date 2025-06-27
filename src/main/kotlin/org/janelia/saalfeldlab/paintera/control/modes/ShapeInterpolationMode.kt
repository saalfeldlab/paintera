package org.janelia.saalfeldlab.paintera.control.modes

import bdv.fx.viewer.render.RenderUnitState
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.Event
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import javafx.util.Subscription
import kotlinx.coroutines.runBlocking
import net.imglib2.Interval
import net.imglib2.algorithm.labeling.ConnectedComponents
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.img.array.ArrayImgs
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.IntervalView
import net.imglib2.view.Views
import org.controlsfx.control.Notifications
import org.janelia.saalfeldlab.bdv.fx.viewer.getDataSourceAndConverter
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.midi.ToggleAction
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.FontIconPatched
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.cache.HashableTransform.Companion.hashable
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache.calculateTargetSamScreenScaleFactor
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController.EditSelectionChoice
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController.EditSelectionChoice.*
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.createViewerMask
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.paint.Fill2DTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.PaintBrushTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamPredictor
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamPredictor.SparseLabel
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationFillTool
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationPaintBrushTool
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationSAMTool
import org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation.ShapeInterpolationTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.util.*
import org.kordamp.ikonli.fontawesome.FontAwesome
import org.kordamp.ikonli.javafx.FontIcon
import kotlin.math.roundToLong

class ShapeInterpolationMode<D : IntegerType<D>>(val controller: ShapeInterpolationController<D>, private val previousMode: ControlMode) : AbstractToolMode() {

	internal val samSliceCache = SamSliceCache()

	private val paintBrushTool by lazy { ShapeInterpolationPaintBrushTool(activeSourceStateProperty, this) }
	private val fill2DTool by lazy { ShapeInterpolationFillTool(controller, activeSourceStateProperty, this) }
	private val samTool by lazy { ShapeInterpolationSAMTool(controller, activeSourceStateProperty, this@ShapeInterpolationMode) }
	private val shapeInterpolationTool by lazy { ShapeInterpolationTool(controller, previousMode, this@ShapeInterpolationMode, this@ShapeInterpolationMode.fill2DTool) }

	override val defaultTool: Tool? by lazy { shapeInterpolationTool }
	override val activeViewerActions by lazy { modeActions() }

	override val allowedActions = AllowedActions.AllowedActionsBuilder()
		.add(PaintActionType.ShapeInterpolation, PaintActionType.Paint, PaintActionType.Erase, PaintActionType.SetBrushSize, PaintActionType.Fill, PaintActionType.SegmentAnything)
		.add(MenuActionType.ToggleMaximizeViewer, MenuActionType.DetachViewer, MenuActionType.ResizeViewers, MenuActionType.ToggleSidePanel, MenuActionType.ResizePanel)
		.add(NavigationActionType.Pan, NavigationActionType.Slice, NavigationActionType.Zoom)
		.create()

	private val samNavigationRequestListener = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, _, curViewer ->
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
				enterShapeInterpolation(viewerAndTransforms.viewer())
			}
		}
	}

	override fun exit() {
		super.exit()

		SamEmbeddingLoaderCache.stopNavigationBasedRequests()
		SamEmbeddingLoaderCache.invalidateAll()
		paintera.baseView.disabledPropertyBindings.remove(controller)
		controller.resetFragmentAlpha()
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
		converter.activeFragmentAlphaProperty().set((activeSelectionAlpha * 255).toInt())
	}

	internal val samStyleBoxToggle = SimpleBooleanProperty(true)

	private fun modeActions(): List<ActionSet> {
		return mutableListOf(
			painteraActionSet(CANCEL, ignoreDisable = true) {
				with(controller) {
					verifyAll(KEY_PRESSED, "Shape Interpolation Controller is Active ") { isControllerActive }
					verifyAll(Event.ANY, "Shape Interpolation Tool is Active") { activeTool is ShapeInterpolationTool }
					val exitMode = { _: Event? ->
						exitShapeInterpolation(false)
						paintera.baseView.changeMode(previousMode)
					}
					KEY_PRESSED(CANCEL) {
						createToolNode = { apply { addStyleClass(Style.FONT_ICON + "reject" + "reject-shape-interpolation") } }
						onAction(exitMode)
					}
					KEY_PRESSED(SHAPE_INTERPOLATION__TOGGLE_MODE) {
						onAction(exitMode)
					}
				}
			},
			painteraActionSet("paint return tool triggers", PaintActionType.Paint, ignoreDisable = true) {
				KEY_RELEASED(paintBrushTool.keyTrigger) {
					name = "switch back to shape interpolation tool from paint brush"
					filter = true
					verify("PaintBrushTool is active") { activeTool is PaintBrushTool }
					onAction { switchTool(shapeInterpolationTool) }
				}
				KEY_RELEASED(fill2DTool.keyTrigger) {
					name = "switch to shape interpolation tool from fill2d"
					filter = true
					verify("Fill2DTool is active") { activeTool is Fill2DTool }
					onAction {
						val switchBack = { switchTool(shapeInterpolationTool) }
						fill2DTool.fillJob?.invokeOnCompletion { switchBack() } ?: switchBack()
					}
				}
			},
			painteraActionSet("paint during shape interpolation", PaintActionType.Paint) {
				KEY_PRESSED(paintBrushTool.keyTrigger) {
					name = "switch to paint tool"
					verify("Active source is MaskedSource") { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
					onAction { switchTool(paintBrushTool) }
				}
				KEY_PRESSED(fill2DTool.keyTrigger) {
					name = "switch to fill2d tool"
					verify("Active source is MaskedSource") { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
					onAction { switchTool(fill2DTool) }
				}
				KEY_PRESSED(samTool.keyTrigger) {
					name = "toggle SAM tool"
					verify("Active source is MaskedSource") { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
					onAction {
						val nextTool = if (activeTool != samTool) samTool else shapeInterpolationTool
						switchTool(nextTool)
					}
				}
			},
			painteraActionSet("key slice navigation") {
				keyPressEditSelectionAction(First, SHAPE_INTERPOLATION__SELECT_FIRST_SLICE)
				keyPressEditSelectionAction(Last, SHAPE_INTERPOLATION__SELECT_LAST_SLICE)
				keyPressEditSelectionAction(Previous, SHAPE_INTERPOLATION__SELECT_PREVIOUS_SLICE)
				keyPressEditSelectionAction(Next, SHAPE_INTERPOLATION__SELECT_NEXT_SLICE)
				//FIXME Caleb: There is a bug when navigating slices quickly that occasionally the correct action above will not
				//  trigger. When the arrows keys are the expected trigger, this then triggers the parent/sibling nodes focuse traversal
				//  and causes the focus to escape the active orthoslice. When this happens it appears that shape interpolation
				//  no longer is working, since the arrow keys do nothing. you can manually re-focuse the orthoslice, but you need to
				//  know to do this.
				//  The following is a hack for now until the cause of the issues can be resolved. For future notes, it appears to be when
				//  asking the KeyTracker if the trigger key matches the current key state, and it erroneously returns false, even when
				//  the KeyEvent.code matches.
				KEY_PRESSED {
					onAction { event ->
						val triggers = listOf(SHAPE_INTERPOLATION__SELECT_FIRST_SLICE, SHAPE_INTERPOLATION__SELECT_LAST_SLICE, SHAPE_INTERPOLATION__SELECT_PREVIOUS_SLICE, SHAPE_INTERPOLATION__SELECT_NEXT_SLICE)
						if (triggers.any { it.primaryCombination.match(event) })
							event?.consume()
					}
				}
			},
			painteraDragActionSet("drag activate SAM mode with box", PaintActionType.Paint, ignoreDisable = true, consumeMouseClicked = true) {
				dragDetectedAction.apply {
					verify("primary click drag only ") { it != null && it.isPrimaryButtonDown && !it.isSecondaryButtonDown && !it.isMiddleButtonDown }
					verify("can't trigger box prompt with active tool") { activeTool in listOf(NavigationTool, shapeInterpolationTool, samTool) }
				}
				onDragDetected {
					switchTool(samTool)
				}
				onDrag {
					(activeTool as? SamTool)?.apply {
						requestBoxPromptPrediction(it)
					}
				}
			},
			painteraActionSet("change auto sam style") {
				KEY_PRESSED(KeyCode.B) {
					onAction {
						samStyleBoxToggle.set(!samStyleBoxToggle.get())
						data class SamStyleToggle(val icon: FontAwesome, val title: String, val text: String)
						val (toggle, title, text) = if (samStyleBoxToggle.get())
							SamStyleToggle(FontAwesome.TOGGLE_RIGHT, "Toggle Sam Style", "Style: Interpolant Interval")
						else
							SamStyleToggle(FontAwesome.TOGGLE_LEFT, "Toggle Sam Style", "Style: Interpolant Distance Point")

						InvokeOnJavaFXApplicationThread {
							val notification = Notifications.create()
								.graphic(FontIconPatched(toggle))
								.title(title)
								.text(text)
								.owner(paintera.baseView.node)
							notification
								.threshold(1, notification)
								.show()
						}
					}
				}
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
							MidiButtonEvent.BUTTON_PRESSED(9) {
								name = "midi go to first slice"
								verify { activeTool !is SamTool }
								onAction { editSelection(First) }

							}
							MidiButtonEvent.BUTTON_PRESSED(10) {
								name = "midi go to previous slice"
								verify { activeTool !is SamTool }
								onAction { editSelection(Previous) }

							}
							MidiButtonEvent.BUTTON_PRESSED(11) {
								name = "midi go to next slice"
								verify { activeTool !is SamTool }
								onAction { editSelection(Next) }

							}
							MidiButtonEvent.BUTTON_PRESSED(12) {
								name = "midi go to last slice"
								verify { activeTool !is SamTool }
								onAction { editSelection(Last) }
							}
						}
					}
				}
			}
		).filterNotNull()
	}

	internal fun applyShapeInterpolationAndExitMode() {
		with(controller) {
			var applyMaskTriggered = false
			var selfReference: Subscription? = null
			val subscription = source.isApplyingMaskProperty.subscribe { applyingMask ->
				if (applyMaskTriggered && !applyingMask) {
					selfReference?.unsubscribe()
					InvokeOnJavaFXApplicationThread {
						paintera.baseView.changeMode(previousMode)
					}
				}

			}
			selfReference = subscription
			applyMaskTriggered = true
			if (!applyMask())
				subscription?.unsubscribe()
		}
	}

	fun switchAndApplyShapeInterpolationActions(toolActions: ActionSet) {
		with(toolActions) {
			KEY_PRESSED(CANCEL) {
				name = "cancel_to_shape_interpolation_tool"
				onAction {
					runBlocking { switchTool(shapeInterpolationTool)?.join() }
					controller.setMaskOverlay(replaceExistingInterpolants = true)
				}
				handleException {
					paintera.baseView.changeMode(previousMode)
				}
			}
			KEY_PRESSED(SHAPE_INTERPOLATION__ACCEPT_INTERPOLATION) {
				onAction {
					runBlocking { switchTool(shapeInterpolationTool)?.join() }
					applyShapeInterpolationAndExitMode()
				}
				handleException {
					LOG.error(it) {}
					paintera.baseView.changeMode(previousMode)
				}
			}
		}
	}

	private fun ActionSet.keyPressEditSelectionAction(choice: EditSelectionChoice, namedKey: NamedKeyBinding) =
		with(controller) {
			KEY_PRESSED(namedKey) {
				createToolNode = { apply { addStyleClass(choice.style) } }
				verify { activeTool is ShapeInterpolationTool }
				onAction { editSelection(choice) }
				handleException {
					exitShapeInterpolation(false)
					paintera.baseView.changeMode(previousMode)
				}
			}
		}

	internal fun cacheLoadSamSliceInfo(depth: Double, translate: Boolean = depth != controller.currentDepth, provideGlobalToViewerTransform: AffineTransform3D? = null): SamSliceInfo {
		return samSliceCache[depth] ?: with(controller) {
			val viewerAndTransforms = this@ShapeInterpolationMode.activeViewerProperty.value!!
			val viewer = viewerAndTransforms.viewer()!!
			val width = viewer.width
			val height = viewer.height

			val globalToViewerTransform = when {
				provideGlobalToViewerTransform != null -> provideGlobalToViewerTransform
				translate -> calculateGlobalToViewerTransformAtDepth(depth)
				else -> AffineTransform3D().also { viewerAndTransforms.viewer().state.getViewerTransform(it) }
			}

			val fallbackPrompt = listOf(doubleArrayOf(width / 2.0, height / 2.0, 0.0) to SparseLabel.IN)
			val predictionPositions = provideGlobalToViewerTransform?.let { fallbackPrompt } ?: let {
				controller.getInterpolationImg(globalToViewerTransform, closest = true)?.let {
					val interpolantInViewer = if (translate) alignTransformAndViewCenter(it, globalToViewerTransform, width, height) else it
					interpolantInViewer.getInterpolantPrompt(samStyleBoxToggle.get())
				} ?: fallbackPrompt
			}


			val maskInfo = MaskInfo(0, currentBestMipMapLevel)
			val mask = source.createViewerMask(maskInfo, viewer, setMask = false, initialGlobalToViewerTransform = globalToViewerTransform)

			val activeSource = activeSourceStateProperty.value!!.sourceAndConverter!!.spimSource
			val sources = mask.viewer.state.sources
				.filter { it.spimSource !== activeSource }
				.map { sac -> getDataSourceAndConverter<Any>(sac) } // to ensure non-volatile
				.toList()

			val renderState = RenderUnitState(mask.initialGlobalToViewerTransform.copy(), mask.info.time, sources, width.toLong(), height.toLong())
			val predictionRequest = SamPredictor.SparsePrediction(predictionPositions.map { (pos, label) -> renderState.getSamPoint(pos[0], pos[1], label) })
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
		viewerMask: ViewerMask = controller.currentViewerMask!!,
		globalTransform: AffineTransform3D = viewerMask.currentGlobalTransform,
		replaceExistingSlice: Boolean = false,
	): SamSliceInfo? {
		val globalToViewerTransform = viewerMask.initialGlobalToMaskTransform
		val sliceDepth = controller.depthAt(globalToViewerTransform)

		/* IF slice exists at depth AND transform is different THEN delete until first non-pregenerated slices */
		samSliceCache[sliceDepth]?.sliceInfo?.let {
			val diffTransform = it.mask.initialGlobalToViewerTransform.hashable() != viewerMask.initialGlobalToViewerTransform.hashable()
			val diffMask = it.maskBoundingBox != selectionIntervalOverMask
			if (diffTransform || diffMask) {
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

		val slice = controller.addSelection(selectionIntervalOverMask, replaceExistingSlice, globalTransform, viewerMask) ?: return null
		return cacheLoadSamSliceInfo(sliceDepth).apply {
			sliceInfo = slice
		}
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}

internal fun RenderUnitState.getSamPoint(screenX: Double, screenY: Double, label: SparseLabel): SamPredictor.SamPoint {
	val screenScaleFactor = calculateTargetSamScreenScaleFactor()
	return SamPredictor.SamPoint(screenX * screenScaleFactor, screenY * screenScaleFactor, label)
}

internal fun IntervalView<UnsignedLongType>.getInterpolantPrompt(box: Boolean): List<Pair<DoubleArray, SparseLabel>> {
	return if (box)
		getMinMaxIntervalPositions().mapIndexed { idx, it -> it to if (idx == 0) SparseLabel.TOP_LEFT_BOX else SparseLabel.BOTTOM_RIGHT_BOX }
	else
		getComponentMaxDistancePosition().map { it to SparseLabel.IN }
}

internal fun IntervalView<UnsignedLongType>.getMinMaxIntervalPositions(): List<DoubleArray> {
	val interval = Intervals.expand(this, *dimensionsAsLongArray().map { (it * .1).roundToLong() }.toLongArray())
	return listOf(
		interval.minAsDoubleArray(),
		interval.maxAsDoubleArray()
	)
}

internal fun IntervalView<UnsignedLongType>.getComponentMaxDistancePosition(): List<DoubleArray> {
	/* find the max point to initialize with */
	val invalidBorderRai = extendValue(Label.INVALID).interval(Intervals.expand(this, 1, 1, 0))
	val distances = ArrayImgs.doubles(*invalidBorderRai.dimensionsAsLongArray())
	val binaryImg = invalidBorderRai.convertRAI(BoolType()) { source, target -> target.set((source.get() != Label.INVALID && source.get() != Label.TRANSPARENT)) }.zeroMin()
	val invertedBinaryImg = binaryImg.convertRAI(BoolType()) { source, target -> target.set(!source.get()) }

	val connectedComponents = ArrayImgs.unsignedInts(*binaryImg.dimensionsAsLongArray())
	ConnectedComponents.labelAllConnectedComponents(
		binaryImg,
		connectedComponents,
		ConnectedComponents.StructuringElement.FOUR_CONNECTED
	)

	DistanceTransform.binaryTransform(invertedBinaryImg, distances, DistanceTransform.DISTANCE_TYPE.EUCLIDIAN)

	val distancePerComponent = mutableMapOf<Int, Pair<Double, LongArray>>()

	var backgroundId = -1;

	LoopBuilder.setImages(BundleView(distances).interval(distances), connectedComponents).forEachPixel { distanceRA, componentType ->
		val thisDist = distanceRA.get().get()
		val componentId = componentType.integer

		val curDist = distancePerComponent[componentId]?.first

		if (curDist != null) {
			if (thisDist > curDist)
				distancePerComponent[componentId] = thisDist to distanceRA.positionAsLongArray()
		} else {
			if (backgroundId == -1 && !binaryImg.getAt(distanceRA).get()) {
				backgroundId = componentId
			} else if (componentId != backgroundId)
				distancePerComponent[componentId] = thisDist to distanceRA.positionAsLongArray()
		}
	}

	return distancePerComponent.values.map {
		it.second.mapIndexed { idx, value ->
			(value + min(idx)).toDouble()
		}.toDoubleArray()
	}.toList()
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

	override fun clear() {
		values.forEach {
			it.mask.shutdown?.run()
		}
		super.clear()
	}

	override fun remove(key: Float): SamSliceInfo? {
		return super.remove(key)?.also {
			it.mask.shutdown?.run()
		}
	}

	override fun remove(key: Float, value: SamSliceInfo): Boolean {
		return if (super.remove(key, value)) {
			value.mask.shutdown?.run()
			true
		} else false
	}

	override fun put(key: Float, value: SamSliceInfo): SamSliceInfo? {
		return super.put(key, value)?.also {
			it.mask.shutdown?.run()
		}
	}
}

internal data class SamSliceInfo(val renderState: RenderUnitState, val mask: ViewerMask, var prediction: SamPredictor.PredictionRequest, var sliceInfo: ShapeInterpolationController.SliceInfo?, var locked: Boolean = false) {
	val preGenerated get() = sliceInfo == null
	val globalToViewerTransform get() = renderState.transform

	fun updatePrediction(viewerX: Double, viewerY: Double, label: SparseLabel = SparseLabel.IN) {
		prediction = SamPredictor.SparsePrediction(listOf(renderState.getSamPoint(viewerX, viewerY, label)))
	}

	fun updatePrediction(viewerPositions: List<DoubleArray>, label: SparseLabel = SparseLabel.IN) {
		prediction = SamPredictor.SparsePrediction(viewerPositions.map { (x, y) -> renderState.getSamPoint(x, y, label) })
	}

	fun updatePrediction(viewerPositionsAndLabels: List<Pair<DoubleArray, SparseLabel>>) {
		prediction = SamPredictor.SparsePrediction(viewerPositionsAndLabels.map { (pos, label) -> renderState.getSamPoint(pos[0], pos[1], label) })

	}
}