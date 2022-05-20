package org.janelia.saalfeldlab.paintera.control.paint

import bdv.fx.viewer.ViewerPanelFX
import bdv.util.Affine3DHelpers
import javafx.beans.property.SimpleLongProperty
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.input.MouseEvent
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.LinAlgHelpers
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.setNewViewerMask
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse
import org.janelia.saalfeldlab.paintera.exception.PainteraException
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendAndTransformBoundingBox
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.union
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.Predicate

private data class Postion(var x: Double = 0.0, var y: Double = 0.0) {

    constructor(mouseEvent: MouseEvent) : this(mouseEvent.x, mouseEvent.y)

    fun update(x: Double, y: Double) {
        this.x = x
        this.y = y
    }

    fun update(mouseEvent: MouseEvent) {
        mouseEvent.apply { update(x, y) }
    }

    private fun toDoubleArray() = doubleArrayOf(x, y)

    override fun toString() = "<Position: ($x, $y)>"

    infix fun linalgSubtract(other: Postion): DoubleArray {
        val thisDoubleArray = toDoubleArray()
        LinAlgHelpers.subtract(thisDoubleArray, other.toDoubleArray(), thisDoubleArray)
        return thisDoubleArray
    }

    operator fun plusAssign(normalizedDragPos: DoubleArray) {
        val xy = this.toDoubleArray() inPlaceAdd normalizedDragPos
        this.x = xy[0]
        this.y = xy[1]
    }

    operator fun minusAssign(normalizedDragPos: DoubleArray) {
        val xy = this.toDoubleArray() inPlaceSubtract normalizedDragPos
        this.x = xy[0]
        this.y = xy[1]
    }

    private infix fun DoubleArray.inPlaceSubtract(other: DoubleArray): DoubleArray {
        LinAlgHelpers.subtract(this, other, this)
        return this
    }

    private infix fun DoubleArray.inPlaceAdd(other: DoubleArray): DoubleArray {
        LinAlgHelpers.add(this, other, this)
        return this
    }
}


