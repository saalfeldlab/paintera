package org.janelia.saalfeldlab.paintera.control

import bdv.fx.viewer.ViewerPanelFX
import bdv.util.Affine3DHelpers
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.concurrent.Task
import javafx.concurrent.Worker
import javafx.scene.paint.Color
import javafx.util.Duration
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.imglib2.*
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE
import net.imglib2.converter.BiConverter
import net.imglib2.converter.Converters
import net.imglib2.converter.RealRandomArrayAccessible
import net.imglib2.converter.logical.Logical
import net.imglib2.converter.read.BiConvertedRealRandomAccessible
import net.imglib2.img.array.ArrayImgFactory
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.loops.LoopBuilder
import net.imglib2.outofbounds.RealOutOfBoundsConstantValueFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.realtransform.Translation3D
import net.imglib2.type.BooleanType
import net.imglib2.type.NativeType
import net.imglib2.type.label.Label
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.ui.TransformListener
import net.imglib2.util.*
import net.imglib2.view.ExtendedRealRandomAccessibleRealInterval
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Tasks.Companion.createTask
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera.Companion.getPaintera
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.getSourceDataInInitialMaskSpace
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.setNewViewerMask
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.data.PredicateDataSource.PredicateConverter
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendAndTransformBoundingBox
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.scaleBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

