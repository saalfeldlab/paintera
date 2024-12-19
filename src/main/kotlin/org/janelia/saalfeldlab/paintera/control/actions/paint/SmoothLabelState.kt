package org.janelia.saalfeldlab.paintera.control.actions.paint

import javafx.event.Event
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.paintera.control.actions.ActionState
import org.janelia.saalfeldlab.paintera.control.actions.verify
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import kotlin.properties.Delegates

//TODO Caleb: Separate Smooth UI from SmoothAction
class SmoothLabelState : ActionState() {
	internal lateinit var labelSource: ConnectomicsLabelState<*, *>
	internal lateinit var paintContext: StatePaintContext<*, *>
	internal var mipMapLevel by Delegates.notNull<Int>()

	val defaultKernelSize: Double
		get() {
			val levelResolution = getLevelResolution(mipMapLevel)
			val min = levelResolution.min()
			val max = levelResolution.max()
			return min + (max - min) / 2.0
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

	companion object {
		private val AffineTransform3D.resolution
			get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])
	}


	override fun <E : Event> Action<E>.verifyState() {
		verify(::labelSource, "Label Source is Active") { paintera.currentSource as? ConnectomicsLabelState<*, *> }
		verify(::paintContext, "Paint Label Mode has StatePaintContext") { PaintLabelMode.statePaintContext }
		verify(::mipMapLevel, "Viewer is Focused") { paintera.activeViewer.get()?.state?.bestMipMapLevel }

		verify("Paint Label Mode is Active") { paintera.currentMode is PaintLabelMode }
		verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
		verify("Mask is in Use") { !paintContext.dataSource.isMaskInUseBinding().get() }
	}
}