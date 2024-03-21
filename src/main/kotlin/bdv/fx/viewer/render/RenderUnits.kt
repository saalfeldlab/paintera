package bdv.fx.viewer.render

import bdv.cache.CacheControl
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.render.AccumulateProjectorFactory
import javafx.scene.image.Image
import net.imglib2.parallel.TaskExecutor
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import java.util.function.Function

data class RenderUnitState(
	val transform: AffineTransform3D,
	val timepoint: Int,
	val sources: List<SourceAndConverter<*>>,
	val width: Long,
	val height: Long
)

open class BaseRenderUnit(
	threadGroup: ThreadGroup,
	protected val renderStateSupplier: () -> RenderUnitState?,
	interpolation: Function<Source<*>, Interpolation>,
	accumulateProjectorFactory: AccumulateProjectorFactory<ARGBType>,
	cacheControl: CacheControl,
	targetRenderNanos: Long,
	renderingTaskExecutor: TaskExecutor,
	var skipOverlays: Boolean = false,
	screenScales : DoubleArray? = null,
	dimensions: LongArray? = null
) : RenderUnit(
	threadGroup,
	interpolation,
	accumulateProjectorFactory,
	cacheControl,
	targetRenderNanos,
	renderingTaskExecutor
) {

	init {
		screenScales?.let { setScreenScales(it) }
		dimensions?.let { setDimensions(it[0], it[1]) }
	}

	override fun paint() {
		val render: MultiResolutionRendererFX
		val target: TransformAwareBufferedImageOverlayRendererFX
		val state: RenderUnitState
		synchronized(this) {
			state = renderStateSupplier() ?: return
			if (dimensions[0] != state.width || dimensions[1] != state.height)
				setDimensions(state.width, state.height)
			render = renderer ?: return
			target = renderTarget ?: return
		}

		val renderedScreenScaleIndex = renderer.paint(
			state.sources,
			state.timepoint,
			state.transform,
			interpolation,
			null
		)

		if (renderedScreenScaleIndex != -1) {
			val screenInterval = render.lastRenderedScreenInterval
			val renderTargetRealInterval = render.lastRenderTargetRealInterval

			if (skipOverlays) {
				val imgBeforeOverlays = target.setBufferedImage(null)
				renderResultProperty.set(
					RenderResult(
						imgBeforeOverlays,
						screenInterval,
						renderTargetRealInterval,
						renderedScreenScaleIndex
					)
				)
			} else target.drawOverlays { img: Image? ->
				renderResultProperty.set(
					RenderResult(
						img,
						screenInterval,
						renderTargetRealInterval,
						renderedScreenScaleIndex
					)
				)
			}
		}
	}
}