class ShapeInterpolationController<D : IntegerType<D>?>(
    val source: MaskedSource<D, *>,
    private val refreshMeshes: Runnable,
    val selectedIds: SelectedIds,
    val idService: IdService,
    val converter: HighlightingStreamConverter<*>,
    private val assignment: FragmentSegmentAssignment
) {
    enum class ControllerState {
        Select, Interpolate, Preview, Off, Moving
    }

    private var lastSelectedId: Long = 0
    private var selectionId: Long = 0

    var currentFillValueProperty = SimpleLongProperty(1)

    private val slicesAndInterpolants = SlicesAndInterpolants()

    var sliceDepthProperty: ObjectProperty<Double> = SimpleObjectProperty()
    var sliceDepth: Double by sliceDepthProperty.nonnull()

    val isBusyProperty = SimpleBooleanProperty(false)
    var isBusy: Boolean by isBusyProperty.nonnull()

    val controllerStateProperty: ObjectProperty<ControllerState> = SimpleObjectProperty(ControllerState.Off)
    var controllerState: ControllerState by controllerStateProperty.nonnull()
    val isControllerActive: Boolean
        get() = controllerState != ControllerState.Off

    private val sliceAtCurrentDepthBinding = sliceDepthProperty.createNonNullValueBinding(slicesAndInterpolants) { slicesAndInterpolants.getSliceAtDepth(it) }
    val sliceAtCurrentDepth by sliceAtCurrentDepthBinding.nullableVal()

    val numSlices: Int get() = slicesAndInterpolants.slices.size

    var activeSelectionAlpha = (AbstractHighlightingARGBStream.DEFAULT_ACTIVE_FRAGMENT_ALPHA ushr 24) / 255.0

    private var activeViewer: ViewerPanelFX? = null

    internal val currentViewerMaskProperty: ObjectProperty<ViewerMask?> = SimpleObjectProperty(null)
    internal var currentViewerMask by currentViewerMaskProperty.nullable()

    private val doneApplyingMaskListener = ChangeListener<Boolean> { _, _, newv -> if (!newv!!) InvokeOnJavaFXApplicationThread { doneApplyingMask() } }

    private var requestRepaintInterval: RealInterval? = null
    private val requestRepaintAfterTask = AtomicBoolean(false)

    val sortedSliceDepths: List<Double>
        get() = runBlocking {
            slicesAndInterpolants.asFlow()
                .filter { it.isSlice }
                .map { it.sliceDepth }
                .toList()
        }


    private val currentBestMipMapLevel: Int
        get() {
            val viewerState = activeViewer!!.state
            val screenScaleTransform = AffineTransform3D()
            activeViewer!!.renderUnit.getScreenScaleTransform(0, screenScaleTransform)
            return viewerState.getBestMipMapLevel(screenScaleTransform, source)
        }


    private val currentDepth: Double by LazyForeignValue(this::globalToViewerTransform) { globalToViewer ->
        val currentViewerInInitialViewer = DoubleArray(3).also {
            initialGlobalToViewerTransform!!.copy().concatenate(globalToViewer.inverse()).apply(it, it)
        }
        BigDecimal(currentViewerInInitialViewer[2]).setScale(5, RoundingMode.HALF_EVEN).toDouble()
    }

    private var selector: Task<Unit>? = null
    private var interpolator: Task<Unit>? = null
    private val onTaskFinished = {
        controllerState = ControllerState.Preview
        synchronized(requestRepaintAfterTask) {
            InvokeOnJavaFXApplicationThread {
                if (requestRepaintAfterTask.getAndSet(false)) {
                    requestRepaintAfterTasks(force = true)
                }
            }
        }
    }

    private var compositeFillAndInterpolationImgs: Pair<RealRandomAccessible<UnsignedLongType>, RealRandomAccessible<VolatileUnsignedLongType>>? = null

    private val viewerTransformDepthUpdater = TransformListener<AffineTransform3D> {
        updateDepth()
        sliceAtCurrentDepth?.mask.let {
            currentViewerMask = it
        }
    }


    fun paint(maskPaintInterval: Interval) {
        if (controllerState == ControllerState.Off) {
            return
        }
        isBusy = true
        addSelection(maskPaintInterval)
    }

    fun deleteCurrentSlice() {
        slicesAndInterpolants.removeSliceAtDepth(currentDepth)?.let { slice ->
            isBusy = true
            interpolateBetweenSlices(true)
            val adjacentRepaintInterval = listOf(
                slicesAndInterpolants.getPreviousSlice(currentDepth),
                slicesAndInterpolants.getNextSlice(currentDepth)
            )
                .mapNotNull { it?.globalBoundingBox }
                .ifEmpty { null }
                ?.reduce { l, r -> l union r }

            requestRepaintAfterTasks(slice.globalBoundingBox union adjacentRepaintInterval)
        }
    }

    @JvmOverloads
    fun addSelection(maskIntervalOverSelection: Interval, keepInterpolation: Boolean = true, z: Double = currentDepth) {
        val globalTransform = paintera().manager().transform

        if (!keepInterpolation && slicesAndInterpolants.getSliceAtDepth(z) != null) {
            slicesAndInterpolants.removeSliceAtDepth(z)
            updateFillAndInterpolantsCompositeMask()
            val slice = SliceInfo(
                currentViewerMask!!,
                globalTransform,
                maskIntervalOverSelection
            )
            slicesAndInterpolants.add(z, slice)
        }
        if (slicesAndInterpolants.getSliceAtDepth(z) == null) {
            val slice = SliceInfo(currentViewerMask!!, globalTransform, maskIntervalOverSelection)
            slice.addSelection(maskIntervalOverSelection)
            slicesAndInterpolants.add(z, slice)
        } else {
            slicesAndInterpolants.getSliceAtDepth(z)!!.addSelection(maskIntervalOverSelection)
            slicesAndInterpolants.clearInterpolantsAroundSlice(z)
        }
        interpolateBetweenSlices(true)
    }


    fun enterShapeInterpolation(viewer: ViewerPanelFX?) {
        if (isControllerActive) {
            LOG.trace("Already in shape interpolation")
            return
        }
        LOG.debug("Entering shape interpolation")

        activeViewer = viewer

        /* Store all the previous activated Ids*/
        lastSelectedId = assignment.getSegment(selectedIds.lastSelection)
        if (lastSelectedId == Label.INVALID) lastSelectedId = idService.next()
        selectNewInterpolationId()
        initialGlobalToViewerTransform = globalToViewerTransform
        activeViewer!!.addTransformListener(viewerTransformDepthUpdater)
        updateDepth()
        controllerState = ControllerState.Select

        sliceAtCurrentDepthBinding.addListener { _, old, new ->
            old?.mask?.setMaskOnUpdate = false
            new?.mask?.setMaskOnUpdate = false
        }
    }

    private fun updateDepth() {
        sliceDepth = currentDepth
    }

    fun exitShapeInterpolation(completed: Boolean) {
        if (!isControllerActive) {
            LOG.debug("Not in shape interpolation")
            return
        }
        LOG.debug("Exiting shape interpolation")
        enableAllViewers()

        // extra cleanup if shape interpolation was aborted
        if (!completed) {
            interruptInterpolation()
            source.resetMasks(true)
        }


        /* Reset the selection state */
        converter.removeColor(lastSelectedId)
        converter.removeColor(selectionId)
        selectedIds.deactivate(selectionId)
        selectedIds.activateAlso(lastSelectedId)
        controllerState = ControllerState.Off
        slicesAndInterpolants.clear()
        sliceDepth = 0.0
        currentViewerMask = null
        interpolator = null
        compositeFillAndInterpolationImgs = null
        lastSelectedId = Label.INVALID
        selectionId = Label.INVALID

        activeViewer!!.removeTransformListener(viewerTransformDepthUpdater)
        activeViewer = null
    }


    private fun enableAllViewers() {
        val orthoViews = paintera().orthogonalViews()
        orthoViews.views().forEach { orthoViews.enableView(it) }
    }

    fun togglePreviewMode() {
        preview = !preview
        interpolateBetweenSlices(true)
        if (slicesAndInterpolants.size > 0) {
            requestRepaintAfterTasks(unionWith = slicesAndInterpolants.slices.map { it.globalBoundingBox }.reduce(Intervals::union))
        }
    }

    @Synchronized
    fun interpolateBetweenSlices(onlyMissing: Boolean) {
        if (slicesAndInterpolants.slices.size < 2) {
            updateFillAndInterpolantsCompositeMask()
            isBusy = false
            return
        }

        InvokeOnJavaFXApplicationThread {
            controllerState = ControllerState.Interpolate
        }

        if (!onlyMissing) {
            slicesAndInterpolants.removeAllInterpolants()
        }
        if (interpolator != null) {
            interpolator!!.cancel()
        }

        isBusy = true
        interpolator = createTask<Unit> { task ->
            synchronized(this) {
                var updateInterval: RealInterval? = null
                for (idx in slicesAndInterpolants.size - 1 downTo 1) {
                    if (task.isCancelled) {
                        return@createTask
                    }
                    val either1 = slicesAndInterpolants[idx - 1]
                    if (either1.isSlice) {
                        val either2 = slicesAndInterpolants[idx]
                        if (either2.isSlice) {
                            val slice1 = either1.getSlice()
                            val slice2 = either2.getSlice()
                            val interpolatedImgs = interpolateBetweenTwoSlices(slice1, slice2)
                            slicesAndInterpolants.add(idx, interpolatedImgs)
                            updateInterval = slice1.globalBoundingBox union slice2.globalBoundingBox union updateInterval
                        }
                    }
                }
                requestRepaintAfterTasks(updateInterval)
                setInterpolatedMasks()
            }
        }
            .onCancelled { _, _ -> LOG.debug("Interpolation Cancelled") }
            .onSuccess { _, _ -> onTaskFinished() }
            .onEnd {
                interpolator = null
                isBusy = false
            }
            .submit()
    }

    enum class EditSelectionChoice {
        First,
        Previous,
        Next,
        Last
    }

    fun editSelection(choice: EditSelectionChoice) {
        val slices = slicesAndInterpolants.slices
        when (choice) {
            EditSelectionChoice.First -> slices.getOrNull(0) /* move to first */
            EditSelectionChoice.Previous -> slicesAndInterpolants.getPreviousSlice(currentDepth) ?: slices.getOrNull(0) /* move to previous, or first if none */
            EditSelectionChoice.Next -> slicesAndInterpolants.getNextSlice(currentDepth) ?: slices.getOrNull(slices.size - 1) /* move to next, or last if none */
            EditSelectionChoice.Last -> slices.getOrNull(slices.size - 1) /* move to last */
        }?.let { slice ->
            selectAndMoveToSlice(slice)
        }
    }

    fun selectAndMoveToSlice(sliceInfo: SliceInfo) {
        controllerState = ControllerState.Moving
        InvokeOnJavaFXApplicationThread {
            paintera().manager().setTransform(sliceInfo.globalTransform, Duration(300.0)) {
                paintera().manager().transform = sliceInfo.globalTransform
                updateDepth()
                controllerState = ControllerState.Select
            }
        }
    }

    @JvmOverloads
    fun applyMask(exit: Boolean = true): Boolean {
        if (numSlices < 2) {
            return false
        }
        if (!preview) {
            togglePreviewMode()
        }
        if (controllerState == ControllerState.Interpolate) {
            // wait until the interpolation is done
            interpolator!!.get()
        }
        assert(controllerState == ControllerState.Preview)

        val sourceToGlobalTransform = source.getSourceTransformForMask(source.currentMask.info)
        val slicesUnionSourceInterval = slicesAndInterpolants.stream()
            .filter(SliceOrInterpolant::isSlice)
            .map(SliceOrInterpolant::getSlice)
            .map(SliceInfo::globalBoundingBox)
            .map { sourceToGlobalTransform.inverse().estimateBounds(it) }
            .reduce(Intervals::union)
            .map { it.smallestContainingInterval }
            .get()

        LOG.trace("Applying interpolated mask using bounding box of size {}", Intervals.dimensionsAsLongArray(slicesUnionSourceInterval))

        if (Label.regular(lastSelectedId)) {
            val maskInfoWithLastSelectedLabelId = MaskInfo(
                source.currentMask.info.time,
                source.currentMask.info.level,
                UnsignedLongType(lastSelectedId)
            )
            source.resetMasks(false)
            val interpolatedMaskImgsA = Converters.convert(
                compositeFillAndInterpolationImgs!!.a,
                { input: UnsignedLongType, output: UnsignedLongType ->
                    val originalLabel = input.long
                    output.set(if (originalLabel == selectionId) lastSelectedId else input.get())
                },
                UnsignedLongType()
            )
            val interpolatedMaskImgsB = Converters.convert(
                compositeFillAndInterpolationImgs!!.b,
                { input: VolatileUnsignedLongType, out: VolatileUnsignedLongType ->
                    val isValid = input.isValid
                    out.isValid = isValid
                    if (isValid) {
                        val originalLabel = input.get().get()
                        out.get().set(if (originalLabel == selectionId) lastSelectedId else originalLabel)
                    }
                },
                VolatileUnsignedLongType()
            )
            source.setMask(
                maskInfoWithLastSelectedLabelId,
                interpolatedMaskImgsA,
                interpolatedMaskImgsB,
                null, null, null, FOREGROUND_CHECK
            )

        }
        source.isApplyingMaskProperty.addListener(doneApplyingMaskListener)
        source.applyMask(source.currentMask, slicesUnionSourceInterval, FOREGROUND_CHECK)
        if (exit) {
            exitShapeInterpolation(true)
        }
        return true
    }

    private fun selectNewInterpolationId() {
        /* Grab the color of the previously active ID. We will make our selection ID color slightly different, to indicate selection. */
        val packedLastARGB = converter.stream.argb(lastSelectedId)
        val originalColor = Colors.toColor(packedLastARGB)
        val fillLabelColor = Color(originalColor.red, originalColor.green, originalColor.blue, activeSelectionAlpha)
        selectionId = idService.nextTemporary()
        converter.setColor(selectionId, fillLabelColor, true)
        selectedIds.activateAlso(selectionId, lastSelectedId)
    }

    private fun doneApplyingMask() {
        source.isApplyingMaskProperty.removeListener(doneApplyingMaskListener)
        // generate mesh for the interpolated shape
        refreshMeshes.run()
    }

    @Throws(MaskInUse::class)
    private fun setInterpolatedMasks() {
        synchronized(source) {
            source.resetMasks(false)
            /* If preview is on, hide all except the first and last fill mask */
            val fillMasks: MutableList<RealRandomAccessible<UnsignedLongType>> = mutableListOf()
            val volatileFillMasks: MutableList<RealRandomAccessible<VolatileUnsignedLongType>> = mutableListOf()
            val slices = slicesAndInterpolants.slices
            slices.forEachIndexed { idx, slice ->
                if (idx == 0 || idx == slices.size - 1 || !preview) {
//                if (slices.size == 1 || !preview) {
//                    fillMasks += slice.mask.viewerImgInSource
//                    volatileFillMasks += slice.mask.volatileViewerImgInSource
                    fillMasks += slice.mask.let {

                        val realViewerImg = Views.extendValue(Views.expandBorder(it.viewerImg, 0, 0, 1), Label.INVALID).interpolateNearestNeighbor()
                        RealViews.affineReal(realViewerImg, it.initialMaskToSourceTransform)
                    }
                    volatileFillMasks += slice.mask.let {
                        val realVolatileViewerImg = Views.extendValue(Views.expandBorder(it.volatileViewerImg, 0, 0, 1), VolatileUnsignedLongType(Label.INVALID)).interpolateNearestNeighbor()
                        RealViews.affineReal(realVolatileViewerImg, it.initialMaskToSourceTransform)
                    }
                }
            }
            if (fillMasks.isEmpty()) {
                val constantInvalid = ConstantUtils.constantRandomAccessible(UnsignedLongType(Label.INVALID), 3).interpolateNearestNeighbor()
                val constantVolatileInvalid = ConstantUtils.constantRandomAccessible(VolatileUnsignedLongType(Label.INVALID), 3).interpolateNearestNeighbor()
                fillMasks += constantInvalid
                volatileFillMasks += constantVolatileInvalid
            }


            val compositeFillMask = RealRandomArrayAccessible(fillMasks, { sources: List<UnsignedLongType>, output: UnsignedLongType ->
                var result: Long = 0
                for (input in sources) {
                    val iVal = input.get()
                    if (iVal > 0 || iVal == Label.TRANSPARENT) {
                        result = iVal
                        break
                    }
                }
                if (output.get() != result) {
                    output.set(result)
                }
            }, UnsignedLongType())


            val volatileCompositeFillMask = RealRandomArrayAccessible(volatileFillMasks, { sources: List<VolatileUnsignedLongType>, output: VolatileUnsignedLongType ->
                var result: Long = 0
                var valid = true
                val outType = output.get()
                for (input in sources) {
                    val iVal = input.get().get()
                    if (iVal > 0 || iVal == Label.TRANSPARENT) {
                        result = iVal
                        valid = valid and input.isValid
                        break
                    }
                }
                if (outType.get() != result) {
                    output.set(result)
                }
                output.isValid = valid
            }, VolatileUnsignedLongType())

            val interpolants = slicesAndInterpolants.interpolants
            val dataMasks: MutableList<RealRandomAccessible<UnsignedLongType>> = mutableListOf()
            val volatileMasks: MutableList<RealRandomAccessible<VolatileUnsignedLongType>> = mutableListOf()

            if (preview) {
                interpolants.forEach { info ->
                    dataMasks += info.dataInterpolant
                    volatileMasks += info.volatileInterpolant
                }
            }


            val interpolatedArrayMask = RealRandomArrayAccessible(dataMasks, { sources: List<UnsignedLongType>, output: UnsignedLongType ->
                var acc: Long = 0
                for (input in sources) {
                    acc += input.get()
                }
                if (output.get() != acc) {
                    output.set(acc)
                }
            }, UnsignedLongType())
            val volatileInterpolatedArrayMask = RealRandomArrayAccessible(volatileMasks, { sources: List<VolatileUnsignedLongType>, output: VolatileUnsignedLongType ->
                var acc: Long = 0
                var valid = true
                val outType = output.get()
                for (input in sources) {
                    acc += input.get().get()
                    valid = valid and input.isValid
                }
                if (outType.get() != acc) {
                    output.set(acc)
                }
                output.isValid = valid
            }, VolatileUnsignedLongType())
            val compositeMask = BiConvertedRealRandomAccessible(compositeFillMask, interpolatedArrayMask, Supplier {
                BiConverter { a: UnsignedLongType, b: UnsignedLongType, c: UnsignedLongType ->
                    val aVal = a.get()
                    if (aVal > 0 || aVal == Label.TRANSPARENT) {
                        c.set(a)
                    } else {
                        c.set(b)
                    }
                }
            }) { UnsignedLongType() }
            val compositeVolatileMask = BiConvertedRealRandomAccessible(volatileCompositeFillMask, volatileInterpolatedArrayMask, Supplier {
                BiConverter { a: VolatileUnsignedLongType, b: VolatileUnsignedLongType, c: VolatileUnsignedLongType ->
                    val aVal = a.get().get()
                    if (a.isValid && (aVal > 0 || aVal == Label.TRANSPARENT)) {
                        c.set(a)
                        c.isValid = true
                    } else {
                        c.set(b)
                    }
                }
            }) { VolatileUnsignedLongType() }

            /* Set the interpolatedMaskImgs to the composite fill+interpolation RAIs*/
            compositeFillAndInterpolationImgs = ValuePair(compositeMask, compositeVolatileMask)
            val maskInfo = currentViewerMask?.info ?: MaskInfo(0, currentBestMipMapLevel, UnsignedLongType(lastSelectedId))
            currentViewerMask?.setMaskOnUpdate = false
            source.setMask(
                maskInfo,
                compositeMask,
                compositeVolatileMask,
                null,
                null,
                null,
                FOREGROUND_CHECK
            )
        }
    }

    private fun requestRepaintAfterTasks(unionWith: RealInterval? = null, force: Boolean = false) {
        fun Task<*>?.notCompleted() = this?.state?.let { it in listOf(Worker.State.READY, Worker.State.SCHEDULED, Worker.State.RUNNING) } ?: false
        InvokeOnJavaFXApplicationThread {
            synchronized(requestRepaintAfterTask) {
                if (!force && interpolator.notCompleted() && selector.notCompleted()) {
                    requestRepaintInterval = requestRepaintInterval?.let { it union unionWith } ?: unionWith
                    requestRepaintAfterTask.set(true)
                    return@InvokeOnJavaFXApplicationThread
                }
            }
            requestRepaintInterval = requestRepaintInterval?.let { it union unionWith } ?: unionWith
            requestRepaintInterval?.let {
                val sourceToGlobal = sourceToGlobalTransform
                val extendedSourceInterval = extendAndTransformBoundingBox(it.smallestContainingInterval, sourceToGlobal.inverse(), 1.0)
                val extendedGlobalInterval = sourceToGlobal.estimateBounds(extendedSourceInterval).smallestContainingInterval
                paintera().orthogonalViews().requestRepaint(extendedGlobalInterval)
            }
            requestRepaintInterval = null
            isBusy = false
        }
    }

    private fun interruptInterpolation() {
        if (interpolator != null) {
            if (interpolator!!.isRunning) {
                interpolator!!.cancel()
            }
            try {
                interpolator?.get()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
        }
    }

    fun selectObject(maskX: Int, maskY: Int, deactivateOthers: Boolean) {

        isBusy = true
        selector = createTask<Unit> {
            synchronized(this) {
                var mask = currentViewerMask ?: return@createTask
                var maskPos = Point(maskX, maskY, 0)
                val maskValue = mask.viewerImg[maskPos]
                if (maskValue.get() == Label.OUTSIDE) return@createTask

                // ignore the background label
                val dataValue = mask.getSourceDataInInitialMaskSpace()[maskPos].realDouble.toLong()
                if (!FOREGROUND_CHECK.test(UnsignedLongType(dataValue))) return@createTask

                val wasSelected = FOREGROUND_CHECK.test(maskValue)
                LOG.debug("Object was clicked: deactivateOthers={}, wasSelected={}", deactivateOthers, wasSelected)
                if (deactivateOthers) {
                    val depthInMaskDisplay = currentDepth
                    val slice = slicesAndInterpolants.getSliceAtDepth(depthInMaskDisplay)
                    if (slice != null) {
                        /* get the repaint bounding box */
                        requestRepaintAfterTasks(slice.globalBoundingBox)
                        /* remove to reset the mask to remove all existing slices and surrounding interpolants */
                        slicesAndInterpolants.removeSlice(slice)
                        /* Then we want to create a new mask */
                        mask = getMask().also {
                            /* get transform between masks */
                            val oldToNewMaskTransform = ViewerMask.maskToMaskTransformation(mask, it)
                            /* set the maskPos for the new mask */
                            maskPos = maskPos.positionAsRealPoint().let { pos ->
                                oldToNewMaskTransform.apply(pos, pos)
                                pos.round()
                            }
                        }
                    } else {
                        /* we aren't in a slice, so remove the interpolation if there is one. */
                        slicesAndInterpolants.removeIfInterpolantAt(depthInMaskDisplay)
                        updateFillAndInterpolantsCompositeMask()
                    }
                }
                val (_, currentViewerInterval) = when {
                    /* We cleared the mask above, now reselect the thing we clicked on */
                    wasSelected && deactivateOthers -> {
                        runFloodFillToSelect(maskPos).also { addSelection(it.second) }
                    }
                    /* Already selected, so deselect it. */
                    wasSelected -> runFloodFillToDeselect(maskPos, maskValue.long)
                    /* Not selected, select it. */
                    else -> {
                        runFloodFillToSelect(maskPos).also { addSelection(it.second) }
                    }
                }
                val globalFillInterval = mask.currentGlobalToViewerTransform.inverse().estimateBounds(currentViewerInterval)
                requestRepaintAfterTasks(globalFillInterval)

                interpolateBetweenSlices(true)
            }
        }
            .onCancelled { _, _ -> LOG.debug("Interpolation Cancelled") }
            .onSuccess { _, _ -> onTaskFinished() }
            .onEnd {
                selector = null
                isBusy = false
            }
            .submit()

    }

    private fun updateFillAndInterpolantsCompositeMask() {
        if (numSlices == 0) {
            source.resetMasks()
            paintera().orthogonalViews().requestRepaint()
        } else {
            try {
                setInterpolatedMasks()
            } catch (e: MaskInUse) {
                LOG.error("Label source already has an active mask")
            }
        }
    }

    /**
     * Flood-fills the mask using a new fill value to mark the object as selected.
     *
     * @param initialMaskPos click position for floodFillToSelect, in viewer mask initial space
     *
     * @return the fill value of the selected object and the affected interval in source coordinates
     */
    private fun runFloodFillToSelect(initialMaskPos: Point): kotlin.Pair<Long, Interval> {
//         currentFillValueProperty.set(currentFillValueProperty.longValue() + 1)
        val fillValue = currentFillValueProperty.longValue()
        LOG.debug("Flood-filling to select object: fill value={}", fillValue)

        val refreshThread = FloodFill2D.refreshDuringFloodFill()
        refreshThread.start()

        val affectedInitialMaskInterval = FloodFill2D.getBackgorundLabelMaskForAssignment(initialMaskPos, currentViewerMask!!, assignment, fillValue)?.let { filter ->
            FloodFill2D.viewerMaskFloodFill(initialMaskPos, currentViewerMask!!, filter, fillValue, false)
        }

        refreshThread.interrupt()
        return fillValue to currentViewerMask!!.initialToCurrentMaskTransform.estimateBounds(affectedInitialMaskInterval).smallestContainingInterval
    }

    /**
     * Flood-fills the mask using the background value to remove the object from the selection.
     *
     * @param initialMaskPos click position for floodFillToSelect, in viewer mask initial space
     * @param maskValue background value of mask
     * @return the fill value of the deselected object
     */
    private fun runFloodFillToDeselect(initialMaskPos: Point, maskValue: Long): kotlin.Pair<Long, Interval> {
        // set the predicate to accept only the fill value at the clicked location to avoid deselecting adjacent objects.
        val predicate: RandomAccessibleInterval<BoolType> = Converters.convert(
            currentViewerMask!!.viewerImg,
            { input, output -> output.set(input.integerLong == maskValue) },
            BoolType()
        )
        LOG.debug("Flood-filling to deselect object: old value={}", maskValue)

        val refreshThread = FloodFill2D.refreshDuringFloodFill()
        refreshThread.start()
        val affectedInitialMaskInterval = FloodFill2D.viewerMaskFloodFill(initialMaskPos, currentViewerMask!!, predicate, Label.BACKGROUND, false)
        refreshThread.interrupt()
        return maskValue to currentViewerMask!!.initialToCurrentMaskTransform.estimateBounds(affectedInitialMaskInterval).smallestContainingInterval
    }

    private val sourceToGlobalTransform: AffineTransform3D get() = AffineTransform3D().also { source.getSourceTransform(activeViewer!!.state.timepoint, currentBestMipMapLevel, it) }
    private val globalToViewerTransform: AffineTransform3D get() = AffineTransform3D().also { activeViewer!!.state.getViewerTransform(it) }

    fun getMask(): ViewerMask {

        var upscalePower = 0
        val currentLevel = currentBestMipMapLevel
        /* If we have a mask, get it; else create a new one */
        currentViewerMask = sliceAtCurrentDepth?.mask?.let {

            while (it.xScaleChange * (2.0.pow(upscalePower)) < .5) {
                upscalePower++
            }

            if (upscalePower > 0) {
                val maskInfo = MaskInfo(0, currentLevel, UnsignedLongType(selectionId))
                source.setNewViewerMask(maskInfo, activeViewer!!, maskSize = it.viewerImg.scaleBy(2.0.pow(upscalePower).toInt()))
            } else {
                it
            }
        } ?: let {
            val maskInfo = MaskInfo(0, currentLevel, UnsignedLongType(selectionId))
            source.setNewViewerMask(maskInfo, activeViewer!!)
        }



        if (upscalePower > 0) {
            convertToHigherResolutionMask(2.0.pow(upscalePower).toInt())
        } else if (sliceAtCurrentDepth == null) {
            /* if we are between slices, we need to copy the interpolated data into the mask before we return it */

            /* Only copy existing interpolation data into mask if:
             *  - we have two slices,
             *  - we want to preview the interpolation,
             *  - and an interpolant between the two slices exists */
            val prevSlice = slicesAndInterpolants.getPreviousSlice(currentDepth)
            val nextSlice = slicesAndInterpolants.getNextSlice(currentDepth)
            if (prevSlice != null && nextSlice != null && preview && slicesAndInterpolants.getInterpolantsAround(prevSlice).b != null) {
                /* copy interpolation into paintMask */
                copyInterpolationAtDepthToMask(prevSlice, nextSlice)
            }
        }

        return currentViewerMask!!
    }

    private fun copyInterpolationAtDepthToMask(prevSlice: SliceInfo, nextSlice: SliceInfo) {
        /* get union of adjacent slices bounding boxes */
        val globalIntervalOverAdjacentSlices = Intervals.union(prevSlice.globalBoundingBox, nextSlice.globalBoundingBox)
        val adjacentSelectionIntervalInMaskSpace = currentViewerMask!!.currentGlobalToMaskTransform.estimateBounds(globalIntervalOverAdjacentSlices)

        val minZSlice = adjacentSelectionIntervalInMaskSpace.minAsDoubleArray().also { it[2] = 0.0 }
        val maxZSlice = adjacentSelectionIntervalInMaskSpace.maxAsDoubleArray().also { it[2] = 0.0 }

        val unionAdjacentSlicesInMaskSpace = FinalRealInterval(minZSlice, maxZSlice)
        val existingInterpolationMask = slicesAndInterpolants.getInterpolantsAround(prevSlice).b

        val interpolationInMask = RealViews.affine(existingInterpolationMask!!.dataInterpolant, currentViewerMask!!.currentMaskToSourceTransform.inverse())
        val interpolatedMaskView = Views.interval(interpolationInMask, unionAdjacentSlicesInMaskSpace.smallestContainingInterval)
        val fillMaskOverInterval = Views.interval(currentViewerMask!!.viewerImg, unionAdjacentSlicesInMaskSpace.smallestContainingInterval)

        LoopBuilder.setImages(interpolatedMaskView, fillMaskOverInterval)
            .forEachPixel { interpolationType, fillMaskType ->
                val fillMaskVal = fillMaskType.get()
                val iVal = interpolationType.get()
                if (fillMaskVal <= 0 && fillMaskVal != Label.TRANSPARENT && iVal > 0) {
                    fillMaskType.set(iVal)
                }
            }


        val globalTransform = paintera().manager().transform
        val slice = SliceInfo(
            currentViewerMask!!,
            globalTransform,
            unionAdjacentSlicesInMaskSpace.smallestContainingInterval
        )
        /* remove the old interpolant*/
        slicesAndInterpolants.removeIfInterpolantAt(currentDepth)
        /* add the new slice */
        slicesAndInterpolants.add(currentDepth, slice)
    }

    private fun convertToHigherResolutionMask(scaleFactor: Int) {
        /* Remove Existing mask */
        val lowResSlice = sliceAtCurrentDepth!!
        slicesAndInterpolants.removeSlice(sliceAtCurrentDepth!!)

        val lowResBoundingBox = lowResSlice.maskBoundingBox
        val highResBoundingBox = lowResBoundingBox.scaleBy(scaleFactor, scaleFactor, 1, scaleMin = true)
        /* Copy existing data into new mask */
        val lowResSelection = lowResSlice.mask.viewerImg.interval(lowResBoundingBox)

        val lowResCursor = Views.flatIterable(lowResSelection).cursor()
        val highResRandomAccess = currentViewerMask!!.viewerImg.randomAccess(highResBoundingBox)

        while (lowResCursor.hasNext()) {
            val lowResVal = lowResCursor.next().get()
            val lowResPosition = lowResCursor.positionAsLongArray()
            (0L until scaleFactor.toLong()).forEach { x ->
                (0L until scaleFactor.toLong()).forEach { y ->
                    highResRandomAccess.setPosition(longArrayOf(lowResPosition[0] * scaleFactor + x, lowResPosition[1] * scaleFactor + y, 0L))
                    highResRandomAccess.get().set(lowResVal)
                }
            }
        }

        val max = currentViewerMask!!.viewerImg.maxAsLongArray()
        val tenth = max.map { (it * .9).toLong() }.toLongArray()
        currentViewerMask!!.viewerImg.interval(FinalInterval(tenth, max)).forEach { it.set(1) }

        /* Add Slice */
        val slice = SliceInfo(
            currentViewerMask!!,
            paintera().manager().transform,
            highResBoundingBox
        )
        slicesAndInterpolants.add(currentDepth, slice)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val FOREGROUND_CHECK = Predicate { t: UnsignedLongType -> Label.isForeground(t.get()) }
        private fun paintera(): PainteraBaseView = getPaintera().baseView

        private fun interpolateBetweenTwoSlices(
            slice1: SliceInfo,
            slice2: SliceInfo
        ): InterpolantInfo? {
            val sliceInfoPair = arrayOf(slice1, slice2)

            // get the two slices as 2D images
            val slices: Array<RandomAccessibleInterval<UnsignedLongType>?> = arrayOfNulls(2)

            val slice2InitialToSlice1Initial = ViewerMask.maskToMaskTransformation(slice2.mask, slice1.mask)

            val slice1InInitial = slice1.maskBoundingBox
            val slice2InSlice1Initial = slice2InitialToSlice1Initial.estimateBounds(slice2.maskBoundingBox)

            val realUnionInSlice1Initial = slice1InInitial union slice2InSlice1Initial
            val unionInSlice1Initial = realUnionInSlice1Initial.smallestContainingInterval

            slices[0] = Views.zeroMin(
                Views.hyperSlice(
                    Views.extendValue(slice1.mask.viewerImg, Label.INVALID).interval(unionInSlice1Initial),
                    2,
                    0
                )
            )
            slices[1] = Views.zeroMin(
                Views.hyperSlice(
                    RealViews.affineReal(Views.extendValue(slice2.mask.viewerImg, Label.INVALID).interpolateNearestNeighbor(), slice2InitialToSlice1Initial).interval(unionInSlice1Initial),
                    2,
                    realUnionInSlice1Initial.realMax(2).roundToLong()
                )
            )
            // compute distance transform on both slices
            val distanceTransformPair: MutableList<RandomAccessibleInterval<FloatType>> = mutableListOf()
            for (i in 0..1) {
                if (Thread.currentThread().isInterrupted) return null
                val distanceTransform = ArrayImgFactory(FloatType()).create(slices[i]).also {
                    val binarySlice = Converters.convert(slices[i], PredicateConverter(FOREGROUND_CHECK), BoolType())
                    computeSignedDistanceTransform(binarySlice, it, DISTANCE_TYPE.EUCLIDIAN)
                }
                distanceTransformPair.add(distanceTransform)
            }
            val currentDistanceBetweenSlices = computeDistanceBetweenSlices(sliceInfoPair[0], sliceInfoPair[1])
            val depthScaleCurrentToInitial = Affine3DHelpers.extractScale(sliceInfoPair[0].mask.initialToCurrentMaskTransform.inverse(), 2)
            val initialDistanceBetweenSlices = currentDistanceBetweenSlices * depthScaleCurrentToInitial

            val transformToSource = AffineTransform3D()
            transformToSource
                .concatenate(sliceInfoPair[0].mask.initialMaskToSourceTransform)
                .concatenate(Translation3D(unionInSlice1Initial.realMin(0), unionInSlice1Initial.realMin(1), 0.0))

            val interpolatedShapeMask = getInterpolatedDistanceTransformMask(
                distanceTransformPair[0],
                distanceTransformPair[1],
                initialDistanceBetweenSlices,
                UnsignedLongType(1),
                transformToSource
            )

            //TODO just use a converter here?
            val volatileInterpolatedShapeMask = getInterpolatedDistanceTransformMask(
                distanceTransformPair[0],
                distanceTransformPair[1],
                initialDistanceBetweenSlices,
                VolatileUnsignedLongType(1),
                transformToSource
            )

            val sourceToGlobalTransform = sliceInfoPair[0].mask.initialSourceToGlobalTransform

            return if (Thread.currentThread().isInterrupted) null else InterpolantInfo(interpolatedShapeMask, volatileInterpolatedShapeMask, sourceToGlobalTransform)
        }

        private fun <R, B : BooleanType<B>> computeSignedDistanceTransform(
            mask: RandomAccessibleInterval<B>,
            target: RandomAccessibleInterval<R>,
            distanceType: DISTANCE_TYPE,
            vararg weights: Double
        ) where R : RealType<R>?, R : NativeType<R>? {
            val distanceInside: RandomAccessibleInterval<R> = ArrayImgFactory(Util.getTypeFromInterval(target)).create(target)
            DistanceTransform.binaryTransform(mask, target, distanceType, *weights)
            DistanceTransform.binaryTransform(Logical.complement(mask), distanceInside, distanceType, *weights)
            LoopBuilder.setImages(target, distanceInside, target).forEachPixel(LoopBuilder.TriConsumer { outside: R, inside: R, result: R ->
                when (distanceType) {
                    DISTANCE_TYPE.EUCLIDIAN -> result!!.setReal(sqrt(outside!!.realDouble) - sqrt(inside!!.realDouble))
                    DISTANCE_TYPE.L1 -> result!!.setReal(outside!!.realDouble - inside!!.realDouble)
                }
            })
        }

        private fun <R : RealType<R>, T> getInterpolatedDistanceTransformMask(
            dt1: RandomAccessibleInterval<R>,
            dt2: RandomAccessibleInterval<R>,
            distance: Double,
            targetValue: T,
            transformToSource: AffineTransform3D
        ): RealRandomAccessible<T> where T : NativeType<T>, T : RealType<T> {
            val extendValue = Util.getTypeFromInterval(dt1)!!.createVariable()
            extendValue!!.setReal(extendValue.maxValue)
            val union = dt1 union dt2
            val distanceTransformStack = Views.stack(
                Views.interval(Views.extendValue(dt1, extendValue), union),
                Views.interval(Views.extendValue(dt2, extendValue), union)
            )

            val extendedDistanceTransformStack = Views.extendValue(distanceTransformStack, extendValue)
            val interpolatedDistanceTransform = Views.interpolate(
                extendedDistanceTransformStack,
                NLinearInterpolatorFactory()
            )

            val min = distanceTransformStack.minAsLongArray()
            val max = distanceTransformStack.maxAsLongArray()
            val stackBounds = FinalInterval(min, max)
            val distanceScale = AffineTransform3D().also {
                it.scale(1.0, 1.0, distance)
            }
            val scaledStackBounds = distanceScale.estimateBounds(stackBounds)

            val scaledInterpolatedDistanceTransform: RealRandomAccessible<R> = RealViews.affineReal(
                interpolatedDistanceTransform,
                distanceScale
            )
            val emptyValue = targetValue.createVariable()

            val interpolatedShape: RealRandomAccessible<T> = Converters.convert(
                scaledInterpolatedDistanceTransform,
                { input: R, output: T -> output.set(if (input.realDouble <= 0) targetValue else emptyValue) },
                targetValue.createVariable()
            )

            val interpolatedShapeRaiInSource = FinalRealRandomAccessibleRealInterval(RealViews.affineReal(interpolatedShape, transformToSource), transformToSource.estimateBounds(scaledStackBounds))

            return ExtendedRealRandomAccessibleRealInterval(interpolatedShapeRaiInSource, RealOutOfBoundsConstantValueFactory(emptyValue))
        }

        private fun computeDistanceBetweenSlices(s1: SliceInfo, s2: SliceInfo): Double {

            val s1OriginInCurrent = DoubleArray(3)
            val s2OriginInCurrentViaS1 = DoubleArray(3)

            s1.mask.initialToCurrentMaskTransform.apply(s1OriginInCurrent, s1OriginInCurrent)

            val s2InitialTo1InitialTransform = s1.mask.initialGlobalToMaskTransform.copy().concatenate(s2.mask.initialGlobalToMaskTransform.inverse())
            s2InitialTo1InitialTransform.apply(s2OriginInCurrentViaS1, s2OriginInCurrentViaS1)
            s1.mask.initialToCurrentMaskTransform.apply(s2OriginInCurrentViaS1, s2OriginInCurrentViaS1)

            return s2OriginInCurrentViaS1[2] - s1OriginInCurrent[2]
        }
    }

    private class SliceOrInterpolant {
        private val sliceAndDepth: Pair<Double, SliceInfo>?
        private val interpolant: InterpolantInfo?

        constructor(depth: Double, slice: SliceInfo) {
            sliceAndDepth = ValuePair(depth, slice)
            interpolant = null
        }

        constructor(interpolant: InterpolantInfo?) {
            sliceAndDepth = null
            this.interpolant = interpolant
        }

        val isSlice: Boolean
            get() = sliceAndDepth != null

        fun isInterpolant(): Boolean {
            return interpolant != null
        }

        fun getSlice(): SliceInfo {
            return sliceAndDepth!!.b
        }

        val sliceDepth: Double
            get() = sliceAndDepth!!.a

        fun getInterpolant(): InterpolantInfo? {
            return interpolant
        }

        override fun equals(other: Any?): Boolean {
            return equalsSlice(other) || equalsInterpolant(other)
        }

        private fun equalsSlice(other: Any?): Boolean {
            return isSlice && getSlice() == other
        }

        private fun equalsInterpolant(other: Any?): Boolean {
            return isInterpolant() && getInterpolant() == other
        }

        override fun hashCode(): Int {
            var result = sliceAndDepth?.hashCode() ?: 0
            result = 31 * result + (interpolant?.hashCode() ?: 0)
            result = 31 * result + isSlice.hashCode()
            result = 31 * result + sliceDepth.hashCode()
            return result
        }
    }

    private class SlicesAndInterpolants : ObservableList<SliceOrInterpolant> by FXCollections.observableArrayList() {

        fun removeSlice(slice: SliceInfo): Boolean {
            for (idx in indices) {
                if (idx >= 0 && idx <= size - 1 && get(idx).equals(slice)) {
                    removeIfInterpolant(idx + 1)
                    LOG.trace("Removing Slice: $idx")
                    removeAt(idx).getSlice()
                    removeIfInterpolant(idx - 1)
                    return true
                }
            }
            return false
        }

        fun removeSliceAtDepth(depth: Double): SliceInfo? {
            return getSliceAtDepth(depth)?.also {
                removeSlice(it)
            }
        }

        fun removeIfInterpolant(idx: Int): InterpolantInfo? {
            return if (idx >= 0 && idx <= size - 1 && get(idx).isInterpolant()) {
                LOG.trace("Removing Interpolant: $idx")
                removeAt(idx).getInterpolant()
            } else null
        }

        fun add(depth: Double, slice: SliceInfo) {
            for (idx in this.indices) {
                if (get(idx).isSlice && get(idx).sliceDepth > depth) {
                    LOG.trace("Adding Slice: $idx")
                    add(idx, SliceOrInterpolant(depth, slice))
                    removeIfInterpolant(idx - 1)
                    return
                }
            }
            LOG.trace("Adding Slice: ${this.size}")
            add(SliceOrInterpolant(depth, slice))
        }

        fun add(idx: Int, interpolant: InterpolantInfo?) {
            LOG.trace("Adding Interpolant: $idx")
            add(idx, SliceOrInterpolant(interpolant))
        }

        fun removeAllInterpolants() {
            for (i in size - 1 downTo 0) {
                removeIfInterpolant(i)
            }
        }

        fun getSliceAtDepth(depth: Double): SliceInfo? {
            for (sliceOrInterpolant in this) {
                if (sliceOrInterpolant.isSlice && sliceOrInterpolant.sliceDepth == depth) {
                    return sliceOrInterpolant.getSlice()
                }
            }
            return null
        }

        fun getPreviousSlice(depth: Double): SliceInfo? {
            var prevSlice: SliceInfo? = null
            for (sliceOrInterpolant in this) {
                if (sliceOrInterpolant.isSlice) {
                    prevSlice = if (sliceOrInterpolant.sliceDepth < depth) {
                        sliceOrInterpolant.getSlice()
                    } else {
                        break
                    }
                }
            }
            return prevSlice
        }

        fun getNextSlice(depth: Double): SliceInfo? {
            for (sliceOrInterpolant in this) {
                if (sliceOrInterpolant.isSlice && sliceOrInterpolant.sliceDepth > depth) {
                    return sliceOrInterpolant.getSlice()
                }
            }
            return null
        }

        fun getInterpolantsAround(slice: SliceInfo?): Pair<InterpolantInfo?, InterpolantInfo?> {
            var interpolantAfter: InterpolantInfo? = null
            var interpolantBefore: InterpolantInfo? = null
            for (idx in 0 until size - 1) {
                if (get(idx).equals(slice)) {
                    if (get(idx + 1).isInterpolant()) {
                        interpolantAfter = get(idx + 1).getInterpolant()
                        if (idx - 1 >= 0 && get(idx - 1).isInterpolant()) {
                            interpolantBefore = get(idx - 1).getInterpolant()
                        }
                    }
                    break
                }
            }
            return ValuePair(interpolantBefore, interpolantAfter)
        }

        val slices: List<SliceInfo>
            get() = stream()
                .filter { it.isSlice }
                .map { it.getSlice() }
                .collect(Collectors.toList())
        val interpolants: List<InterpolantInfo>
            get() = stream()
                .filter { it.isInterpolant() }
                .map { it.getInterpolant()!! }
                .collect(Collectors.toList())

        fun clearInterpolantsAroundSlice(z: Double) {
            for (idx in this.indices) {
                if (get(idx).isSlice && get(idx).sliceDepth == z) {
                    removeIfInterpolant(idx + 1)
                    removeIfInterpolant(idx - 1)
                    return
                }
            }
        }

        fun removeIfInterpolantAt(depthInMaskDisplay: Double) {
            for (idx in this.indices) {
                if (get(idx).isSlice && get(idx).sliceDepth > depthInMaskDisplay) {
                    removeIfInterpolant(idx - 1)
                    return
                }
            }
        }
    }

    var initialGlobalToViewerTransform: AffineTransform3D? = null
    val previewProperty = SimpleBooleanProperty(true)
    var preview by previewProperty.nonnull()

    class InterpolantInfo(
        val dataInterpolant: RealRandomAccessible<UnsignedLongType>,
        val volatileInterpolant: RealRandomAccessible<VolatileUnsignedLongType>,
        val sourceToGlobalTransform: AffineTransform3D
    )

    class SliceInfo(
        var mask: ViewerMask,
        val globalTransform: AffineTransform3D,
        selectionInterval: Interval
    ) {
        internal val maskBoundingBox: Interval get() = computeBoundingBoxInInitialMask()
        internal val sourceBoundingBox: RealInterval get() = mask.initialMaskToSourceTransform.estimateBounds(maskBoundingBox)
        internal val globalBoundingBox: RealInterval get() = mask.source.getSourceTransformForMask(mask.info).estimateBounds(sourceBoundingBox.smallestContainingInterval)

        val selectionIntervalsInInitialSpace: MutableList<Interval> = mutableListOf()

        val selectionIntervals: List<RealInterval>
            get() = selectionIntervalsInInitialSpace.map { mask.initialToCurrentMaskTransform.estimateBounds(it) }.toList()

        init {
            addSelection(selectionInterval)
        }

        private fun computeBoundingBoxInInitialMask(): Interval {
            return selectionIntervalsInInitialSpace.reduce { l, r -> l union r }
        }

        fun addSelection(selectionInterval: Interval) {
            selectionIntervalsInInitialSpace.add(selectionInterval)
        }
    }
}
