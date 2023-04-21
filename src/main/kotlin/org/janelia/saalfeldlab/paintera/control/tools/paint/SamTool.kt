package org.janelia.saalfeldlab.paintera.control.tools.paint

import com.google.common.util.concurrent.ThreadFactoryBuilder
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.application.Platform
import javafx.beans.Observable
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.embed.swing.SwingFXUtils
import javafx.scene.SnapshotParameters
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.input.MouseEvent.MOUSE_MOVED
import javafx.scene.input.ScrollEvent
import javafx.scene.shape.Circle
import net.imglib2.Interval
import net.imglib2.Point
import org.apposed.appose.Appose
import org.apposed.appose.Service
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.verifyPainteraNotDisabled
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.position
import org.janelia.saalfeldlab.labels.Label
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
import java.awt.image.DataBufferByte
import java.io.File
import java.lang.invoke.MethodHandles
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import kotlin.math.sign

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

    private lateinit var setImageTask: Service.Task
    private var maskPredictionTask: Service.Task? = null

    internal val maskedSource: MaskedSource<*, *>?
        get() = activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>

    private var currentViewerMask: ViewerMask? = null

    protected open var externallyManagedMask = false
    internal var viewerMask: ViewerMask? = null
        get() {
            if (field == null) {
                field = maskedSource!!.setNewViewerMask(
                    MaskInfo(0, activeViewer!!.state.bestMipMapLevel),
                    activeViewer!!
                )
            }
            currentViewerMask = field
            return field!!
        }
        set(value) {
            field = value
            currentViewerMask = field
        }

    private var predictTask: UtilityTask<SamTaskInfo?>? = null
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

    override fun activate() {
        super.activate()
        threshold = 5.0
        setCurrentLabelToSelection()
        statePaintContext?.selectedIds?.apply { addListener(selectedIdListener) }
        saveActiveViewerImage()
        statusProperty.set("Preparing SAM")
        paintera.baseView.disabledPropertyBindings[this] = isBusyProperty
        Tasks.createTask {
            setImage()
            Platform.runLater { paintera.currentSource!!.isVisibleProperty.set(true) }
            activeViewer?.let { viewer ->
                if (viewer.isMouseInside) {
                    Platform.runLater { statusProperty.set("Predicting...") }
                    val x = viewer.mouseXProperty.get().toLong()
                    val y = viewer.mouseYProperty.get().toLong()
                    includePoints.clear()
                    excludePoints.clear()
                    includePoints += Point(x, y)
                    Platform.runLater { viewer.children.removeIf { SamPointStyle.POINT in it.styleClass } }
                    predict(includePoints, excludePoints)
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
        predictTask?.cancel()
        predictTask = null
        maskPredictionTask = null
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
                predictTask?.cancel()
                val prediction = lastPrediction ?: run {
                    includePoints.clear()
                    excludePoints.clear()
                    includePoints += it!!.position.toPoint()
                    activeViewer?.let { viewer -> Platform.runLater { viewer.children.removeIf { SamPointStyle.POINT in it.styleClass } } }
                    predict(includePoints, excludePoints)
                    predictTask?.get()
                }
                prediction?.let { samPrediction ->
                    val (maskedSource, maskInterval) = samPrediction
                    val currrentMask = maskedSource.currentMask as ViewerMask
                    val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(maskInterval.asRealInterval, currrentMask.initialMaskToSourceWithDepthTransform, .5)
                    maskedSource.applyMask(currrentMask, sourceInterval.smallestContainingInterval, MaskedSource.VALID_LABEL_CHECK)
                    paintera.currentSource!!.isVisibleProperty.set(true)
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
                    paintera.currentSource!!.isVisibleProperty.set(true)
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
                predict(includePoints, excludePoints)
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
                activeViewer?.let { viewer -> Platform.runLater { viewer.children.removeIf { child -> SamPointStyle.POINT in child.styleClass } } }
                includePoints += it!!.position.toPoint()
                predict(includePoints, excludePoints)
            }
        }

        /* Handle Erasing */
        MOUSE_CLICKED(MouseButton.PRIMARY, withKeysDown = arrayOf(KeyCode.CONTROL)) {
            name = "include point"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            onAction {
                includePoints += it!!.position.toPoint()
                activeViewer?.let { viewer ->
                    Platform.runLater {
                        viewer.children += Circle( 5.0).apply {
                            translateX = it!!.x - viewer.width / 2
                            translateY = it.y - viewer.height / 2
                            styleClass += SamPointStyle.POINT
                            styleClass += SamPointStyle.INCLUDE
                        }
                    }
                }
                predict(includePoints, excludePoints)
            }
        }

        MOUSE_CLICKED(MouseButton.SECONDARY, withKeysDown = arrayOf(KeyCode.CONTROL)) {
            name = "exclude point"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            onAction {
                excludePoints += it!!.position.toPoint()
                activeViewer?.let { viewer ->
                    Platform.runLater {
                        viewer.children += Circle( 5.0).apply {
                            translateX = it!!.x - viewer.width / 2
                            translateY = it.y - viewer.height / 2
                            styleClass += SamPointStyle.POINT
                            styleClass += SamPointStyle.EXCLUDE
                        }
                    }
                }
                predict(includePoints, excludePoints)
            }
        }
    })

    private fun setImage() {
        val setImage = samPythonService.task(
            """
                from paintera_sam.onnx_paintera_predictor import set_image
                set_image(paintera_image)
            """.trimIndent(),
            mapOf("paintera_image" to PAINTER_PREDICTION_INPUT)
        ).also {
            setImageTask = it
            isBusy = true
            it.listen { taskEvent ->
                if (taskEvent.responseType == Service.ResponseType.FAILURE) {
                    taskEvent.task.error?.also { error -> System.err.println(error) }
                }
                if (taskEvent.responseType >= Service.ResponseType.COMPLETION) {
                    isBusy = false
                }
            }
        }
        loadModelTask.waitFor()
        setImage.start()
    }

    private fun predict(pointsIn: List<Point>, pointsOut: List<Point>) {
        synchronized(this) {
            val maskSource = maskedSource ?: let { return@synchronized }
            val prevTask = predictTask
            val predictionOutputMaskImage = PREDICTION_OUTPUT_MASK_IMAGE
            predictTask = Tasks.createTask {
                if (setImageTask.status <= Service.TaskStatus.RUNNING) {
                    isBusy = true
                }
                loadModelTask.waitFor()
                setImageTask.waitFor()
                prevTask?.cancel()
                maskPredictionTask?.waitFor()

                samPythonService.task(
                    """
                    from paintera_sam.onnx_paintera_predictor import predict, save_mask
                    predict(points_in, points_out, threshold=threshold)
                    save_mask(out)
                """.trimIndent(),
                    mapOf(
                        "points_in" to pointsIn.map { point -> listOf(point.getIntPosition(0), point.getIntPosition(1)) }.toList(),
                        "points_out" to pointsOut.map { point -> listOf(point.getIntPosition(0), point.getIntPosition(1)) }.toList(),
                        "threshold" to threshold,
                        "out" to predictionOutputMaskImage
                    )
                ).also { newPredictionTask ->
                    newPredictionTask.listen { taskEvent ->
                        taskEvent?.task?.error?.also { error ->
                            it.cancel()
                            LOG.error(error)
                        }
                    }
                    if (it.isCancelled) {
                        return@createTask null
                    }
                    maskPredictionTask = newPredictionTask
                    try {
                        newPredictionTask.waitFor()
                    } catch (_: InterruptedException) {
                    }
                    maskPredictionTask = null
                }
                if (it.isCancelled) {
                    return@createTask null
                }

                val predictionMask = ImageIO.read(File(predictionOutputMaskImage))
                if (it.isCancelled) {
                    return@createTask null
                }

                val paintMask = viewerMask ?: let { _ ->
                    it.cancel()
                    return@createTask null
                }

                val predicationMaskInterval = paintMask.getScreenInterval(predictionMask.width.toLong(), predictionMask.height.toLong())
                val paintMaskOverPredictionInterval = paintMask.viewerImg.interval(predicationMaskInterval)
                val paintMaskCursor = paintMaskOverPredictionInterval.cursor()

                (predictionMask.raster.dataBuffer as DataBufferByte).data.forEachIndexed { index, prediction ->
                    if (it.isCancelled) {
                        /* write invalid over anything we set prior to cancel */
                        val clearCursor = paintMaskOverPredictionInterval.cursor()
                        for (clearIdx in 0 until index) {
                            if (clearCursor.hasNext()) {
                                clearCursor.next()
                                clearCursor.get().set(Label.INVALID)
                            } else {
                                return@createTask SamTaskInfo(maskSource, predicationMaskInterval)
                            }
                        }
                        return@createTask SamTaskInfo(maskSource, predicationMaskInterval)
                    }
                    if (paintMaskCursor.hasNext()) {
                        paintMaskCursor.next()
                        if (prediction.toInt() != 0) {
                            paintMaskCursor.get().set(currentLabelToPaint)
                        } else {
                            paintMaskCursor.get().set(Label.INVALID)
                        }
                    }
                }
                SamTaskInfo(maskSource, predicationMaskInterval)
            }.onSuccess { _, task ->
                lastPrediction = task.get()!!
            }.onEnd {
                maskPredictionTask?.waitFor()
                maskPredictionTask = null
                currentViewerMask?.viewer?.requestRepaint()
                predictTask = null
                Files.deleteIfExists(Path.of(predictionOutputMaskImage))
            }.submit(SAM_TASK_SERVICE)
        }
    }

    private fun saveActiveViewerImage() {
        val snapshotParameters = SnapshotParameters()
        val image = activeViewer!!.snapshot(snapshotParameters, null)
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", File(PAINTER_PREDICTION_INPUT))
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

        private const val PAINTER_PREDICTION_INPUT = "/tmp/paintera-prediction-input.png"
        private val PREDICTION_OUTPUT_MASK_IMAGE
            get() = Files.createTempFile("paintera-prediction-output", ".png").toString()

        private val samPythonEnvironment = Appose
            .base(File("/home/hulbertc@hhmi.org/anaconda3/envs/paintera-sam"))
            .build()
        private val samPythonService = samPythonEnvironment.python()
        private val loadModelTask = samPythonService.let {
            val task = it.task(
                """
                    import paintera_sam.onnx_paintera_predictor
                """.trimIndent()
            )
            task.listen { taskEvent ->
                if (taskEvent.responseType == Service.ResponseType.FAILURE) {
                    System.err.println(taskEvent.task.error)
                }
            }
            task.start()
        }

        data class SamTaskInfo(val maskedSource: MaskedSource<*, *>, val maskInterval: Interval)
    }
}
