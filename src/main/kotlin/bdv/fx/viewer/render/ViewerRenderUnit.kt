package bdv.fx.viewer.render

import bdv.cache.CacheControl
import bdv.fx.viewer.ViewerState
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.render.AccumulateProjectorFactory
import javafx.scene.image.Image
import net.imglib2.parallel.TaskExecutor
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool.viewerTransform
import java.util.function.Function
import java.util.function.Supplier

/**
 * Manages rendering of arbitrary intervals from a [ViewerState].
 */
open class ViewerRenderUnit(
	threadGroup: ThreadGroup,
	protected val viewerStateSupplier: Supplier<ViewerState?>,
	interpolation: Function<Source<*>, Interpolation>,
	accumulateProjectorFactory: AccumulateProjectorFactory<ARGBType>,
	cacheControl: CacheControl,
	targetRenderNanos: Long,
	renderingTaskExecutor: TaskExecutor
) : BaseRenderUnit(
	threadGroup,
	viewerStateSupplier.getRenderState(),
	interpolation,
	accumulateProjectorFactory,
	cacheControl,
	targetRenderNanos,
	renderingTaskExecutor
) {
	override fun paint() {
		if (viewerStateSupplier.get()?.isVisible == true)
			super.paint()
	}
	companion object {

		private fun Supplier<ViewerState?>.getRenderState() : () -> RenderUnitState? = {
			get()?.run {
				val viewerTransform = AffineTransform3D().also { getViewerTransform(it) }
				val (width, height) = dimensions
				RenderUnitState(viewerTransform, timepoint, sources, width.toLong(), height.toLong())
			}
		}
		private fun (() -> ViewerState?).getRenderState() : () -> RenderUnitState? = {
			this()?.run {
				val viewerTransform = AffineTransform3D().also { getViewerTransform(it) }
				val (width, height) = dimensions
				RenderUnitState(viewerTransform, timepoint, sources, width.toLong(), height.toLong())
			}
		}
	}
}
