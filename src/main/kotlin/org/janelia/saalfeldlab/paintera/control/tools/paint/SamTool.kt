package org.janelia.saalfeldlab.paintera.control.tools.paint

import ai.onnxruntime.OrtException
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
import javafx.beans.Observable
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
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
import net.imglib2.algorithm.labeling.ConnectedComponents
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement
import net.imglib2.histogram.Real1dBinMapper
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.*
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.type.volatiles.VolatileFloatType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.apache.commons.io.output.NullPrintStream
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.control.VPotControl
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiFaderEvent
import org.janelia.saalfeldlab.fx.midi.MidiPotentiometerEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.StyleGroup
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.ai.ImageEncoderCache
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.renderState
import org.janelia.saalfeldlab.paintera.ai.sam.MAX_DIM_TARGET
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

	private val selectedIdListener: (obs: Observable) -> Unit = {
		statePaintContext?.selectedIds?.lastSelection?.let { currentLabelToPaint = it }
	}

	@Suppress("UNNECESSARY_LATEINIT") // lateinit so we can self-reference, so it removes itself after being triggered.
	private lateinit var setCursorWhenDoneApplying: ChangeListener<Boolean>
	internal val maskedSource: MaskedSource<*, *>?
		get() = activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>

	private var currentViewerMask: ViewerMask? = null
	private var originalBackingImage: RandomAccessibleInterval<UnsignedLongType>? = null
	private var originalWritableBackingImage: RandomAccessibleInterval<UnsignedLongType>? = null
	private var originalVolatileBackingImage: RandomAccessibleInterval<VolatileUnsignedLongType>? = null
	private var originalWritableVolatileBackingImage: RandomAccessibleInterval<VolatileUnsignedLongType>? = null
	private var maskProvided = false

	//TODO Caleb: document this; it stops `cleanup()` from removing the wrapped overlay of the prediction
	var unwrapResult = true

	private var setViewer: ViewerPanelFX? = null
	internal var viewerMask: ViewerMask? = null
		get() {
			if (field == null) {
				field = maskedSource!!.createViewerMask(
					MaskInfo(0, setViewer!!.state.bestMipMapLevel),
					setViewer!!
				)
				originalBackingImage = field?.viewerImg?.wrappedSource
				originalWritableBackingImage = field?.viewerImg?.writableSource
				originalVolatileBackingImage = field?.volatileViewerImg?.wrappedSource
				originalWritableVolatileBackingImage = field?.volatileViewerImg?.writableSource
				maskProvided = false
			}
			currentViewerMask = field
			return field!!
		}
		set(value) {
			field = value
			maskProvided = value != null
			currentViewerMask = field
			originalBackingImage = field?.viewerImg?.wrappedSource
			originalWritableBackingImage = field?.viewerImg?.writableSource
			originalVolatileBackingImage = field?.volatileViewerImg?.wrappedSource
			originalWritableVolatileBackingImage = field?.volatileViewerImg?.writableSource
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
		setCursorWhenDoneApplying = ChangeListener { observable, _, _ ->
			observable.removeListener(setCursorWhenDoneApplying)
		}
		val healthCheckScope = CoroutineScope(Dispatchers.IO)
		paintera.properties.segmentAnythingConfig.subscribe { _ ->
			isValidProperty.set(false)
			healthCheckScope.launch {
				supervisorScope {
					isValidProperty.set(ImageEncoderCache.healthCheck)
				}
			}
		}
	}

	private val isBusyProperty = SimpleBooleanProperty(false)

	private var isBusy by isBusyProperty.nonnull()

	private var screenScale = Double.NaN

	private val predictionChannel = Channel<Pair<SamPrompt, Boolean>>(1)

	private var currentPredictionRequest: Pair<SamPrompt, Boolean>? = null
		set(value) = runBlocking {
			predictionChannel.tryReceive() /* capacity 1, so this will always either do nothing, or empty the channel */
			value?.let { (request, _) ->
				predictionChannel.send(value)
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
		encodeRequest = ImageEncoderCache.request(renderState)
	}

	internal fun cleanup() {
		clearPromptDrawings()
		currentLabelToPaint = Label.INVALID
		predictionJob.cancel()
		predictionChannel.tryReceive() /*clear the channel if not empty */
		if (unwrapResult) {
			if (!maskProvided) {
				maskedSource?.resetMasks()
			} else {
				currentViewerMask?.updateBackingImages(
					originalBackingImage!! to originalVolatileBackingImage!!,
					originalWritableBackingImage!! to originalWritableVolatileBackingImage!!
				)
			}
		}
		InvokeOnJavaFXApplicationThread { setViewer?.children?.removeIf { SAM_POINT.style in it.styleClass } }
		paintera.baseView.disabledPropertyBindings -= this
		lastPrediction?.maskInterval?.let { currentViewerMask?.requestRepaint(it) }
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
						val highResPrediction = currentPrediction!!.image

						val name = "high res"
						val maskRai = highResPrediction

						val (max, mean, std) = maskRai.let {
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
						val zeroMinValue = maskRai.convertRAI(FloatType()) { input, output -> output.set(input.get() - min) }
						val predictionSource = paintera.baseView.addConnectomicsRawSource<FloatType, VolatileFloatType>(
							zeroMinValue.let {
								val prediction3D = Views.addDimension(it)
								val interval3D = Intervals.createMinMax(*it.minAsLongArray(), -1, *it.maxAsLongArray(), 1)
								prediction3D.interval(interval3D)
							},
							doubleArrayOf(1.0, 1.0, 1.0),
							doubleArrayOf(0.0, 0.0, 0.0),
							0.0, max - min,
							"$name prediction"
						)

						val transform = object : AffineTransform3D() {
							override fun set(value: Double, row: Int, column: Int) {
								super.set(value, row, column)
								predictionSource.backend.updateTransform(this)
								setViewer!!.requestRepaint()
							}
						}

						val viewerToGlobal = viewerMask!!.initialGlobalToViewerTransform.inverse()
						val predToGlobal = AffineTransform3D()
							.concatenate(viewerToGlobal)
							.concatenate(Scale3D(setViewer!!.width / maskRai.shape()[0], setViewer!!.height / maskRai.shape()[1], 1.0))
							.concatenate(Translation3D(.5, .5, 0.0))
						transform.set(*predToGlobal.inverse().rowPackedCopy)
						predictionSource.backend.updateTransform(transform)
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
//					verifyPainteraNotDisabled()
					verify(" label is not valid ") { isLabelValid }
					onAction {
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
	}

	private fun SamTaskInfo.submitPrediction() {
		val (maskedSource, maskInterval) = this
		(maskedSource.currentMask as? ViewerMask)?.let { currentMask ->
			if (!maskProvided) {
				val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(maskInterval.asRealInterval, currentMask.initialMaskToSourceWithDepthTransform, .5)
				maskedSource.applyMask(currentMask, sourceInterval.smallestContainingInterval, MaskedSource.VALID_LABEL_CHECK)
				viewerMask = null
			} else {
				val predictionMaxInterval = originalWritableBackingImage!!.intersect(maskInterval)
				LoopBuilder
					.setImages(originalWritableBackingImage!!.interval(predictionMaxInterval), currentMask.viewerImg.wrappedSource.interval(predictionMaxInterval))
					.multiThreaded()
					.forEachPixel { originalImage, currentImage ->
						originalImage.set(currentImage.get())
					}
				val volatilePredictionMaxInterval = originalWritableVolatileBackingImage!!.intersect(maskInterval)
				LoopBuilder
					.setImages(originalWritableVolatileBackingImage!!.interval(volatilePredictionMaxInterval), currentMask.volatileViewerImg.wrappedSource.interval(volatilePredictionMaxInterval))
					.multiThreaded()
					.forEachPixel { originalImage, currentImage ->
						originalImage.isValid = currentImage.isValid
						originalImage.get().set(currentImage.get())
					}
				currentMask.updateBackingImages(originalBackingImage!! to originalVolatileBackingImage!!)
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
		if (predictionJob.isCancelled)
			LOG.debug(exception) { "SAM Prediction job cancelled, ignoring exception" }
		else
			LOG.error(exception) { "Error during SAM Prediction " }
		isBusy = false
		deactivate()
		mode?.apply {
			InvokeOnJavaFXApplicationThread {
				switchTool(defaultTool)
			}
		}
		SAM_TASK_SCOPE = CoroutineScope(Dispatchers.IO + Job())
	}

	protected open var currentDisplay = false

	private fun startPredictionJob() {
		val maskSource = maskedSource ?: return
		predictionJob = SAM_TASK_SCOPE.launch(resetSAMTaskOnException) {
			val ignored = ImageEncoderCache.createOrtSessionTask.await()
			val encodedImage = runCatching {
				isBusy = true
				encodeRequest!!.await()
			}.getOrElse { exception ->
				isBusy = false

                when (exception) {
                    is InterruptedException if !currentCoroutineContext().isActive -> return@launch
                    is CancellationException -> return@launch
					else -> {
						LOG.error(exception) { "prediction job failed" }
						throw exception
					}
                }
			}

			LOG.trace { "encodedImage received" }
			val predictor = SamPredictor(encodedImage)

			while (predictionJob.isActive) {
				val (predictionRequest, estimateThreshold) = predictionChannel.receive()
				ensureActive()
				val newPredictionRequest = estimateThreshold || currentPrediction == null
				if (newPredictionRequest) {
					currentPrediction = runPredictionWithRetry(predictor, predictionRequest)
				}
				val prediction = currentPrediction!!
				val predictionLabel = currentLabelToPaint

				fun SamPrompt.getPoints(): List<PointPrompt> {
					return prompts.flatMap {
						when (it) {
							is MaskPrompt -> emptyList()
							is PointPrompt -> listOf(it)
							is SamPrompt -> it.getPoints()
						}
					}
				}

//				val prompts = prediction.prompt.flatten()
//				val boxPrompt = prompts.filterIsInstance<BoxPrompt>().firstOrNull()
//				val points = prompts.filterIsInstance<PointPrompt>()
				val points = prediction.prompt.getPoints()

				if (estimateThreshold) {
					/* If there is only a box (no points) then use the sub-interval of the box to estimate the threshold.
					*   In all other cases, estimate threshold based on the entire image. */
					val estimateOverBox = if (points.all { it.label > SamPointLabel.FOREGROUND }) intervalOfBox(points) else null
					setBestEstimatedThreshold(estimateOverBox)
//					boxPrompt?.takeIf { points.isEmpty() }?.let { (topLeft, bottomRight) ->
//						val (x1, y1) = topLeft
//						val (x2, y2) = bottomRight
//						val boxInterval = Intervals.createMinMax( x1.toLong(), y1.toLong(), x2.toLong(), y2.toLong() )
//						setBestEstimatedThreshold( boxInterval )
//					}
				}

				val paintMask = viewerMask!!

				val minPoint = longArrayOf(Long.MAX_VALUE, Long.MAX_VALUE)
				val maxPoint = longArrayOf(Long.MIN_VALUE, Long.MIN_VALUE)

				val predictedImage = prediction.image

                val thresholdFilter = ArrayImgs.booleans(*predictedImage.dimensionsAsLongArray())
					.extendValue(Float.NEGATIVE_INFINITY)
                val thresholdCursor = thresholdFilter.interval(predictedImage).cursor()
                val predictionMaskCursor = predictedImage
                    .extendValue(Float.NEGATIVE_INFINITY)
                    .interval(predictedImage)
                    .localizingCursor()

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


				val connectedComponents: RandomAccessibleInterval<UnsignedLongType> = ArrayImgs.unsignedLongs(*predictedImage.dimensionsAsLongArray())
				/* FIXME: This is annoying, but I don't see a better way around it at the moment.
				*   `labelAllConnectedComponents` can be interrupted, but doing so causes an
				*   internal method to `printStackTrace()` on the error. So even when
				*   It's intentionally and interrupted and handeled, the consol still logs the
				*   stacktrace to stderr. We temporarily wrap stderr to swalleow it.
				*   When [https://github.com/imglib/imglib2-algorithm/issues/98] is resolved,
				*   hopefully this will be as well */
				val stdErr = System.err
				System.setErr(NullPrintStream.INSTANCE)
				try {
					ConnectedComponents.labelAllConnectedComponents(
						thresholdFilter,
						connectedComponents,
						StructuringElement.FOUR_CONNECTED
					)
				} catch (e: InterruptedException) {
					System.setErr(stdErr)
					LOG.debug(e) { "Connected Components Interrupted During SAM" }
					cancel("Connected Components Interrupted During SAM")
					continue
				} finally {
					System.setErr(stdErr)
				}


				val previousPredictionInterval = lastPrediction?.maskInterval?.extendBy(1.0)?.smallestContainingInterval
				if (noneAccepted) {
					paintMask.requestRepaint(previousPredictionInterval)
					lastPrediction = null
					continue
				}

				val acceptedComponents = points.asSequence()
					.filter { it.label == SamPointLabel.FOREGROUND }
					.map { it.x.toLong() to it.y.toLong() }
					.filter { (x, y) -> thresholdFilter.getAt(x, y).get() }
					.map { (x, y) -> connectedComponents.getAt(x, y).get() }
					.toMutableSet()

				points.firstOrNull { it.label == SamPointLabel.BOX_TOP_LEFT }?.let { topLeft ->
					points.firstOrNull { it.label == SamPointLabel.BOX_BOTTOM_RIGHT }?.let { bottomRight ->

                        val minPos = longArrayOf(topLeft.x.toLong(), topLeft.y.toLong())
                        val maxPos = longArrayOf(bottomRight.x.toLong(), bottomRight.y.toLong())
                        val boxInterval = FinalInterval(minPos, maxPos)
                        val thresholdCursor = thresholdFilter.interval(boxInterval).cursor()
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

                val selectedComponents = connectedComponents.convertRAI(FloatType()) { source, output ->
                    val value = if (source.get() in acceptedComponents) 1.0f else 0.0f
                    output.set(value)
                }

                val (width, height) = predictedImage.dimensionsAsLongArray()
                val predictionToViewerScale = Scale2D(setViewer!!.width / width, setViewer!!.height / height)
                val halfPixelOffset = Translation2D(.5, .5)
                val translationToViewer =
                    Translation2D(*paintMask.displayPointToMask(0, 0, currentDisplay).positionAsDoubleArray())
                val predictionToViewerTransform =
                    AffineTransform2D().concatenate(translationToViewer).concatenate(predictionToViewerScale)
                        .concatenate(halfPixelOffset)
                val maskAlignedSelectedComponents = selectedComponents
                    .extendValue(0.0)
                    .interpolate(NLinearInterpolatorFactory())
                    .affineReal(predictionToViewerTransform)
                    .convert(UnsignedLongType(Label.INVALID)) { source, output -> output.set(if (source.get() > .8) predictionLabel else Label.INVALID) }
                    .addDimension()
                    .raster()
                    .interval(paintMask.viewerImg)


                val compositeMask = originalBackingImage!!
                    .extendValue(Label.INVALID)
                    .convertWith(
                        maskAlignedSelectedComponents,
                        UnsignedLongType(Label.INVALID)
                    ) { original, prediction, composite ->
                        val getCompositeVal = {
                            val predictedVal = prediction.get()
                            if (predictedVal == predictionLabel) predictionLabel else original.get()
                        }
                        val compositeVal =
                            if (maskPriority == MaskPriority.PREDICTION) getCompositeVal()
                            else original.get().takeIf { it != Label.INVALID } ?: getCompositeVal()
                        composite.set(compositeVal)
                    }.interval(maskAlignedSelectedComponents)

				val compositeMaskAsVolatile = compositeMask.convertRAI(VolatileUnsignedLongType(0L)) { source, output ->
					output.set(source.get())
					output.isValid = true
				}

				paintMask.updateBackingImages(
					compositeMask to compositeMaskAsVolatile,
					writableSourceImages = originalBackingImage to originalVolatileBackingImage
				)

				val predictionInterval3D = Intervals.createMinMax(*minPoint, 0, *maxPoint, 0)
				val predictionIntervalInViewerSpace = predictionToViewerTransform.estimateBounds(predictionInterval3D).smallestContainingInterval

				paintMask.requestRepaint(predictionIntervalInViewerSpace union previousPredictionInterval)
				lastPrediction = SamTaskInfo(maskSource, predictionIntervalInViewerSpace, encodedImage, predictionRequest)
			}
		}
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


	private fun setBestEstimatedThreshold(interval: Interval? = null) {
		/* [-40..30] seems from testing like a reasonable range to include the vast majority of
		*  prediction values, excluding perhaps some extreme outliers (which imo is desirable) */
		val binMapper = Real1dBinMapper<FloatType>(-40.0, 30.0, 256, false)
		val histogram = LongArray(binMapper.binCount.toInt())

		val threshPredictInterval = interval?.intersect(currentPrediction?.image)?.let { intersection ->
			if (Intervals.isEmpty(intersection)) null else intersection
		}

		val predictionRAI = threshPredictInterval?.let { currentPrediction!!.image.interval(it) } ?: currentPrediction!!.image
		LoopBuilder.setImages(predictionRAI)
			.forEachPixel {
				val binIdx = binMapper.map(it).toInt()
				if (binIdx != -1)
					histogram[binIdx]++
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

	private fun runPredictionWithRetry(predictor: SamPredictor, prompt: SamPrompt): SamPredictor.SamPrediction {
		/* FIXME: This is a bit hacky, but works for now until a better solution is found.
		*   Some explenation. When running the SAM predictions, occasionally the following OrtException is thrown:
		*   [E:onnxruntime:, sequential_executor.cc:494 ExecuteKernel]
		*       Non-zero status code returned while running Resize node.
		*       Name:'/Resize_1' Status Message: upsamplebase.h:334 ScalesValidation Scale value should be greater than 0.
		*   This seems to only happen infrequently, and only when installed via conda (not the platform installer, or running from source).
		*   The temporary solution here is to just call it again, recursively, until it succeeds. I have not yet seen this
		*   to be a problem in practice, but ideally it wil be unnecessary in the future. Either by the underlying issue
		*   no longer occuring, or finding a better solution. */
		return try {
			predictor.predict(prompt)
		} catch (e: OrtException) {
			LOG.trace { "${e.message}" }
			runPredictionWithRetry(predictor, prompt)
		}
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
