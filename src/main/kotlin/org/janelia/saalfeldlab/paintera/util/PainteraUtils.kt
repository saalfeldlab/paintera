package org.janelia.saalfeldlab.paintera.util

import net.imglib2.FinalInterval
import net.imglib2.RealInterval
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState

object PainteraUtils {

	fun ViewerPanelFX.intervalInSourceSpace(source : SourceState<*, *>, scaleLevel: Int = 0, time : Int = 0) : RealInterval? {
		val sourceToGlobalTransform = AffineTransform3D().also { transform -> source.dataSource.getSourceTransform(time, scaleLevel, transform) }
		return intervalInSourceSpace(sourceToGlobalTransform)
	}

	fun ViewerPanelFX.intervalInSourceSpace(sourceToGlobalTransform : AffineTransform3D) : RealInterval? {
		val viewerToGlobalTransform = AffineTransform3D().also { transform -> state.getViewerTransform(transform) }.inverse()
		val globalToSourceTransform = sourceToGlobalTransform.copy().inverse()
		val viewerToSourceTransform = globalToSourceTransform.concatenate(viewerToGlobalTransform)

		val screenInterval = FinalInterval(width.toLong(), height.toLong(), 1L)
		return viewerToSourceTransform.estimateBounds(screenInterval)
	}

	fun <S : SourceState<*, *>> S.viewerIntervalsInSourceSpace(time: Int = 0, scaleLevel : Int = 0, filterSourceInterval : ((RealInterval) -> RealInterval?)? = null) : Set<RealInterval?> {
		return paintera.baseView.orthogonalViews().viewerAndTransforms().mapNotNullTo(mutableSetOf()) { viewerAndTransform ->
			viewerAndTransform.viewer().intervalInSourceSpace(this, scaleLevel, time)?.let {
				filterSourceInterval?.invoke(it) ?: it
			}
		}
	}
}