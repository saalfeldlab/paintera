package org.janelia.saalfeldlab.paintera.control.tools.paint

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import bdv.cache.SharedQueue
import bdv.fx.viewer.ViewerPanelFX
import bdv.fx.viewer.render.RenderUnit
import bdv.viewer.Interpolation
import bdv.viewer.SourceAndConverter
import com.amazonaws.util.Base64
import com.google.common.util.concurrent.ThreadFactoryBuilder
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.application.Platform
import javafx.beans.Observable
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.scene.control.ButtonBase
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.input.MouseEvent.MOUSE_MOVED
import javafx.scene.input.ScrollEvent.SCROLL
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.labeling.ConnectedComponents
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement
import net.imglib2.converter.Converters
import net.imglib2.histogram.Real1dBinMapper
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.iterator.IntervalIterator
import net.imglib2.loops.LoopBuilder
import net.imglib2.parallel.TaskExecutors
import net.imglib2.realtransform.*
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.type.volatiles.VolatileFloatType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.apache.commons.io.output.NullPrintStream
import org.apache.http.HttpException
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.createViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.getGlobalViewerInterval
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamPredictor.Companion.LOW_RES_MASK_DIM
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamPredictor.SamPoint
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.predicate.threshold.Bounds
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.paintera.util.algorithms.otsuThresholdPrediction
import org.janelia.saalfeldlab.util.*
import org.slf4j.LoggerFactory
import paintera.net.imglib2.view.BundleView
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.invoke.MethodHandles
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import javax.imageio.ImageIO
import kotlin.collections.set
import kotlin.math.*
import kotlin.properties.Delegates


open class SamTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) : PaintTool(activeSourceStateProperty, mode) {

