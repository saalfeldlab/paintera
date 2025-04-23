package org.janelia.saalfeldlab.paintera.control.actions.paint

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.scene.control.ButtonType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.type.Type
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.IntervalView
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelState.Mode
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.dialogs.AnimatedProgressBarAlert
import org.janelia.saalfeldlab.util.convert
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.interval
import kotlin.coroutines.coroutineContext
import kotlin.jvm.optionals.getOrNull
import kotlin.math.nextUp
import net.imglib2.type.label.Label as ImgLib2Label

class ReplaceLabel(menuText: String, val mode: Mode) : MenuAction(menuText) {

	companion object {
		fun replaceMenu() = ReplaceLabel("_Replace Labels...", Mode.Replace)
		fun deleteMenu() = ReplaceLabel("_Delete Labels...", Mode.Delete)
	}

	private val LOG = KotlinLogging.logger { }

	init {
		verifyPermission(*mode.permissions)
		onActionWithState({ ReplaceLabelState(mode) }) {
			val title = "${mode.name} Labels"
			val buttonType = getDialog(title).showAndWait().getOrNull()
			if (buttonType != ButtonType.OK)
				return@onActionWithState

			val replacementLabel = replacementLabelProperty.value ?: return@onActionWithState

			val progressTextProperty = SimpleStringProperty()
			val progressProperty = SimpleDoubleProperty(0.0)

			val job = CoroutineScope(Dispatchers.Default).async {
				replaceLabels(replacementLabel, *fragmentsToReplace.toLongArray(), progressProperty = progressProperty, progressTextProperty = progressTextProperty)
				if (activateReplacementLabelProperty.value)
					sourceState.selectedIds.activate(replacementLabel)
			}
			job.invokeOnCompletion { cause ->
				cause?.printStackTrace()
			}

			AnimatedProgressBarAlert(title, "", progressTextProperty, progressProperty).apply {

				val cancelOrDone = canCancel.and(progressProperty.lessThan(1.0))
				canCancelProperty.bind(cancelOrDone)
				val buttonType = showAndWait().getOrNull()
				if (buttonType == ButtonType.CANCEL)
					job.cancel()
			}
		}
	}

	private fun <T : IntegerType<T>> ReplaceLabelState<*, *>.generateReplaceLabelMask(newLabel: Long, vararg fragments: Long) = with(maskedSource) {
		val dataSource = getDataSource(0, 0)

		val fragmentsSet = fragments.toHashSet()

		DiskCachedCellImgFactory(UnsignedLongType(Label.INVALID)).create(dataSource) { replacedLabelChunk ->
			val sourceCursor = dataSource.interval(replacedLabelChunk).cursor()
			val maskCursor = replacedLabelChunk.cursor()

			while (sourceCursor.hasNext() && maskCursor.hasNext()) {
				val sourceLabel = sourceCursor.next()
				val maskedLabel = maskCursor.next()

				val value = if (sourceLabel.integerLong in fragmentsSet) newLabel else ImgLib2Label.INVALID
				maskedLabel.set(value)
			}
		}
	}

	private suspend fun <T : IntegerType<T>> ReplaceLabelState<*, *>.replaceLabels(newLabel: Long, vararg oldLabels: Long, progressProperty: DoubleProperty?, progressTextProperty: StringProperty?) = with(maskedSource) {
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
		var operationText = "Processing "
		val actualizeBlocksProgress = SimpleDoubleProperty(0.0)
		val applyMaskProgress = SimpleDoubleProperty(0.0)
		progressProperty?.let {
			val totalProgressBinding = actualizeBlocksProgress.createObservableBinding(applyMaskProgress) {
				val actualizedProgress = actualizeBlocksProgress.get().coerceIn(0.0, 0.5)
				val applyProgress = applyMaskProgress.get().coerceIn(0.0, 0.5)

				/*Estimate blocks done per operation*/
				val blockProgress = 2 * (applyProgress.takeIf { it > 0.0 } ?: actualizedProgress)
				val blockEstimate = (blockProgress * numBlocks).nextUp().toLong().coerceAtMost(numBlocks.toLong())
				progressTextProperty?.value = "$operationText Blocks: $blockEstimate / $numBlocks"

				actualizedProgress + applyProgress
			}
			it.bind(totalProgressBinding)
		}

		/* actualize the blocks through the converter prior to apply, to give the
		* user a chance to cancel long-running operations without affecting the mask */
		blocks.forEachIndexed { idx, block ->
			coroutineContext.ensureActive()
			sourceMask.rai.interval(block).first().get()
			progressProperty?.let {
				InvokeOnJavaFXApplicationThread {
					actualizeBlocksProgress.set((idx + 1) / blocks.size.toDouble())
				}
			}
		}

		progressProperty?.let {
			InvokeOnJavaFXApplicationThread {
				operationText = "Applying"
			}
		}
		setMask(sourceMask) { it == newLabel }
		applyMaskOverIntervals(sourceMask, blocks, applyMaskProgress) { it == newLabel }

		requestRepaintOverIntervals(blocks)
		sourceState.refreshMeshes()
	}

	private fun ReplaceLabelState<*, *>.requestRepaintOverIntervals(sourceIntervals: List<Interval>? = null) {
		val globalInterval = sourceIntervals
			?.reduce(Intervals::union)
			?.let { maskedSource.getSourceTransformForMask(MaskInfo(0, 0)).estimateBounds(it) }

		paintera.baseView.orthogonalViews().requestRepaint(globalInterval)
	}

	fun ReplaceLabelState<*, *>.blocksForLabels(scale0: Int, vararg labels: Long): List<Interval> = with(maskedSource) {
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
	replacedFragmentMask: IntervalView<UnsignedLongType>,
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
