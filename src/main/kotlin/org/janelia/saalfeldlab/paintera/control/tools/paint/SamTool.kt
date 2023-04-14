package org.janelia.saalfeldlab.paintera.control.tools.paint

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
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.input.MouseEvent.MOUSE_MOVED
import net.imglib2.Interval
import net.imglib2.Point
import net.imglib2.loops.LoopBuilder
import org.apposed.appose.Appose
import org.apposed.appose.Service
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.verifyPainteraNotDisabled
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
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
import java.awt.image.DataBufferByte
import java.io.*
import javax.imageio.ImageIO

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

    init {
        setCursorWhenDoneApplying = ChangeListener { observable, _, isApplying ->
            observable.removeListener(setCursorWhenDoneApplying)
        }
    }

    override fun activate() {
        super.activate()
        setCurrentLabelToSelection()
        statePaintContext?.selectedIds?.apply { addListener(selectedIdListener) }
        saveActiveViewerImage()
        statusProperty.set("Preparing SAM")
        Tasks.createTask {
            setImage()
            Platform.runLater { paintera.currentSource!!.isVisibleProperty.set(true) }
            activeViewer?.let { viewer ->
                if (viewer.isMouseInside) {
                    Platform.runLater { statusProperty.set("Predicting...") }
                    val x = viewer.mouseXProperty.get().toLong()
                    val y = viewer.mouseYProperty.get().toLong()
                    predict(Point(x, y))
                }
            }
        }.onSuccess { _, _ ->
            Platform.runLater { statusProperty.set("Ready") }
        }.onCancelled { _, _ ->
            Platform.runLater { statusProperty.set("Cancelled") }
            deactivate()
        }.submit()
    }

    override fun deactivate() {
        currentLabelToPaint = Label.INVALID
        super.deactivate()
    }

    private fun setCurrentLabelToSelection() {
        currentLabelToPaint = statePaintContext?.paintSelection?.invoke() ?: Label.INVALID
    }

    private fun getSamActions() = arrayOf(painteraActionSet("sam selections", PaintActionType.Paint, ignoreDisable = true) {
        /* Handle Painting */
        MOUSE_CLICKED(MouseButton.PRIMARY) {
            name = "start selection paint"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            verify { isLabelValid && imageReady }
            onAction {
                isPainting = true
                predict(it!!.position.toPoint(), true)
            }
        }

        MOUSE_MOVED {
            name = "prediction overlay"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            verify { isLabelValid && imageReady }
            onAction {
                predict(it!!.position.toPoint())
            }
        }

        /* Handle Erasing */
        MOUSE_CLICKED(MouseButton.SECONDARY) {
            name = "start transparent erase"
            verifyEventNotNull()
            verifyPainteraNotDisabled()
            verify { KeyCode.SHIFT !in keyTracker!!.getActiveKeyCodes(true) }
            onAction {
                isPainting = true
            }
        }
    })

    companion object {
        private const val PAINTER_PREDICTION_INPUT = "/tmp/paintera-prediction-input.png"
        private const val PREDICTION_OUTPUT_MASK_IMAGE = "/tmp/paintera-prediction-output.png"

        private val samPythonEnvironment = Appose
            .base(File("/home/hulbertc@hhmi.org/anaconda3/envs/paintera-sam"))
            .build()
        private val samPythonService = samPythonEnvironment.python()
        private val loadModelTask = samPythonService.let {
            val task = it.task(
                """
                    from paintera_sam.paintera_predictor import PainteraPredictor
                    predictor = PainteraPredictor()
                """.trimIndent()
            )
            task.listen { taskEvent ->
                if (taskEvent.responseType == Service.ResponseType.FAILURE) {
                    System.err.println(taskEvent.task.error)
                }
            }
            task.start()
        }

        private data class SamTaskInfo(val maskedSource: MaskedSource<*, *>, val maskInterval: Interval)
    }

    private var imageReady = false;

    private fun setImage() {
        loadModelTask.waitFor()
        val setImageTask = samPythonService.task(
            """
                from paintera_sam.paintera_predictor import PainteraPredictor
                predictor = PainteraPredictor()
                predictor.set_image(paintera_image)
            """.trimIndent(),
            mapOf("paintera_image" to PAINTER_PREDICTION_INPUT)
        )
        setImageTask.listen {
            imageReady = when (it.responseType) {
                Service.ResponseType.LAUNCH -> false
                Service.ResponseType.COMPLETION -> true
                Service.ResponseType.FAILURE -> {
                    it.task.error?.also { error -> System.err.println(error) }
                    false
                }

                else -> imageReady
            }
        }
        setImageTask.waitFor()
    }

    private val maskedSource: MaskedSource<*, *>?
        get() = activeState?.dataSource as? MaskedSource<*, *>

    private var currentViewerMask: ViewerMask? = null
    private var viewerMask: ViewerMask? = null
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

    private fun predict(xy: Point, applyMask: Boolean = false) {
        synchronized(this) {
            predictTask?.let { return }
            val maskSource = maskedSource ?: let { return }
            predictTask = Tasks.createTask {
                val x = xy.getIntPosition(0)
                val y = xy.getIntPosition(1)

                val pythonPredictTask = samPythonService.task(
                    """
                    from paintera_sam.paintera_predictor import PainteraPredictor
                    predictor = PainteraPredictor()
                    predictor.predict(x, y)
                    predictor.save_mask(out)
                """.trimIndent(),
                    mapOf(
                        "x" to x,
                        "y" to y,
                        "out" to PREDICTION_OUTPUT_MASK_IMAGE
                    )
                )
                pythonPredictTask.listen { taskEvent ->
                    taskEvent?.task?.error?.also { error -> System.err.println(error) }
                }
                pythonPredictTask.waitFor()
                if (pythonPredictTask.status != Service.TaskStatus.COMPLETE) {
                    it.cancel()
                    return@createTask null
                }

                val predictionMask = ImageIO.read(File(PREDICTION_OUTPUT_MASK_IMAGE))
                val paintMask = viewerMask!!

                val predicationMaskInterval = paintMask.getScreenInterval(predictionMask.width.toLong(), predictionMask.height.toLong())
                val paintMaskOverPredictionInterval = paintMask.viewerImg.interval(predicationMaskInterval)

                val paintMaskCursor = paintMaskOverPredictionInterval.cursor()
                for (prediction in (predictionMask.raster.dataBuffer as DataBufferByte).data) {
                    if (paintMaskCursor.hasNext()) {
                        paintMaskCursor.next()
                        if (prediction.toInt() != 0) {
                            paintMaskCursor.get().set(currentLabelToPaint)
                        } else if (!applyMask) {
                            paintMaskCursor.get().set(Label.INVALID)
                        }
                    }
                }
                return@createTask SamTaskInfo(maskSource, predicationMaskInterval)
            }
        }
        if (applyMask) {
            predictTask!!.let {
                it.onSuccess { _, task ->
                    val (maskedSource, maskInterval) = task.get()!!
                    /* Reset the current mask */
                    viewerMask = null
                    val viewerMask = maskedSource.currentMask as ViewerMask
                    val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(maskInterval.asRealInterval, viewerMask.initialMaskToSourceWithDepthTransform, .5)
                    maskedSource.applyMask(viewerMask, sourceInterval.smallestContainingInterval, MaskedSource.VALID_LABEL_CHECK)
                    paintera.currentSource!!.isVisibleProperty.set(true)
                    paintera.baseView.disabledPropertyBindings -= this
                }
            }
            paintera.baseView.disabledPropertyBindings[this] = SimpleBooleanProperty(true)
        }
        predictTask!!.onEnd {
            currentViewerMask?.viewer?.requestRepaint()
            predictTask = null
        }
        predictTask!!.submit()
    }

    private fun saveActiveViewerImage() {
        val snapshotParameters = SnapshotParameters()
        val image = activeViewer!!.snapshot(snapshotParameters, null)
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", File(PAINTER_PREDICTION_INPUT))
    }
}
