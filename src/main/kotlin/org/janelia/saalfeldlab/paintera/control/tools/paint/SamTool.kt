package org.janelia.saalfeldlab.paintera.control.tools.paint

import org.janelia.saalfeldlab.samlink.decode.BoxPrompt
import org.janelia.saalfeldlab.samlink.decode.SamPrompt
import org.janelia.saalfeldlab.samlink.decode.MaskPrompt
import org.janelia.saalfeldlab.samlink.decode.SamPointLabel
import org.janelia.saalfeldlab.samlink.decode.PointPrompt
import org.janelia.saalfeldlab.samlink.decode.SamPromptBase
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.input.MouseEvent.MOUSE_MOVED
import javafx.scene.input.ScrollEvent.SCROLL
import javafx.scene.layout.Pane
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealPoint
import net.imglib2.algorithm.labeling.ConnectedComponents
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement
import net.imglib2.histogram.Real1dBinMapper
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.*
import net.imglib2.type.logic.NativeBoolType
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.type.volatiles.VolatileFloatType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.IntervalView
import net.imglib2.view.RandomAccessibleIntervalCursor
import org.apache.commons.io.output.NullPrintStream
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.control.VPotControl
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiFaderEvent
import org.janelia.saalfeldlab.fx.midi.MidiPotentiometerEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.net.imglib2.algorithms.Morph2D
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.StyleGroup
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.renderState
import org.janelia.saalfeldlab.paintera.ai.SamEncoder
import org.janelia.saalfeldlab.paintera.ai.sam.MAX_DIM_TARGET
import org.janelia.saalfeldlab.paintera.ai.sam.MultipleChoicePrompt
import org.janelia.saalfeldlab.paintera.ai.sam.SamPredictor
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.NavigationControlMode.waitForEvent
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.createViewerMask
import org.janelia.saalfeldlab.paintera.control.tools.REQUIRES_ACTIVE_VIEWER
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool.Companion.SamStyle.SAM_BOX_OVERLAY
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool.Companion.SamStyle.SAM_POINT
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.predicate.threshold.Bounds
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.paintera.util.algorithms.otsuThresholdPrediction
import org.janelia.saalfeldlab.util.*
import java.util.concurrent.CancellationException
import kotlin.collections.List
import kotlin.collections.any
import kotlin.collections.asSequence
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.collections.filterIsInstance
import kotlin.collections.filterNotNull
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.indexOfFirst
import kotlin.collections.indexOfLast
import kotlin.collections.listOf
import kotlin.collections.max
import kotlin.collections.minusAssign
import kotlin.collections.mutableListOf
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.toTypedArray
import kotlin.math.*


open class SamTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, override val mode: ToolMode? = null) : PaintTool(activeSourceStateProperty, mode) {

	override fun newToolBarControl()  = super.newToolBarControl().apply {
		properties[REQUIRES_ACTIVE_VIEWER] = true
		addStyleClass(SamStyle.SAM_SELECT_TOOL)
	}
	override val name = "Segment Anything"
	override val keyTrigger = SEGMENT_ANYTHING__TOGGLE_MODE

	internal var currentLabelToPaint: Long = Label.INVALID
	private val isLabelValid get() = currentLabelToPaint != Label.INVALID

	private val primaryClickLabelProperty = SimpleObjectProperty<SamPointLabel>(null)
	private var primaryClickLabel: SamPointLabel? by primaryClickLabelProperty.nullable()

	override val actionSets by LazyForeignValue({ activeViewerAndTransforms }) {
		mutableListOf(
			*getSamActions().filterNotNull().toTypedArray(),
		)
	}

	override val statusProperty = SimpleStringProperty()

	private val selectedIdListener = InvalidationListener {
		statePaintContext?.selectedIds?.lastSelection?.let { currentLabelToPaint = it }
	}

	internal val maskedSource: MaskedSource<*, *>?
		get() = activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>

    private var currentViewerMask: ViewerMaskOverlay? = null
	private var maskProvided = false

	//TODO Caleb: document this; it stops `cleanup()` from removing the wrapped overlay of the prediction
	var unwrapResult = true

    protected data class ViewerMaskOverlay(val viewerMask: ViewerMask) {
        var background: RandomAccessibleInterval<UnsignedLongType> = viewerMask.viewerImg.wrappedSource
        var overlay: RandomAccessibleInterval<UnsignedLongType>? = viewerMask.viewerImg.writableSource
        var volatileBackground: RandomAccessibleInterval<VolatileUnsignedLongType> =
            viewerMask.volatileViewerImg.wrappedSource
        var volatileOverlay: RandomAccessibleInterval<VolatileUnsignedLongType>? =
            viewerMask.volatileViewerImg.writableSource

        fun resetOverlay() {
            viewerMask.updateBackingImages(
                background to volatileBackground,
                overlay to volatileOverlay
            )
        }
    }

	private var setViewer: ViewerPanelFX? = null
	internal var viewerMask: ViewerMask? = null
		get() {
            field = field ?: let {
				maskProvided = false
                val maskInfo = MaskInfo(0, setViewer!!.state.bestMipMapLevel)
                maskedSource!!.createViewerMask(maskInfo, setViewer!!)
			}
            val curOrNewMask = field!!
            currentViewerMask = ViewerMaskOverlay(curOrNewMask)
            return curOrNewMask
		}
		set(value) {
			field = value
			maskProvided = value != null
            currentViewerMask = field?.let { mask -> ViewerMaskOverlay(mask) }
		}

	private var predictionJob: Job = Job().apply { complete() }

	internal val lastPredictionProperty = SimpleObjectProperty<SamTaskInfo?>(null)
	var lastPrediction by lastPredictionProperty.nullable()
		private set

	var temporaryPrompt = true

	private var thresholdBounds = Bounds(-40.0, 30.0)
	private val estimatedThresholdProperty = SimpleObjectProperty<Double?>(null)
	private var estimatedThreshold by estimatedThresholdProperty.nullable()
	private var threshold = 0.0
		set(value) {
			field = value.coerceIn(thresholdBounds.min, thresholdBounds.max)
		}

	init {

		subscriptions += SamEncoder.healthCheckProperty.subscribe { it ->
			isValidProperty.set(it)
		}
	}

	private val isBusyProperty = SimpleBooleanProperty(false)

	private var isBusy by isBusyProperty.nonnull()

	private var screenScale = Double.NaN

    private val promptChannel = Channel<Pair<SamPrompt, Boolean>>(1)