	override val graphic = { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "sam-select") } }
	override val name = "Segment Anything"
	override val keyTrigger = listOf(KeyCode.A)

	override val toolBarButton: ButtonBase
		get() {
			val button = ToggleButton(null, graphic())
			mode?.apply {
				button.onAction = EventHandler {
					this@SamTool.activeViewer?.let {
						if (activeTool == this@SamTool) {
							switchTool(defaultTool)
						} else {
							disableUnfocusedViewers()
							switchTool(this@SamTool)
						}
					} ?: let {
						if (activeTool == this@SamTool) {
							switchTool(defaultTool)
						} else {
							statusProperty.unbind()
							selectViewerBefore {
								disableUnfocusedViewers()
								switchTool(this@SamTool)
							}
						}
					}
				}
			}

			return button.also {
				it.userData = this
				it.disableProperty().bind(paintera.baseView.isDisabledProperty)
				it.styleClass += "toolbar-button"
				it.tooltip = Tooltip("$name: ${KeyTracker.keysToString(*keyTrigger.toTypedArray())}")
			}
		}


	private val currentLabelToPaintProperty = SimpleObjectProperty(Label.INVALID)
	internal var currentLabelToPaint: Long by currentLabelToPaintProperty.nonnull()
	private val isLabelValid get() = currentLabelToPaint != Label.INVALID
	private var controlMode = false

	override val actionSets by LazyForeignValue({ activeViewerAndTransforms }) {
		mutableListOf(
			*super.actionSets.toTypedArray(),
			*getSamActions().filterNotNull().toTypedArray(),
		)
	}

	override val statusProperty = SimpleStringProperty()

	private val selectedIdListener: (obs: Observable) -> Unit = {
		statePaintContext?.selectedIds?.lastSelection?.let { currentLabelToPaint = it }
	}

	/* lateinit so we can self-reference, so it removes itself after being triggered. */
	private lateinit var setCursorWhenDoneApplying: ChangeListener<Boolean>
	internal val maskedSource: MaskedSource<*, *>?
		get() = activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>

	private var currentViewerMask: ViewerMask? = null
	private var originalBackingImage: RandomAccessibleInterval<UnsignedLongType>? = null
	private var originalWritableBackingImage: RandomAccessibleInterval<UnsignedLongType>? = null
	private var originalVolatileBackingImage: RandomAccessibleInterval<VolatileUnsignedLongType>? = null
	private var originalWritableVolatileBackingImage: RandomAccessibleInterval<VolatileUnsignedLongType>? = null
	private var maskProvided = false

	private var setViewer: ViewerPanelFX? = null

	private val imgWidth: Int
		get() = ceil(setViewer!!.width * screenScale).toInt()
	private val imgHeight: Int
		get() = ceil(setViewer!!.height * screenScale).toInt()

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

	private var predictionTask: UtilityTask<Unit>? = null

	private val lastPredictionProperty = SimpleObjectProperty<SamTaskInfo?>(null)
	var lastPrediction by lastPredictionProperty.nullable()
		private set
	private val points = mutableListOf<SamPoint>()

	private var clearPoints = true;


	private var thresholdBounds = Bounds(-40.0, 30.0)
	private var threshold = 0.0
		set(value) {
			field = value.coerceIn(thresholdBounds.min, thresholdBounds.max)
		}

	init {
		setCursorWhenDoneApplying = ChangeListener { observable, _, _ ->
			observable.removeListener(setCursorWhenDoneApplying)
		}
	}

	private val isBusyProperty = SimpleBooleanProperty(false)

	private var isBusy by isBusyProperty.nonnull()

	private var screenScale by Delegates.notNull<Double>()
	private val predictionToOriginalImageScaleWithoutPadding: Double
		get() = max(imgWidth, imgHeight).toDouble() / LOW_RES_MASK_DIM

	private var predictionImagePngInputStream = PipedInputStream()
	private var predictionImagePngOutputStream = PipedOutputStream(predictionImagePngInputStream)
	override fun activate() {
		super.activate()
		if (mode is PaintLabelMode) {
			PaintLabelMode.disableUnfocusedViewers()
		}
		controlMode = false
		threshold = 0.0
		setCurrentLabelToSelection()
		statePaintContext?.selectedIds?.apply { addListener(selectedIdListener) }
		setViewer = activeViewer
		screenScale = calculateTargetScreenScaleFactor()
		statusProperty.set("Preparing SAM")
		paintera.baseView.disabledPropertyBindings[this] = isBusyProperty
		Tasks.createTask {
			predictionImagePngInputStream = PipedInputStream()
			predictionImagePngOutputStream = PipedOutputStream(predictionImagePngInputStream)
			saveActiveViewerImageFromRenderer()
			providedEmbedding ?: getImageEmbeddingTask()
			setViewer?.let { viewer ->
				if (viewer.isMouseInside) {
					Platform.runLater { statusProperty.set("Predicting...") }
					val x = viewer.mouseXProperty.get().toLong()
					val y = viewer.mouseYProperty.get().toLong()
					resetPredictionPoints()
					points += SamPoint(x * screenScale, y * screenScale, SamPredictor.SparseLabel.IN)
					requestPrediction(points)
				}
			}
		}.onSuccess { _, _ ->
			Platform.runLater { statusProperty.set("Ready") }
		}.onCancelled { _, _ ->
			Platform.runLater { statusProperty.set("Cancelled") }
			deactivate()
		}.submit(SAM_TASK_SERVICE)
	}

	override fun deactivate() {
		resetPredictionPoints()
		currentLabelToPaint = Label.INVALID
		predictionTask?.cancel()
		predictionTask = null
		if (!maskProvided) {
			maskedSource?.resetMasks()
		} else {
			currentViewerMask?.updateBackingImages(
				originalBackingImage!! to originalVolatileBackingImage!!,
				originalWritableBackingImage!! to originalWritableVolatileBackingImage!!
			)
		}
		currentViewerMask?.viewer?.children?.removeIf { SAM_POINT_STYLE in it.styleClass }
		paintera.baseView.disabledPropertyBindings -= this
		lastPrediction?.maskInterval?.let { currentViewerMask?.requestRepaint(it) }
		viewerMask = null
		controlMode = false
		if (mode is PaintLabelMode) {
			PaintLabelMode.enableAllViewers()
		}
		super.deactivate()
	}

	protected open fun setCurrentLabelToSelection() {
		currentLabelToPaint = statePaintContext?.paintSelection?.invoke() ?: Label.INVALID
	}

	private fun getSamActions() = arrayOf(
		painteraActionSet("sam selections", PaintActionType.Paint, ignoreDisable = true) {
			/* Handle Painting */
			MOUSE_CLICKED(MouseButton.PRIMARY) {
				name = "apply last segmentation result to canvas"
				verifyEventNotNull()
				verifyPainteraNotDisabled()
				verify("cannot be in control mode") { !controlMode }
				verify(" label is not valid ") { isLabelValid }
				onAction {
					lastPrediction?.submitPrediction()
					resetPredictionPoints()
				}
			}
			KEY_PRESSED(KeyCode.ENTER) {
				name = "key apply last segmentation result to canvas"
				verifyEventNotNull()
				verifyPainteraNotDisabled()
				verify(" label is not valid ") { isLabelValid }
				onAction {
					lastPrediction?.submitPrediction()
					resetPredictionPoints()
				}
			}
			KEY_PRESSED(KeyCode.D) {
				name = "view prediction"
				verifyEventNotNull()
				verifyPainteraNotDisabled()
				verify("now current prediction ") { currentPrediction != null }
				var toggle = true
				onAction {

					val lowResWidth: Long
					val lowResHeight: Long
					if (imgWidth > imgHeight) {
						lowResWidth = LOW_RES_MASK_DIM.toLong()
						lowResHeight = ceil(imgHeight / predictionToOriginalImageScaleWithoutPadding).toLong()
					} else {
						lowResHeight = LOW_RES_MASK_DIM.toLong()
						lowResWidth = ceil(imgWidth / predictionToOriginalImageScaleWithoutPadding).toLong()
					}

					val highResPrediction = ArrayImgs.floats(currentPrediction!!.masks!!.floatBuffer.array(), imgWidth.toLong(), imgHeight.toLong())
					val lowResPrediction = currentPrediction!!.image

					val name: String
					val (mask, maskRai) = if (toggle) {
						toggle = false
						name = "high res"
						highResPrediction to highResPrediction.interval(Intervals.createMinSize(0, 0, imgWidth.toLong(), imgHeight.toLong()))
					} else {
						toggle = true
						name = "low res"
						lowResPrediction to lowResPrediction.interval(Intervals.createMinSize(0, 0, lowResWidth, lowResHeight))
					}

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
					val zeroMinValue = mask.convert(FloatType()) { input, output -> output.set(input.get() - min) }
					val predictionSource = paintera.baseView.addConnectomicsRawSource<FloatType, VolatileFloatType>(
						zeroMinValue.let {
							val prediction3D = Views.addDimension(it)
							val interval3D = Intervals.createMinMax(*it.minAsLongArray(), 0, *it.maxAsLongArray(), 0)
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


					setViewer!!.getGlobalViewerInterval().also {
						val width = it.realMax(0) - it.realMin(0)
						val height = it.realMax(1) - it.realMin(1)
						val depth = it.realMax(2) - it.realMin(2)
						transform.set(
							*AffineTransform3D()
								.concatenate(Translation3D(it.realMin(0), it.realMin(1), it.realMin(2)))
								.concatenate(Scale3D(width / maskRai.shape()[0], height / maskRai.shape()[1], depth))
								.concatenate(Translation3D(.5, .5, 0.0)) //half-pixel offset
								.inverse()
								.rowPackedCopy
						)
					}
					predictionSource.backend.updateTransform(transform)

//					Stage().apply {
//						val makeFields: (Int) -> SpatialField<DoubleProperty> = { idx ->
//							SpatialField.doubleField(0.0, { true }, Region.USE_COMPUTED_SIZE, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST).also {
//								it.showHeader = false
//								if (idx == 3) {
//									it.setValues(transform[0, idx], transform[1, idx], transform[2, idx])
//									it.x.valueProperty().addListener { _, _, new -> transform.set(new.toDouble(), 0, idx) }
//									it.y.valueProperty().addListener { _, _, new -> transform.set(new.toDouble(), 1, idx) }
//									it.z.valueProperty().addListener { _, _, new -> transform.set(new.toDouble(), 2, idx) }
//								} else {
//									it.setValues(transform[idx, 0], transform[idx, 1], transform[idx, 2])
//									it.x.valueProperty().addListener { _, _, new -> transform.set(new.toDouble(), idx, 0) }
//									it.y.valueProperty().addListener { _, _, new -> transform.set(new.toDouble(), idx, 1) }
//									it.z.valueProperty().addListener { _, _, new -> transform.set(new.toDouble(), idx, 2) }
//								}
//							}
//						}
//						val sf1 = makeFields(0)
//						val sf2 = makeFields(1)
//						val sf3 = makeFields(2)
//						val sf4 = makeFields(3)
//						scene = Scene(VBox(sf1.node, sf2.node, sf3.node, sf4.node), 450.0, 800.0)
//					}.show()

					predictionSource.composite = ARGBCompositeAlphaAdd()
					setViewer!!.requestRepaint()
				}
			}
			KEY_PRESSED(KeyCode.CONTROL) {
				onAction { controlMode = true }
			}
			KEY_RELEASED(KeyCode.CONTROL) {
				onAction { controlMode = false }
			}

			SCROLL {
				verify("scroll size at least 1 pixel") { max(it!!.deltaX.absoluteValue, it.deltaY.absoluteValue) > 1.0 }
				verify { controlMode }
				verifyEventNotNull()
				verifyPainteraNotDisabled()
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
					requestPrediction(points, true)
				}
			}

			MOUSE_MOVED {
				name = "prediction overlay"
				verifyEventNotNull()
				verifyPainteraNotDisabled()
				verify("Cannot be in control mode") { !controlMode }
				verify("Label is not valid") { isLabelValid }
				onAction {
					resetPredictionPoints()
					points += SamPoint(it!!.x * screenScale, it.y * screenScale, SamPredictor.SparseLabel.IN)
					requestPrediction(points)
				}
			}

			/* Handle Include Points */
			MOUSE_CLICKED(MouseButton.PRIMARY) {
				name = "include point"
				verifyEventNotNull()
				verifyPainteraNotDisabled()
				verify { controlMode }
				onAction {
					if (clearPoints)
						resetPredictionPoints()
					clearPoints = false
					val point = SamPoint(it!!.x * screenScale, it.y * screenScale, SamPredictor.SparseLabel.IN)
					drawCircle(it, point, SamPointStyle.Include)
					points += point
					requestPrediction(points)
				}
			}

			MOUSE_CLICKED ( MouseButton.SECONDARY) {
				name = "exclude point"
				verifyEventNotNull()
				verifyPainteraNotDisabled()
				verify { controlMode }
				onAction {
					if (clearPoints)
						resetPredictionPoints()
					clearPoints = false
					val point = SamPoint(it!!.x * screenScale, it.y * screenScale, SamPredictor.SparseLabel.OUT)
					drawCircle(it, point, SamPointStyle.Exclude)
					points += point
					requestPrediction(points)
				}
			}
		},

		DeviceManager.xTouchMini?.let { device ->
			activeViewerProperty.get()?.viewer()?.let { viewer ->
				painteraMidiActionSet("midi sam tool actions", device, viewer, PaintActionType.Paint) {
					MidiButtonEvent.BUTTON_PRESED(8) {
						onAction { controlMode = true }
					}
					MidiButtonEvent.BUTTON_RELEASED(8) {
						onAction { controlMode = false }
					}
				}
			}
		},

		painteraDragActionSet("box prediction request", PaintActionType.Paint, ignoreDisable = true, consumeMouseClicked = true) {
			var boxOverlay: Rectangle? = null
			onDragDetected {
				clearPoints = false
				boxOverlay = Rectangle().also { newBox ->
					newBox.styleClass += SAM_BOX_OVERLAY_STYLE
					setViewer?.let { viewer ->
						val oldBox = boxOverlay
						InvokeOnJavaFXApplicationThread {
							oldBox?.let { viewer.children -= oldBox }
							viewer.children += newBox
						}
					}
				}
			}
			onDrag { mouse ->
				val box = boxOverlay!!
				setViewer?.let { viewer ->
					InvokeOnJavaFXApplicationThread {
						val (minX, maxX) = (if (startX < mouse.x) startX to mouse.x else mouse.x to startX)
						val (minY, maxY) = (if (startY < mouse.y) startY to mouse.y else mouse.y to startY)

						box.width = maxX - minX
						box.height = maxY - minY
						box.translateX = maxX - (box.width + viewer.width) / 2
						box.translateY = maxY - (box.height + viewer.height) / 2


						points.removeIf { it.label > SamPredictor.SparseLabel.IN }
						points += SamPoint(minX * screenScale, minY * screenScale, SamPredictor.SparseLabel.TOP_LEFT_BOX)
						points += SamPoint(maxX * screenScale, maxY * screenScale, SamPredictor.SparseLabel.BOTTOM_RIGHT_BOX)
						requestPrediction(points)
					}
				}
			}
		}
	)

	private fun drawCircle(it: MouseEvent, point: SamPoint, samStyle: SamPointStyle) {
		setViewer?.let { viewer ->
			Platform.runLater {
				viewer.children += Circle(5.0).apply {
					translateX = it.x - viewer.width / 2
					translateY = it.y - viewer.height / 2
					styleClass += samStyle.styles

					/* If clicked again, remove it */
					painteraActionSet("remove-circle", ignoreDisable = true) {
						MOUSE_CLICKED {
							onAction {
								points -= point
								viewer.children -= this@apply
								requestPrediction(points)
							}
						}
					}.also { installActionSet(it) }
				}
			}
		}
	}

	private fun resetPredictionPoints() {
		points.clear()
		clearCircles()
		clearBox()
		clearPoints = true
	}

	private fun clearCircles() = setViewer?.let { viewer ->
		Platform.runLater { viewer.children.removeIf { SAM_POINT_STYLE in it.styleClass } }
	}

	private fun clearBox() = setViewer?.let { viewer ->
		Platform.runLater { viewer.children.removeIf { SAM_BOX_OVERLAY_STYLE in it.styleClass } }
	}

	private fun SamTaskInfo.submitPrediction() {
		val (maskedSource, maskInterval) = this
		(maskedSource.currentMask as? ViewerMask)?.let { currentMask ->
			if (!maskProvided) {
				val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(maskInterval.asRealInterval, currentMask.initialMaskToSourceWithDepthTransform, .5)
				maskedSource.applyMask(currentMask, sourceInterval.smallestContainingInterval, MaskedSource.VALID_LABEL_CHECK)
				viewerMask = null
			} else {
				LoopBuilder
					.setImages(originalWritableBackingImage!!.interval(maskInterval), currentMask.viewerImg.wrappedSource.interval(maskInterval))
					.multiThreaded()
					.forEachPixel { originalImage, currentImage ->
						originalImage.set(currentImage.get())
					}
				LoopBuilder
					.setImages(originalWritableVolatileBackingImage!!.interval(maskInterval), currentMask.volatileViewerImg.wrappedSource.interval(maskInterval))
					.multiThreaded()
					.forEachPixel { originalImage, currentImage ->
						originalImage.isValid = currentImage.isValid
						originalImage.get().set(currentImage.get())
					}
				currentMask.updateBackingImages(originalBackingImage!! to originalVolatileBackingImage!!)
			}
		}
	}

	protected lateinit var getImageEmbeddingTask: UtilityTask<OnnxTensor>

	private val predictionQueue = LinkedBlockingQueue<Pair<SamPredictor.PredictionRequest, Boolean>>(1)

	fun requestPrediction(points: List<SamPoint> = emptyList(), refresh: Boolean = false) {
		if (predictionTask == null || predictionTask?.isCancelled == true) {
			startPredictionTask()
		}
		synchronized(predictionQueue) {
			predictionQueue.clear()
			predictionQueue.put(SamPredictor.points(listOf(*points.toTypedArray())) to refresh)
		}
	}

	private fun getImageEmbeddingTask() {
		Tasks.createTask {
			isBusy = true
			val entityBuilder = MultipartEntityBuilder.create()
			entityBuilder.addBinaryBody("image", predictionImagePngInputStream, ContentType.APPLICATION_OCTET_STREAM, "null")

			val client = HttpClients.createDefault()
			val post = HttpPost(paintera.properties.segmentAnythingConfig.serviceUrl)
			post.entity = entityBuilder.build()

			val response = client.execute(post)
			val entity = response.entity
			EntityUtils.toByteArray(entity).let {
				val decodedEmbedding: ByteArray
				try {
					decodedEmbedding = Base64.decode(it)
				} catch (e: IllegalArgumentException) {
					throw HttpException(String(it))
				}
				val directBuffer = ByteBuffer.allocateDirect(decodedEmbedding.size).order(ByteOrder.nativeOrder())
				directBuffer.put(decodedEmbedding, 0, decodedEmbedding.size)
				directBuffer.position(0)
				val floatBuffEmbedding = directBuffer.asFloatBuffer()
				floatBuffEmbedding.position(0)
				/* need the ortEnv to be initialized, which is done during session initialization; So block and wait here. */
				createOrtSessionTask.get() /* But we don't actually need the session here. */
				OnnxTensor.createTensor(ortEnv, floatBuffEmbedding, longArrayOf(1, 256, 64, 64))!!
			}
		}.onEnd {
			isBusy = false
		}.onFailed { _, task ->
			LOG.error("Failure retrieving image embedding", task.exception)
			mode?.switchTool(mode.defaultTool)
		}.also {
			getImageEmbeddingTask = it
			it.submit(SAM_TASK_SERVICE)
		}
	}


	internal var providedEmbedding: OnnxTensor? = null
	private var currentPrediction: SamPredictor.SamPrediction? = null


	private fun startPredictionTask() {
		val maskSource = maskedSource ?: return
		val task = Tasks.createTask { task ->
			val session = createOrtSessionTask.get()
			val embedding = providedEmbedding ?: getImageEmbeddingTask.get()
			val predictor = SamPredictor(ortEnv, session, embedding, imgWidth to imgHeight)
			while (!task.isCancelled) {
				val (predictionRequest, refresh) = predictionQueue.take()

				val lowResWidth: Long
				val lowResHeight: Long
				if (imgWidth > imgHeight) {
					lowResWidth = LOW_RES_MASK_DIM.toLong()
					lowResHeight = ceil(imgHeight / predictionToOriginalImageScaleWithoutPadding).toLong()
				} else {
					lowResHeight = LOW_RES_MASK_DIM.toLong()
					lowResWidth = ceil(imgWidth / predictionToOriginalImageScaleWithoutPadding).toLong()
				}
				val lowResIntervalWithoutPadding = Intervals.createMinSize(0, 0, lowResWidth, lowResHeight)

				val newPredictionRequest = !refresh || currentPrediction == null
				if (newPredictionRequest) {
					currentPrediction = runPredictionWithRetry(predictor, predictionRequest)
				}
				if (!refresh) {
					setBestEstimatedThreshold(lowResIntervalWithoutPadding)
				}

				val paintMask = viewerMask!!

				val minPointInLowResMask = longArrayOf(Long.MAX_VALUE, Long.MAX_VALUE)
				val maxPointInLowResMask = longArrayOf(Long.MIN_VALUE, Long.MIN_VALUE)

				var noneAccepted = true
				val lowResFilter = Converters.convert(
					BundleView(currentPrediction!!.image),
					{ predictionMaskRA, output ->
						val predictionType = predictionMaskRA.get()
						val predictionValue = predictionType.get()
						val accept = predictionValue >= threshold
						output.set(accept)
						if (accept) {
							noneAccepted = false
							/* TODO Caleb: Check if `localize()` is worth optimizing here.
							*   Should be easy, as long as it's thread safe and/or not parallel. */
							val pos = predictionMaskRA.positionAsLongArray()
							minPointInLowResMask[0] = min(minPointInLowResMask[0], pos[0])
							minPointInLowResMask[1] = min(minPointInLowResMask[1], pos[1])

							maxPointInLowResMask[0] = max(maxPointInLowResMask[0], pos[0])
							maxPointInLowResMask[1] = max(maxPointInLowResMask[1], pos[1])
						}
					},
					BoolType()
				)

				val lowResConnectedComponents: RandomAccessibleInterval<UnsignedLongType> = ArrayImgs.unsignedLongs(*lowResIntervalWithoutPadding.dimensionsAsLongArray())
				/* FIXME: This is annoying, but I don't see a better way around it at the moment.
				*   `labelAllConnectedComponents` can be interrupted, but doing so causes an
				*   internal method to `printStackTrace()` on the error. So even when
				*   It's intentionally and interrupted and handeled, the consol still logs the
				*   stacktrace to stderr. We temporarily wrap stderr to swalleow it.
				*   When [https://github.com/imglib/imglib2-algorithm/issues/98] is resolved,
				*   hopefully this will be as well */
				val stdErr = System.err
				System.setErr(NullPrintStream())
				try {
					ConnectedComponents.labelAllConnectedComponents(
						lowResFilter,
						lowResConnectedComponents,
						StructuringElement.FOUR_CONNECTED
					)
				} catch (e: InterruptedException) {
					System.setErr(stdErr)
					LOG.debug("Connected Components Interrupted During SAM", e)
					task.cancel()
					continue
				} finally {
					System.setErr(stdErr)
				}


				val previousPredictionInterval = lastPredictionProperty.get()?.maskInterval?.extendBy(1.0)?.smallestContainingInterval
				if (noneAccepted) {
					paintMask.requestRepaint(previousPredictionInterval)
					lastPredictionProperty.set(null)
					continue
				}
				val predictionPoints = (predictionRequest as? SamPredictor.SparsePrediction)?.points
				val acceptedComponents = predictionPoints
					?.asSequence()
					?.filter { it.label == SamPredictor.SparseLabel.IN }
					?.map { highResPoint ->
						val originalImgToPredictionScale = 1 / predictionToOriginalImageScaleWithoutPadding
						val (x, y) = highResPoint.centerScaledCoordinates(originalImgToPredictionScale)
						x.toLong() to y.toLong()
					}
					?.filter { (x, y) -> lowResFilter.getAt(x, y).get() }
					?.map { (x, y) -> lowResConnectedComponents.getAt(x, y).get() }
					?.toMutableSet() ?: mutableSetOf()


				predictionPoints?.firstOrNull { it.label == SamPredictor.SparseLabel.TOP_LEFT_BOX }?.let { topLeft ->
					predictionPoints.firstOrNull { it.label == SamPredictor.SparseLabel.BOTTOM_RIGHT_BOX }?.let { bottomRight ->
						val originalImgToPredictionScale = 1 / predictionToOriginalImageScaleWithoutPadding
						val minPos = topLeft.centerScaledCoordinates(originalImgToPredictionScale).let {
							longArrayOf(it.first.toLong(), it.second.toLong())
						}
						val maxPos = bottomRight.centerScaledCoordinates(originalImgToPredictionScale).let {
							longArrayOf(it.first.toLong(), it.second.toLong())
						}
						val intervalIter = IntervalIterator(FinalInterval(minPos, maxPos))
						val posInBox = LongArray(2)
						while (intervalIter.hasNext()) {
							intervalIter.fwd()
							intervalIter.localize(posInBox)
							if (lowResFilter.getAt(*posInBox).get()) {
								acceptedComponents += lowResConnectedComponents.getAt(*posInBox).get()
							}
						}
					}
				}

				val lowResSelectedComponents = Converters.convertRAI(
					lowResConnectedComponents,
					{ source, output ->
						output.set(if (source.get() in acceptedComponents) 1.0f else 0.0f)
					},
					FloatType()
				)

				val predictionToViewerScale = Scale2D(setViewer!!.width / lowResWidth, setViewer!!.height / lowResHeight)
				val halfPixelOffset = Translation2D(.5, .5)
				val translationToViewer = Translation2D(*paintMask.displayPointToInitialMaskPoint(0, 0).positionAsDoubleArray())
				val predictionToViewerTransform = AffineTransform2D().concatenate(translationToViewer).concatenate(predictionToViewerScale).concatenate(halfPixelOffset)
				val maskAlignedSelectedComponents = lowResSelectedComponents
					.extendValue(0.0)
					.interpolate(NLinearInterpolatorFactory())
					.affineReal(predictionToViewerTransform)
					.convert(UnsignedLongType(Label.INVALID)) { source, output -> output.set(if (source.get() in .9..1.1) currentLabelToPaint else Label.INVALID) }
					.addDimension()
					.raster()
					.interval(paintMask.viewerImg)


				val compositeMask = Converters.convertRAI(
					originalBackingImage, maskAlignedSelectedComponents,
					{ original, overlay, composite ->
						val overlayVal = overlay.get()
						composite.set(
							if (overlayVal == currentLabelToPaint) currentLabelToPaint else original.get()
						)
					},
					UnsignedLongType(Label.INVALID)
				)

				val compositeVolatileMask = Converters.convertRAI(
					originalVolatileBackingImage, maskAlignedSelectedComponents,
					{ original, overlay, composite ->
						var checkOriginal = false
						val overlayVal = overlay.get()
						if (overlayVal == currentLabelToPaint) {
							composite.get().set(currentLabelToPaint)
							composite.isValid = true
						} else checkOriginal = true
						if (checkOriginal) {
							if (original.isValid) {
								composite.set(original)
								composite.isValid = true
							} else composite.isValid = false
							composite.isValid = true
						}
					},
					VolatileUnsignedLongType(Label.INVALID)
				)

				paintMask.updateBackingImages(
					compositeMask to compositeVolatileMask,
					writableSourceImages = originalBackingImage to originalVolatileBackingImage
				)

				val predictionInterval3D = Intervals.createMinMax(*minPointInLowResMask, -1, *maxPointInLowResMask, 1)
				val predictionIntervalInViewerSpace = predictionToViewerTransform.estimateBounds(predictionInterval3D).smallestContainingInterval

				paintMask.requestRepaint(predictionIntervalInViewerSpace union previousPredictionInterval)
				lastPredictionProperty.set(SamTaskInfo(maskSource, predictionIntervalInViewerSpace))
			}
		}
		predictionTask = task
		task.submit(SAM_TASK_SERVICE)
	}

	private fun setBestEstimatedThreshold(lowResIntervalWithoutPadding: FinalInterval?) {
		val binMapper = Real1dBinMapper<FloatType>(-40.0, 30.0, 256, false)
		val histogram = LongArray(binMapper.binCount.toInt())

		val histogramInterval = points.filter { it.label > SamPredictor.SparseLabel.IN }.let {
			if (it.size == 2) {
				val (x1, y1) = it[0].run { (x / predictionToOriginalImageScaleWithoutPadding).toLong() to (y / predictionToOriginalImageScaleWithoutPadding).toLong() }
				val (x2, y2) = it[1].run { (x / predictionToOriginalImageScaleWithoutPadding).toLong() to (y / predictionToOriginalImageScaleWithoutPadding).toLong() }
				FinalInterval(longArrayOf(x1, y1), longArrayOf(x2, y2))
			} else null
		} ?: lowResIntervalWithoutPadding
		LoopBuilder.setImages(Views.interval(currentPrediction!!.image, histogramInterval))
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
	}

	private fun runPredictionWithRetry(predictor: SamPredictor, vararg predictionRequest: SamPredictor.PredictionRequest): SamPredictor.SamPrediction {
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
			predictor.predict(*predictionRequest)
		} catch (e: OrtException) {
			LOG.trace(e.message)
			runPredictionWithRetry(predictor, *predictionRequest)
		}
	}

	/**
	 * Calculates the target screen scale factor based on the highest screen scale and the viewer's dimensions.
	 * The resulting scale factor will always be the smaller of either:
	 *  1. the highest explicitly specified factor, or
	 *  2. [SamPredictor.MAX_DIM_TARGET] / `max(width, height)`
	 *
	 *  This means if the `scaleFactor * maxEdge` is less than [SamPredictor.MAX_DIM_TARGET] it will be used,
	 *  but if the `scaleFactor * maxEdge` is still larger than [SamPredictor.MAX_DIM_TARGET], then a more
	 *  aggressive scale factor will be returned. See [SamPredictor.MAX_DIM_TARGET] for more information.
	 *
	 * @return The calculated scale factor.
	 */
	private fun calculateTargetScreenScaleFactor(): Double {
		val highestScreenScale = setViewer!!.renderUnit.screenScalesProperty.get().max()
		val (width, height) = setViewer!!.width to setViewer!!.height
		val maxEdge = max(ceil(width * highestScreenScale), ceil(height * highestScreenScale))
		return min(highestScreenScale, SamPredictor.MAX_DIM_TARGET / maxEdge)
	}

	private fun saveActiveViewerImageFromRenderer() {
		setViewer?.let { viewer ->
			val width = viewer.width
			val height = viewer.height

			val threadGroup = ThreadGroup(this.toString())
			val sharedQueue = SharedQueue(PainteraBaseView.reasonableNumFetcherThreads(), 50)

			val renderUnit = object : RenderUnit(
				threadGroup,
				viewer::getState,
				{ Interpolation.NLINEAR },
				CompositeProjectorPreMultiply.CompositeProjectorFactory(paintera.baseView.sourceInfo().composites()),
				sharedQueue,
				30 * 1000000L,
				TaskExecutors.singleThreaded() //TODO rendering name it (and more threads? )
			) {

				override fun paint() {
					val viewerTransform = AffineTransform3D()
					var timepoint = 0
					val sacs = mutableListOf<SourceAndConverter<*>>()
					synchronized(this) {
						if (renderer != null && renderTarget != null && viewerStateSupplier.get().isVisible) {
							val viewerState = viewerStateSupplier.get()
							synchronized(viewerState) {
								viewerState.getViewerTransform(viewerTransform)
								timepoint = viewerState.timepoint
								val activeSourceToSkip = activeState?.sourceAndConverter?.spimSource
								viewerState.sources.forEach {
									if (it.spimSource != activeSourceToSkip) {
										sacs += it
									}
								}
							}

						}

					}

					val renderedScreenScaleIndex = renderer.paint(sacs, timepoint, viewerTransform, interpolation, null)
					if (renderedScreenScaleIndex != -1) {
						val screenInterval = renderer.lastRenderedScreenInterval
						val renderTargetRealInterval = renderer.lastRenderTargetRealInterval

						val image = renderTarget.bufferedImage
						renderResultProperty.set(RenderResult(image, screenInterval, renderTargetRealInterval, renderedScreenScaleIndex))
					}
				}
			}
			renderUnit.setScreenScales(doubleArrayOf(screenScale))
			renderUnit.setDimensions(width.toLong(), height.toLong())
			renderUnit.renderedImageProperty.addListener { _, _, result ->
				result.image?.let { img ->
					ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", predictionImagePngOutputStream)
					predictionImagePngOutputStream.close()
				}
			}
			renderUnit.requestRepaint()
		}
	}

	companion object {

		const val SAM_POINT_STYLE = "sam-point"
		const val SAM_BOX_OVERLAY_STYLE = "sam-box-overlay"

		private enum class SamPointStyle(val styles: Array<String>) {
			Include(arrayOf(SAM_POINT_STYLE, "sam-include")),
			Exclude(arrayOf(SAM_POINT_STYLE, "sam-exclude")),
			Box(arrayOf(SAM_POINT_STYLE, "sam-box"))
		}

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val SAM_TASK_SERVICE = Executors.newFixedThreadPool(
			Runtime.getRuntime().availableProcessors(),
			ThreadFactoryBuilder()
				.setNameFormat("sam-task-%d")
				.setDaemon(true)
				.build()
		)

		private lateinit var ortEnv: OrtEnvironment
		private val createOrtSessionTask by LazyForeignValue({ properties.segmentAnythingConfig.modelLocation }) { modelLocation ->
			Tasks.createTask {
				if (!::ortEnv.isInitialized)
					ortEnv = OrtEnvironment.getEnvironment()
				val modelArray = try {
					Companion::class.java.classLoader.getResourceAsStream(modelLocation)!!.readAllBytes()
				} catch (e: Exception) {
					Files.readAllBytes(Paths.get(modelLocation))
				}
				val session = ortEnv.createSession(modelArray)
				session
			}.submit()
		}.beforeValueChange {
			it?.cancel()
		}

		data class SamTaskInfo(val maskedSource: MaskedSource<*, *>, val maskInterval: Interval)

	}
}