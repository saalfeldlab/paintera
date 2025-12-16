package org.janelia.saalfeldlab.bdv.fx.viewer.render

import bdv.cache.CacheControl
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerState
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.render.AccumulateProjectorFactory
import javafx.beans.property.SimpleBooleanProperty
import net.imglib2.parallel.TaskExecutor
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
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
	renderingTaskExecutor,
	useVolatileIfAvailable = true
) {

	val repaintRequestProperty = SimpleBooleanProperty()

	override fun paint() {
		if (viewerStateSupplier.get()?.isVisible == true)
			super.paint()
	}

	private fun notifyRepaintObservable() = repaintRequestProperty.set(!repaintRequestProperty.value)

	override fun requestRepaint() {
		super.requestRepaint()
		notifyRepaintObservable()
	}

	override fun requestRepaint(screenScaleIndex: Int) {
		super.requestRepaint(screenScaleIndex)
		notifyRepaintObservable()
	}

	override fun requestRepaint(min: LongArray?, max: LongArray?) {
		super.requestRepaint(min, max)
		notifyRepaintObservable()
	}

	override fun requestRepaint(screenScaleIndex: Int, min: LongArray?, max: LongArray?) {
		super.requestRepaint(screenScaleIndex, min, max)
		notifyRepaintObservable()
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