	private var currentPredictionRequest: Pair<SamPrompt, Boolean>? = null
		set(value) = runBlocking {
            promptChannel.tryReceive() /* capacity 1, so this will always either do nothing, or empty the channel */
			value?.let { (request, _) ->
                promptChannel.send(value)
				if (!temporaryPrompt)
					request.drawPrompt()
			}
			field = value
		}

	internal lateinit var renderState: RenderUnitState

	protected var requestOnActivate : Boolean = true

	override fun activate() {
		mode?.apply {
			actionBar.showGroup(actionBar.modeActionsGroup, false)
			actionBar.showGroup(actionBar.modeToolsGroup, false)
		}
		super.activate()
		(mode as? PaintLabelMode)?.apply {
			disableUnfocusedViewers()
		}
		primaryClickLabel = null
		initializeSam()
		/* Trigger initial prediction request when activating the tool */
		setViewer?.takeIf { requestOnActivate }?.let { viewer ->
			statusProperty.set("Predicting...")
			val x = viewer.mouseXProperty.get().toLong()
			val y = viewer.mouseYProperty.get().toLong()
			LOG.trace { "initial prediction at viewer ($x, $y), screenScale=$screenScale" }
			temporaryPrompt = true
			val points = listOf(PointPrompt((x * screenScale).toFloat(), (y * screenScale).toFloat(), SamPointLabel.FOREGROUND))
			requestPrediction(points)
		}
	}

	override fun deactivate() {
		mode?.apply {
			actionBar.showGroup(actionBar.modeActionsGroup, true)
			actionBar.showGroup(actionBar.modeToolsGroup, true)
		}
		cleanup()
		(mode as? PaintLabelMode)?.enableAllViewers()
		super.deactivate()
	}

	internal fun initializeSam(renderUnitState: RenderUnitState? = null) {
		unwrapResult = true
		threshold = 0.0
		setCurrentLabelToSelection()
		statePaintContext?.selectedIds?.apply { addListener(selectedIdListener) }
		setViewer = activeViewer //TODO Caleb: We should try not to use the viewer directly
		screenScale = renderUnitState?.calculateTargetScreenScaleFactor() ?: calculateTargetScreenScaleFactor(setViewer!!)
		statusProperty.set("Preparing SAM")
		paintera.baseView.disabledPropertyBindings[this] = isBusyProperty
		renderState = renderUnitState ?: activeViewer!!.renderState(excludeActiveSource = true)
		LOG.trace { "initializeSam" }
		encodeRequest = SamEncoder.cache.request(renderState)
	}

	internal fun cleanup() {
		clearPromptDrawings()
		currentLabelToPaint = Label.INVALID
		predictionJob.cancel()
        promptChannel.tryReceive() /*clear the channel if not empty */
		if (unwrapResult) {
			if (!maskProvided) {
				maskedSource?.resetMasks()
			} else {
                currentViewerMask?.resetOverlay()
			}
		}
		InvokeOnJavaFXApplicationThread { setViewer?.children?.removeIf { SAM_POINT.style in it.styleClass } }
		statePaintContext?.selectedIds?.removeListener(selectedIdListener)
		paintera.baseView.disabledPropertyBindings -= this
        lastPrediction?.maskInterval?.let { currentViewerMask?.viewerMask?.requestRepaint(it) }
		viewerMask = null
		estimatedThreshold = null
		primaryClickLabel = null
		encodeRequest = null
	}

	protected open fun setCurrentLabelToSelection() {
		currentLabelToPaint = statePaintContext?.paintSelection?.invoke() ?: Label.INVALID
	}


