package org.janelia.saalfeldlab.paintera.control.tools.paint

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import bdv.fx.viewer.ViewerPanelFX
import bdv.fx.viewer.render.RenderUnit
import bdv.util.volatiles.SharedQueue
import bdv.viewer.Interpolation
import bdv.viewer.SourceAndConverter
import bdv.viewer.render.AccumulateProjectorARGB
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
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.input.MouseEvent.MOUSE_MOVED
import javafx.scene.input.ScrollEvent
import javafx.scene.shape.Circle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import net.imglib2.Interval
import net.imglib2.Point
import net.imglib2.RealPoint
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.realtransform.Scale
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.verifyPainteraNotDisabled
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.position
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.setNewViewerMask
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.toPoint
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.invoke.MethodHandles
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sign

private const val H_ONNX_MODEL = "/home/hulbertc@hhmi.org/git/saalfeld/paintera_sam/sam_vit_h_4b8939.onnx"

open class SamTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) : PaintTool(activeSourceStateProperty, mode) {

    override val graphic = { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "sam-select") } }
    override val name = "SAM"
    override val keyTrigger = listOf(KeyCode.A)


    private val currentLabelToPaintProperty = SimpleObjectProperty(Label.INVALID)
    internal var currentLabelToPaint: Long by currentLabelToPaintProperty.nonnull()
    private val isLabelValid get() = currentLabelToPaint != Label.INVALID

    override val actionSets by LazyForeignValue({ activeViewerAndTransforms }) {
        mutableListOf(
            *super.actionSets.toTypedArray(),
            *getSamActions(),
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

    private var setViewer: ViewerPanelFX? = null

    protected open var externallyManagedMask = false
    internal var viewerMask: ViewerMask? = null
        get() {
            if (field == null) {
                field = maskedSource!!.setNewViewerMask(
                    MaskInfo(0, setViewer!!.state.bestMipMapLevel),
                    setViewer!!
                )
            }
            currentViewerMask = field
            return field!!
        }
        set(value) {
            field = value
            currentViewerMask = field
        }

    private var predictionTask: UtilityTask<Unit>? = null

    val lastPredictionProperty = SimpleObjectProperty<SamTaskInfo?>(null)
    var lastPrediction by lastPredictionProperty.nullable()
        private set
    private val includePoints = mutableListOf<Point>()

    private val excludePoints = mutableListOf<Point>()

    private var threshold = 5.0
        set(value) {
            field = value.coerceAtLeast(0.0)
        }

    init {
        setCursorWhenDoneApplying = ChangeListener { observable, _, isApplying ->
            observable.removeListener(setCursorWhenDoneApplying)
        }
    }

    private val isBusyProperty = SimpleBooleanProperty(false)

    private var isBusy by isBusyProperty.nonnull()

    private val screenScale
        get() = setViewer?.renderUnit?.screenScalesProperty?.get()?.get(0)

    override fun activate() {
        super.activate()
        threshold = 5.0
        setCurrentLabelToSelection()
        statePaintContext?.selectedIds?.apply { addListener(selectedIdListener) }
        setViewer = activeViewer
        statusProperty.set("Preparing SAM")
        paintera.baseView.disabledPropertyBindings[this] = isBusyProperty
        Tasks.createTask {
            saveActiveViewerImageFromRenderer()
            getImageEmbeddingTask()
            setViewer?.let { viewer ->
                if (viewer.isMouseInside) {
                    Platform.runLater { statusProperty.set("Predicting...") }
                    val x = viewer.mouseXProperty.get().toLong()
                    val y = viewer.mouseYProperty.get().toLong()
                    includePoints.clear()
                    excludePoints.clear()
                    includePoints += Point(x, y)
                    Platform.runLater { viewer.children.removeIf { SamPointStyle.POINT in it.styleClass } }
                    requestPrediction(includePoints, excludePoints)
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
        currentLabelToPaint = Label.INVALID
        predictionTask?.cancel()
        predictionTask = null
        if (!externallyManagedMask) {
            maskedSource?.resetMasks()
        }
        currentViewerMask?.viewer?.requestRepaint()
        currentViewerMask?.viewer?.children?.removeIf { SamPointStyle.POINT in it.styleClass }
        viewerMask = null
        paintera.baseView.disabledPropertyBindings -= this
        super.deactivate()
    }

    protected open fun setCurrentLabelToSelection() {
        currentLabelToPaint = statePaintContext?.paintSelection?.invoke() ?: Label.INVALID
    }

    private fun getSamActions() = arrayOf(painteraActionSet("sam selections", PaintActionType.Paint, ignoreDisable = true) {
        /* Handle Painting */
        MOUSE_CLICKED(MouseButton.PRIMARY) {
            name = "start selection paint"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            verify("Control cannot be down") { it?.isControlDown == false } /* If control is down, we are in point selection mode */
            verify(" label is not valid ") { isLabelValid }
            verify("Cannot apply prediction if mask is managed externally") { !externallyManagedMask }
            onAction {
                lastPrediction?.let { samPrediction ->
                    val (maskedSource, maskInterval) = samPrediction
                    val currrentMask = maskedSource.currentMask as ViewerMask
                    val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(maskInterval.asRealInterval, currrentMask.initialMaskToSourceWithDepthTransform, .5)
                    maskedSource.applyMask(currrentMask, sourceInterval.smallestContainingInterval, MaskedSource.VALID_LABEL_CHECK)
                    viewerMask = null
                }
            }
        }
        KEY_PRESSED(KeyCode.ENTER) {
            verify("Cannot apply prediction if mask is managed externally") { !externallyManagedMask }
            onAction {
                lastPrediction?.let { samPrediction ->
                    val (maskedSource, maskInterval) = samPrediction
                    val currrentMask = maskedSource.currentMask as ViewerMask
                    val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(maskInterval.asRealInterval, currrentMask.initialMaskToSourceWithDepthTransform, .5)
                    maskedSource.applyMask(currrentMask, sourceInterval.smallestContainingInterval, MaskedSource.VALID_LABEL_CHECK)
                    viewerMask = null
                }
            }
        }

        ScrollEvent.SCROLL(KeyCode.CONTROL) {
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            onAction {
                val delta = arrayOf(it!!.deltaX, it.deltaY).maxBy { it.absoluteValue }
                threshold += (delta.sign * .1)
                requestPrediction(includePoints, excludePoints)
            }
        }

        MOUSE_MOVED {
            name = "prediction overlay"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            verify("Control cannot be down") { it?.isControlDown == false } /* If control is down, we are in point selection mode */
            verify("Label is not valid") { isLabelValid }
            onAction {
                includePoints.clear()
                excludePoints.clear()
                setViewer?.let { viewer -> Platform.runLater { viewer.children.removeIf { child -> SamPointStyle.POINT in child.styleClass } } }
                includePoints += it!!.position.toPoint()
                requestPrediction(includePoints, excludePoints)
            }
        }

        /* Handle Erasing */
        MOUSE_CLICKED(MouseButton.PRIMARY, withKeysDown = arrayOf(KeyCode.CONTROL)) {
            name = "include point"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            onAction {
                includePoints += it!!.position.toPoint()
                setViewer?.let { viewer ->
                    Platform.runLater {
                        viewer.children += Circle(5.0).apply {
                            translateX = it!!.x - viewer.width / 2
                            translateY = it.y - viewer.height / 2
                            styleClass += SamPointStyle.POINT
                            styleClass += SamPointStyle.INCLUDE
                        }
                    }
                }
                requestPrediction(includePoints, excludePoints)
            }
        }

        MOUSE_CLICKED(MouseButton.SECONDARY, withKeysDown = arrayOf(KeyCode.CONTROL)) {
            name = "exclude point"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            onAction {
                excludePoints += it!!.position.toPoint()
                setViewer?.let { viewer ->
                    Platform.runLater {
                        viewer.children += Circle(5.0).apply {
                            translateX = it!!.x - viewer.width / 2
                            translateY = it.y - viewer.height / 2
                            styleClass += SamPointStyle.POINT
                            styleClass += SamPointStyle.EXCLUDE
                        }
                    }
                }
                requestPrediction(includePoints, excludePoints)
            }
        }
    })

    private lateinit var getImageEmbeddingTask: UtilityTask<OnnxTensor>

    private val predictionQueue = LinkedBlockingQueue<PredictionRequest>(1)

    private data class PredictionRequest(val includePoints: List<Point>, val excludePoints: List<Point>)

    private fun requestPrediction(includePoints: List<Point>, excludePoints: List<Point>) {
        if (predictionTask == null || predictionTask?.isCancelled == true) {
            startPredictionTask()
        }
        val include = MutableList(includePoints.size) { includePoints[it] }
        val exclude = MutableList(excludePoints.size) { excludePoints[it] }
        synchronized(predictionQueue) {
            predictionQueue.clear()
            predictionQueue.put(PredictionRequest(include, exclude))
        }
    }

    private val os = PipedOutputStream()
    private val ins = PipedInputStream(os)

    private fun getImageEmbeddingTask() {
        Tasks.createTask {
            isBusy = true
            runBlocking { saveImageToFileLock.lock() }
            val entityBuilder = MultipartEntityBuilder.create()

            entityBuilder.addBinaryBody("image", File(PAINTERA_PREDICTION_INPUT))
            val client = HttpClients.createDefault()
            val post = HttpPost("http://10.40.4.189:5000/embedded_model")
            post.entity = entityBuilder.build()

            val response = client.execute(post)
            val entity = response.entity
            EntityUtils.toByteArray(entity).let {
                val decodedEmbedding = Base64.decode(it)
                val directBuffer = ByteBuffer.allocateDirect(decodedEmbedding.size)
                directBuffer.put(decodedEmbedding, 0, decodedEmbedding.size)
                directBuffer.position(0);
                val floatBuffEmbedding = directBuffer.asFloatBuffer()
                floatBuffEmbedding.position(0)
                OnnxTensor.createTensor(ortEnv, floatBuffEmbedding, longArrayOf(1, 256, 64, 64))
            }
        }.onEnd {
            isBusy = false
            saveImageToFileLock.unlock()
        }.also {
            getImageEmbeddingTask = it
            it.submit(SAM_TASK_SERVICE)
        }
    }

    private fun Point.scaledPoint(scale: Double): Point {
        return Point((getDoublePosition(0) * scale).toInt(), (getDoublePosition(1) * scale).toInt())
    }

    private fun startPredictionTask() {
        val maskSource = maskedSource ?: return
        val task = Tasks.createTask { task ->
            val session = createOrtSessionTask.get()
            val embedding = getImageEmbeddingTask.get()

            while (!task.isCancelled) {
                val (pointsIn, pointsOut) = predictionQueue.take()

                val coordsArray = FloatArray(2 * (pointsIn.size + pointsOut.size))
                val labels = FloatArray(coordsArray.size / 2)
                var idx = 0

                mapOf(pointsIn to 1f, pointsOut to 0f).forEach { (points, label) ->
                    points.forEach {
                        val convertedCoord = convertCoordinate(RealPoint(it.scaledPoint(screenScale ?: 1.0)))
                        labels[idx / 2] = label
                        coordsArray[idx++] = convertedCoord.getFloatPosition(0)
                        coordsArray[idx++] = convertedCoord.getFloatPosition(1)
                    }
                }

                val coordsBuffer = FloatBuffer.wrap(coordsArray)
                val onnxCoords = OnnxTensor.createTensor(ortEnv, coordsBuffer, longArrayOf(1, labels.size.toLong(), 2))

                val labelsBuffer = FloatBuffer.wrap(labels.map { it }.toFloatArray())
                val onnxLabels = OnnxTensor.createTensor(ortEnv, labelsBuffer, longArrayOf(1, labels.size.toLong()))

                /* NOTE: This is (height, width) */
                val onnxImgSize = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatArrayOf(imgHeight!!, imgWidth!!)), longArrayOf(2))

                val maskInput = OnnxTensor.createTensor(ortEnv, ByteBuffer.allocateDirect(1 * 1 * 256 * 256 * 4).asFloatBuffer(), longArrayOf(1, 1, 256, 256))
                val hasMaskInput = OnnxTensor.createTensor(ortEnv, ByteBuffer.allocateDirect(4).asFloatBuffer(), longArrayOf(1))
                session.run(
                    mapOf<String, OnnxTensorLike>(
                        "image_embeddings" to embedding,
                        "point_coords" to onnxCoords,
                        "point_labels" to onnxLabels,
                        "orig_im_size" to onnxImgSize,
                        "mask_input" to maskInput,
                        "has_mask_input" to hasMaskInput,
                    )
                ).use {

                    val mask = it.get("masks").get() as OnnxTensor

                    val maskImg = ArrayImgs.floats(mask.floatBuffer.array(), imgWidth!!.toLong(), imgHeight!!.toLong())
                    val realMask = Views.interpolate(Views.extendZero(maskImg), NearestNeighborInterpolatorFactory())
                    val invertScreenScale = 1.0 / (screenScale ?: 1.0)
                    val scale = Scale(invertScreenScale, invertScreenScale)
                    val scaledMask = RealViews.transform(realMask, scale)
                    val scaledSize = DoubleArray(2).also { size ->
                        size[0] = imgWidth!!.toDouble()
                        size[1] = imgHeight!!.toDouble()
                        scale.apply(size, size)
                    }
                    val scaleInterval = Intervals.createMinSize(0, 0, scaledSize[0].toLong(), scaledSize[1].toLong())
                    val predictionMask = Views.raster(scaledMask).interval(scaleInterval)

                    val paintMask = viewerMask!!
                    val predictionMaskInterval = paintMask.getScreenInterval(scaledSize[0].toLong(), scaledSize[1].toLong())
                    val paintMaskOverPredictionInterval = Views.hyperSlice(paintMask.viewerImg.interval(predictionMaskInterval), 2, 0)

                    LoopBuilder.setImages(predictionMask, paintMaskOverPredictionInterval).multiThreaded().forEachPixel { prediction, mask ->
                        mask.set(
                            if (prediction.get() >= threshold) {
                                currentLabelToPaint
                            } else {
                                Label.INVALID
                            }
                        )
                    }

                    currentViewerMask?.viewer?.requestRepaint()
                    lastPredictionProperty.set(SamTaskInfo(maskSource, predictionMaskInterval))
                }
            }
        }
        predictionTask = task
        task.submit(SAM_TASK_SERVICE)
    }

    private var imgWidth: Float? = null
    private var imgHeight: Float? = null

    private fun convertCoordinate(coord: RealPoint): RealPoint {
        val (height, width) = imgHeight!! to imgWidth!!
        val x = coord.getFloatPosition(0)
        val y = coord.getFloatPosition(1)
        val target = 1024
        val scale = target * (1.0 / max(height, width))
        val (scaledWidth, scaledHeight) = ((width * scale) + 0.5).toInt() to ((height * scale) + 0.5).toInt()
        val (scaledX, scaledY) = x * (scaledWidth / width) to y * (scaledHeight / height)

        coord.setPosition(floatArrayOf(scaledX, scaledY))

        return coord
    }

    private val saveImageToFileLock = Mutex()

    private fun saveActiveViewerImageFromRenderer() {
        setViewer?.let { viewer ->
            runBlocking { saveImageToFileLock.lock() }
            val width = viewer.width
            val height = viewer.height

            val threadGroup = ThreadGroup(this.toString())
            val sharedQueue = SharedQueue(PainteraBaseView.reasonableNumFetcherThreads(), 50)

            val renderUnit = object : RenderUnit(
                threadGroup,
                viewer::getState,
                { activeState?.interpolationProperty()?.value ?: Interpolation.NLINEAR },
                AccumulateProjectorARGB.factory,
                sharedQueue,
                30 * 1000000L,
                1,
                Executors.newSingleThreadExecutor()
            ) {

                override fun paint() {
                    val viewerTransform = AffineTransform3D()
                    var timepoint = 0
                    val sacs = mutableListOf<SourceAndConverter<*>>()
                    synchronized(this) {
                        if (renderer != null && renderTarget != null && viewerState.get().isVisible) {
                            val viewerState = viewerState.get()
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

                        val image = renderTarget.pendingImage
                        renderResultProperty.set(RenderResult(image, screenInterval, renderTargetRealInterval, renderedScreenScaleIndex))
                    }
                }
            }
            renderUnit.setScreenScales(doubleArrayOf(screenScale ?: 1.0))
            renderUnit.setDimensions(width.toLong(), height.toLong())
            renderUnit.renderedImageProperty.addListener { _, _, result ->
                result.image?.let { img ->
                    imgWidth = img.width.toFloat()
                    imgHeight = img.height.toFloat()



                    ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", File(PAINTERA_PREDICTION_INPUT))
                    saveImageToFileLock.unlock()
                }
            }
            renderUnit.requestRepaint()
        }
    }

    companion object {
        private object SamPointStyle {
            const val POINT = "sam-point"
            const val INCLUDE = "sam-include-point"
            const val EXCLUDE = "sam-exclude-point"
        }

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private val SAM_TASK_SERVICE = Executors.newCachedThreadPool(
            ThreadFactoryBuilder()
                .setNameFormat("sam-task-%d")
                .setDaemon(true)
                .build()
        )

        private const val PAINTERA_PREDICTION_INPUT = "/tmp/paintera-prediction-input.png"
        private lateinit var ortEnv: OrtEnvironment
        private val createOrtSessionTask = Tasks.createTask {
            ortEnv = OrtEnvironment.getEnvironment()
            val session = ortEnv.createSession(H_ONNX_MODEL)
            session
        }.submit()


        data class SamTaskInfo(val maskedSource: MaskedSource<*, *>, val maskInterval: Interval)
    }
}
