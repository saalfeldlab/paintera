package org.janelia.saalfeldlab.paintera.control.actions.paint

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.event.ActionEvent
import javafx.event.Event
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.type.Type
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.IntervalView
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.onAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelState.Mode
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.convert
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.interval
import kotlin.math.nextUp
import net.imglib2.type.label.Label as ImgLib2Label

class ReplaceLabel(menuText: String, val mode: Mode) : MenuAction(menuText) {

	companion object {
		fun replaceMenu() = ReplaceLabel("_Replace Labels...", Mode.Replace)
		fun deleteMenu() = ReplaceLabel("_Delete Labels...", Mode.Delete)
	}

	private val LOG = KotlinLogging.logger { }

	init {
		val permissions = when (mode) {
			Mode.Replace -> arrayOf(PaintActionType.Fill)
			Mode.Delete -> arrayOf(PaintActionType.Erase, PaintActionType.Background)
			Mode.All -> arrayOf(PaintActionType.Fill, PaintActionType.Erase, PaintActionType.Background)
		}
		verifyPermission(*permissions)
		onAction<ReplaceLabelState> {
			initializeForMode(mode)
			showDialog(it)
		}
	}

	private fun <T : IntegerType<T>> ReplaceLabelState.generateReplaceLabelMask(newLabel: Long, vararg fragments: Long) = with(maskedSource) {
		val dataSource = getDataSource(0, 0)

		val fragmentsSet = fragments.toHashSet()
		dataSource.convert(UnsignedLongType(ImgLib2Label.INVALID)) { src, target ->
			val value = if (src.integerLong in fragmentsSet) newLabel else ImgLib2Label.INVALID
			target.set(value)
		}.interval(dataSource)
	}

	private fun <T : IntegerType<T>> ReplaceLabelState.replaceLabels(newLabel: Long, vararg oldLabels: Long) = with(maskedSource) {
		val blocks = blocksForLabels(0, *oldLabels)
		val replacedLabelMask = generateReplaceLabelMask(newLabel, *oldLabels)

		val sourceMask = SourceMask(
			MaskInfo(0, 0),
			replacedLabelMask,
			replacedLabelMask.convert(VolatileUnsignedLongType(ImgLib2Label.INVALID)) { input, output ->
				output.set(input.integerLong)
				output.isValid = true
			}.interval(replacedLabelMask),
			null,
			null
		) {}


		val numBlocks = blocks.size
		val progressTextBinding = progressProperty.createObservableBinding {
			val blocksDone = (it.doubleValue() * numBlocks).nextUp().toLong().coerceAtMost(numBlocks.toLong())
			"Blocks: $blocksDone / $numBlocks"
		}
		InvokeOnJavaFXApplicationThread {
			progressTextProperty.bind(progressTextBinding)
		}

		setMask(sourceMask) { it == newLabel }
		applyMaskOverIntervals(sourceMask, blocks, progressProperty) { it == newLabel }

		requestRepaintOverIntervals(blocks)
		sourceState.refreshMeshes()
	}

	private fun ReplaceLabelState.showDialog(event: Event?) {
		Dialog<Boolean>().apply {
			isResizable = true
			PainteraAlerts.initAppDialog(this)
			Paintera.registerStylesheets(dialogPane)
			dialogPane.buttonTypes += ButtonType.APPLY
			dialogPane.buttonTypes += ButtonType.CANCEL
			title = name?.replace("_", "")

			dialogPane.content = ReplaceLabelUI(this@showDialog, mode)
			dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
				val disableApply = paintera.baseView.isDisabledProperty.or(replacementLabelProperty.isNull)
				applyButton.disableProperty().bind(disableApply)
				applyButton.cursorProperty().bind(paintera.baseView.node.cursorProperty())
				applyButton.addEventFilter(ActionEvent.ACTION) { event ->
					event.consume()
					val replacementLabel = replacementLabelProperty.value ?: return@addEventFilter
					val fragmentsToReplace = fragmentsToReplace.toLongArray()
					val validForMode = when (mode) {
						Mode.Replace -> replacementLabel > 0
						Mode.Delete -> replacementLabel == 0L
						Mode.All -> replacementLabel >= 0
					}
					if (!validForMode) {
						LOG.warn { "Cannot ${mode.name} Labels, invalid replacement label $replacementLabel for mode ($mode)" }
						return@addEventFilter
					}
					if (fragmentsToReplace.isEmpty()) {
						LOG.warn { "Cannot ${mode.name} Labels, no fragment IDs selected" }
						return@addEventFilter
					}
					CoroutineScope(Dispatchers.Default).async {
						replaceLabels(replacementLabel, *fragmentsToReplace)
						if (activateReplacementLabelProperty.value)
							sourceState.selectedIds.activate(replacementLabel)
					}
				}
			}
			dialogPane.lookupButton(ButtonType.CANCEL).disableProperty().bind(paintContext.dataSource.isApplyingMaskProperty())
			dialogPane.scene.window.setOnCloseRequest {
				if (paintContext.dataSource.isApplyingMaskProperty().get())
					it.consume()
			}
		}.show()
	}

	private fun ReplaceLabelState.requestRepaintOverIntervals(sourceIntervals: List<Interval>? = null) {
		val globalInterval = sourceIntervals
			?.reduce(Intervals::union)
			?.let { maskedSource.getSourceTransformForMask(MaskInfo(0, 0)).estimateBounds(it) }

		paintera.baseView.orthogonalViews().requestRepaint(globalInterval)
	}

	fun ReplaceLabelState.blocksForLabels(scale0: Int, vararg labels: Long): List<Interval> = with(maskedSource) {
		val blocksFromSource = labels.flatMap { sourceState.labelBlockLookup.read(LabelBlockLookupKey(scale0, it)).toList() }

		/* Read from canvas access (if in canvas) */
		val cellGrid = getCellGrid(0, scale0)
		val cellIntervals = cellGrid.cellIntervals().randomAccess()
		val cellPos = LongArray(cellGrid.numDimensions())
		val blocksFromCanvas = labels.flatMap {
			getModifiedBlocks(scale0, it).toArray().map { block ->
				cellGrid.getCellGridPositionFlat(block, cellPos)
				FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
			}
		}

		return blocksFromSource + blocksFromCanvas
	}
}

private fun <T : IntegerType<T>> MaskedSource<T, out Type<*>>.addReplaceMaskAsSource(
	replacedFragmentMask: IntervalView<UnsignedLongType>
): ConnectomicsLabelState<UnsignedLongType, VolatileUnsignedLongType> {
	val metadataState = (underlyingSource() as? N5DataSource)?.getMetadataState()!!
	return paintera.baseView.addConnectomicsLabelSource<UnsignedLongType, VolatileUnsignedLongType>(
		replacedFragmentMask,
		metadataState.resolution,
		metadataState.translation,
		1L,
		"fragmentMask",
		LabelBlockLookupAllBlocks.fromSource(underlyingSource())
	)!!
}