	private fun getSamActions(): Array<ActionSet?> {
		lateinit var primaryClickToggleIncludeAction: Action<*>
		lateinit var primaryClickToggleExcludeAction: Action<*>
		lateinit var resetPromptAction: Action<*>
		lateinit var applyPredictionAction: Action<*>
		return arrayOf(
			painteraActionSet("sam-selections", PaintActionType.Paint, ignoreDisable = true) {
				/* Handle Painting */
				MOUSE_CLICKED(MouseButton.PRIMARY, withKeysDown = arrayOf(KeyCode.CONTROL)) {
					name = "apply last segmentation result to canvas"
					verifyEventNotNull()
					verify(" label is not valid ") { isLabelValid }
					onAction { applyPrediction() }
				}
				KEY_PRESSED(KeyCode.D) {
					name = "view prediction"
					verifyEventNotNull()
					verify { Paintera.debugMode }
					verify("no current prediction ") { currentPrediction != null }
					onAction {

                        val prediction = currentPrediction!!.decodeResult.run {
                            val morph2D = Morph2D(bestMask, FloatArray(bestMask.size), maskSize to maskSize)
                            val centreFilteredLogits = morph2D.centre( 5 to 5).output
                            currentPrediction!!.raiInDecodeSpace(centreFilteredLogits)
                        }

                        val viewerMask = currentViewerMask!!.viewerMask

                        val (max, mean, std) = prediction.let {
							var sum = 0.0
							var sumSquared = 0.0
							var max = Float.MIN_VALUE
							it.forEach { float ->
								val floatVal = float.get()
								sum += floatVal
								sumSquared += floatVal.pow(2)
								if (max < floatVal) max = floatVal
							}
							val area = Intervals.numElements(it)
							val mean = sum / area
							val stddev = sqrt(sumSquared / area - mean.pow(2))
							doubleArrayOf(max.toDouble(), mean, stddev)
						}
						val min = (mean - std).toFloat()
                        val zeroMinValue =
                            prediction.convertRAI(FloatType()) { input, output -> output.set(input.get() - min) }

                        val (width, height) = prediction.dimensionsAsLongArray()
                        val maskOriginOffset = viewerMask.displayPointToMask(0, 0, currentDisplay)
                        val predictionToMask = AffineTransform3D()
                            .concatenate(Translation3D(*maskOriginOffset.positionAsDoubleArray()))
                            .concatenate(Scale3D(setViewer!!.width / width, setViewer!!.height / height, 1.0))
                            .concatenate(Translation3D(.5, .5, 0.0))

                        val predictionToGlobal = viewerMask.initialMaskToGlobalWithDepthTransform.copy()
                            .concatenate(predictionToMask)

                        val prediction3D = zeroMinValue.addDimension(-1, 1)
                        val globalInterval = predictionToGlobal.estimateBounds(prediction3D).smallestContainingInterval
                        val predictionInGlobal = prediction3D
                            .extendBorder()
                            .interpolate(ClampingNLinearInterpolatorFactory())
                            .affineReal(predictionToGlobal)
                            .raster()
                            .interval(globalInterval)

						val predictionSource = paintera.baseView.addConnectomicsRawSource<FloatType, VolatileFloatType>(
                            predictionInGlobal,
							doubleArrayOf(1.0, 1.0, 1.0),
                            globalInterval.minAsDoubleArray(),
							0.0, max - min,
							"$name prediction"
						)
						predictionSource.composite = ARGBCompositeAlphaAdd()
						setViewer!!.requestRepaint()
					}
				}

				SCROLL {
					verify("scroll size at least 1 pixel") { max(it!!.deltaX.absoluteValue, it.deltaY.absoluteValue) > 1.0 }
					verifyEventNotNull()
					onAction { scroll ->
						/* ScrollEvent deltas are internally multiplied to correspond to some estimate of pixels-per-unit-scroll.
						* For example, on the platform I'm using now, it's `40` for both x and y. But our threshold is NOT
						* in pixel units, so we divide by the multiplier, and specify our own.  */
						val delta = with(scroll!!) {
							when {
								deltaY.absoluteValue > deltaX.absoluteValue -> deltaY / multiplierY
								else -> deltaX / multiplierX
							}
						}
						val increment = (thresholdBounds.max - thresholdBounds.min) / 100.0
						threshold += delta * increment
						currentPredictionRequest?.first?.let {
							requestPrediction(it, false)
						}
					}
				}

				MOUSE_MOVED {
					name = "prediction overlay"
					verifyEventNotNull()
//					verifyPainteraNotDisabled()
					verify("Must be a temporary prompt") {  temporaryPrompt }
					verify("Label is not valid") { isLabelValid }
					onAction {
						clearPromptDrawings()
						temporaryPrompt = true

						requestPrediction(listOf(
							PointPrompt(
								(it!!.x * screenScale).toFloat(),
								(it.y * screenScale).toFloat(),
								SamPointLabel.FOREGROUND
							)
						))
					}
				}

				val primaryClickLabelToggleGroup = ToggleGroup()
				primaryClickLabelProperty.addListener { _, _, new ->
					val id = when (new) {
						SamPointLabel.FOREGROUND -> "add include point"
						SamPointLabel.BACKGROUND -> "add exclude point"
						else -> null
					}
					primaryClickLabelToggleGroup.toggles.forEach { toggle ->
						toggle.isSelected = (toggle as? ToggleButton)?.let { it.id != null && it.id == id } ?: false
					}
				}
				/* Handle Include Points */
				MOUSE_CLICKED(MouseButton.PRIMARY) {
					name = "add include point"
					createToolNode = {
						ToggleButton().apply {
							addStyleClass(SamStyle.SAM_INCLUDE_TOOL)
							toggleGroup = primaryClickLabelToggleGroup
						}
					}
					onAction {
						CoroutineScope(Dispatchers.IO).launch {
							/* If no event, triggered via button; set Label to IN */
							it ?: let {
								/* If already IN, toggle off and return, otherwise toggle on */
								if (primaryClickLabel == SamPointLabel.FOREGROUND) {
									primaryClickLabel = null
									return@launch
								} else {
									primaryClickLabel = SamPointLabel.FOREGROUND
								}
							}

							/* If no event, triggered via button, wait for click before continuing */
							(it ?: viewerMask!!.viewer.waitForEvent<MouseEvent>(MOUSE_CLICKED))?.let { event ->
								val label = primaryClickLabel ?: SamPointLabel.FOREGROUND
								val points = currentPredictionRequest?.first.addPoints(PointPrompt((event.x * screenScale).toFloat(), (event.y * screenScale).toFloat(), label))
								temporaryPrompt = false
								requestPrediction(points)
							}
						}
					}
				}.also { primaryClickToggleIncludeAction = it }

				MOUSE_CLICKED(MouseButton.SECONDARY) {
					name = "add exclude point"
					createToolNode = {
						ToggleButton().apply {
							addStyleClass(SamStyle.SAM_EXCLUDE_TOOL)
							toggleGroup = primaryClickLabelToggleGroup
						}
					}
					onAction {
						CoroutineScope(Dispatchers.IO).launch {
							it ?: let {
								/* If already IN, toggle off and return, otherwise toggle on */
								if (primaryClickLabel == SamPointLabel.BACKGROUND) {
									primaryClickLabel = null
									return@launch
								} else {
									primaryClickLabel = SamPointLabel.BACKGROUND
								}
							}

							/* If no event, triggered via button, wait for click before continuing */
							(it ?: viewerMask!!.viewer.waitForEvent<MouseEvent>(MOUSE_CLICKED))?.let { event ->
								val points = currentPredictionRequest?.first.addPoints(PointPrompt((event.x * screenScale).toFloat(), (event.y * screenScale).toFloat(),
									SamPointLabel.BACKGROUND))
								temporaryPrompt = false
								requestPrediction(points)
							}
						}
					}
				}.also { primaryClickToggleExcludeAction = it }

				KEY_PRESSED(SEGMENT_ANYTHING__RESET_PROMPT) {
					createToolNode = { apply { addStyleClass(Style.RESET_ICON) } }
					onAction {
						resetPromptAndPrediction()
						primaryClickLabel = null
						primaryClickLabelToggleGroup.selectedToggle?.isSelected = false
						temporaryPrompt = true
					}
				}.also { resetPromptAction = it }

				KEY_PRESSED(SEGMENT_ANYTHING__ACCEPT_SEGMENTATION) {
					name = "apply last segmentation result to canvas"
					createToolNode = { apply { addStyleClass(Style.ACCEPT_ICON) } }
					verify(" label is not valid ") { isLabelValid }
					onAction {
						lastPrediction ?: return@onAction
						applyPrediction()
						clearPromptDrawings()
						currentPredictionRequest = null
						primaryClickLabel = null
						primaryClickLabelToggleGroup.selectedToggle?.isSelected = false
						temporaryPrompt = true
					}
				}.also { applyPredictionAction = it }

				KEY_PRESSED(CANCEL) {
					name = "exit SAM tool"
					createToolNode = { apply { addStyleClass(Style.REJECT_ICON) } }
					onAction { mode?.apply { switchTool(defaultTool) } }
				}
			},

			DeviceManager.xTouchMini?.let { device ->
				activeViewerProperty.get()?.viewer()?.let { viewer ->
					painteraMidiActionSet("sam", device, viewer) {
						MidiToggleEvent.BUTTON_TOGGLE(8) {
							name = "PrimaryClickIn"
							afterRegisterEvent = {
								toggleDisplayProperty.bind(primaryClickLabelProperty.isEqualTo(SamPointLabel.FOREGROUND))
							}
							onAction { primaryClickToggleIncludeAction() }
						}
						MidiToggleEvent.BUTTON_TOGGLE(9) {
							name = "PrimaryClickOut"
							afterRegisterEvent = {
								toggleDisplayProperty.bind(primaryClickLabelProperty.isEqualTo(SamPointLabel.BACKGROUND))
							}
							onAction { primaryClickToggleExcludeAction() }
						}

						MidiButtonEvent.BUTTON_PRESSED(10) {
							name = "ResetPrompt"
							verifyEventNotNull()
							onAction { resetPromptAction() }
						}
						MidiButtonEvent.BUTTON_PRESSED(11) {
							name = "ApplyPrediction"
							verifyEventNotNull()
							onAction {
								applyPredictionAction()
								Platform.runLater {
									resetPromptAction()
								}
							}
						}

						MidiFaderEvent.FADER(0) {
							estimatedThresholdProperty.addListener { _, _, _ ->
								min = (thresholdBounds.min).toInt()
								max = (thresholdBounds.max).toInt()
							}
							onAction {
								threshold = it!!.value.toDouble()
								currentPredictionRequest?.first?.let {
									requestPrediction(it, false)
								}
							}
						}

						MidiPotentiometerEvent.POTENTIOMETER_ABSOLUTE(7) {
							displayType = VPotControl.DisplayType.FAN
							asPercent = true
							estimatedThresholdProperty.addListener { _, _, estimated ->
								/*do nothing if estimated threshold is null */
								estimated ?: return@addListener
								val thresholdPercent = ((threshold - thresholdBounds.min) / (thresholdBounds.max - thresholdBounds.min)).absoluteValue.coerceIn(0.0, 1.0)
								control.setValueSilently((127 * thresholdPercent).toInt())
								control.display()
							}
							onAction {
								threshold = thresholdBounds.min + (thresholdBounds.max - thresholdBounds.min) * it!!.value.toDouble()
								currentPredictionRequest?.first?.let {
									requestPrediction(it, false)
								}
							}
						}
					}
				}
			},

			painteraDragActionSet("box prediction request", PaintActionType.Paint, ignoreDisable = true, consumeMouseClicked = true) {
				onDrag { mouse ->
					requestBoxPromptPrediction(mouse)
				}
			}
		)
	}

