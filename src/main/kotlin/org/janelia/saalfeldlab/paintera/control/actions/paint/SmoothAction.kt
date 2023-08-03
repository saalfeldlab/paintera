package org.janelia.saalfeldlab.paintera.control.actions.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.value.ChangeListener
import javafx.event.ActionEvent
import javafx.event.Event
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.imglib2.FinalInterval
import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.algorithm.convolution.kernel.Kernel1D
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution
import net.imglib2.algorithm.gauss3.Gauss3
import net.imglib2.algorithm.lazy.Lazy
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.converter.Converters
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.img.cell.CellGrid
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_GLYPH
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode.statePaintContext
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.util.extendZero
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.union
import kotlin.collections.List
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.min
import kotlin.collections.minusAssign
import kotlin.collections.mutableListOf
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import net.imglib2.type.label.Label as Imglib2Label


open class MenuAction(val label: String) : Action<Event>(Event.ANY) {


	init {
		keysDown = null
		name = label
	}

	val menuItem by lazy {
		MenuItem(label).also {
			it.onAction = EventHandler { this(it) }
			it.isDisable = isValid(null)
		}
	}

}

object SmoothAction : MenuAction("Smooth") {

	private var smoothTask: UtilityTask<Unit>? = null
	private lateinit var updateSmoothMask: ((Boolean) -> Unit)

	private var finalizeSmoothing = false;

	private val replacementLabelProperty = SimpleLongProperty(0)

	private val replacementLabel by replacementLabelProperty.nonnullVal()
	private val kernelSizeProperty = SimpleDoubleProperty()

	private val currentMipMapLevelProperty = SimpleIntegerProperty()
	private var currentMipMapLevel by currentMipMapLevelProperty.nonnull()

	private val kernelSize by kernelSizeProperty.nonnullVal()

	private lateinit var labelSource: ConnectomicsLabelState<*, *>
	private lateinit var paintLabelMode: PaintLabelMode
	private lateinit var paintContext: StatePaintContext<*, *>

	private var sourceSmoothInterval: Interval? = null
	private var globalSmoothInterval: RealInterval? = null

	init {
		verify("Label Source is Active") { paintera.currentSource is ConnectomicsLabelState<*, *> }
		verify("Paint Label Mode is Active") { paintera.currentMode is PaintLabelMode }
		verify("Paint Label Mode has StatePaintContext") { statePaintContext != null }
		verify("Viewer is Focused") { paintera.activeViewer.get()?.state != null }
		verify { !paintera.baseView.isDisabledProperty.get() }
		onAction {
			val updateLevelListener: (transform: AffineTransform3D) -> Unit = { currentMipMapLevel = paintera.activeViewer.get()!!.state!!.bestMipMapLevel }
			paintera.baseView.manager().addListener(updateLevelListener)
			currentMipMapLevel = paintera.activeViewer.get()!!.state!!.bestMipMapLevel

			labelSource = paintera.currentSource as ConnectomicsLabelState<*, *>
			paintLabelMode = paintera.currentMode as PaintLabelMode
			paintContext = statePaintContext!!
			finalizeSmoothing = false
			startSmoothTask()
			showSmoothDialog()
			paintera.baseView.manager().removeListener(updateLevelListener)
		}
	}

