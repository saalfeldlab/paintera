package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.Event
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.control.actions.ActionState
import org.janelia.saalfeldlab.paintera.control.actions.verify
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import kotlin.math.floor

//TODO Caleb: Separate Smooth UI from SmoothAction
class SmoothLabelState : ActionState, SmoothLabelUIState {
	internal lateinit var labelSource: ConnectomicsLabelState<*, *>
	internal lateinit var paintContext: StatePaintContext<*, *>
	internal lateinit var viewer: ViewerPanelFX
	internal val mipMapLevel
		get() = viewer.state.bestMipMapLevel

	val levelResolution by LazyForeignValue(::mipMapLevel) lazy@{

		return@lazy getLevelResolution(it)
	}

	val defaultKernelSize: Double
		get() {
			val min = levelResolution.min()
			val max = levelResolution.max()
			return min + (max - min) / 2.0
		}

	override val replacementLabelProperty = SimpleObjectProperty<Long?>(0L)
	override val activateReplacementLabelProperty = SimpleBooleanProperty(true)
	override val minKernelSize by lazy { floor(levelResolution.min() / 2) }
	override val maxKernelSize by lazy { levelResolution.max() * 10 }
	override val kernelSizeProperty by lazy { SimpleObjectProperty(defaultKernelSize) }
	override val statusProperty = SimpleStringProperty("")
	override val progressProperty = SimpleDoubleProperty(0.0)
	override val isApplyingMaskProperty by lazy { paintContext.dataSource.isApplyingMaskProperty }

	override fun nextNewId() = labelSource.idService.next()

	fun getLevelResolution(level: Int) : DoubleArray {
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

	companion object {
		private val AffineTransform3D.resolution
			get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])
	}

	override fun <E : Event> Action<E>.verifyState() {
		verify(::labelSource, "Label Source is Active") { paintera.currentSource as? ConnectomicsLabelState<*, *> }
		verify(::paintContext, "Get paintContext from active PaintLabelMode") {
			val paintLabelModeIsActive = paintera.currentMode as? PaintLabelMode
			paintLabelModeIsActive?.statePaintContext
		}
		verify(::viewer, "Viewer Detected") { paintera.baseView.lastFocusHolder.value?.viewer() }

		verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
		verify("Mask is in Use") { !paintContext.dataSource.isMaskInUseBinding().get() }
	}

	override fun copyVerified() = SmoothLabelState().also {
		it.labelSource = this@SmoothLabelState.labelSource
		it.paintContext = this@SmoothLabelState.paintContext
		it.viewer = this@SmoothLabelState.viewer
	}
}