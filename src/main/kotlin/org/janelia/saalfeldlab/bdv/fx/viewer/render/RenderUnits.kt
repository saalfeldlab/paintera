package org.janelia.saalfeldlab.bdv.fx.viewer.render

import bdv.cache.CacheControl
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.render.AccumulateProjectorFactory
import javafx.scene.image.Image
import net.imglib2.parallel.TaskExecutor
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.paintera.cache.HashableTransform.Companion.hashable
import java.util.Objects
import java.util.function.Function

open class RenderUnitState(
	val transform: AffineTransform3D,
	val timepoint: Int,
	val sources: List<SourceAndConverter<*>>,
	val width: Long,
	val height: Long
) {

	override fun equals(other: Any?): Boolean {
		return (other as? RenderUnitState)?.let {
			val transformEquals = it.transform.hashable() == transform.hashable()
			it.timepoint == timepoint
					&& it.width == width
					&& it.height == height
					&& transformEquals
					&& it.sources == sources
		} ?: false
	}

	override fun hashCode(): Int {
		return Objects.hash(timepoint, width, height, transform.hashable(), *sources.toTypedArray())
	}
}

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
	dimensions: LongArray? = null,
	useVolatileIfAvailable: Boolean = true
) : RenderUnit(
	threadGroup,
	interpolation,
	accumulateProjectorFactory,
	cacheControl,
	targetRenderNanos,
	renderingTaskExecutor,
	useVolatileIfAvailable
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