package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.property.*
import javafx.event.ActionEvent
import javafx.event.Event
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import kotlinx.coroutines.cancel
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.actions.ActionState
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.finalizeSmoothing
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.scopeJob
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.smoothJob
import org.janelia.saalfeldlab.paintera.control.actions.verify
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import kotlin.math.roundToInt

internal interface SmoothLabelUIState {
	val labelSelectionProperty: Property<LabelSelection>
	val infillStrategyProperty: Property<InfillStrategy>
	val replacementLabelProperty: LongProperty
	val activateReplacementLabelProperty: BooleanProperty
	val kernelSizeProperty: IntegerProperty
	val defaultKernelSize: Int
	val resolution: DoubleArray
	val statusProperty: Property<SmoothStatus>
	val progressProperty: DoubleProperty

	fun newId(): Long
}

internal enum class LabelSelection() {
	ActiveFragments,
	ActiveSegments
}

internal enum class InfillStrategy() {
	Replace,
	Background,
	NearestLabel

}

internal enum class SmoothStatus(val text: String) {
	Smoothing("Smoothing... "),
	Done("        Done "),
	Applying(" Applying... "),
	Empty("             ")
}

internal class SmoothLabelState : ActionState<SmoothLabelState>, SmoothLabelUIState {
	internal lateinit var labelSource: ConnectomicsLabelState<*, *>
	private lateinit var paintContext: StatePaintContext<*, *>
	internal lateinit var viewer: ViewerPanelFX

	override fun <E : Event> Action<E>.verifyState() {
		verify(::labelSource, "Label Source is Active") { paintera.currentSource as? ConnectomicsLabelState<*, *> }
		verify(::paintContext, "Get paintContext from active PaintLabelMode") { (paintera.currentMode as? PaintLabelMode)?.statePaintContext }
		verify(::viewer, "Viewer Detected") { paintera.baseView.lastFocusHolder.value?.viewer() }

		verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
		verify("Mask is in Use") { !dataSource.isMaskInUseBinding().get() }
	}

	override fun copyVerified(): SmoothLabelState {
		return SmoothLabelState().also {
			it.labelSource = labelSource
			it.paintContext = paintContext
			it.viewer = viewer
		}
	}


	val dataSource by lazy { paintContext.dataSource }
	val selectedIds by lazy { paintContext.selectedIds }
	fun refreshMeshes() = paintContext.refreshMeshes()

	internal val mipMapLevel by lazy { viewer.state.bestMipMapLevel }
	override val resolution by LazyForeignValue(::mipMapLevel) { getLevelResolution(it) }

	override val defaultKernelSize: Int
		get() {
			val min = resolution.min()
			val max = resolution.max()
			return (min + (max - min) / 2.0).roundToInt()
		}

	override val labelSelectionProperty = SimpleObjectProperty(LabelSelection.ActiveFragments)
	override val infillStrategyProperty = SimpleObjectProperty(InfillStrategy.NearestLabel)
	override val replacementLabelProperty by lazy { createReplacementLabelProperty() }
	override val activateReplacementLabelProperty by lazy { createActivateReplacementLabelProperty() }
	override val kernelSizeProperty by lazy { SimpleIntegerProperty(defaultKernelSize) }

	override val statusProperty = SimpleObjectProperty(SmoothStatus.Empty)
	override val progressProperty by lazy { createProgressProperty() }
	var progress
		get() = progressProperty.get()
		set(value) = progressProperty.set(value)

	override fun newId() = labelSource.idService.next()

	private fun createReplacementLabelProperty() = SimpleLongProperty(0).apply {
		subscribe { prev, cur -> activateReplacementLabel(prev.toLong(), cur.toLong()) }
	}

	private fun createActivateReplacementLabelProperty() = SimpleBooleanProperty(false).apply {
		subscribe { _, activate ->
			val replacementLabel = replacementLabelProperty.get()
			if (activate)
				activateReplacementLabel(0L, replacementLabel)
			else
				activateReplacementLabel(replacementLabel, 0L)
		}
	}

	private fun createProgressProperty() = SimpleDoubleProperty(0.0).apply {
		subscribe { progress ->
			val progress = progress.toDouble()
			val isApplyMask = dataSource.isApplyingMaskProperty()
			statusProperty.value = when {
				progress == 0.0 -> SmoothStatus.Empty
				progress == 1.0 -> SmoothStatus.Done
				isApplyMask.get() -> SmoothStatus.Applying
				scopeJob?.isActive == true -> SmoothStatus.Smoothing
				else -> SmoothStatus.Empty
			}
		}
	}

	private fun activateReplacementLabel(current: Long, next: Long) {
		if (current != selectedIds.lastSelection)
			selectedIds.deactivate(current)

		if (activateReplacementLabelProperty.get() && next > 0L)
			selectedIds.activateAlso(selectedIds.lastSelection, next)
	}

	fun getLevelResolution(level: Int): DoubleArray {

		if (level == 0)
			return labelSource.resolution

		val n5Backend = labelSource.backend as? SourceStateBackendN5<*, *>
		val metadataState = n5Backend?.metadataState as? MultiScaleMetadataState
		val metadataScales = metadataState?.scaleTransforms?.get(level)
		if (metadataScales != null)
			return metadataScales.resolution

		(labelSource.backend as? RandomAccessibleIntervalBackend<*, *>)?.resolutions?.get(level)?.let { resolution ->
			return resolution
		}

		return doubleArrayOf(1.0, 1.0, 1.0)
	}

	fun SmoothLabelState.showSmoothDialog(title: String = "Smooth Label") = Dialog<Boolean>().apply {
		Paintera.registerStylesheets(dialogPane)
		dialogPane.buttonTypes += ButtonType.APPLY
		dialogPane.buttonTypes += ButtonType.CANCEL
		this.title = title
		this.dialogPane.content = SmoothLabelUI(this@showSmoothDialog)

		PainteraAlerts.initAppDialog(this)
		val cleanupOnDialogClose = {
			scopeJob?.cancel()
			smoothJob?.cancel()
			close()
		}
		dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
			applyButton.disableProperty().bind(paintera.baseView.isDisabledProperty)
			applyButton.cursorProperty().bind(paintera.baseView.node.cursorProperty())
			applyButton.addEventFilter(ActionEvent.ACTION) { event ->
				//So the dialog doesn't close until the smoothing is done
				event.consume()
				// but listen for when the smoothTask finishes
				smoothJob?.invokeOnCompletion { cause ->
					cause?.let {
						cleanupOnDialogClose()
					}
				}
				// indicate the smoothTask should try to apply the current smoothing mask to canvas
				progress = 0.0
				finalizeSmoothing = true
			}
		}
		val cancelButton = dialogPane.lookupButton(ButtonType.CANCEL)
		cancelButton.disableProperty().bind(dataSource.isApplyingMaskProperty())
		cancelButton.addEventFilter(ActionEvent.ACTION) { _ -> cleanupOnDialogClose() }
		dialogPane.scene.window.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
			if (event.code == KeyCode.ESCAPE && (statusProperty.get() == SmoothStatus.Smoothing || (scopeJob?.isActive == true))) {
				/* Cancel if still running */
				event.consume()
				scopeJob?.cancel("Escape Pressed")
				progress = 0.0
			}
		}
		dialogPane.scene.window.setOnCloseRequest {
			if (!dataSource.isApplyingMaskProperty().get()) {
				cleanupOnDialogClose()
			}

		}
		show()
	}

	companion object {
		private val AffineTransform3D.resolution
			get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])
	}
}