class PaintClickOrDragController(
    private val paintera: PainteraBaseView,
    private val viewer: ViewerPanelFX,
    private val paintId: () -> Long?,
    private val brushRadius: () -> Double,
    private val brushDepth: () -> Double
) {

    fun submitPaint() {
        synchronized(this) {
            when {
                !isPainting -> LOG.debug("Not currently painting -- will not do anything")
                paintIntoThis == null -> LOG.debug("No current source available -- will not do anything")
                !submitMask -> {
                    LOG.debug("submitMask flag: $submitMask")
                    isPainting = false
                }
                else -> try {
                    with(paintIntoThis!!) {
                        viewerMask?.let { mask ->
                            val sourceInterval = extendAndTransformBoundingBox(viewerInterval!!.asRealInterval, mask.currentSourceToViewerTransform.inverse(), .5)
                            val repaintInterval = extendAndTransformBoundingBox(sourceInterval.smallestContainingInterval, mask.initialSourceToGlobalTransform, 0.5)
                            applyMask(currentMask, sourceInterval.smallestContainingInterval, FOREGROUND_CHECK)
                            var refreshAfterApplyingMask: ChangeListener<Boolean>? = null
                            refreshAfterApplyingMask = ChangeListener<Boolean> { obs, _, isApplyingMask ->
                                if (!isApplyingMask) {
                                    paintera.orthogonalViews().requestRepaint(repaintInterval)
                                    obs.removeListener(refreshAfterApplyingMask!!)
                                }
                            }
                            isApplyingMaskProperty.addListener(refreshAfterApplyingMask)
                        }
                    }
                } catch (e: Exception) {
                    InvokeOnJavaFXApplicationThread { exceptionAlert(Constants.NAME, "Exception when trying to submit mask.", e).show() }
                } finally {
                    viewerMask?.disable()
                    release()
                }
            }
        }
    }

    class IllegalIdForPainting(val id: Long?) : PainteraException("Cannot paint this id: $id")

    private var fillLabelSetManually: Boolean = false

    @get:Synchronized
    private var isPainting = false

    private var paintIntoThis: MaskedSource<*, *>? = null

    var fillLabelProperty = SimpleLongProperty(0)
    private var fillLabel by fillLabelProperty.nonnull()
    internal var viewerInterval: Interval? = null
    private val position = Postion()

    var viewerMask: ViewerMask? = null
    private var submitMask = true


    internal fun provideMask(viewerMask: ViewerMask) {
        submitMask = false
        this.viewerMask = viewerMask
    }

    fun isPainting(): Boolean {
        return isPainting
    }

    fun getViewerMipMapLevel(): Int {
        (paintera.sourceInfo().currentSourceProperty().get() as MaskedSource<*, *>).let { currentSource ->
            val screenScaleTransform = AffineTransform3D().also {
                viewer.renderUnit.getScreenScaleTransform(0, it)
            }
            return viewer.state.getBestMipMapLevel(screenScaleTransform, currentSource)
        }
    }

    fun startPaint(event: MouseEvent) {
        LOG.debug("Starting New Paint", event)
        if (isPainting) {
            LOG.debug("Already painting -- will not start new paint.")
            return
        }
        synchronized(this) {
            (paintera.sourceInfo().currentSourceProperty().get() as? MaskedSource<*, *>)?.let { currentSource ->
                try {

                    val screenScaleTransform = AffineTransform3D().also {
                        viewer.renderUnit.getScreenScaleTransform(0, it)
                    }
                    val viewerTransform = AffineTransform3D()
                    val state = viewer.state
                    val level = synchronized(state) {
                        state.getViewerTransform(viewerTransform)
                        state.getBestMipMapLevel(screenScaleTransform, currentSource)
                    }

                    /* keep and set mask on source, or generate new and set  */
                    when {
                        viewerMask == null -> {
                            generateViewerMask(level, currentSource)
                        }
                        currentSource.currentMask == null -> {
                            viewerMask!!.setViewerMaskOnSource()
                        }
                        else -> LOG.trace("Viewer Mask was Provided, but source already has a mask. Doing Nothing. ")
                    }

                    isPainting = true
                    if (!fillLabelSetManually) {
                        ++fillLabel
                    }
                    viewerInterval = null
                    paintIntoThis = currentSource
                    position.update(event)
                    paint(position)
                } catch (e: MaskInUse) {
                    // Ensure we never enter a painting state when an exception occurs
                    viewerMask?.disable()
                    release()
                    InvokeOnJavaFXApplicationThread {
                        if (e.offerReset()) {
                            PainteraAlerts.confirmation("Yes", "No", true, paintera.pane.scene.window).apply {
                                headerText = "Unable to paint."

                                contentText = """
                                     The Busy Masks alert has displayed at least three times without being cleared. Would you like to force reset the mask?

                                     This may result in loss of some of the most recent uncommitted label annotations. Only do this if you suspect and error has occured. You may consider waiting a bit to see if the mask releases on it's own.
                                """.trimIndent()
                                (dialogPane.lookupButton(ButtonType.OK) as? Button)?.let {
                                    it.isFocusTraversable = false
                                    it.onAction = EventHandler {
                                        currentSource.resetMasks()
                                    }
                                }

                                show()
                                //NOTE: Normally, the "OK" is focused by default, however since we are always holding down SPACE during paint (at least currently)
                                // Then when we release space, it will trigger the Ok, without the user meaning to, perhaps. Request focus away from the Ok button.
                                dialogPane.requestFocus()
                            }
                        } else {
                            exceptionAlert(Constants.NAME, "Unable to paint.", e).apply {
                                show()
                                //NOTE: Normally, the "OK" is focused by default, however since we are always holding down SPACE during paint (at least currently)
                                // Then when we release space, it will trigger the Ok, without the user meaning to, perhaps. Request focus away from the Ok button.
                                dialogPane.requestFocus()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ensure we never enter a painting state when an exception occurs
                    viewerMask?.disable()
                    release()
                    InvokeOnJavaFXApplicationThread {
                        exceptionAlert(Constants.NAME, "Unable to paint.", e).showAndWait()
                    }
                }
            }
        }
    }

    /*NOTE: If `submitMask` is false, then it is the developers respondsibility to
     * relase the resources from this controller, and to apply the mask MaskedSource when desired,
     * and ultimately reset the MaskedSource's masks */
    fun generateViewerMask(
        level: Int = getViewerMipMapLevel(),
        currentSource: MaskedSource<*, *> = paintera.sourceInfo().currentSourceProperty().get() as MaskedSource<*, *>,
        submitMask: Boolean = true
    ): ViewerMask {

        val id = paintId() ?: throw PaintClickOrDragController.IllegalIdForPainting(null)
        val maskInfo = MaskInfo(0, level, UnsignedLongType(id))
        return currentSource.setNewViewerMask(maskInfo, viewer, brushDepth()).also {
            viewerMask = it
            this.submitMask = submitMask
        }
    }

    internal fun setFillLabel(fillVal: Long) {
        fillLabel = fillVal
        fillLabelSetManually = true
    }

    internal fun resetFillLabel() {
        fillLabelSetManually = false
    }

    fun extendPaint(event: MouseEvent) {
        if (!isPainting) {
            LOG.debug("Not currently painting -- will not paint")
            return
        }
        synchronized(this) {
            val targetPosition = Postion(event)
            if (targetPosition != position) {
                try {
                    LOG.debug("Drag: paint at screen from $position to $targetPosition")
                    var draggedDistance: Double
                    val normalizedDragPos = (targetPosition linalgSubtract position).also {
                        //NOTE: Calculate distance before noramlizing
                        draggedDistance = LinAlgHelpers.length(it)
                        LinAlgHelpers.normalize(it)
                    }
                    LOG.debug("Number of paintings triggered {}", draggedDistance + 1)
                    repeat(draggedDistance.toInt() + 1) {
                        paint(position)
                        position += normalizedDragPos
                    }
                    LOG.debug("Painting ${draggedDistance + 1} times with radius ${brushRadius()} took a total of {}ms")
                } finally {
                    position.update(event)
                }
            }
        }
    }

    @Synchronized
    private fun paint(pos: Postion) = pos.run { paint(x, y) }

    @Synchronized
    private fun paint(viewerX: Double, viewerY: Double) {
        LOG.trace("At {} {}", viewerX, viewerY)
        when {
            !isPainting -> LOG.debug("Not currently activated for painting, returning without action").also { return }
            viewerMask == null -> LOG.debug("Current mask is null, returning without action").also { return }
        }

        viewerMask?.run {

            val initialPoint = currentToInitialPoint(viewerX, viewerY)

            val paintIntervalInInitialViewer = Paint2D.paintIntoViewer(
                viewerRai,
                fillLabel,
                initialPoint,
                initialBrushRadius()
            )


            val paintIntervalInCurrentViewer = initialToCurrentViewerTransform.estimateBounds(paintIntervalInInitialViewer).smallestContainingInterval
            val sourcePaintInterval = extendAndTransformBoundingBox(paintIntervalInCurrentViewer, currentSourceToViewerTransform.inverse(), .5)
            val globalPaintInterval = extendAndTransformBoundingBox(sourcePaintInterval, initialSourceToGlobalTransform, .5)


            viewerInterval = paintIntervalInCurrentViewer union viewerInterval
            paintera.orthogonalViews().requestRepaint(globalPaintInterval)
        }


    }

    private fun ViewerMask.initialBrushRadius(): Double {
        val globalToInitialViewerTransform = initialToCurrentViewerTransform.copy().inverse().concatenate(currentGlobalToViewerTransform)
        val brushRadius = Affine3DHelpers.extractScale(globalToInitialViewerTransform, 0) * brushRadius()
        return brushRadius
    }

    internal fun release() {
        viewerMask = null
        isPainting = false
        viewerInterval = null
        paintIntoThis = null
        submitMask = true
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val FOREGROUND_CHECK = Predicate { t: UnsignedLongType -> Label.isForeground(t.get()) }
    }

}