	internal fun DragActionSet.requestBoxPromptPrediction(mouse: MouseEvent) {
		val (width, height) = activeViewer?.run { width to height } ?: return
		val scale = if (!screenScale.isNaN()) screenScale else return

        val xInBounds = mouse.x.coerceIn(0.0, width - 1.0)
        val yInBounds = mouse.y.coerceIn(0.0, height - 1.0)

		val (minX, maxX) = (if (startX < mouse.x) startX to xInBounds else xInBounds to startX)
		val (minY, maxY) = (if (startY < mouse.y) startY to yInBounds else yInBounds to startY)

		val x1 = (minX * scale).toFloat()
		val y1 = (minY * scale).toFloat()
		val x2 = (maxX * scale).toFloat()
		val y2 = (maxY * scale).toFloat()
		val prompt = setBoxPrompt( x1, y1, x2, y2)
		temporaryPrompt = false
		requestPrediction(prompt)
	}

	private fun resetPromptAndPrediction() {
		clearPromptDrawings()
		currentPredictionRequest = null
		viewerMask?.apply {
			val newImgs = newBackingImages()
			updateBackingImages(newImgs, newImgs)
			requestRepaint()
		}
	}

	private fun SamPrompt?.addPoints(vararg newPoints: PointPrompt): SamPrompt {
		val prompt = if (temporaryPrompt) SamPrompt() else (this ?: SamPrompt())

		prompt.prompts += newPoints.toSet()
		return prompt
	}

	private fun SamPrompt?.removePoints(vararg removePoints: PointPrompt, removeBox: Boolean = false): SamPrompt {
		if (temporaryPrompt) {
			return SamPrompt()
		}


		return this?.apply {
			prompts.removeAll(removePoints.toSet())
			if (removeBox) {
				prompts.removeIf { it is BoxPrompt }
			}
		} ?: SamPrompt()
	}

	fun setBoxPrompt(x1 : Float, y1 : Float, x2: Float, y2 : Float): SamPrompt {
		val prompt = currentPredictionRequest?.first.removePoints(removeBox = true)
		prompt.prompts += BoxPrompt(x1, y1, x2, y2)
		return prompt
	}

	private fun Pane.drawCircle(point: PointPrompt) {
		val samStyle = SamStyle[point.label] ?: return
		InvokeOnJavaFXApplicationThread {
			children += Circle(5.0).apply {
				translateX = point.x / screenScale - width / 2
				translateY = point.y / screenScale - height / 2
				addStyleClass(samStyle)

				/* If clicked again, remove it */
				painteraActionSet("remove-circle", ignoreDisable = true) {
					MOUSE_CLICKED {
						onAction {
							children -= this@apply
							requestPrediction(currentPredictionRequest?.first.removePoints(point))
						}
					}
				}.also { installActionSet(it) }
			}
		}
	}

	internal fun SamPrompt.drawPrompt() {
		InvokeOnJavaFXApplicationThread {
			val diff = VisualPromptDiff(this@drawPrompt)
			diff.removeMissingPrompts()
			for (prompt in diff.promptsToDraw()) {
				when (prompt) {
					is BoxPrompt -> setViewer?.drawBox(prompt)
					is PointPrompt -> setViewer?.drawCircle(prompt)
					else -> LOG.warn { "Unknown prompt type for visualization (${prompt::class.simpleName})" }
				}
			}
		}
	}

