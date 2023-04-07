package org.janelia.saalfeldlab.paintera.control.tools.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.Observable
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.scene.SnapshotParameters
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import net.imglib2.Interval
import net.imglib2.Point
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.Tasks
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

    private val filterSpaceHeldDown = EventHandler<KeyEvent> {
        if (paintera.keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE)) {
            it.consume()
        }
    }

    override val actionSets by LazyForeignValue({ activeViewerAndTransforms }) {
        mutableListOf(
            *super.actionSets.toTypedArray(),
            *getSamActions(),
        )
    }

    override val statusProperty = SimpleStringProperty().apply {
        val labelNumToString: (Long) -> String = {
            when (it) {
                Label.BACKGROUND -> "BACKGROUND"
                Label.TRANSPARENT -> "TRANSPARENT"
                Label.INVALID -> "INVALID"
                Label.OUTSIDE -> "OUTSIDE"
                Label.MAX_ID -> "MAX_ID"
                else -> "$it"
            }
        }
        bind(currentLabelToPaintProperty.createNonNullValueBinding { "Painting Label: ${labelNumToString(it)}" })
    }

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
            verify { isLabelValid }
            onAction {
                isPainting = true
                saveActiveViewerImage()
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
        private val samDaemon = ProcessBuilder(
            "/home/caleb/anaconda3/envs/paintera-sam/bin/python",
            "/home/caleb/git/saalfeld/paintera-sam/paintera-sam/daemon.py",
        ).start()

        private val samIn = BufferedWriter(OutputStreamWriter(samDaemon.outputStream))
        private val samOut = BufferedReader(InputStreamReader(samDaemon.inputStream))

        private data class SamTaskInfo(val maskedSource: MaskedSource<*, *>, val maskInterval: Interval)
    }

    private  fun predict(xy: Point) {
        Tasks.createTask {
            val samReadyOut = samOut.readLine()
            if (!samReadyOut.equals("ready!")) {
                throw RuntimeException("SAM not Ready ($samReadyOut)")
            }

            val mask = ImageIO.read(File("/tmp/mask.png"))
            val x = xy.getIntPosition(0)
            val y = xy.getIntPosition(1)

            val runSam = "/tmp/sam.png $x $y\n"
            samIn.write(runSam)
            val samDoneOut = samOut.readLine()
            if (!samDoneOut.equals("done!")) {
                throw RuntimeException("SAM not Ready ($samDoneOut)")
            }

            val maskedSource = activeState?.dataSource as? MaskedSource<*, *>
            val viewerMask = maskedSource!!.setNewViewerMask(
                MaskInfo(0, activeViewer!!.state.bestMipMapLevel),
                activeViewer!!
            )
            val labelMaskTopLeft = viewerMask.displayPointToInitialMaskPoint(0, 0)
            val topLeftX = labelMaskTopLeft.getIntPosition(0).toLong()
            val topLeftY = labelMaskTopLeft.getIntPosition(1).toLong()
            val maskInterval = Intervals.createMinSize(topLeftX, topLeftY, 0, mask.width.toLong(), mask.height.toLong(), 1)
            val interval = viewerMask.viewerImg.interval(maskInterval)

            val cursor = interval.cursor()
            for (it in (mask.raster.dataBuffer as DataBufferByte).data) {
                if (cursor.hasNext()) {
                    cursor.next()
                    if (it.toInt() != 0) {
                        cursor.get().set(currentLabelToPaint)
                    }
                }
            }
            return@createTask SamTaskInfo(maskedSource, maskInterval)
        }.onEnd {
            val (maskedSource, maskInterval) = it.get()
            val viewerMask = maskedSource.currentMask as ViewerMask
            val sourceInterval = IntervalHelpers.extendAndTransformBoundingBox(maskInterval.asRealInterval, viewerMask.initialMaskToSourceWithDepthTransform, .5)
            maskedSource.applyMask(viewerMask, sourceInterval.smallestContainingInterval, MaskedSource.VALID_LABEL_CHECK)
            paintera.currentSource!!.isVisibleProperty.set(true)
            paintera.baseView.disabledPropertyBindings -= this
            deactivate()
        }

        paintera.baseView.disabledPropertyBindings[this] = SimpleBooleanProperty(true)
    }

    private fun saveActiveViewerImage() {
        val snapshotParameters = SnapshotParameters()
        val image = activeViewer!!.snapshot(snapshotParameters, null)
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", File("/tmp/sam.png"))
    }
}