	private val AffineTransform3D.resolution
		get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])

	private fun showSmoothDialog() {
		Dialog<Boolean>().apply {
			Paintera.registerStylesheets(dialogPane)
			dialogPane.buttonTypes += ButtonType.APPLY
			dialogPane.buttonTypes += ButtonType.CANCEL
			title = name

			val level = paintera.activeViewer.get()?.state?.bestMipMapLevel!!
			val levelResolution: DoubleArray = getLevelResolution(level)
			val replacementLabelField = NumberField.longField(Imglib2Label.BACKGROUND, { it >= Imglib2Label.BACKGROUND }, *SubmitOn.values())
			replacementLabelProperty.unbind()
			replacementLabelProperty.bind(replacementLabelField.valueProperty())

			val nextIdButton = Button().apply {
				styleClass += ADD_GLYPH
				graphic = FontAwesome[FontAwesomeIcon.PLUS, 2.0]
				onAction = EventHandler { replacementLabelField.valueProperty().set(labelSource.idService.next()) }
			}
			val minRes = levelResolution.min()
			val minKernelSize = floor(minRes / 2)
			val maxKernelSize = levelResolution.max() * 10
			val defaultKernelSize = minRes * 4
			val kernelSizeSlider = Slider(log10(minKernelSize), log10(maxKernelSize), log10(defaultKernelSize))
			val kernelSizeField = NumberField.doubleField(defaultKernelSize, { it > 0.0 }, *SubmitOn.values())

			/* slider sets field */
			kernelSizeSlider.valueProperty().addListener { _, _, sliderVal ->
				kernelSizeField.valueProperty().set(10.0.pow(sliderVal.toDouble()).roundToLong().toDouble())
			}
			/* field sets slider*/
			kernelSizeField.valueProperty().addListener { _, _, fieldVal ->
				kernelSizeSlider.valueProperty().set(log10(fieldVal.toDouble()))
			}
			/* size property binds field */
			kernelSizeProperty.unbind()
			kernelSizeProperty.bind(kernelSizeField.valueProperty())


			dialogPane.scene.cursorProperty().bind(paintera.baseView.node.cursorProperty())
			dialogPane.content = VBox(10.0).apply {
				val replacementIdLabel = Label("Replacement Label")
				val kernelSizeLabel = Label("Kernel Size (phyiscal units)")
				replacementIdLabel.alignment = Pos.BOTTOM_RIGHT
				kernelSizeLabel.alignment = Pos.BOTTOM_RIGHT
				children += HBox(10.0, replacementIdLabel, replacementLabelField.textField, nextIdButton)
				children += HBox(10.0, kernelSizeLabel, kernelSizeSlider, kernelSizeField.textField)
				HBox.setHgrow(kernelSizeLabel, Priority.NEVER)
				HBox.setHgrow(kernelSizeSlider, Priority.ALWAYS)
				HBox.setHgrow(kernelSizeField.textField, Priority.NEVER )
			}
			dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
				applyButton.disableProperty().bind(paintera.baseView.isDisabledProperty)
				applyButton.addEventFilter(ActionEvent.ACTION) { _ -> finalizeSmoothing = true }
			}
			dialogPane.lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION) { _ ->
				smoothTask?.cancel()
				close()
			}
			dialogPane.scene.window.setOnCloseRequest {
				smoothTask?.cancel()
				close()
			}
		}.show()
	}

	private fun getLevelResolution(level: Int): DoubleArray {
		val metadataScales = ((labelSource.backend as? SourceStateBackendN5<*, *>)?.getMetadataState() as? MultiScaleMetadataState)?.scaleTransforms?.get(level)
		val resFromRai = (labelSource.backend as? RandomAccessibleIntervalBackend<*, *>)?.resolutions
		return when {
			level == 0 -> labelSource.resolution
			metadataScales != null -> metadataScales.resolution
			resFromRai != null -> resFromRai[level]
			else -> doubleArrayOf(1.0, 1.0, 1.0)
		}
	}

	private val smoothingProperty = SimpleBooleanProperty("Smoothing", "Smooth Action is Running", false)
	private var smoothing by smoothingProperty.nonnull()

	private fun startSmoothTask() {
		var updateSmoothing = false
		val updateOnKernelSizeChange: ChangeListener<in Number> = ChangeListener { _, _, _ -> updateSmoothing = true }
		smoothTask = Tasks.createTask { task ->
			paintera.baseView.disabledPropertyBindings[task] = smoothingProperty
			kernelSizeProperty.addListener(updateOnKernelSizeChange)
			smoothing = true
			initializeSmoothLabel()
			smoothing = false
			while (!finalizeSmoothing && !task.isCancelled) {
				if (updateSmoothing) {
					smoothing = true
					updateSmoothMask(true)
					smoothing = false
					updateSmoothing = false
				}
				Thread.sleep(100)
			}
			updateSmoothMask(false)
		}.onCancelled { _, _ ->
			paintContext.dataSource.resetMasks()
			paintera.baseView.orthogonalViews().requestRepaint(globalSmoothInterval!!)
			paintera.baseView.disabledPropertyBindings -= smoothTask
		}.onSuccess { _, _ ->
			var refreshAfterApplyingMask: ChangeListener<Boolean>? = null
			refreshAfterApplyingMask = ChangeListener<Boolean> { obs, _, isApplyingMask ->
				if (!isApplyingMask) {
					paintera.baseView.orthogonalViews().requestRepaint(globalSmoothInterval)
					statePaintContext?.refreshMeshes?.invoke()
					obs.removeListener(refreshAfterApplyingMask!!)
					paintera.baseView.disabledPropertyBindings -= smoothTask
				}
			}
			val maskedSource = paintContext.dataSource
			maskedSource.isApplyingMaskProperty.addListener(refreshAfterApplyingMask)
			val mask = maskedSource.currentMask
			maskedSource.applyMask(mask, sourceSmoothInterval) { it.integerLong >= 0 }
		}.submit()
	}

	private fun initializeSmoothLabel() {

		val maskedSource = paintContext.dataSource as MaskedSource<*, *>
		val maskInfo = MaskInfo(0, currentMipMapLevel)
		val labels = paintContext.selectedIds.activeIds


		/* Read from the labelBlockLookup (if already persisted) */
		val blocksFromSource = labels.toArray().flatMap { paintContext.getBlocksForLabel(currentMipMapLevel, it).toList() }

		/* Read from canvas access (if in canvas) */
		val cellGrid = maskedSource.getCellGrid(0, currentMipMapLevel)
		val cellIntervals = cellGrid.cellIntervals().randomAccess()
		val cellPos = LongArray(cellGrid.numDimensions())
		val blocksFromCanvas = let {
			labels.toArray().flatMap {
				maskedSource.getModifiedBlocks(currentMipMapLevel, it).toArray().map { block ->
					cellGrid.getCellGridPositionFlat(block, cellPos)
					FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
				}
			}
		}

		val blocksWithLabel = blocksFromSource + blocksFromCanvas
		if (blocksWithLabel.isEmpty()) return

		setNewSourceMask(maskedSource, currentMipMapLevel, maskInfo)

		val sourceLabels = Converters.convert(
			maskedSource.getReadOnlyDataBackground(0, currentMipMapLevel),
			{ source, output -> output.set(source.realDouble.toLong()) },
			UnsignedLongType()
		)

		val collapsedLabelStack = Views.collapse(
			Views.stack(sourceLabels, maskedSource.getReadOnlyDataCanvas(0, currentMipMapLevel))
		)

		val virtualLabelMask = Converters.convert(collapsedLabelStack, { input, output ->
			for (i in 1 downTo 0) {
				val labelVal = input.get(i.toLong()).get()
				if (labelVal != Imglib2Label.INVALID) {
					output.set(if (labelVal in labels) 1.0 else 0.0)
					break
				}
			}
		}, DoubleType())

		val labelMask = Lazy.generate(virtualLabelMask, cellGrid.cellDimensions, DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE)) { output ->
			val input = virtualLabelMask.interval(output)
			LoopBuilder.setImages(input, output).multiThreaded().forEachPixel { inVal, outVal -> if (inVal.get() == 1.0) outVal.set(1.0) }
		}

		var labelRoi: Interval = FinalInterval(blocksWithLabel[0])
		blocksWithLabel.forEach { labelRoi = labelRoi union it }

		updateSmoothMask = { preview ->
			val blocksToSmoothOver = if (preview) pruneBlock(blocksWithLabel) else blocksWithLabel
			smoothMask(labelMask, cellGrid, blocksToSmoothOver, preview)
		}
		updateSmoothMask(true)
	}

	private fun pruneBlock(blocksWithLabel: List<Interval>): List<Interval> {
		val viewsInSourceSpace = viewerIntervalsInSourceSpace()

		/* remove any blocks that don't intersect with them*/
		return blocksWithLabel
			.filter { block -> viewsInSourceSpace.firstOrNull { viewer -> !Intervals.isEmpty(viewer.intersect(block)) } != null }
			.toList()
	}

	private fun viewerIntervalsInSourceSpace(): Array<FinalRealInterval> {
		/* get viewer screen intervals for each orthognal view in source space*/
		val viewerAndTransforms = paintera.baseView.orthogonalViews().viewerAndTransforms()
		val viewsInSourceSpace = viewerAndTransforms
			.map {
				val globalToViewerTransform = AffineTransform3D()
				it.viewer().state.getViewerTransform(globalToViewerTransform)
				val width = it.viewer().width
				val height = it.viewer().height
				val screenInterval = FinalInterval(width.toLong(), height.toLong(), 1L)
				val sourceToGlobal = AffineTransform3D()
				labelSource.getDataSource().getSourceTransform(0, currentMipMapLevel, sourceToGlobal)
				val viewerToSource = sourceToGlobal.inverse().copy().concatenate(globalToViewerTransform.inverse())
				viewerToSource.estimateBounds(screenInterval)
			}.toTypedArray()
		return viewsInSourceSpace
	}

	private fun smoothMask(labelMask: CachedCellImg<DoubleType, *>, cellGrid: CellGrid, blocksWithLabel: List<Interval>, preview : Boolean = false) {
		val levelResolution = getLevelResolution(currentMipMapLevel)
		val sigma = DoubleArray(3) { kernelSize / levelResolution[it] }

		val halfkernels = Gauss3.halfkernels(sigma)
		val symmetric = Kernel1D.symmetric(halfkernels)
		val smoothedImg = Lazy.generate(labelMask, cellGrid.cellDimensions, DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE)) {
			SeparableKernelConvolution.convolution(*symmetric).process(labelMask.extendZero(), it)
		}

		val mask = paintContext.dataSource.currentMask
		val viewerIntervalsInSource = viewerIntervalsInSourceSpace()

		runBlocking {
			coroutineScope {
				val smoothJobs = mutableListOf<Job>()
				for (block in blocksWithLabel) {
					if (smoothTask!!.isCancelled) return@coroutineScope

					if (preview) {
						for (interval in viewerIntervalsInSource) {
							val slice = interval.intersect(block)
							if (Intervals.isEmpty(slice)) continue
							smoothJobs += launch {
								LoopBuilder.setImages(
									labelMask.interval(slice),
									smoothedImg.interval(slice),
									mask.rai.interval(slice)
								).multiThreaded().forEachPixel { original, smooth, mask ->
									if (smoothTask!!.isCancelled) return@forEachPixel

									if (smooth.get() < 0.5 && original.get() == 1.0) {
										mask.setInteger(replacementLabel)
									} else if (mask.get() == replacementLabel) {
										mask.setInteger(Imglib2Label.INVALID)
									}
								}
							}
						}
					} else {
						smoothJobs += launch {
							LoopBuilder.setImages(
								labelMask.interval(block),
								smoothedImg.interval(block),
								mask.rai.interval(block)
							).multiThreaded().forEachPixel { original, smooth, mask ->
								if (smoothTask!!.isCancelled) return@forEachPixel

								if (smooth.get() < 0.5 && original.get() == 1.0) {
									mask.setInteger(replacementLabel)
								} else if (mask.get() == replacementLabel) {
									mask.setInteger(Imglib2Label.INVALID)
								}
							}
						}
					}


				}
				smoothJobs.forEach { it.join() }
			}
		}
		val maskedSource = paintContext.dataSource
		val maskInfo = mask.info

		var labelRoi: Interval = FinalInterval(blocksWithLabel[0])
		blocksWithLabel.forEach { labelRoi = labelRoi union it }
		sourceSmoothInterval = labelRoi
		globalSmoothInterval = maskedSource.getSourceTransformForMask(maskInfo).estimateBounds(sourceSmoothInterval)
		paintera.baseView.orthogonalViews().requestRepaint(globalSmoothInterval)
	}

	private fun setNewSourceMask(maskedSource: MaskedSource<*, *>, level: Int, maskInfo: MaskInfo) {
		val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(level)
		val mask = SourceMask(maskInfo, store, volatileStore.rai, store.cache, volatileStore.invalidate) { store.shutdown() }
		maskedSource.setMask(mask) { it.integerLong >= 0 }
	}
}
