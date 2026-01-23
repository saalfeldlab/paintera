package org.janelia.saalfeldlab.paintera.control

import bdv.viewer.TransformListener
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.scene.paint.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.*
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.converter.BiConverter
import net.imglib2.converter.logical.Logical
import net.imglib2.converter.read.BiConvertedRealRandomAccessible
import net.imglib2.img.array.ArrayImgFactory
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.loops.LoopBuilder
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
import net.imglib2.util.ConstantUtils
import net.imglib2.util.Intervals
import net.imglib2.view.ExtendedRealRandomAccessibleRealInterval
import net.imglib2.view.IntervalView
import net.imglib2.view.Views
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.net.imglib2.MultiRealIntervalAccessibleRealRandomAccessible
import org.janelia.saalfeldlab.net.imglib2.outofbounds.RealOutOfBoundsConstantValueFactory
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.util.math.HashableTransform.Companion.hashable
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
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.math.absoluteValue
import kotlin.math.sqrt

private val EMPTY_3D_INTERVAL = Intervals.createMinSize(0, 0, 0, 0, 0, 0)

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

	internal var lastSelectedId: Long = 0

	internal var interpolationId: Long = Label.INVALID

	private val slicesAndInterpolants = SlicesAndInterpolants()

	val isBusyProperty = SimpleBooleanProperty(false, "Shape Interpolation Controller is Busy")
	private var isBusy: Boolean by isBusyProperty.nonnull()

	val controllerStateProperty: ObjectProperty<ControllerState> = SimpleObjectProperty(ControllerState.Off)
	var controllerState: ControllerState by controllerStateProperty.nonnull()
	val isControllerActive: Boolean
		get() = controllerState != ControllerState.Off

	internal val currentDepthProperty = SimpleDoubleProperty()
	internal val currentDepth: Double by currentDepthProperty.nonnullVal()

	internal val sliceAtCurrentDepthProperty = slicesAndInterpolants.createObservableBinding(slicesAndInterpolants, currentDepthProperty) { it.getSliceAtDepth(currentDepth) }
	private val sliceAtCurrentDepth by sliceAtCurrentDepthProperty.nullableVal()

	val currentSliceMaskInterval get() = sliceAtCurrentDepth?.maskBoundingBox

	val numSlices: Int get() = slicesAndInterpolants.count { it.isSlice }

	var activeSelectionAlpha = (AbstractHighlightingARGBStream.DEFAULT_ACTIVE_FRAGMENT_ALPHA ushr 24) / 255.0

	private var activeViewer: ViewerPanelFX? = null

	private val currentViewerMaskProperty: ObjectProperty<ViewerMask?> = SimpleObjectProperty(null)
	internal var currentViewerMask by currentViewerMaskProperty.nullable()

	private val doneApplyingMaskListener = ChangeListener<Boolean> { _, _, newv -> if (!newv!!) InvokeOnJavaFXApplicationThread { doneApplyingMask() } }

	private var requestRepaintInterval: AtomicReference<RealInterval?> = AtomicReference(null)

	val sortedSliceDepths: List<Double>
		get() = slicesAndInterpolants
			.filter { it.isSlice }
			.map { it.sliceDepth }
			.toList()


	internal val currentBestMipMapLevel: Int
		get() {
			val viewerState = activeViewer!!.state
			val screenScaleTransform = AffineTransform3D()
			activeViewer!!.renderUnit.getScreenScaleTransform(0, screenScaleTransform)
			return viewerState.getBestMipMapLevel(screenScaleTransform, source)
		}

	private val interpolationSupervisor = SupervisorJob()

	@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
	private val interpolationScope = CoroutineScope(newSingleThreadContext("Slice Interpolation Context") + interpolationSupervisor)

	private var requestRepaintUpdaterJob: Job = Job().apply { complete() }

	private var globalCompositeFillAndInterpolationImgs: Pair<RealRandomAccessible<UnsignedLongType>, RealRandomAccessible<VolatileUnsignedLongType>>? = null

	private val viewerTransformDepthUpdater = TransformListener<AffineTransform3D> {
		currentDepthProperty.set(depthAt(it))
	}

	internal fun depthAt(globalTransform: AffineTransform3D): Double {
		val currentViewerInInitialViewer = DoubleArray(3).also {
			initialGlobalToViewerTransform!!.copy().concatenate(globalTransform.inverse()).apply(it, it)
		}
		return BigDecimal(currentViewerInInitialViewer[2]).setScale(5, RoundingMode.HALF_EVEN).toDouble()
	}

	/**
	 * Delete slice or interpolant at [depth]
	 *
	 * @return the global interval of the mask just removed (useful for repainting after removing)
	 */
	fun deleteSliceOrInterpolant(depth: Double): RealInterval? {
		val (slice1, slice2) = adjacentSlices(depth)
		val removedInterpolant = slicesAndInterpolants.removeIfInterpolantAt(depth)
		var removedSlice: SliceInfo? = null
		if (!removedInterpolant)
			removedSlice = slicesAndInterpolants.removeSliceAtDepth(depth)
		val repaintInterval = listOf(slice1, slice2, removedSlice)
			.mapNotNull { it?.globalBoundingBox }
			.reduceOrNull(Intervals::union)
		return repaintInterval
	}

	fun deleteSliceAt(depth: Double, reinterpolate: Boolean = true): RealInterval? {
		return sliceAt(depth)
			?.let { deleteSliceOrInterpolant(depth) }
			?.also { repaintInterval ->
				if (reinterpolate) {
					isBusy = true
					setMaskOverlay()
					requestRepaint(repaintInterval)
				}
			}
	}

	@JvmOverloads
	fun addSelection(
		maskIntervalOverSelection: Interval,
		replaceExistingSlice: Boolean = false,
		globalTransform: AffineTransform3D,
		viewerMask: ViewerMask
	): SliceInfo? {
		if (controllerState == ControllerState.Off) return null
		isBusy = true
		val selectionDepth = depthAt(globalTransform)
		val currentSlice = slicesAndInterpolants.getSliceAtDepth(selectionDepth)

		fun newSlice(): SliceInfo {
			val slice = SliceInfo(viewerMask, globalTransform, maskIntervalOverSelection)
			slicesAndInterpolants.add(selectionDepth, slice)
			return slice
		}

		val slice = when {
			currentSlice == null -> newSlice()
			replaceExistingSlice -> {
				deleteSliceAt(selectionDepth, false)
				newSlice()
			}
			else -> {
				currentSlice.addSelection(maskIntervalOverSelection)
				slicesAndInterpolants.clearInterpolantsAroundSlice(selectionDepth)
				currentSlice
			}
		}

		interpolateBetweenSlices(false)
   		return slice
	}

	internal fun sliceAt(depth: Double) = slicesAndInterpolants.getSliceAtDepth(depth)

	internal fun adjacentSlices(depth: Double) = slicesAndInterpolants.run {
		previousSlice(depth) to nextSlice(depth)
	}

	fun enterShapeInterpolation(viewer: ViewerPanelFX) {
		if (isControllerActive) {
			LOG.trace { "Already in shape interpolation" }
			return
		}
		LOG.debug { "Entering shape interpolation" }

		activeViewer = viewer

		/* Store all the previous activated Ids*/
		lastSelectedId = assignment.getSegment(selectedIds.lastSelection)
		if (lastSelectedId == Label.INVALID) lastSelectedId = idService.next()
		selectNewInterpolationId()
		initialGlobalToViewerTransform = globalToViewerTransform
		activeViewer!!.addTransformListener(viewerTransformDepthUpdater)
		controllerState = ControllerState.Select

		sliceAtCurrentDepthProperty.addListener { _, old, new ->
			old?.mask?.setMaskOnUpdate = false
			new?.mask?.also {
				currentViewerMask = it
				it.setMaskOnUpdate = false
			}
		}
	}

	fun exitShapeInterpolation(completed: Boolean) {
		requestRepaintUpdaterJob.cancel()

		if (!isControllerActive) {
			LOG.debug { "Not in shape interpolation" }
			return
		}
		LOG.debug { "Exiting shape interpolation" }

		// extra cleanup if shape interpolation was aborted
		if (!completed) {
			interruptInterpolation("Exiting Shape Interpolation")
			source.resetMasks(true)
		}


		/* Reset the selection state */
		converter.removeColor(lastSelectedId)
		converter.removeColor(interpolationId)
		selectedIds.deactivate(interpolationId)
		selectedIds.activateAlso(lastSelectedId)
		controllerState = ControllerState.Off
		slicesAndInterpolants.clear()
		currentViewerMask = null
		globalCompositeFillAndInterpolationImgs = null
		lastSelectedId = Label.INVALID
		interpolationId = Label.INVALID

		activeViewer!!.removeTransformListener(viewerTransformDepthUpdater)
		activeViewer = null
	}

	fun togglePreviewMode() {
		freezeInterpolation = false
		preview = !preview
		if (preview) interpolateBetweenSlices(false)
		else updateSliceAndInterpolantsCompositeMask()
		if (slicesAndInterpolants.size > 0) {
			val globalUnion = slicesAndInterpolants.slices.mapNotNull { it.globalBoundingBox }.reduceOrNull(Intervals::union)
			requestRepaint(interval = globalUnion)
		} else isBusy = false
	}

	internal fun setMaskOverlay(replaceExistingInterpolants: Boolean = false) {
		if (preview) interpolateBetweenSlices(replaceExistingInterpolants)
		else {
			updateSliceAndInterpolantsCompositeMask()
			val globalUnion = slicesAndInterpolants.slices.mapNotNull { it.globalBoundingBox }.reduceOrNull(Intervals::union)
			isBusy = false
			requestRepaint(interval = globalUnion)
		}
		if (slicesAndInterpolants.isEmpty())
			isBusy = false
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Synchronized
	fun interpolateBetweenSlices(replaceExistingInterpolants: Boolean) {
		if (freezeInterpolation) return
		if (slicesAndInterpolants.slices.size < 2) {
			updateSliceAndInterpolantsCompositeMask()
			isBusy = false
			return
		}

		InvokeOnJavaFXApplicationThread {
			controllerState = ControllerState.Interpolate
		}

		if (replaceExistingInterpolants) {
			slicesAndInterpolants.removeAllInterpolants()
			interruptInterpolation("Replacing Existing Interpolants")
		}

		isBusy = true
		interpolationScope.async {
			var updateInterval: RealInterval? = null
			for ((firstSlice, secondSlice) in slicesAndInterpolants.zipWithNext().reversed()) {
				ensureActive()

				if (firstSlice.isInterpolant || secondSlice.isInterpolant)
					continue

				val slice1 = firstSlice.getSlice()
				val slice2 = secondSlice.getSlice()
				val interpolant = interpolateBetweenTwoSlices(slice1, slice2, interpolationId)
				slicesAndInterpolants.add(firstSlice.sliceDepth, interpolant!!)
				updateInterval = sequenceOf(slice1.globalBoundingBox, slice2.globalBoundingBox, updateInterval)
					.filterNotNull()
					.reduceOrNull(Intervals::union)
			}
			updateSliceAndInterpolantsCompositeMask()
			updateInterval
		}.also { job ->
			job.invokeOnCompletion { cause ->
				cause?.let {
					LOG.debug(cause) { "Interpolation job cancelled" }
				} ?: InvokeOnJavaFXApplicationThread { controllerState = ControllerState.Preview }

				job.getCompleted()?.let { updateInterval ->
					requestRepaint(updateInterval)
				} ?: let {
					/* a bit of a band-aid. It shouldn't be triggered often, but occasionally when an interpolation is triggered
					* and there is no interpolation to be done (i.e. interpolation is already done, no new slices) then the refresh
					* interval can desync, and show an empty interpolation result. This isn't a great fix, doesn't solve the underlying
					* cause, but should stop it from happening as frequently. */
					paintera().orthogonalViews().requestRepaint()
				}
				isBusy = false
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
			runBlocking { interpolationSupervisor.children.forEach { it.join() } }
		}
		assert(controllerState == ControllerState.Preview)

		val globalToSource = source.getSourceTransformForMask(source.currentMask.info).inverse()
		val slicesUnionSourceInterval = slicesAndInterpolants
			.filter(SliceOrInterpolant::isSlice).asSequence()
			.map(SliceOrInterpolant::getSlice)
			.mapNotNull(SliceInfo::globalBoundingBox)
			.map { globalToSource.estimateBounds(it)!! }
			.reduce(Intervals::union)
			.smallestContainingInterval

		LOG.trace { "Applying interpolated mask using bounding box of size ${Intervals.dimensionsAsLongArray(slicesUnionSourceInterval)}" }

		val finalLastSelectedId = lastSelectedId
		val finalInterpolationId = interpolationId
		if (Label.regular(finalLastSelectedId)) {
			val maskInfo = source.currentMask.info
			source.resetMasks(false)
			val interpolatedMaskImgsA = globalCompositeFillAndInterpolationImgs!!.first
				.affineReal(globalToSource)
				.convert(UnsignedLongType(Label.INVALID)) { input, output ->
					val originalLabel = input.long
					val label = if (originalLabel == finalInterpolationId) {
						finalLastSelectedId
					} else input.get()
					output.set(label)
				}
			val interpolatedMaskImgsB = globalCompositeFillAndInterpolationImgs!!.second
				.affineReal(globalToSource)
				.convert(VolatileUnsignedLongType(Label.INVALID)) { input, out ->
					val isValid = input.isValid
					out.isValid = isValid
					if (isValid) {
						val originalLabel = input.get().get()
						val label = if (originalLabel == finalInterpolationId) finalLastSelectedId else input.get().get()
						out.get().set(label)
					}
				}
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

	private val freezeInterpolationProperty = SimpleBooleanProperty(false)
	var freezeInterpolation: Boolean by freezeInterpolationProperty.nonnull()


	@Throws(MaskInUse::class)
	private fun setCompositeMask(includeInterpolant: Boolean = preview) {
		if (freezeInterpolation) return
		synchronized(source) {
			source.resetMasks(false)
			val fillMasks: MutableList<RealRandomAccessibleRealInterval<UnsignedLongType>> = mutableListOf()
			val slices = slicesAndInterpolants.slices
			slices.forEachIndexed { idx, slice ->
				fillMasks += slice.mask.run {
					viewerImg
						.expandborder(0, 0, 1)
						.extendValue(Label.INVALID)
						.interpolateNearestNeighbor()
						.affineReal(initialGlobalToMaskTransform.inverse())
						.realInterval(slice.globalBoundingBox ?: EMPTY_3D_INTERVAL)
				}
			}
			val invalidLabel = UnsignedLongType(Label.INVALID)

			val composedSlices =
				if (slices.isEmpty())
					ConstantUtils.constantRandomAccessible(invalidLabel, 3).interpolateNearestNeighbor()
				else
					MultiRealIntervalAccessibleRealRandomAccessible(fillMasks, invalidLabel) { it.get() != Label.INVALID }


			val interpolants = slicesAndInterpolants.interpolants
			val composedInterpolations =
				if (!preview || interpolants.isEmpty())
					ConstantUtils.constantRealRandomAccessible(invalidLabel, composedSlices.numDimensions())
				else {
					val interpolantRais = interpolants.map { info -> info.dataInterpolant.realInterval(info.interval!!) }.toList()
					val interpolantBounds: RealInterval = interpolantRais.map { it as RealInterval }.reduce { l, r -> l.union(r) }
					val outOfBounds = invalidLabel
					ExtendedRealRandomAccessibleRealInterval(
						MultiRealIntervalAccessibleRealRandomAccessible(interpolantRais, outOfBounds) { it.get() != Label.INVALID }.realInterval(interpolantBounds),
						RealOutOfBoundsConstantValueFactory(outOfBounds)
					)
				}

			val compositeMaskInGlobal = BiConvertedRealRandomAccessible(composedSlices, composedInterpolations, Supplier {
				BiConverter { fillValue: UnsignedLongType, interpolationValue: UnsignedLongType, compositeValue: UnsignedLongType ->
					val aVal = fillValue.get()
					val aOrB = if (aVal.isInterpolationLabel) fillValue else interpolationValue
					compositeValue.set(aOrB)
				}
			}) { invalidLabel.copy() }
			val compositeVolatileMaskInGlobal = compositeMaskInGlobal.convert(VolatileUnsignedLongType(Label.INVALID)) { source, target ->
				target.get().set(source.get())
				target.isValid = true
			}

			/* Set the interpolatedMaskImgs to the composite fill+interpolation RAIs*/
			globalCompositeFillAndInterpolationImgs = compositeMaskInGlobal to compositeVolatileMaskInGlobal
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

	private fun newRepaintRequestUpdater() = InvokeOnJavaFXApplicationThread {

		while (isActive) {
			/* Don't trigger repaints while interpolating*/
			interpolationSupervisor.children.forEach { it.join() }
			requestRepaintInterval.getAndSet(null)?.let {
				processRepaintRequest(it)
			}
			awaitPulse()
		}
	}

	private fun processRepaintRequest(interval: RealInterval) {
		val sourceToGlobal = sourceToGlobalTransform
		val extendedSourceInterval = IntervalHelpers.extendAndTransformBoundingBox(interval.smallestContainingInterval, sourceToGlobal.inverse(), 1.0)
		val extendedGlobalInterval = sourceToGlobal.estimateBounds(extendedSourceInterval).smallestContainingInterval
		paintera().orthogonalViews().requestRepaint(extendedGlobalInterval)
	}

	fun requestRepaint(interval: RealInterval? = null, force: Boolean = false) {
		if (!requestRepaintUpdaterJob.isActive)
			requestRepaintUpdaterJob = newRepaintRequestUpdater()

		if (force) {
			val union = requestRepaintInterval.getAndSet(null)?.union(interval) ?: interval ?: return
			processRepaintRequest(union)
		} else
			requestRepaintInterval.getAndAccumulate(interval) { l, r -> l?.union(r) ?: r }
	}

	private fun interruptInterpolation(reason: String = "Interrupting Interpolation") {
		val cause = CancellationException(reason)
		LOG.debug(cause) { "Interuppting Interpolation" }
		interpolationSupervisor.cancelChildren(cause)
	}

	private fun updateSliceAndInterpolantsCompositeMask() {
		if (numSlices == 0) {
			source.resetMasks()
			paintera().orthogonalViews().requestRepaint()
		} else {
			try {
				setCompositeMask()
			} catch (e: MaskInUse) {
				LOG.error(e) { "Label source already has an active mask" }
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
	private val globalToViewerTransform: AffineTransform3D
		get() = AffineTransform3D().also {
			activeViewer!!.state.getViewerTransform(it)
		}

	private fun newMask(targetMipMapLevel: Int) : ViewerMask {
		val maskInfo = MaskInfo(0, targetMipMapLevel)
		return source.createViewerMask(maskInfo, activeViewer!!, setMask = false)
	}

	fun getMask(targetMipMapLevel: Int = currentBestMipMapLevel, ignoreExisting: Boolean = false): ViewerMask {

		/* If we have a mask, get it; else create a new one */
		val depth = currentDepth
		val currentSlice = sliceAtCurrentDepth
		val currentSliceBoundingBox = currentSlice?.maskBoundingBox

 		currentViewerMask = when {
			/* No existing slice, or we are ignoring it; make a new one */
			currentSlice == null || ignoreExisting -> newMask(targetMipMapLevel)
			/* existing slice, but delete it since its empty */
			currentSliceBoundingBox == null -> {
				deleteSliceAt(depth, false)
				newMask(targetMipMapLevel)
			}
		    /* No bounding box means current slice is empty, so nothing to reuse */
		    currentSlice.maskBoundingBox == null -> null
		    /* No scale change, so no need to scale and wrap with new mask */
		    currentSlice.mask.xScaleChange == 1.0 -> currentSlice.mask
			/* wrap the existing mask at a different scale level */
			else -> wrapExistingSlice(currentSlice, targetMipMapLevel)
		}
		currentViewerMask?.setViewerMaskOnSource()

		if (preview && slicesAndInterpolants.getInterpolantAtDepth(currentDepth) != null)
			copyInterpolationToMask()

		return currentViewerMask!!
	}

	private fun wrapExistingSlice(existingSlice : SliceInfo, mipMapLevel: Int): ViewerMask? {
		val existingBoundingBox = existingSlice.maskBoundingBox ?: return null

		val existingMask = existingSlice.mask
		if (existingMask.xScaleChange == 1.0) return existingMask

		val maskInfo = MaskInfo(0, mipMapLevel)
		val newMask = source.createViewerMask(maskInfo, activeViewer!!, setMask = false)

		val oldToNewMask = ViewerMask.maskToMaskTransformation(existingMask, newMask)

		val oldIntervalInNew = oldToNewMask.estimateBounds(existingBoundingBox)

		val oldInNew = existingMask.viewerImg.wrappedSource
			.interpolateNearestNeighbor()
			.affine(oldToNewMask)
			.interval(oldIntervalInNew)

		val oldInNewVolatile = existingMask.volatileViewerImg.wrappedSource
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
		slicesAndInterpolants.removeSlice(existingSlice)
		existingSlice.mask.shutdown?.run()

		val newSlice = SliceInfo(
			newMask,
			paintera().manager().transform,
			FinalRealInterval(
				oldIntervalInNew.minAsDoubleArray().also { it[2] = 0.0 },
				oldIntervalInNew.maxAsDoubleArray().also { it[2] = 0.0 }
			).smallestContainingInterval
		)
		slicesAndInterpolants.add(currentDepth, newSlice)
		return newMask
	}

	/**
	 * Get 2d interpolation img slice at [depth].
	 *  If [closest] then grab the closest if no interpolation result at [depth]
	 *
	 * @param globalToViewerTransform to slice the interpolation img at
	 * @param closest will grab the closest img if no interpolation img results at [depth]
	 * @return 2D img in viewer space containing the interpolation img at [depth], or [closest].
	 *  Null if the img at the slice is present, but intentionally empty.
	 */
	internal fun getInterpolationImg(globalToViewerTransform: AffineTransform3D, closest: Boolean = false): IntervalView<UnsignedLongType>? {

		fun SliceInfo.maskInViewerSpace(): IntervalView<UnsignedLongType>? {

			val maskInterval = maskBoundingBox ?: return null


			val translation = mask.initialMaskToViewerTransform.translation.also { it[2] = 0.0 }
			return Views.translate(mask.viewerImg.interval(maskInterval), *translation.map { it.toLong() }.toLongArray())
		}

		val depth = depthAt(globalToViewerTransform)
		sliceAt(depth).takeIf {
			val maskTransform = it?.mask?.initialGlobalToViewerTransform?.hashable()
			val currentTransform = globalToViewerTransform.hashable()
			maskTransform == currentTransform
		}?.let { return it.maskInViewerSpace() }

		val imgSliceMask = source.createViewerMask(MaskInfo(0, currentBestMipMapLevel), activeViewer!!, setMask = false, initialGlobalToViewerTransform = globalToViewerTransform)
		copyInterpolationToMask(imgSliceMask, replaceExisting = false)?.let { it.maskInViewerSpace()?.let { img -> return img } }

		if (!closest)
			return null

		return adjacentSlices(depth)
			.toList()
			.filterNotNull()
			.minByOrNull { (depthAt(it.globalTransform) - depth).absoluteValue }
			?.let { return it.maskInViewerSpace() }
	}

	private fun copyInterpolationToMask(viewerMask: ViewerMask = currentViewerMask!!, replaceExisting: Boolean = true): SliceInfo? {
		val globalTransform = viewerMask.initialGlobalTransform.copy()
		val (interpolantInterval, interpolant) = slicesAndInterpolants.getInterpolantAtDepth(depthAt(globalTransform))?.run { interval?.let { it to this } } ?: let {
			LOG.debug { "No Interpolant to copy" }
			return null
		}

		/* get union of adjacent slices bounding boxes */
		val unionInterval = let {

			val sourceIntervalInMaskSpace = viewerMask.run { currentMaskToSourceTransform.inverse().estimateBounds(source.getSource(0, info.level)) }
			val interpolantIntervalInMaskSpace = viewerMask.currentGlobalToMaskTransform.estimateBounds(interpolantInterval)

			val minZSlice = interpolantIntervalInMaskSpace.minAsDoubleArray().also { it[2] = 0.0 }
			val maxZSlice = interpolantIntervalInMaskSpace.maxAsDoubleArray().also { it[2] = 0.0 }
			val interpolantIntervalSliceInMaskSpace = FinalRealInterval(minZSlice, maxZSlice) intersect sourceIntervalInMaskSpace


			val interpolatedMaskView = interpolant.dataInterpolant
				.affine(viewerMask.currentGlobalToMaskTransform)
				.interval(interpolantIntervalSliceInMaskSpace)
			val fillMaskOverInterval = viewerMask.viewerImg.apply {
				extendValue(Label.INVALID)
			}.interval(interpolantIntervalSliceInMaskSpace)

			LoopBuilder.setImages(interpolatedMaskView, fillMaskOverInterval)
				.multiThreaded()
				.forEachPixel { interpolationType, fillMaskType ->
					val fillMaskVal = fillMaskType.get()
					val interpolationVal = interpolationType.get()
					if (!fillMaskVal.isInterpolationLabel && interpolationVal.isInterpolationLabel) {
						fillMaskType.set(interpolationVal)
					}
				}
			interpolantIntervalSliceInMaskSpace.smallestContainingInterval
		}

		val slice = SliceInfo(
			viewerMask,
			globalTransform,
			unionInterval
		)
		if (replaceExisting) {
			/* remove the old interpolant*/
			slicesAndInterpolants.removeIfInterpolantAt(currentDepth)
			/* add the new slice */
			slicesAndInterpolants.add(currentDepth, slice)
		}
		return slice
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
		private fun paintera(): PainteraBaseView = Paintera.getPaintera().baseView

		private val Long.isInterpolationLabel
			get() = this.toULong() < Label.MAX_ID.toULong()

		private fun interpolateBetweenTwoSlices(
			slice1: SliceInfo,
			slice2: SliceInfo,
			fillValue: Long
		): InterpolantInfo? {


			val slice2InitialToSlice1Initial = ViewerMask.maskToMaskTransformation(slice2.mask, slice1.mask)

			val (slice1InInitial, slice2InSlice1Initial) = when {
				slice1.maskBoundingBox != null && slice2.maskBoundingBox != null -> {
					slice1.maskBoundingBox!! to slice2InitialToSlice1Initial.estimateBounds(slice2.maskBoundingBox!!)
				}

				slice1.maskBoundingBox != null -> {
					val depthPoint = doubleArrayOf(0.0, 0.0, 0.0).also { slice2InitialToSlice1Initial.apply(it, it) }
					val fakeSlice2BoundingBox = FinalRealInterval(
						slice1.maskBoundingBox!!.minAsDoubleArray().also { it[2] = depthPoint[2] },
						slice1.maskBoundingBox!!.maxAsDoubleArray().also { it[2] = depthPoint[2] }
					)
					slice1.maskBoundingBox!! to fakeSlice2BoundingBox
				}

				slice2.maskBoundingBox != null -> {
					val fakeSlice1BoundingBox = FinalInterval(
						slice2.maskBoundingBox!!.minAsLongArray().also { it[2] = 0L },
						slice2.maskBoundingBox!!.maxAsLongArray().also { it[2] = 0L }
					)
					fakeSlice1BoundingBox to slice2InitialToSlice1Initial.estimateBounds(slice2.maskBoundingBox!!)
				}

				else -> return InterpolantInfo(ConstantUtils.constantRealRandomAccessible(UnsignedLongType(Label.INVALID), slice1.mask.viewerImg.numDimensions()), null)
			}

			val sliceInfoPair = arrayOf(slice1, slice2)

			// get the two slices as 2D images
			val slices: Array<RandomAccessibleInterval<UnsignedLongType>?> = arrayOfNulls(2)

			val realUnionInSlice1Initial = slice2InSlice1Initial union slice1InInitial
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
					val binarySlice = slices[i]!!.convertRAI(BoolType()) { source, target ->
						val label = source.get().isInterpolationLabel
						target.set(label)
					}
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

			return if (Thread.currentThread().isInterrupted) null else InterpolantInfo(interpolatedShapeMask, FinalRealInterval(interpolatedShapeMask.source))
		}

		private fun <R, B : BooleanType<B>> computeSignedDistanceTransform(
			mask: RandomAccessibleInterval<B>,
			target: RandomAccessibleInterval<R>,
			distanceType: DistanceTransform.DISTANCE_TYPE,
			vararg weights: Double
		) where R : RealType<R>?, R : NativeType<R>? {
			val distanceInside: RandomAccessibleInterval<R> = ArrayImgFactory(target.type).create(target)
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
		): ExtendedRealRandomAccessibleRealInterval<T, RealRandomAccessibleRealInterval<T>> where T : NativeType<T>, T : RealType<T> {
			val extendValue = dt1.type.createVariable()
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

			val interpolatedShapeRaiInSource = scaledInterpolatedDistanceTransform.convert(targetValue.createVariable()) { input, output: T ->
				val value = if (input.realDouble <= 0) targetValue else invalidValue
				output.set(value)
			}
				.affineReal(transformToSource)
				.realInterval(transformToSource.copy().concatenate(distanceScale).estimateBounds(distanceTransformStack))

			return ExtendedRealRandomAccessibleRealInterval(interpolatedShapeRaiInSource, RealOutOfBoundsConstantValueFactory(invalidValue))
		}

		private fun computeDistanceBetweenSlices(s1: SliceInfo, s2: SliceInfo) = ViewerMask.maskToMaskTransformation(s2.mask, s1.mask).translation[2]
	}

	private class SliceOrInterpolant {
		private val sliceAndDepth: Pair<Double, SliceInfo>?
		private val interpolant: InterpolantInfo?

		constructor(depth: Double, slice: SliceInfo) {
			sliceAndDepth = depth to slice
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
			return sliceAndDepth!!.second
		}

		val sliceDepth: Double
			get() = sliceAndDepth!!.first

		fun getInterpolant(): InterpolantInfo {
			return interpolant!!
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

	private class SlicesAndInterpolants(
		private val list : MutableList<SliceOrInterpolant> = Collections.synchronizedList(mutableListOf())
	) : List<SliceOrInterpolant> by list, Observable {

		private val listeners = mutableSetOf<InvalidationListener>()

		override fun addListener(listener: InvalidationListener?) {
			listener?.let {
				listeners.add(it)
			}
		}

		override fun removeListener(listener: InvalidationListener?) : Unit {
			listener?.let {
				listeners.remove(listener)
			}
		}

		private fun invalidate() {
			listeners.forEach { it.invalidated(this) }
		}

		@Synchronized
		fun removeSlice(slice: SliceInfo): Boolean {
			for (idx in indices) {
				if (idx >= 0 && idx <= size - 1 && get(idx).equals(slice)) {
					removeIfInterpolant(idx + 1, invalidate = false)
					LOG.trace { "Removing Slice: $idx" }
					list.removeAt(idx).getSlice()
					removeIfInterpolant(idx - 1, invalidate = false)
					invalidate()
					return true
				}
			}
			return false
		}

		@Synchronized
		fun removeSliceAtDepth(depth: Double): SliceInfo? {
			return getSliceAtDepth(depth)?.also {
				removeSlice(it)
			}
		}

		@Synchronized
		private fun removeIfInterpolant(idx: Int, invalidate : Boolean = true): InterpolantInfo? {
			return if (idx in indices && get(idx).isInterpolant) {
				LOG.trace { "Removing Interpolant: $idx" }
				list.removeAt(idx)
					.getInterpolant()
					.also { if (invalidate) invalidate() }
			} else null

		}

		fun add(depth: Double, interpolant: InterpolantInfo) {
			add(depth, SliceOrInterpolant(interpolant))
		}

		fun add(depth: Double, slice: SliceInfo) {
			add(depth, SliceOrInterpolant(depth, slice))
		}

		@Synchronized
		fun clear() {
			list.clear()
			invalidate()
		}

		@Synchronized
		fun add(depth: Double, sliceOrInterpolant: SliceOrInterpolant) {
			for (idx in this.indices) {
				if (get(idx).isSlice && get(idx).sliceDepth > depth) {
					LOG.trace { "Adding Slice: $idx" }
					list.add(idx, sliceOrInterpolant)
					removeIfInterpolant(idx - 1, invalidate = false)
					invalidate()
					return
				}
			}
			LOG.trace { "Adding Slice: ${size}" }
			list.add(sliceOrInterpolant)
			invalidate()
		}

		@Synchronized
		fun removeAllInterpolants() {
			var invalidate = false
			for (i in size - 1 downTo 0) {
				removeIfInterpolant(i, invalidate = false)?.also { invalidate = true }
			}
			if (invalidate)
				invalidate()
		}

		@Synchronized
		fun getSliceAtDepth(depth: Double): SliceInfo? {
			for (sliceOrInterpolant in list) {
				if (sliceOrInterpolant.isSlice && sliceOrInterpolant.sliceDepth == depth) {
					return sliceOrInterpolant.getSlice()
				}
			}
			return null
		}

		@Synchronized
		fun getInterpolantAtDepth(depth: Double): InterpolantInfo? {
			for ((index, sliceOrInterpolant) in withIndex()) {
				return when {
					sliceOrInterpolant.isSlice -> continue //If slice, check the next one
					getOrNull(index - 1)?.run { sliceDepth >= depth } == true -> null //if the preceding slice is already past our depth, return null
					getOrNull(index + 1)?.run { sliceDepth > depth } == true -> sliceOrInterpolant.getInterpolant() // if the next depth is beyond our depth, return this interpolant
					else -> continue //no valid interpolants, return null
				}
			}
			return null
		}

		@Synchronized
		fun previousSlice(depth: Double): SliceInfo? {
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

		@Synchronized
		fun nextSlice(depth: Double): SliceInfo? {
			for (sliceOrInterpolant in list) {
				if (sliceOrInterpolant.isSlice && sliceOrInterpolant.sliceDepth > depth) {
					return sliceOrInterpolant.getSlice()
				}
			}
			return null
		}

		val slices: List<SliceInfo>
			@Synchronized
			get() = mutableListOf<SliceInfo>().let {
				val iterator = iterator()
				while (iterator.hasNext()) {
					val element = iterator.next()
					if (element.isSlice)
						it.add(element.getSlice())
				}
				it.toList()
			}

		val interpolants: List<InterpolantInfo>
			@Synchronized
			get() = mutableListOf<InterpolantInfo>().let {
				val iterator = iterator()
				while (iterator.hasNext()) {
					val element = iterator.next()
					if (element.isInterpolant)
						it.add(element.getInterpolant())
				}
				it.toList()
			}

		@Synchronized
		fun clearInterpolantsAroundSlice(z: Double) {
			for (idx in indices) {
				if (get(idx).isSlice && get(idx).sliceDepth == z) {
					removeIfInterpolant(idx + 1, invalidate = false)
					removeIfInterpolant(idx - 1, invalidate = false)
					invalidate()
					return
				}
			}
		}

		@Synchronized
		fun removeIfInterpolantAt(depthInMaskDisplay: Double): Boolean {
			val removed = removeIfInterpolantAtInternal(depthInMaskDisplay)
			if (removed) invalidate()
			return removed
		}

		@Synchronized
		fun removeIfInterpolantAtInternal(depthInMaskDisplay: Double): Boolean {
			for (idx in indices) {
				val sliceOrInterpolant = get(idx)
				if (sliceOrInterpolant.isSlice) {
					val sliceDepth = get(idx).sliceDepth
					return when {
						// We have a slice at this depth, so there is no interpolant
						sliceDepth == depthInMaskDisplay -> false
						// we are at the next slice if there is an interpolant, remove it; return either way.
						sliceDepth > depthInMaskDisplay -> removeIfInterpolant(idx - 1, invalidate = true) != null
						// we haven't reached the following slice yet, check the next one
						else -> continue
					}
				}
			}
			return false
		}
	}

	var initialGlobalToViewerTransform: AffineTransform3D? = null
		private set
		get() = field?.copy()
	val previewProperty = SimpleBooleanProperty(true)
	var preview: Boolean by previewProperty.nonnull()

	class InterpolantInfo(val dataInterpolant: RealRandomAccessible<UnsignedLongType>, val interval: RealInterval?)

	open class SliceInfo(
		var mask: ViewerMask,
		val globalTransform: AffineTransform3D,
		selectionInterval: Interval? = null
	) {
		val maskBoundingBox: Interval? by LazyForeignValue({ selectionIntervals.toList() }) {
			computeBoundingBoxInInitialMask()
		}
		val globalBoundingBox: RealInterval?
			get() = maskBoundingBox?.let { mask.initialMaskToGlobalWithDepthTransform.estimateBounds(it.extendBy(0.0, 0.0, .5)) }

		private val selectionIntervals: MutableList<Interval> = mutableListOf()

		init {
			selectionInterval?.let { addSelection(it) }
		}

		private fun computeBoundingBoxInInitialMask(): Interval? {
			class ShrinkingInterval(
				val ndim: Int,
				val minPos: LongArray = LongArray(ndim) { Long.MAX_VALUE },
				val maxPos: LongArray = LongArray(ndim) { Long.MIN_VALUE }
			) : Interval {
				override fun numDimensions() = minPos.size
				override fun min(d: Int) = minPos[d]
				override fun max(d: Int) = maxPos[d]

				fun update(vararg pos: Long) {
					minPos.zip(maxPos).forEachIndexed { idx, (min, max) ->
						if (pos[idx] < min) minPos[idx] = pos[idx]
						if (pos[idx] > max) maxPos[idx] = pos[idx]
					}
				}

				val isValid: Boolean
					get() =
						minPos.zip(maxPos)
							.map { (min, max) -> min != Long.MAX_VALUE && max != Long.MIN_VALUE }
							.reduce { a, b -> a && b }
			}

			val sourceInMaskInterval = mask.initialMaskToSourceTransform.inverse().estimateBounds(mask.source.getSource(0, mask.info.level))
			selectionIntervals
				.map { BundleView(mask.viewerImg).interval(it intersect sourceInMaskInterval) }
				.map {
					val shrinkingInterval = ShrinkingInterval(it.numDimensions())
					LoopBuilder.setImages(it).forEachPixel { access ->
						val pixel = access.get().get()
						if (pixel != Label.TRANSPARENT && pixel != Label.INVALID)
							shrinkingInterval.update(*access.positionAsLongArray())
					}
					shrinkingInterval
				}.forEachIndexed { idx, it ->
					selectionIntervals[idx] = if (it.isValid) FinalInterval(it) else it
				}
			selectionIntervals.removeIf { it is ShrinkingInterval }
			return selectionIntervals.reduceOrNull(Intervals::union)
		}

		fun addSelection(selectionInterval: Interval) {
			selectionIntervals.add(selectionInterval)
			maskBoundingBox //recompute the bounding box, incase we "add" TRANSPARENT pixels (that is, erase)
		}
	}
}

fun main() {

	val longs = ArrayImgs.longs(10)
	longs.forEachIndexed { idx, it -> it.set(idx.toLong()) }

	val interval = Intervals.createMinMax(2, 8)
	val view = longs.interval(interval)

	view.forEach { print("${it.get()} ") }
	println("")
	val bv = BundleView(view)
	bv.interval(interval).forEach { print("${it.get()} ") }
}