	private inner class VisualPromptDiff(prompt: SamPrompt) {
		val viewer = setViewer!!
		val prompts = prompt.flatten().toSet()
		val newPoints = prompts.filterIsInstance<PointPrompt>() ?: emptySet()
		val newBox = prompt.flatten().filterIsInstance<BoxPrompt>().firstOrNull()

		val existingCircles = viewer.children
			.filterIsInstance<Circle>()
			.filter { it.styleClass.any { s -> s == SAM_POINT.style } }
		val existingBox = viewer.children
			.filterIsInstance<Rectangle>()
			.firstOrNull { it.styleClass.any { s -> s == SAM_BOX_OVERLAY.style } }

		fun removeMissingPrompts() {
			existingCircles.forEach { circle ->
				if (circle.toPointPrompt() !in newPoints) viewer.children -= circle
			}
			if (existingBox != null && existingBox.toBoxPrompt() != newBox) viewer.children -= existingBox
		}

		fun promptsToDraw(): List<SamPromptBase> {
			val existingPoints = existingCircles.map { it.toPointPrompt() }.toSet()
			val newCircles = newPoints.filter { it !in existingPoints }
			val box = if (newBox != null && newBox != existingBox?.toBoxPrompt()) listOf(newBox) else emptyList()
			return newCircles + box
		}

		private fun Circle.toPointPrompt(): PointPrompt {
			val x = ((translateX + viewer.width / 2) * screenScale).toFloat()
			val y = ((translateY + viewer.height / 2) * screenScale).toFloat()
			val label = labelFor(this) ?: SamPointLabel.FOREGROUND
			return PointPrompt(x, y, label)
		}

		private fun Rectangle.toBoxPrompt(): BoxPrompt {
			val maxX = (translateX + (width + viewer.width) / 2) * screenScale
			val maxY = (translateY + (height + viewer.height) / 2) * screenScale
			val minX = maxX - width * screenScale
			val minY = maxY - height * screenScale
			return BoxPrompt(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
		}

		private fun labelFor(node: Node): SamPointLabel? = when {
			node.styleClass.contains(SamStyle.SAM_INCLUDE.style) -> SamPointLabel.FOREGROUND
			node.styleClass.contains(SamStyle.SAM_EXCLUDE.style) -> SamPointLabel.BACKGROUND
			else -> null
		}


	}

	private fun clearPromptDrawings() {
		setViewer?.also { viewer ->
			InvokeOnJavaFXApplicationThread {
				viewer.children.removeIf {
					it.styleClass.any { style -> style == SAM_POINT.style || style == SAM_BOX_OVERLAY.style }
				}
			}
		}
	}

	private fun Pane.drawBox(boxPrompt: BoxPrompt) {
		InvokeOnJavaFXApplicationThread {
			val boxOverlay = children
				.filterIsInstance<Rectangle>()
				.firstOrNull { it.styleClass.contains(SAM_BOX_OVERLAY.style) }
				?: Rectangle().also {
					it.isMouseTransparent = true
					it.styleClass += SAM_BOX_OVERLAY.style
					children += it
				}

			val (topLeft, bottomRight) = boxPrompt
			val maxX = bottomRight.x / screenScale
			val maxY = bottomRight.y / screenScale
			val minX = topLeft.x / screenScale
			val minY = topLeft.y / screenScale
			boxOverlay.width = maxX - minX
			boxOverlay.height = maxY - minY
			boxOverlay.translateX = maxX - (boxOverlay.width + width) / 2
			boxOverlay.translateY = maxY - (boxOverlay.height + height) / 2
		}
	}


	open fun applyPrediction() {
		lastPrediction?.submitPrediction()
		clearPromptDrawings()
		/*
		 * the prediction job paints into the mask captured when the job started; that mask was
		 * just applied and detached, so cancel and let the next request restart with a fresh mask
		 */
		predictionJob.cancel()
	}

	private fun SamTaskInfo.submitPrediction() {
		val (maskedSource, maskInterval) = this
        currentViewerMask?.run {
			if (!maskProvided) {
                val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(
                    maskInterval.asRealInterval,
                    this.viewerMask.initialMaskToSourceWithDepthTransform,
                    .5
                )
                maskedSource.applyMask(
                    this.viewerMask,
                    sourceInterval.smallestContainingInterval,
                    MaskedSource.VALID_LABEL_CHECK
                )
                this@SamTool.viewerMask = null
			} else {
                val overlay = overlay ?: return
                val volatileOverlay = volatileOverlay ?: return
                val predictionMaxInterval = overlay.intersect(maskInterval)
				LoopBuilder
                    .setImages(
                        overlay.interval(predictionMaxInterval),
                        this.viewerMask.viewerImg.wrappedSource.interval(predictionMaxInterval)
                    )
					.multiThreaded()
					.forEachPixel { originalImage, currentImage ->
						originalImage.set(currentImage.get())
					}
                val volatilePredictionMaxInterval = volatileOverlay.intersect(maskInterval)
				LoopBuilder
                    .setImages(
                        volatileOverlay.interval(volatilePredictionMaxInterval),
                        this.viewerMask.volatileViewerImg.wrappedSource.interval(volatilePredictionMaxInterval)
                    )
					.multiThreaded()
					.forEachPixel { originalImage, currentImage ->
						originalImage.isValid = currentImage.isValid
						originalImage.get().set(currentImage.get())
					}
                this.viewerMask.updateBackingImages(background to volatileBackground)
			}
		}
	}

	fun requestPrediction(prompt: SamPrompt, estimateThreshold: Boolean = true) {
		if (prompt.prompts.isEmpty())
			temporaryPrompt = true

		if (!predictionJob.isActive) {
			startPredictionJob()
		}
		currentPredictionRequest = prompt to estimateThreshold
	}

	open fun requestPrediction(promptPoints: List<PointPrompt>, estimateThreshold: Boolean = true) {
		val prompt = SamPrompt().run {
			addPoints(*(promptPoints.toTypedArray()))
		}
		requestPrediction(prompt, estimateThreshold)
	}

	enum class MaskPriority {
		MASK,
		PREDICTION
	}

	var maskPriority = MaskPriority.PREDICTION

	private var encodeRequest: Deferred<EncoderResult>? = null

	private var currentPrediction: SamPredictor.SamPrediction? = null

	private val resetSAMTaskOnException = CoroutineExceptionHandler { _, exception ->
        if (exception is CancellationException)
			LOG.debug(exception) { "SAM Prediction job cancelled, ignoring exception" }
		else
			LOG.error(exception) { "Error during SAM Prediction " }
		isBusy = false
        runCatching { deactivate() }
            .onFailure { LOG.error(it) { "deactivate SAM Tool error after exception during prediction " } }
		mode?.apply {
			InvokeOnJavaFXApplicationThread {
				switchTool(defaultTool)
			}
		}
		SAM_TASK_SCOPE = CoroutineScope(Dispatchers.IO + Job())
	}

	protected open var currentDisplay = false

    private suspend fun getSamPredictor(): SamPredictor? {
				isBusy = true
        return runCatching { encodeRequest!!.await() }.map {
            SamPredictor(it)
			}.getOrElse { exception ->
				isBusy = false

                when (exception) {
                is InterruptedException if !currentCoroutineContext().isActive -> null
                is CancellationException -> null
					else -> {
						LOG.error(exception) { "prediction job failed" }
						throw exception
					}
                }
			}
				}

    private fun SamPrompt.getPoints(): List<PointPrompt> {
					return prompts.flatMap {
						when (it) {
							is MaskPrompt -> emptyList()
							is PointPrompt -> listOf(it)
							is SamPrompt -> it.getPoints()
						}
					}
				}

    private data class ThresholdPrediction(val img: RandomAccessibleInterval<NativeBoolType>, val interval: Interval)

    private fun thresholdPrediction(prediction: RandomAccessibleInterval<FloatType>): ThresholdPrediction? {

        val dims = prediction.dimensionsAsLongArray()
        val thresholdImg = ArrayImgs.booleans(*dims)
        val thresholdCursor = thresholdImg
			.extendValue(Float.NEGATIVE_INFINITY)
			.interval(prediction)
			.cursor()
		val predictionMaskCursor = prediction
			.extendValue(Float.NEGATIVE_INFINITY)
			.interval(prediction)
			.localizingCursor()

		val minPoint = longArrayOf(Long.MAX_VALUE, Long.MAX_VALUE)
		val maxPoint = longArrayOf(Long.MIN_VALUE, Long.MIN_VALUE)
		var noneAccepted = true
		while (thresholdCursor.hasNext()) {
			val predictionValue = predictionMaskCursor.next().get()
			val thresholdValue = predictionValue >= threshold
			thresholdCursor.next().set(thresholdValue)
			if (thresholdValue) {
				noneAccepted = false
				val pos = predictionMaskCursor.positionAsLongArray()
				minPoint[0] = min(minPoint[0], pos[0])
				minPoint[1] = min(minPoint[1], pos[1])

				maxPoint[0] = max(maxPoint[0], pos[0])
				maxPoint[1] = max(maxPoint[1], pos[1])
			}
		}

        if (noneAccepted)
            return null

        val interval = Intervals.createMinMax(minPoint[0], minPoint[1], maxPoint[0], maxPoint[1])
        return ThresholdPrediction(thresholdImg, interval)
    }

    protected suspend fun selectConnectedComponents(
        thresholdPrediction: RandomAccessibleInterval<NativeBoolType>,
        prompt: SamPrompt
    ): RandomAccessibleInterval<NativeBoolType>? {

        val dims = thresholdPrediction.dimensionsAsLongArray()
        val connectedComponents = ArrayImgs.unsignedLongs(*dims)
				/* FIXME: This is annoying, but I don't see a better way around it at the moment.
             `labelAllConnectedComponents` can be interrupted, but doing so causes an
             internal method to `printStackTrace()` on the error. So even when
             It's intentionally and interrupted and handeled, the console still logs the
             stacktrace to stderr. We temporarily wrap stderr to swalleow it.
             When [https://github.com/imglib/imglib2-algorithm/issues/98] is resolved,
             hopefully this will be as well
         */
				val stdErr = System.err
				System.setErr(NullPrintStream.INSTANCE)
				try {
					ConnectedComponents.labelAllConnectedComponents(
                thresholdPrediction,
						connectedComponents,
						StructuringElement.FOUR_CONNECTED
					)
				} catch (e: InterruptedException) {
					System.setErr(stdErr)
					LOG.debug(e) { "Connected Components Interrupted During SAM" }
            val cancellation = CancellationException("Connected Components Interrupted During SAM")
            currentCoroutineContext().cancel(cancellation)
            return null
				} finally {
					System.setErr(stdErr)
				}

        val points = prompt.getPoints()

				val acceptedComponents = points.asSequence()
					.filter { it.label == SamPointLabel.FOREGROUND }
					.map { it.x.toLong() to it.y.toLong() }
            .filter { (x, y) -> thresholdPrediction.getAt(x, y).get() }
					.map { (x, y) -> connectedComponents.getAt(x, y).get() }
					.toMutableSet()

				points.firstOrNull { it.label == SamPointLabel.BOX_TOP_LEFT }?.let { topLeft ->
					points.firstOrNull { it.label == SamPointLabel.BOX_BOTTOM_RIGHT }?.let { bottomRight ->

                        val minPos = longArrayOf(topLeft.x.toLong(), topLeft.y.toLong())
                        val maxPos = longArrayOf(bottomRight.x.toLong(), bottomRight.y.toLong())
                        val boxInterval = FinalInterval(minPos, maxPos)
						thresholdPrediction.extendBorder().randomAccess(boxInterval)
						val thresholdCursor = RandomAccessibleIntervalCursor(thresholdPrediction.extendBorder(), boxInterval)
                        val componentsCursor = connectedComponents.interval(boxInterval).cursor()
                        while (thresholdCursor.hasNext()) {
                            val meetsThreshold = thresholdCursor.next()
                            if (meetsThreshold.get())
                                acceptedComponents += componentsCursor.next().get()
                            else
                                componentsCursor.fwd()
                        }
                    }
                }

        return connectedComponents.convertRAI(NativeBoolType()) { source, output ->
            output.set(source.get() in acceptedComponents)
        }
                }


    protected data class AlignedViewAndTransform<T>(
        val viewerInterval: IntervalView<T>,
        val transform: AffineTransform2D
    )

    protected fun <T : NumericType<T>> alignImageToViewer(
        predictedMask: RandomAccessibleInterval<T>,
        viewerMask: ViewerMask
    ): AlignedViewAndTransform<T> {

        val (width, height) = predictedMask.dimensionsAsLongArray()
                val predictionToViewerScale = Scale2D(setViewer!!.width / width, setViewer!!.height / height)
                val halfPixelOffset = Translation2D(.5, .5)
        val screenOriginMaskOffset = viewerMask.displayPointToMask(0, 0, currentDisplay)
        val viewerTranslation = Translation2D(*screenOriginMaskOffset.positionAsDoubleArray())
        val predictionToViewerTransform = AffineTransform2D()
            .concatenate(viewerTranslation)
            .concatenate(predictionToViewerScale)
                        .concatenate(halfPixelOffset)

        val alignedView = predictedMask
            .extendBorder()
            .interpolateNearestNeighbor()
            .affineReal(predictionToViewerTransform)
            .addDimension()
            .raster()
            .interval(viewerMask.viewerImg)


        return AlignedViewAndTransform(alignedView, predictionToViewerTransform)
    }

    protected fun overlayPredictionOnViewerMask(
        alignedPredictedLabels: RandomAccessibleInterval<NativeBoolType>,
        overlay: ViewerMaskOverlay,
        predictionLabel: Long
    ) {
        val compositeMask = overlay.background
                    .extendValue(Label.INVALID)
                    .convertWith(
                alignedPredictedLabels,
                        UnsignedLongType(Label.INVALID)
                    ) { original, prediction, composite ->
                        val getCompositeVal = {
                    val inPrediction = prediction.get()
                    if (inPrediction) predictionLabel else original.get()
                        }
                        val compositeVal =
                            if (maskPriority == MaskPriority.PREDICTION) getCompositeVal()
                            else original.get().takeIf { it != Label.INVALID } ?: getCompositeVal()
                        composite.set(compositeVal)
            }.interval(alignedPredictedLabels)

				val compositeMaskAsVolatile = compositeMask.convertRAI(VolatileUnsignedLongType(0L)) { source, output ->
					output.set(source.get())
					output.isValid = true
				}

        with(overlay) {
            viewerMask.updateBackingImages(
					compositeMask to compositeMaskAsVolatile,
                writableSourceImages = background to volatileBackground
				)
        }

    }


    private fun startPredictionJob() {
        val maskSource = maskedSource ?: return
        val overlay = viewerMask?.let { currentViewerMask }
        val paintMask = overlay?.viewerMask ?: return

        // clear the last prediction, so errors here aren't hidden by the last successful prediction
        lastPrediction = null

        predictionJob = SAM_TASK_SCOPE.launch(resetSAMTaskOnException) {

            val predictor = getSamPredictor() ?: return@launch

            while (predictionJob.isActive) {

                var (prompt, estimateThreshold) = promptChannel.receive()
                ensureActive()

                val prediction = currentPrediction
                    ?.takeUnless { estimateThreshold }
                    ?: predictor.predict(prompt)

				prompt = (prompt as? MultipleChoicePrompt)?.preferredPrompt ?: prompt
                currentPrediction = prediction
                val predictionLabel = currentLabelToPaint

                val (decodeSpacePrediction, promptSpacePrediction) = prediction.decodeResult.run {
                    val morph2D = Morph2D(bestMask, FloatArray(bestMask.size), maskSize to maskSize)
                    val centreFilteredLogits = morph2D.centre( 5 to 5).output
                    prediction.raiInDecodeSpace(centreFilteredLogits) to prediction.raiInPromptSpace(centreFilteredLogits)
				}

                val promptInDecodedSpace = prompt.scaleToDecodeOutput(prediction.encodeResult, prediction.decodeResult)

                if (estimateThreshold)
                    updateThresholdEstimate(promptInDecodedSpace, decodeSpacePrediction)

                val previousInterval = lastPrediction?.maskInterval
                val previousRepaintInterval = previousInterval?.extendBy(1.0)?.smallestContainingInterval

                val (binaryPredictionMask, predictionInterval2D) = thresholdPrediction(promptSpacePrediction) ?: let {
                    paintMask.requestRepaint(previousRepaintInterval)
                    lastPrediction = null
                    continue
		}

                val selectedComponentMask =
                    selectConnectedComponents(binaryPredictionMask, prompt) ?: continue

                val (alignedComponentMask, predictionToViewerTransform) = alignImageToViewer(
                    selectedComponentMask,
                    paintMask
                )

                overlayPredictionOnViewerMask(alignedComponentMask, overlay, predictionLabel)

                val predictionInterval3D = Intervals.addDimension(predictionInterval2D, 0, 0)
                val predictionIntervalInViewerSpace =
                    predictionToViewerTransform.estimateBounds(predictionInterval3D).smallestContainingInterval

                paintMask.requestRepaint(predictionIntervalInViewerSpace union previousRepaintInterval)
                val encodedImage = predictor.encodeResult
                lastPrediction = SamTaskInfo(
                    maskSource,
                    predictionIntervalInViewerSpace,
                    encodedImage,
                    prompt
                )
            }
        }
    }

    private fun updateThresholdEstimate(prompt: SamPrompt, prediction: RandomAccessibleInterval<FloatType>) {

        val points = prompt.getPoints()

        /* If there is only a box (no points) then use the sub-interval of the box to estimate the threshold.
         *   In all other cases, estimate threshold based on the entire image. */
        val onlyBoxPoints = points.all { it.label > SamPointLabel.FOREGROUND }
        val estimateOverBox =
            if (onlyBoxPoints) intervalOfBox(points)
            else null
        /* filter the threshold estimate based on the rendered source space; restrict to the box region if present */
        val inSourceFilter = sourceSpaceFilter(prediction, estimateOverBox)
		setBestEstimatedThreshold(estimateOverBox, prediction, inSourceFilter)
	}

	/**
	 * Boolean filter image in prediction space. Useful for filtering out non-source values from threshold estimation.
	 * If this isn't done, the threshold estimate is thrown off by the background pixels. In this case background refers
	 * to the background of the rendered image, not the background within the rendered source(s).
	 *
	 * @param prediction prediction image to align to filter to
	 * @param interval subset to only compute the filter over that sub-region of [prediction] instead
	 * of the entire prediction, avoiding redundant work when the estimate is already restricted to a box.
	 *
	 * @return the filter image, positioned to align with [prediction], or null when filtering is unnecessary; that is,
	 *  when every pixel is in-source (nothing to exclude) or no pixel is in-source (nothing to keep). A null return
	 *  signals downstream to skip filtering.
	 */
	private fun sourceSpaceFilter(
		prediction: RandomAccessibleInterval<FloatType>,
		interval: Interval? = null
	): RandomAccessibleInterval<NativeBoolType>? {
		/* only cover the region the estimate will actually histogram (the box, if any), not the whole prediction */
		val region = interval?.intersect(prediction)?.takeUnless { Intervals.isEmpty(it) } ?: prediction
		val offset = region.minAsLongArray()

		val decodeToViewerScale = renderState.width.toDouble() / prediction.dimension(0)
		val sources = renderState.sources.map { sac ->
			val sourceToGlobal = AffineTransform3D().also { sac.spimSource.getSourceTransform(renderState.timepoint, 0, it) }
			sourceToGlobal to sac.spimSource.getSource(renderState.timepoint, 0).asRealInterval.extendBy(0.5)
		}

		val coverage = ArrayImgs.booleans(*region.dimensionsAsLongArray())
		val viewerPoint = RealPoint(3)
		val globalPoint = RealPoint(3)
		val sourcePoint = RealPoint(3)
		var anyCovered = false
		var allCovered = true
		val cursor = coverage.localizingCursor()
		val position = DoubleArray(3)
		while (cursor.hasNext()) {
			cursor.fwd()
			/* cursor is local to the region; shift by the region offset to get the absolute prediction coordinate */
			position[0] = (offset[0] + cursor.getLongPosition(0)) * decodeToViewerScale
			position[1] = (offset[1] + cursor.getLongPosition(1)) * decodeToViewerScale
			viewerPoint.setPosition(position)
			renderState.transform.applyInverse(globalPoint, viewerPoint)
			val covered = sources.any { (sourceToGlobal, sourceInterval) ->
				sourceToGlobal.applyInverse(sourcePoint, globalPoint)
				Intervals.contains(sourceInterval, sourcePoint)
			}
			if (covered)
				anyCovered = true
			else
				allCovered = false
			cursor.get().set(covered)
		}
		/* position the filter at the region so it aligns with the matching sub-interval of the prediction */
		return coverage.translate(*offset).takeIf { anyCovered && !allCovered }
	}

	private fun intervalOfBox(points: List<PointPrompt>): FinalInterval? {
		return points.filter { it.label > SamPointLabel.FOREGROUND }.let {
			if (it.size == 2) {
				val (x1, y1) = it[0]
				val (x2, y2) = it[1]
				FinalInterval(longArrayOf(x1.toLong(), y1.toLong()), longArrayOf(x2.toLong(), y2.toLong()))
			} else null
		}
	}


    private fun setBestEstimatedThreshold(
		interval: Interval? = null,
	    prediction: RandomAccessibleInterval<FloatType>,
	    filter: RandomAccessibleInterval<NativeBoolType>? = null
	) {
		/* [-40..30] seems from testing like a reasonable range to include the vast majority of
		*  prediction values, excluding perhaps some extreme outliers (which imo is desirable) */
		val binMapper = Real1dBinMapper<FloatType>(-40.0, 30.0, 256, false)
		val histogram = LongArray(binMapper.binCount.toInt())

        val threshPredictInterval = interval?.intersect(prediction)?.let { intersection ->
			if (Intervals.isEmpty(intersection)) null else intersection
		}

        val predictionRAI = threshPredictInterval?.let { prediction.interval(it) } ?: prediction
		if (filter != null) {
			val sourcePredictionIntersectionRai = threshPredictInterval?.let { filter.interval(it) } ?: filter
			LoopBuilder.setImages(predictionRAI, sourcePredictionIntersectionRai)
				.forEachPixel { predictionValue, inSource ->
					if (inSource.get()) {
						val binIdx = binMapper.map(predictionValue).toInt()
						if (binIdx != -1)
							histogram[binIdx]++
					}
				}
		} else {
			LoopBuilder.setImages(predictionRAI)
				.forEachPixel {
					val binIdx = binMapper.map(it).toInt()
					if (binIdx != -1)
						histogram[binIdx]++
				}
		}


		val binVar = FloatType()
		val minThreshold = histogram.indexOfFirst { it > 0 }.let {
			if (it == -1) return@let thresholdBounds.min
			binMapper.getLowerBound(it.toLong(), binVar)
			binVar.get().toDouble()
		}
		val maxThreshold = histogram.indexOfLast { it > 0 }.let {
			if (it == -1) return@let thresholdBounds.max
			binMapper.getUpperBound(it.toLong(), binVar)
			binVar.get().toDouble()
		}
		val otsuIdx = otsuThresholdPrediction(histogram)
		binMapper.getUpperBound(otsuIdx, binVar)

		thresholdBounds = Bounds(minThreshold, maxThreshold)
		threshold = binVar.get().toDouble()
		estimatedThreshold = threshold
	}

	companion object {

		enum class SamStyle(val style: String, vararg classes: String) : StyleGroup by StyleGroup.of(style, *classes) {
			CIRCLE("circle"),
			SAM_POINT("sam-point"),
			SAM_BOX_OVERLAY("sam-box-overlay"),
			SAM_INCLUDE("sam-include", SAM_POINT),
			SAM_EXCLUDE("sam-exclude", SAM_POINT),
			SAM_SELECT_TOOL("sam-select", Style.FONT_ICON);

			constructor(style: String, vararg styles: StyleGroup) : this(style, *styles.flatMap { it.classes.toList() }.toTypedArray())
			constructor(style: String) : this(style, *emptyArray<StyleGroup>())

			companion object {

				val SAM_INCLUDE_TOOL = StyleGroup.of(SAM_INCLUDE, Style.IGNORE_DISABLE)
				val SAM_EXCLUDE_TOOL = StyleGroup.of(SAM_EXCLUDE, Style.IGNORE_DISABLE)

				val SAM_INCLUDE_CIRCLE = StyleGroup.of(SAM_INCLUDE, CIRCLE)
				val SAM_EXCLUDE_CIRCLE = StyleGroup.of(SAM_EXCLUDE, CIRCLE)

				operator fun get(label: SamPointLabel) = when (label) {
					SamPointLabel.FOREGROUND -> SAM_INCLUDE_CIRCLE
					SamPointLabel.BACKGROUND -> SAM_EXCLUDE_CIRCLE
					else -> null
				}
			}
		}

		private val LOG = KotlinLogging.logger { }

		private var SAM_TASK_SCOPE = CoroutineScope(Dispatchers.IO + Job())


		private fun calculateTargetScreenScaleFactor(viewer: ViewerPanelFX): Double {
			val highestScreenScale = viewer.renderUnit.screenScalesProperty.get().max()
			return calculateTargetScreenScaleFactor(viewer.width, viewer.height, highestScreenScale)
		}

		private fun RenderUnitState.calculateTargetScreenScaleFactor(): Double {
			val maxScreenScale = paintera.properties.screenScalesConfig.screenScalesProperty().get().scalesCopy.max()
			return calculateTargetScreenScaleFactor(width.toDouble(), height.toDouble(), maxScreenScale)
		}

		/**
		 * Calculates the target screen scale factor based on the highest screen scale and the viewer's dimensions.
		 * The resulting scale factor will always be the smallest of either:
		 *  1. the highest explicitly specified factor, or
		 *  2. [SamPredictor.MAX_DIM_TARGET] / `max(width, height)`
		 *
		 *  This means if the `scaleFactor * maxEdge` is less than [SamPredictor.MAX_DIM_TARGET] it will be used,
		 *  but if the `scaleFactor * maxEdge` is still larger than [SamPredictor.MAX_DIM_TARGET], then a more
		 *  aggressive scale factor will be returned. See [SamPredictor.MAX_DIM_TARGET] for more information.
		 *
		 * @return The calculated scale factor.
		 */
		private fun calculateTargetScreenScaleFactor(width: Double, height: Double, highestScreenScale: Double): Double {
			val maxEdge = max(ceil(width * highestScreenScale), ceil(height * highestScreenScale))
			return min(highestScreenScale, MAX_DIM_TARGET / maxEdge)
		}


		data class SamTaskInfo(val maskedSource: MaskedSource<*, *>, val maskInterval: Interval, val encodedImage: EncoderResult, val samPrompt: SamPrompt)
	}
}
