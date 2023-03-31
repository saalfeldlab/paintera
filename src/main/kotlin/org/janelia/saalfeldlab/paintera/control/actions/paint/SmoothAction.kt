package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ChangeListener
import javafx.event.ActionEvent
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.MenuItem
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.algorithm.convolution.kernel.Kernel1D
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution
import net.imglib2.algorithm.gauss3.Gauss3
import net.imglib2.algorithm.lazy.Lazy
import net.imglib2.converter.Converters
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.loops.LoopBuilder
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.view.BundleView
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.ui.DoubleField
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode.activeSourceStateProperty
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode.statePaintContext
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.union
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger


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

	init {
		verify("Label Source is Active") { paintera.currentSource is ConnectomicsLabelState<*, *> }
		verify("Paint Label Mode is Active") { paintera.currentMode is PaintLabelMode }
		verify { !paintera.baseView.isDisabledProperty.get() }
		onAction { showSmoothDialog() }
	}

	private fun showSmoothDialog() {
		Dialog<Boolean>().apply {
			dialogPane.buttonTypes += ButtonType.APPLY
			dialogPane.buttonTypes += ButtonType.CANCEL
			title = name
			val sigma = DoubleField(1.0)
			dialogPane.content = VBox(10.0).apply {
				children += HBox(10.0, javafx.scene.control.Label("sigma"), sigma.textField())
			}
			dialogPane.lookupButton(ButtonType.APPLY).addEventFilter(ActionEvent.ACTION) { _ ->
				startSmoothTask(sigma)
			}
			dialogPane.lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION) { _ ->
				smoothTask?.cancel()
				close()

			}
		}.show()
	}


	private fun startSmoothTask(sigma: DoubleField) {
		smoothTask = Tasks.createTask { task ->
			paintera.baseView.disabledPropertyBindings[task] = SimpleBooleanProperty(true, "Smooth Action is Running")
			smoothLabel(sigma.valueProperty().get())
		}.onEnd { _ ->
			paintera.baseView.disabledPropertyBindings -= smoothTask
		}.submit()
	}

	private fun smoothLabel(sigma: Double) {
		(paintera.currentMode as? PaintLabelMode)?.statePaintContext?.let { stateCtx ->
			(stateCtx.dataSource as? MaskedSource<*, *>)?.let { maskedSource ->
				val label = stateCtx.selectedIds.lastSelection

				val level = 0
				val maskInfo = MaskInfo(0, level)


				/* Read from the labelBlockLookup (if already persisted) */
				val blocksFromSource = stateCtx.getBlocksForLabel(level, label)

				/* Read from canvas access (if in canvas) */
				val cellGrid = maskedSource.getCellGrid(0, level)
				val cellIntervals = cellGrid.cellIntervals().randomAccess()
				val cellPos = LongArray(cellGrid.numDimensions())
				val blocksFromCanvas = maskedSource.getModifiedBlocks(level, label).toArray().map { block ->
					cellGrid.getCellGridPositionFlat(block, cellPos)
					FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
				}.toTypedArray()

				val blocksWithLabel = blocksFromSource + blocksFromCanvas

				if (blocksWithLabel.isEmpty()) return

				val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(0)
				val mask = SourceMask(maskInfo, store, volatileStore.rai, store.getCache(), volatileStore.invalidate) { store.shutdown() }
				maskedSource.setMask(mask) { Label.isForeground(it.integerLong)}

				val sourceLabels = Converters.convert(
					maskedSource.getReadOnlyDataBackground(0, level),
					{ source, output -> output.set(source.realDouble.toLong()) },
					UnsignedLongType()
				)

				val stack = Views.stack(
					sourceLabels,
					maskedSource.getReadOnlyDataCanvas(0, level),
					Views.extendZero(mask.rai).interval(sourceLabels)
				)
				val labels = Converters.convert(Views.collapse(stack), {input, output ->
					for (i in 2 downTo 0) {
						val labelVal = input.get(i.toLong()).get()
						if (labelVal != Label.INVALID && labelVal != 0L) {
							output.set(labelVal)
							break
						}
					}
				}, UnsignedLongType(Label.INVALID))

				val bundledLabels = BundleView(labels).interval(labels)

				val labelMask = Converters.convert(
					labels,
					{ labelData: UnsignedLongType, bool: DoubleType -> bool.set(if (labelData.integerLong == label) 1.0 else 0.0) },
					DoubleType()
				)

				var labelRoi: Interval = FinalInterval(blocksWithLabel[0])
				blocksWithLabel.forEach { labelRoi = labelRoi union it }

				(activeSourceStateProperty.get() as? ConnectomicsLabelState<*, *>)?.let { sourceState ->
					val smoothedImg = Lazy.generate(labelMask, cellGrid.cellDimensions, DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE)) {
						val sigmas = DoubleArray(3) { sigma / sourceState.resolution[it] }
						val halfkernels = Gauss3.halfkernels(sigmas)
						val symmetric = Kernel1D.symmetric(halfkernels)

						SeparableKernelConvolution.convolution(*symmetric).apply {
							setExecutor(Executors.newCachedThreadPool())
							process(Views.extendZero(labelMask), it)
						}
					}
					val nextTemporary = sourceState.idService.nextTemporary()
					LoopBuilder.setImages(
						Views.extendZero(smoothedImg).interval(labelRoi),
						mask.rai.interval(labelRoi),
						labelMask.interval(labelRoi)
					).multiThreaded().forEachPixel { smooth, source, original ->
						val smoothVal = smooth.get()
						if (smoothVal >= 0.5) {
							source.setInteger(label)
						} else if (original.get() == 1.0) {
							source.setInteger(nextTemporary)
						}
					}

					val bundledLabelRoi = BundleView(mask.rai).interval(labelRoi)
					var prevTempCount = 0
					val remainingTmpCount = AtomicInteger(-1)
					while (remainingTmpCount.get() != 0) {

						val setTransparent =  remainingTmpCount.get() == prevTempCount
						prevTempCount = remainingTmpCount.getAndSet(0)

						LoopBuilder.setImages(bundledLabelRoi).multiThreaded().forEachChunk { chunk ->
							chunk.forEachPixel { labelType ->
								if (setTransparent) {
									remainingTmpCount.set(0)
									return@forEachPixel
								}
								val maskLabel = labelType.get()
								if (maskLabel.get() == nextTemporary) {
									val neighborhoodsRA = bundledLabels.getAt(labelType)
									val resolutionNeighborMap = mutableMapOf<Long, Double>()
									for (i in 0 until sourceState.resolution.size) {
										neighborhoodsRA.move(1, i)
										var neighborLabel = neighborhoodsRA.get().get()
										val resolutionIsHigher = sourceState.resolution[i] < (resolutionNeighborMap[neighborLabel] ?: Double.POSITIVE_INFINITY)
										val validLabel = neighborLabel != nextTemporary && neighborLabel != label

										if (validLabel && resolutionIsHigher) {
											resolutionNeighborMap[neighborLabel] = sourceState.resolution[i]
										}
										neighborhoodsRA.move(-2, i)
										neighborLabel = neighborhoodsRA.get().get()
										if (validLabel && resolutionIsHigher) {
											resolutionNeighborMap[neighborLabel] = sourceState.resolution[i]
										}
										neighborhoodsRA.move(1, i)
									}
									resolutionNeighborMap.minByOrNull { it.value }?.key?.let {
										maskLabel.set(it)
									} ?: remainingTmpCount.incrementAndGet()

								}

							}
						}
					}

					maskedSource.applyMask(mask, labelRoi) { Label.isForeground(it.integerLong) }
					val repaintInterval = maskedSource.getSourceTransformForMask(maskInfo).estimateBounds(labelRoi)
					var refreshAfterApplyingMask: ChangeListener<Boolean>? = null
					refreshAfterApplyingMask = ChangeListener<Boolean> { obs, _, isApplyingMask ->
						if (!isApplyingMask) {
							paintera.baseView.orthogonalViews().requestRepaint(repaintInterval)
							statePaintContext?.refreshMeshes?.invoke()
							obs.removeListener(refreshAfterApplyingMask!!)
						}
					}
					maskedSource.isApplyingMaskProperty.addListener(refreshAfterApplyingMask)
				}


			}
		}
	}
}
