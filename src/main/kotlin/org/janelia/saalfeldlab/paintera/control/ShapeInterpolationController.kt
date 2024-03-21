package org.janelia.saalfeldlab.paintera.control

import bdv.fx.viewer.ViewerPanelFX
import bdv.viewer.TransformListener
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
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
import net.imglib2.converter.BiConverter
import net.imglib2.converter.Converters
import net.imglib2.converter.logical.Logical
import net.imglib2.converter.read.BiConvertedRealRandomAccessible
import net.imglib2.img.array.ArrayImgFactory
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.loops.LoopBuilder
import net.imglib2.outofbounds.RealOutOfBoundsConstantValueFactory
import net.imglib2.realtransform.AffineTransform3D
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
import net.imglib2.util.*
import net.imglib2.view.ExtendedRealRandomAccessibleRealInterval
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.createViewerMask
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.math.sqrt

class ShapeInterpolationController<D : IntegerType<D>>(
	val source: MaskedSource<D, *>,
	private val refreshMeshes: () -> Unit,
	val selectedIds: SelectedIds,
	val idService: IdService,
	val converter: HighlightingStreamConverter<*>,
	private val assignment: FragmentSegmentAssignment
) {
	enum class ControllerState {
		Select, Interpolate, Preview, Off, Moving
	}

	private var lastSelectedId: Long = 0

	internal var interpolationId: Long = Label.INVALID

	private val slicesAndInterpolants = SlicesAndInterpolants()

	val sliceDepthProperty = SimpleDoubleProperty(0.0)
	private var sliceDepth: Double by sliceDepthProperty.nonnull()

	val isBusyProperty = SimpleBooleanProperty(false, "Shape Interpolation Controller is Busy")
	private var isBusy: Boolean by isBusyProperty.nonnull()

	val controllerStateProperty: ObjectProperty<ControllerState> = SimpleObjectProperty(ControllerState.Off)
	var controllerState: ControllerState by controllerStateProperty.nonnull()
	val isControllerActive: Boolean
		get() = controllerState != ControllerState.Off

	private val sliceAtCurrentDepthBinding = sliceDepthProperty.createNonNullValueBinding(slicesAndInterpolants) { slicesAndInterpolants.getSliceAtDepth(it.toDouble()) }
	private val sliceAtCurrentDepth by sliceAtCurrentDepthBinding.nullableVal()

	val currentSliceMaskInterval get() = sliceAtCurrentDepth?.maskBoundingBox

	val numSlices: Int get() = slicesAndInterpolants.slices.size

	var activeSelectionAlpha = (AbstractHighlightingARGBStream.DEFAULT_ACTIVE_FRAGMENT_ALPHA ushr 24) / 255.0

	private var activeViewer: ViewerPanelFX? = null

	private val currentViewerMaskProperty: ObjectProperty<ViewerMask?> = SimpleObjectProperty(null)
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


	internal val currentBestMipMapLevel: Int
		get() {
			val viewerState = activeViewer!!.state
			val screenScaleTransform = AffineTransform3D()
			activeViewer!!.renderUnit.getScreenScaleTransform(0, screenScaleTransform)
			return viewerState.getBestMipMapLevel(screenScaleTransform, source)
		}


	private val currentDepth: Double by LazyForeignValue(this::globalToViewerTransform) { depthAt(it) }

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

	private var globalCompositeFillAndInterpolationImgs: Pair<RealRandomAccessible<UnsignedLongType>, RealRandomAccessible<VolatileUnsignedLongType>>? = null

	private val viewerTransformDepthUpdater = TransformListener<AffineTransform3D> {
		updateDepth()
		sliceAtCurrentDepth?.mask.let {
			currentViewerMask = it
		}
	}

	private fun depthAt(globalToViewer: AffineTransform3D): Double {
		val currentViewerInInitialViewer = DoubleArray(3).also {
			initialGlobalToViewerTransform!!.copy().concatenate(globalToViewer.inverse()).apply(it, it)
		}
		return BigDecimal(currentViewerInInitialViewer[2]).setScale(5, RoundingMode.HALF_EVEN).toDouble()
	}


	fun paint(maskPaintInterval: Interval, currentGlobalTransform: AffineTransform3D = paintera().manager().transform) {
		if (controllerState == ControllerState.Off) {
			return
		}
		isBusy = true
		addSelection(maskPaintInterval, globalToSelectionTransform = currentGlobalTransform)
	}

	/**
	 * Delete current slice or interpolant
	 *
	 * @return the global interval of the mask just removed (useful for repainting after removing)
	 */
	fun deleteCurrentSliceOrInterpolant(): RealInterval? {
		slicesAndInterpolants.removeIfInterpolantAt(currentDepth)
		return slicesAndInterpolants.removeSliceAtDepth(currentDepth)?.globalBoundingBox
	}

	fun deleteCurrentSlice() {
		slicesAndInterpolants.removeSliceAtDepth(currentDepth)?.let { slice ->
			isBusy = true
			interpolateBetweenSlices(true)
			val adjacentRepaintInterval = listOf(
				slicesAndInterpolants.getPreviousSlice(currentDepth),
				slicesAndInterpolants.getNextSlice(currentDepth)
			).mapNotNull { it?.globalBoundingBox }
				.ifEmpty { null }
				?.reduce { l, r -> l union r }

			requestRepaintAfterTasks(slice.globalBoundingBox union adjacentRepaintInterval)
		}
	}

	@JvmOverloads
	fun addSelection(
		maskIntervalOverSelection: Interval,
		keepInterpolation: Boolean = true,
		globalToSelectionTransform: AffineTransform3D,
		viewerMask: ViewerMask = currentViewerMask!!
	) {

		val selectionDepth = depthAt(globalToSelectionTransform)
		if (!keepInterpolation && slicesAndInterpolants.getSliceAtDepth(selectionDepth) != null) {
			slicesAndInterpolants.removeSliceAtDepth(selectionDepth)
			updateFillAndInterpolantsCompositeMask()
			val slice = SliceInfo(viewerMask, globalToSelectionTransform, maskIntervalOverSelection)
			slicesAndInterpolants.add(selectionDepth, slice)
		}
		if (slicesAndInterpolants.getSliceAtDepth(selectionDepth) == null) {
			val slice = SliceInfo(viewerMask, globalToSelectionTransform, maskIntervalOverSelection)
			slicesAndInterpolants.add(selectionDepth, slice)
		} else {
			slicesAndInterpolants.getSliceAtDepth(selectionDepth)!!.addSelection(maskIntervalOverSelection)
			slicesAndInterpolants.clearInterpolantsAroundSlice(selectionDepth)
		}
		interpolateBetweenSlices(true)
	}

	internal fun getSliceAt(depth: Double) = slicesAndInterpolants.getSliceAtDepth(depth)
	internal fun getSliceAt(globalToSelection: AffineTransform3D) = slicesAndInterpolants.getSliceAtDepth(depthAt(globalToSelection))
	internal fun removeSlice(slice: SliceInfo) : Boolean = slicesAndInterpolants.removeSlice(slice)


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

		// extra cleanup if shape interpolation was aborted
		if (!completed) {
			interruptInterpolation()
			source.resetMasks(true)
		}


		/* Reset the selection state */
		converter.removeColor(lastSelectedId)
		converter.removeColor(interpolationId)
		selectedIds.deactivate(interpolationId)
		selectedIds.activateAlso(lastSelectedId)
		controllerState = ControllerState.Off
		slicesAndInterpolants.clear()
		sliceDepth = 0.0
		currentViewerMask = null
		interpolator = null
		globalCompositeFillAndInterpolationImgs = null
		lastSelectedId = Label.INVALID
		interpolationId = Label.INVALID

		activeViewer!!.removeTransformListener(viewerTransformDepthUpdater)
		activeViewer = null
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
		interpolator = Tasks.createTask { task ->
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
							val interpolatedImgs = interpolateBetweenTwoSlices(slice1, slice2, interpolationId)
							slicesAndInterpolants.add(idx, interpolatedImgs)
							updateInterval = slice1.globalBoundingBox union slice2.globalBoundingBox union updateInterval
						}
					}
				}
				setInterpolatedMasks()
				requestRepaintAfterTasks(updateInterval)
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
			EditSelectionChoice.Previous -> slicesAndInterpolants.getPreviousSlice(currentDepth)
				?: slices.getOrNull(0) /* move to previous, or first if none */
			EditSelectionChoice.Next -> slicesAndInterpolants.getNextSlice(currentDepth)
				?: slices.getOrNull(slices.size - 1) /* move to next, or last if none */
			EditSelectionChoice.Last -> slices.getOrNull(slices.size - 1) /* move to last */
		}?.let { slice ->
			selectAndMoveToSlice(slice)
		}
	}

	private fun selectAndMoveToSlice(sliceInfo: SliceInfo) {
		controllerState = ControllerState.Moving
		InvokeOnJavaFXApplicationThread {
			val sliceGlobalTransform = sliceInfo.globalTransform
			paintera().manager().apply {
				setTransform(sliceGlobalTransform, Duration(300.0)) {
					transform = sliceGlobalTransform
					updateDepth()
					controllerState = ControllerState.Select
				}
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

		val globalToSource = source.getSourceTransformForMask(source.currentMask.info).inverse()
		val slicesUnionSourceInterval = slicesAndInterpolants.stream()
			.filter(SliceOrInterpolant::isSlice)
			.map(SliceOrInterpolant::getSlice)
			.map(SliceInfo::globalBoundingBox)
			.map { globalToSource.estimateBounds(it) }
			.reduce(Intervals::union)
			.map { it.smallestContainingInterval }
			.get()

		LOG.trace("Applying interpolated mask using bounding box of size {}", Intervals.dimensionsAsLongArray(slicesUnionSourceInterval))

		val finalLastSelectedId = lastSelectedId
		val finalInterpolationId = interpolationId
		if (Label.regular(finalLastSelectedId)) {
			val maskInfo = source.currentMask.info
			source.resetMasks(false)
			val interpolatedMaskImgsA = Converters.convert(
				globalCompositeFillAndInterpolationImgs!!.a.affineReal(globalToSource),
				{ input: UnsignedLongType, output: UnsignedLongType ->
					val originalLabel = input.long
					val label = if (originalLabel == finalInterpolationId) {
						finalLastSelectedId
					} else input.get()
					output.set(label)
				},
				UnsignedLongType(Label.INVALID)
			)
			val interpolatedMaskImgsB = Converters.convert(
				globalCompositeFillAndInterpolationImgs!!.b.affineReal(globalToSource),
				{ input: VolatileUnsignedLongType, out: VolatileUnsignedLongType ->
					val isValid = input.isValid
					out.isValid = isValid
					if (isValid) {
						val originalLabel = input.get().get()
						val label = if (originalLabel == finalInterpolationId) finalLastSelectedId else input.get().get()
						out.get().set(label)
					}
				},
				VolatileUnsignedLongType(Label.INVALID)
			)
			source.setMask(
				maskInfo,
				interpolatedMaskImgsA,
				interpolatedMaskImgsB,
				null, null, null, MaskedSource.VALID_LABEL_CHECK
			)

		}
		source.isApplyingMaskProperty.addListener(doneApplyingMaskListener)
		source.applyMask(source.currentMask, slicesUnionSourceInterval, MaskedSource.VALID_LABEL_CHECK)
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
		interpolationId = idService.nextTemporary()
		converter.setColor(interpolationId, fillLabelColor, true)
		selectedIds.activateAlso(interpolationId, lastSelectedId)
	}

	private fun doneApplyingMask() {
		source.isApplyingMaskProperty.removeListener(doneApplyingMaskListener)
		// generate mesh for the interpolated shape
		refreshMeshes()
	}


	@Throws(MaskInUse::class)
	private fun setInterpolatedMasks() {
		synchronized(source) {
			source.resetMasks(false)
			/* If preview is on, hide all except the first and last fill mask */
			val fillMasks: MutableList<RealRandomAccessible<UnsignedLongType>> = mutableListOf()
			val slices = slicesAndInterpolants.slices
			slices.forEachIndexed { idx, slice ->
				if (idx == 0 || idx == slices.size - 1 || !preview) {
					fillMasks += slice.mask.run {
						viewerImg
							.expandborder(0, 0, 1)
							.extendValue(Label.INVALID)
							.interpolateNearestNeighbor()
							.affineReal(initialGlobalToMaskTransform.inverse())
					}


				}
			}
			val constantInvalid = ConstantUtils.constantRandomAccessible(UnsignedLongType(Label.INVALID), 3).interpolateNearestNeighbor()
			if (fillMasks.isEmpty()) {
				fillMasks += constantInvalid
			}

			var compositeFill: RealRandomAccessible<UnsignedLongType> = fillMasks[0]
			for ((index, dataMask) in fillMasks.withIndex()) {
				if (index == 0) continue

				compositeFill = compositeFill.convertWith(dataMask, UnsignedLongType(Label.INVALID)) { composite, mask, result ->
					val maskVal = mask.get()
					result.setInteger(if (maskVal != Label.INVALID) maskVal else composite.get())
				}
			}

			val interpolants = slicesAndInterpolants.interpolants
			val dataMasks: MutableList<RealRandomAccessible<UnsignedLongType>> = mutableListOf()

			if (preview) {
				interpolants.forEach { info ->
					dataMasks += info.dataInterpolant
				}
			}

			var compositeInterpolation: RealRandomAccessible<UnsignedLongType> = dataMasks.getOrNull(0) ?: ConstantUtils.constantRealRandomAccessible(UnsignedLongType(Label.INVALID), compositeFill.numDimensions())
			for ((index, dataMask) in dataMasks.withIndex()) {
				if (index == 0) continue

				compositeInterpolation = compositeInterpolation.convertWith(dataMask, UnsignedLongType(Label.INVALID)) { composite, mask, result ->
					val maskVal = mask.get()
					result.setInteger(if (maskVal != Label.INVALID) maskVal else composite.get())
				}
			}

			val compositeMaskInGlobal = BiConvertedRealRandomAccessible(compositeFill, compositeInterpolation, Supplier {
				BiConverter { fillValue: UnsignedLongType, interpolationValue: UnsignedLongType, compositeValue: UnsignedLongType ->
					val aVal = fillValue.get()
					val aOrB = if (aVal.isInterpolationLabel) fillValue else interpolationValue
					compositeValue.set(aOrB)
				}
			}) { UnsignedLongType(Label.INVALID) }
			val compositeVolatileMaskInGlobal = compositeMaskInGlobal.convert(VolatileUnsignedLongType(Label.INVALID)) { source, target ->
				target.get().set(source.get())
				target.isValid = true
			}

			/* Set the interpolatedMaskImgs to the composite fill+interpolation RAIs*/
			globalCompositeFillAndInterpolationImgs = ValuePair(compositeMaskInGlobal, compositeVolatileMaskInGlobal)
			val maskInfo = currentViewerMask?.info ?: MaskInfo(0, currentBestMipMapLevel)
			currentViewerMask?.setMaskOnUpdate = false
			val globalToSource = AffineTransform3D().also { source.getSourceTransform(0, currentBestMipMapLevel, it) }.inverse()
			source.setMask(
				maskInfo,
				compositeMaskInGlobal.affine(globalToSource),
				compositeVolatileMaskInGlobal.affine(globalToSource),
				null,
				null,
				null,
				MaskedSource.VALID_LABEL_CHECK
			)
		}
	}

	private fun requestRepaintAfterTasks(unionWith: RealInterval? = null, force: Boolean = false) {
		fun Task<*>?.notCompleted() = this?.state?.let { it in listOf(Worker.State.READY, Worker.State.SCHEDULED, Worker.State.RUNNING) }
			?: false
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
				val extendedSourceInterval = IntervalHelpers.extendAndTransformBoundingBox(it.smallestContainingInterval, sourceToGlobal.inverse(), 1.0)
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

	internal val sourceToGlobalTransform: AffineTransform3D
		get() = AffineTransform3D().also {
			source.getSourceTransform(
				activeViewer!!.state.timepoint,
				currentBestMipMapLevel,
				it
			)
		}
	private val globalToViewerTransform: AffineTransform3D get() = AffineTransform3D().also { activeViewer!!.state.getViewerTransform(it) }

	fun getMask(targetMipMapLevel: Int = currentBestMipMapLevel): ViewerMask {

		/* If we have a mask, get it; else create a new one */
		currentViewerMask = sliceAtCurrentDepth?.let { oldSlice ->
			val oldMask = oldSlice.mask

			if (oldMask.xScaleChange == 1.0) return@let oldMask

			val maskInfo = MaskInfo(0, targetMipMapLevel)
			val newMask = source.createViewerMask(maskInfo, activeViewer!!, paintDepth = null, setMask = false)

			val oldToNewMask = ViewerMask.maskToMaskTransformation(oldMask, newMask)

			val oldSliceBoundingBox = oldSlice.maskBoundingBox
			val oldIntervalInNew = oldToNewMask.estimateBounds(oldSliceBoundingBox)

			val oldInNew = oldMask.viewerImg.wrappedSource
				.interpolateNearestNeighbor()
				.affine(oldToNewMask)
				.interval(oldIntervalInNew)

			val oldInNewVolatile = oldMask.volatileViewerImg.wrappedSource
				.interpolateNearestNeighbor()
				.affine(oldToNewMask)
				.interval(oldIntervalInNew)


			/* We want to use the old mask as the backing mask, and have a new writable one on top.
			* So let's re-use the images this mask created, and replace them with the old mask images (transformed) */
			val newImg = newMask.viewerImg.wrappedSource
			val newVolatileImg = newMask.volatileViewerImg.wrappedSource

			newMask.viewerImg.wrappedSource = oldInNew
			newMask.volatileViewerImg.wrappedSource = oldInNewVolatile

			/* then we push the `newMask` back in front, as a writable layer */
			newMask.pushNewImageLayer(newImg to newVolatileImg)

			/* Replace old slice info */
			slicesAndInterpolants.removeSlice(oldSlice)

			val newSlice = SliceInfo(
				newMask,
				paintera().manager().transform,
				FinalRealInterval(
					oldIntervalInNew.minAsDoubleArray().also { it[2] = 0.0 },
					oldIntervalInNew.maxAsDoubleArray().also { it[2] = 0.0 }
				)
			)
			slicesAndInterpolants.add(currentDepth, newSlice)
			newMask
		} ?: let {
			val maskInfo = MaskInfo(0, targetMipMapLevel)
			source.createViewerMask(maskInfo, activeViewer!!, paintDepth = null, setMask = false)
		}
		currentViewerMask?.setViewerMaskOnSource()

		if (preview && slicesAndInterpolants.getInterpolantAtDepth(currentDepth) != null)
			copyInterpolationToMask()

		return currentViewerMask!!
	}

	private fun copyInterpolationToMask(viewerMask: ViewerMask = currentViewerMask!!, globalTransform: AffineTransform3D = paintera().manager().transform) {
		val maskDepth = depthAt(viewerMask.currentGlobalToViewerTransform)
		val nextSlice = slicesAndInterpolants.getNextSlice(maskDepth) ?: let {
			logger.warn { "No following slice, nothing to copy" }
			return
		}
		val prevSlice = slicesAndInterpolants.getPreviousSlice(maskDepth) ?: let {
			logger.warn { "No previous slice, nothing to copy" }
			return
		}
		val interpolant = slicesAndInterpolants.getInterpolantsAround(prevSlice).b ?: let {
			logger.warn { "No Interpolant to copy" }
			return
		}

		/* get union of adjacent slices bounding boxes */
		val globalIntervalOverAdjacentSlices = Intervals.union(prevSlice.globalBoundingBox, nextSlice.globalBoundingBox)
		val adjacentSelectionIntervalInMaskSpace = viewerMask.currentGlobalToMaskTransform.estimateBounds(globalIntervalOverAdjacentSlices)

		val minZSlice = adjacentSelectionIntervalInMaskSpace.minAsDoubleArray().also { it[2] = 0.0 }
		val maxZSlice = adjacentSelectionIntervalInMaskSpace.maxAsDoubleArray().also { it[2] = 0.0 }
		val unionAdjacentSlicesInMaskSpace = FinalRealInterval(minZSlice, maxZSlice)


		val interpolatedMaskView = interpolant.dataInterpolant
			.affine(currentViewerMask!!.currentGlobalToMaskTransform)
			.interval(unionAdjacentSlicesInMaskSpace)
		val fillMaskOverInterval = viewerMask.viewerImg.interval(unionAdjacentSlicesInMaskSpace)

		LoopBuilder.setImages(interpolatedMaskView, fillMaskOverInterval)
			.multiThreaded()
			.forEachPixel { interpolationType, fillMaskType ->
				val fillMaskVal = fillMaskType.get()
				val interpolationVal = interpolationType.get()
				if (!fillMaskVal.isInterpolationLabel && interpolationVal.isInterpolationLabel) {
					fillMaskType.set(interpolationVal)
				}
			}
		val slice = SliceInfo(
			viewerMask,
			globalTransform,
			unionAdjacentSlicesInMaskSpace.smallestContainingInterval
		)
		/* remove the old interpolant*/
		slicesAndInterpolants.removeIfInterpolantAt(currentDepth)
		/* add the new slice */
		slicesAndInterpolants.add(currentDepth, slice)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
		private val logger = KotlinLogging.logger { }
		private fun paintera(): PainteraBaseView = Paintera.getPaintera().baseView

		private val Long.isInterpolationLabel
			get() = this.toULong() < Label.MAX_ID.toULong()

		private fun interpolateBetweenTwoSlices(
			slice1: SliceInfo,
			slice2: SliceInfo,
			fillValue: Long
		): InterpolantInfo? {
			val sliceInfoPair = arrayOf(slice1, slice2)

			// get the two slices as 2D images
			val slices: Array<RandomAccessibleInterval<UnsignedLongType>?> = arrayOfNulls(2)

			val slice2InitialToSlice1Initial = ViewerMask.maskToMaskTransformation(slice2.mask, slice1.mask)

			val slice1InInitial = slice1.maskBoundingBox
			val slice2InSlice1Initial = slice2InitialToSlice1Initial.estimateBounds(slice2.maskBoundingBox)

			val realUnionInSlice1Initial = slice1InInitial union slice2InSlice1Initial
			val unionInSlice1Initial = realUnionInSlice1Initial.smallestContainingInterval

			slices[0] = slice1.mask.viewerImg
				.extendValue(Label.INVALID)
				.interval(unionInSlice1Initial)
				.hyperSlice()
				.zeroMin()

			val xyUnionInSlice1AtSlice2Depth = FinalInterval(
				unionInSlice1Initial.minAsLongArray().also { it[2] = 0 },
				unionInSlice1Initial.maxAsLongArray().also { it[2] = 0 }
			)

			val slice2InitialInSlice1InitialAtSlice2Depth = slice2InitialToSlice1Initial.copy().preConcatenate(Translation3D(0.0, 0.0, realUnionInSlice1Initial.realMax(2)).inverse())

			slices[1] = slice2.mask.viewerImg
				.extendValue(Label.INVALID)
				.interpolateNearestNeighbor()
				.affine(slice2InitialInSlice1InitialAtSlice2Depth)
				.interval(xyUnionInSlice1AtSlice2Depth)
				.hyperSlice()
				.zeroMin()

			// compute distance transform on both slices
			val distanceTransformPair: MutableList<RandomAccessibleInterval<FloatType>> = mutableListOf()
			for (i in 0..1) {
				if (Thread.currentThread().isInterrupted) return null
				val distanceTransform = ArrayImgFactory(FloatType()).create(slices[i]).also {
					val binarySlice = Converters.convert(slices[i], { source, target -> target.set(source.get().isInterpolationLabel) }, BoolType())
					computeSignedDistanceTransform(binarySlice, it, DistanceTransform.DISTANCE_TYPE.EUCLIDIAN)
				}
				distanceTransformPair.add(distanceTransform)
			}
			val distanceBetweenSlices = computeDistanceBetweenSlices(sliceInfoPair[0], sliceInfoPair[1])

			val transformToGlobal = AffineTransform3D()
			transformToGlobal
				.concatenate(sliceInfoPair[0].mask.sourceToGlobalTransform)
				.concatenate(sliceInfoPair[0].mask.initialMaskToSourceTransform)
				.concatenate(Translation3D(unionInSlice1Initial.realMin(0), unionInSlice1Initial.realMin(1), 0.0))

			val interpolatedShapeMask = getInterpolatedDistanceTransformMask(
				distanceTransformPair[0],
				distanceTransformPair[1],
				distanceBetweenSlices,
				UnsignedLongType(fillValue),
				transformToGlobal,
				UnsignedLongType(Label.INVALID)
			)

			return if (Thread.currentThread().isInterrupted) null else InterpolantInfo(interpolatedShapeMask)
		}

		private fun <R, B : BooleanType<B>> computeSignedDistanceTransform(
			mask: RandomAccessibleInterval<B>,
			target: RandomAccessibleInterval<R>,
			distanceType: DistanceTransform.DISTANCE_TYPE,
			vararg weights: Double
		) where R : RealType<R>?, R : NativeType<R>? {
			val distanceInside: RandomAccessibleInterval<R> = ArrayImgFactory(Util.getTypeFromInterval(target)).create(target)
			DistanceTransform.binaryTransform(mask, target, distanceType, *weights)
			DistanceTransform.binaryTransform(Logical.complement(mask), distanceInside, distanceType, *weights)
			LoopBuilder.setImages(target, distanceInside).multiThreaded().forEachPixel { result, inside ->
				when (distanceType) {
					DistanceTransform.DISTANCE_TYPE.EUCLIDIAN -> result!!.setReal(sqrt(result.realDouble) - sqrt(inside!!.realDouble))
					DistanceTransform.DISTANCE_TYPE.L1 -> result!!.setReal(result.realDouble - inside!!.realDouble)
				}
			}
		}

		private fun <R : RealType<R>, T> getInterpolatedDistanceTransformMask(
			dt1: RandomAccessibleInterval<R>,
			dt2: RandomAccessibleInterval<R>,
			distance: Double,
			targetValue: T,
			transformToSource: AffineTransform3D,
			invalidValue: T
		): RealRandomAccessible<T> where T : NativeType<T>, T : RealType<T> {
			val extendValue = Util.getTypeFromInterval(dt1)!!.createVariable()
			extendValue!!.setReal(extendValue.maxValue)

			val union = dt1 union dt2
			val distanceTransformStack = Views.stack(
				dt1.extendValue(extendValue).interval(union),
				dt2.extendValue(extendValue).interval(union)
			)

			val distanceScale = AffineTransform3D().also {
				it.scale(1.0, 1.0, distance)
			}

			val scaledInterpolatedDistanceTransform = distanceTransformStack
				.extendValue(extendValue)
				.interpolate(NLinearInterpolatorFactory())
				.affineReal(distanceScale)

			val interpolatedShapeRaiInSource = Converters.convert(
				scaledInterpolatedDistanceTransform,
				{ input: R, output: T -> output.set(if (input.realDouble <= 0) targetValue else invalidValue) },
				targetValue.createVariable()
			)
				.affineReal(transformToSource)
				.realInterval(transformToSource.copy().concatenate(distanceScale).estimateBounds(distanceTransformStack))

			return ExtendedRealRandomAccessibleRealInterval(interpolatedShapeRaiInSource, RealOutOfBoundsConstantValueFactory(invalidValue))
		}

		private fun computeDistanceBetweenSlices(s1: SliceInfo, s2: SliceInfo) = ViewerMask.maskToMaskTransformation(s2.mask, s1.mask).translation[2]
	}

	fun getGlobalBoundingBoxAtDepth(globalToSelectionTransform: AffineTransform3D): RealInterval? {
		return slicesAndInterpolants.getGlobalIntervalAtDepth(depthAt(globalToSelectionTransform))
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

		val isInterpolant: Boolean
			get() = interpolant != null


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
			return isInterpolant && getInterpolant() == other
		}

		override fun hashCode(): Int {
			var result = sliceAndDepth?.hashCode() ?: 0
			result = 31 * result + (interpolant?.hashCode() ?: 0)
			result = 31 * result + isSlice.hashCode()
			result = 31 * result + sliceDepth.hashCode()
			return result
		}
	}

	private class SlicesAndInterpolants : ObservableList<SliceOrInterpolant> by FXCollections.synchronizedObservableList(FXCollections.observableArrayList()) {

		fun removeSlice(slice: SliceInfo): Boolean {
			synchronized(this) {
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
		}

		fun removeSliceAtDepth(depth: Double): SliceInfo? {
			synchronized(this) {
				return getSliceAtDepth(depth)?.also {
					removeSlice(it)
				}
			}
		}

		fun removeIfInterpolant(idx: Int): InterpolantInfo? {
			synchronized(this) {
				return if (idx >= 0 && idx <= size - 1 && get(idx).isInterpolant) {
					LOG.trace("Removing Interpolant: $idx")
					removeAt(idx).getInterpolant()
				} else null
			}

		}

		fun add(depth: Double, slice: SliceInfo) {
			synchronized(this) {
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
		}

		fun add(idx: Int, interpolant: InterpolantInfo?) {
			synchronized(this) {
				LOG.trace("Adding Interpolant: $idx")
				add(idx, SliceOrInterpolant(interpolant))
			}
		}

		fun removeAllInterpolants() {
			synchronized(this) {
				for (i in size - 1 downTo 0) {
					removeIfInterpolant(i)
				}
			}
		}

		fun getSliceAtDepth(depth: Double): SliceInfo? {
			synchronized(this) {
				for (sliceOrInterpolant in this) {
					if (sliceOrInterpolant.isSlice && sliceOrInterpolant.sliceDepth == depth) {
						return sliceOrInterpolant.getSlice()
					}
				}
				return null
			}
		}

		fun getInterpolantAtDepth(depth: Double): InterpolantInfo? {
			synchronized(this) {
				for ((index, sliceOrInterpolant) in this.withIndex()) {
					if (
						sliceOrInterpolant.isInterpolant
						&& this.getOrNull(index - 1)?.let { it.sliceDepth < depth } == true
						&& this.getOrNull(index + 1)?.let { it.sliceDepth > depth } == true
					)
						return sliceOrInterpolant.getInterpolant()
				}
				return null
			}
		}

		/**
		 * Get global interval at [depth]. Behavior has 4 possibilities:
		 * 1. [depth] is at a slice, so return the [SliceInfo.globalBoundingBox]
		 * 2. [depth] is between slices, return the union of both [SliceInfo.globalBoundingBox]
		 * 3. [depth] is before OR after a slice, but not both (i.e., depth is farther from the interpolation than any slices).
		 *      return the closest [SliceInfo.globalBoundingBox]
		 * 4. No slices, return `null`
		 *
		 * @param depth to query the
		 * @return an interval, or null if no slices present
		 */
		fun getGlobalIntervalAtDepth(depth: Double): RealInterval? {
			synchronized(this) {
				return getSliceAtDepth(depth)?.globalBoundingBox
					?: getPreviousSlice(depth)?.globalBoundingBox?.let { prev -> prev union getNextSlice(depth)?.globalBoundingBox }
					?: getNextSlice(depth)?.globalBoundingBox
			}
		}


		fun getPreviousSlice(depth: Double): SliceInfo? {
			synchronized(this) {
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
		}

		fun getNextSlice(depth: Double): SliceInfo? {
			synchronized(this) {
				for (sliceOrInterpolant in this) {
					if (sliceOrInterpolant.isSlice && sliceOrInterpolant.sliceDepth > depth) {
						return sliceOrInterpolant.getSlice()
					}
				}
				return null
			}
		}

		fun getInterpolantsAround(slice: SliceInfo?): Pair<InterpolantInfo?, InterpolantInfo?> {
			synchronized(this) {
				var interpolantAfter: InterpolantInfo? = null
				var interpolantBefore: InterpolantInfo? = null
				for (idx in 0 until size - 1) {
					if (get(idx).equals(slice)) {
						if (get(idx + 1).isInterpolant) {
							interpolantAfter = get(idx + 1).getInterpolant()
							if (idx - 1 >= 0 && get(idx - 1).isInterpolant) {
								interpolantBefore = get(idx - 1).getInterpolant()
							}
						}
						break
					}
				}
				return ValuePair(interpolantBefore, interpolantAfter)
			}
		}

		val slices: List<SliceInfo>
			get() = synchronized(this) {
				stream()
					.filter { it.isSlice }
					.map { it.getSlice() }
					.collect(Collectors.toList())
			}
		val interpolants: List<InterpolantInfo>
			get() = synchronized(this) {
				stream()
					.filter { it.isInterpolant }
					.map { it.getInterpolant()!! }
					.collect(Collectors.toList())
			}

		fun clearInterpolantsAroundSlice(z: Double) {
			synchronized(this) {
				for (idx in this.indices) {
					if (get(idx).isSlice && get(idx).sliceDepth == z) {
						removeIfInterpolant(idx + 1)
						removeIfInterpolant(idx - 1)
						return
					}
				}
			}
		}

		fun removeIfInterpolantAt(depthInMaskDisplay: Double): Boolean {
			synchronized(this) {
				for (idx in this.indices) {
					if (get(idx).isSlice && get(idx).sliceDepth > depthInMaskDisplay) {
						if (removeIfInterpolant(idx - 1) != null)
							return true
					}
				}
				return false
			}
		}
	}

	private var initialGlobalToViewerTransform: AffineTransform3D? = null
	val previewProperty = SimpleBooleanProperty(true)
	var preview: Boolean by previewProperty.nonnull()

	class InterpolantInfo(val dataInterpolant: RealRandomAccessible<UnsignedLongType>)

	internal open class SliceInfo(
		var mask: ViewerMask,
		val globalTransform: AffineTransform3D,
		selectionInterval: RealInterval
	) {
		val maskBoundingBox: Interval get() = computeBoundingBoxInInitialMask()
		val globalBoundingBox: RealInterval
			get() = mask.initialGlobalToMaskTransform.inverse().estimateBounds(maskBoundingBox)

		private val selectionIntervalsInInitialSpace: MutableList<RealInterval> = mutableListOf()

		val selectionIntervals: List<RealInterval>
			get() = selectionIntervalsInInitialSpace.map { mask.initialToCurrentMaskTransform.estimateBounds(it) }.toList()

		init {
			addSelection(selectionInterval)
		}

		private fun computeBoundingBoxInInitialMask(): Interval {
			return selectionIntervalsInInitialSpace.reduce { l, r -> l union r }.smallestContainingInterval
		}

		fun addSelection(selectionInterval: RealInterval) {
			selectionIntervalsInInitialSpace.add(selectionInterval)
		}
	}
}
