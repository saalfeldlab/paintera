package org.janelia.saalfeldlab.paintera.control.actions.paint

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
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
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.onAction
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
import net.imglib2.type.label.Label as Imglib2Label

object ReplaceLabel : MenuAction("_Replace or Delete Label...") {

	private val LOG = KotlinLogging.logger { }

	private val state = ReplaceLabelState()

	private val replacementLabelProperty = state.replacementLabel
	private val replacementLabel by replacementLabelProperty.nonnullVal()

	private val activateReplacementProperty = state.activeReplacementLabel.apply {
		subscribe { _, activate ->
			if (activate)
				activateReplacementLabel(0L, replacementLabel)
			else
				activateReplacementLabel(replacementLabel, 0L)
		}
	}
	private val activateReplacement by activateReplacementProperty.nonnull()

	private val progressProperty = SimpleDoubleProperty()
	private val progressTextProperty = SimpleStringProperty()

	init {
		verifyPermission(PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onAction(state) {
			showDialog()
		}
	}

	private fun <T : IntegerType<T>> ReplaceLabelState<T>.generateReplaceLabelMask(newLabel: Long, vararg fragments: Long) = with(maskedSource) {
		val dataSource = getDataSource(0, 0)

		val fragmentsSet = fragments.toHashSet()
		dataSource.convert(UnsignedLongType(Imglib2Label.INVALID)) { src, target ->
			val value = if (src.integerLong in fragmentsSet) newLabel else Imglib2Label.INVALID
			target.set(value)
		}.interval(dataSource)
	}


	private fun <T : IntegerType<T>> ReplaceLabelState<T>.deleteActiveFragment() = replaceActiveFragment(0L)
	private fun <T : IntegerType<T>> ReplaceLabelState<T>.deleteActiveSegment() = replaceActiveSegment(0L)
	private fun <T : IntegerType<T>> ReplaceLabelState<T>.deleteAllActiveFragments() = replaceAllActiveFragments(0L)
	private fun <T : IntegerType<T>> ReplaceLabelState<T>.deleteAllActiveSegments() = replaceAllActiveSegments(0L)
	private fun <T : IntegerType<T>> ReplaceLabelState<T>.replaceActiveFragment(newLabel: Long) = replaceLabels(newLabel, activeFragment)
	private fun <T : IntegerType<T>> ReplaceLabelState<T>.replaceActiveSegment(newLabel: Long) = replaceLabels(newLabel, *fragmentsForActiveSegment)
	private fun <T : IntegerType<T>> ReplaceLabelState<T>.replaceAllActiveFragments(newLabel: Long) = replaceLabels(newLabel, *allActiveFragments)
	private fun <T : IntegerType<T>> ReplaceLabelState<T>.replaceAllActiveSegments(newLabel: Long) = replaceLabels(newLabel, *fragmentsForActiveSegment)


	private fun <T : IntegerType<T>> ReplaceLabelState<T>.replaceLabels(newLabel: Long, vararg oldLabels: Long) = with(maskedSource) {
		val blocks = blocksForLabels(0, *oldLabels)
		val replacedLabelMask = generateReplaceLabelMask(newLabel, *oldLabels)

		val sourceMask = SourceMask(
			MaskInfo(0, 0),
			replacedLabelMask,
			replacedLabelMask.convert(VolatileUnsignedLongType(Imglib2Label.INVALID)) { input, output ->
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
			progressTextProperty.unbind()
			progressTextProperty.bind(progressTextBinding)
		}

		setMask(sourceMask) { it == newLabel }
		applyMaskOverIntervals(
			sourceMask,
			blocks,
			progressProperty
		) { it == newLabel }

		requestRepaintOverIntervals(blocks)
		sourceState.refreshMeshes()
	}

	private fun activateReplacementLabel(current: Long, next: Long) {
		val selectedIds = state.paintContext.selectedIds
		if (current != selectedIds.lastSelection) {
			selectedIds.deactivate(current)
		}
		if (activateReplacement && next > 0L) {
			selectedIds.activateAlso(selectedIds.lastSelection, next)
		}
	}

	fun showDialog() = state.showDialog()

	private fun ReplaceLabelState<*>.showDialog() {
		Dialog<Boolean>().apply {
			isResizable = true
			PainteraAlerts.initAppDialog(this)
			Paintera.registerStylesheets(dialogPane)
			dialogPane.buttonTypes += ButtonType.APPLY
			dialogPane.buttonTypes += ButtonType.CANCEL
			title = name?.replace("_", "")

			progressTextProperty.unbind()
			progressTextProperty.set("")

			progressProperty.unbind()
			progressProperty.set(0.0)

			val replaceLabelUI = ReplaceLabelUI(state)
			replaceLabelUI.progressBarProperty.bind(progressProperty)
			replaceLabelUI.progressLabelText.bind(progressTextProperty)

			dialogPane.content = replaceLabelUI
			dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
				applyButton.disableProperty().bind(paintera.baseView.isDisabledProperty)
				applyButton.cursorProperty().bind(paintera.baseView.node.cursorProperty())
				applyButton.addEventFilter(ActionEvent.ACTION) { event ->
					event.consume()
					val replacementLabel = state.replacementLabel.value
					val fragmentsToReplace = state.fragmentsToReplace.toLongArray()
					if (replacementLabel != null && replacementLabel >= 0 && fragmentsToReplace.isNotEmpty()) {
						CoroutineScope(Dispatchers.Default).async {
							replaceLabels(replacementLabel, *fragmentsToReplace)
							if (activateReplacement)
								state.sourceState.selectedIds.activate(replacementLabel)
						}
					}
				}
			}
			dialogPane.lookupButton(ButtonType.CANCEL).apply {
				disableProperty().bind(paintContext.dataSource.isApplyingMaskProperty())
			}
			dialogPane.scene.window.setOnCloseRequest {
				if (paintContext.dataSource.isApplyingMaskProperty().get())
					it.consume()
			}
		}.show()
	}

	private fun ReplaceLabelState<*>.requestRepaintOverIntervals(sourceIntervals: List<Interval>? = null) {
		val globalInterval = sourceIntervals
			?.reduce(Intervals::union)
			?.let { maskedSource.getSourceTransformForMask(MaskInfo(0, 0)).estimateBounds(it) }

		paintera.baseView.orthogonalViews().requestRepaint(globalInterval)
	}

	fun ReplaceLabelState<*>.blocksForLabels(scale0: Int, vararg labels: Long): List<Interval> = with(maskedSource) {
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
