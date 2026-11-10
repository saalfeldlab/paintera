package org.janelia.saalfeldlab.bdv.fx.viewer.render

import bdv.img.cache.VolatileCachedCellImg
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.render.MipmapOrdering
import bdv.viewer.render.MipmapOrdering.Level
import bdv.viewer.render.Prefetcher
import io.github.oshai.kotlinlogging.KotlinLogging
import net.imglib2.Dimensions
import net.imglib2.RandomAccessible
import net.imglib2.RealRandomAccessible
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.cache.volatiles.LoadingStrategy
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import org.janelia.saalfeldlab.paintera.data.SlicedRenderSource
import java.util.function.IntFunction

private val LOG = KotlinLogging.logger { }

internal class SliceMipmapFactory(private val prefetchCells: Boolean) {

	/**
	 * [levels] in render order, each transformed to the slice, after prefetching them in prefetch order
	 *
	 * @param viewerToSlice the screen scale transform for the viewer plane, or the viewer to a filter's slice
	 */
	fun <S> create(
		source: Source<*>,
		timepoint: Int,
		globalToViewer: AffineTransform3D,
		viewerToSlice: AffineTransform3D,
		levels: List<Level>,
		sliceShape: Dimensions,
		interpolation: Interpolation,
		imageAtLevel: IntFunction<RealRandomAccessible<S>>
	): List<RandomAccessible<S>> {
		if (prefetchCells) {
			for (level in levels.sortedWith(MipmapOrdering.prefetchOrderComparator)) {
				val cacheHints = level.prefetchCacheHints
				if (cacheHints == null || cacheHints.loadingStrategy != LoadingStrategy.DONTLOAD)
					prefetch(source, timepoint, globalToViewer, viewerToSlice, level.mipmapLevel, cacheHints, sliceShape, interpolation)
			}
		}
		return levels.sortedWith(MipmapOrdering.renderOrderComparator).map { level ->
			setCacheHints(source, timepoint, level.mipmapLevel, level.renderCacheHints)
			interpolatedImgAtSlice(source, timepoint, level.mipmapLevel, globalToViewer, viewerToSlice, imageAtLevel.apply(level.mipmapLevel))
		}
	}

	companion object {

		@JvmStatic
		fun setCacheHints(source: Source<*>, timepoint: Int, mipmapIndex: Int, cacheHints: CacheHints?) {
			val img = source.getSource(timepoint, mipmapIndex)
			if (img is VolatileCachedCellImg<*, *>)
				img.setCacheHints(cacheHints)
			else if (source is SlicedRenderSource && source.isSliced)
				source.setSliceCacheHints(mipmapIndex, cacheHints)
		}

		@JvmStatic
		fun <S> interpolatedImgAtSlice(
			source: Source<*>,
			timepoint: Int,
			mipmapIndex: Int,
			globalToViewer: AffineTransform3D,
			viewerToSlice: AffineTransform3D,
			interpolatedImg: RealRandomAccessible<S>
		): RandomAccessible<S> {
			val sourceToSlice = sourceToSlice(source, timepoint, mipmapIndex, globalToViewer, viewerToSlice)
			LOG.debug { "Transforming source $source (name=${source.name}) for t=$timepoint level=$mipmapIndex to the slice with transform=$sourceToSlice" }
			return RealViews.affine(interpolatedImg, sourceToSlice)
		}

		@JvmStatic
		fun sourceToSlice(
			source: Source<*>,
			timepoint: Int,
			mipmapIndex: Int,
			globalToViewer: AffineTransform3D,
			viewerToSlice: AffineTransform3D
		): AffineTransform3D {
			val sourceToGlobal = AffineTransform3D()
			source.getSourceTransform(timepoint, mipmapIndex, sourceToGlobal)
			return viewerToSlice.copy().concatenate(globalToViewer).concatenate(sourceToGlobal)
		}

		private fun prefetch(
			source: Source<*>,
			timepoint: Int,
			globalToViewer: AffineTransform3D,
			viewerToSlice: AffineTransform3D,
			mipmapIndex: Int,
			prefetchCacheHints: CacheHints?,
			sliceShape: Dimensions,
			interpolation: Interpolation
		) {
			val img = source.getSource(timepoint, mipmapIndex)
			if (img is VolatileCachedCellImg<*, *>) {
				val hints = prefetchCacheHints ?: CacheHints(LoadingStrategy.VOLATILE, img.defaultCacheHints.queuePriority, false)
				img.setCacheHints(hints)
				Prefetcher.fetchCells(
					sourceToSlice(source, timepoint, mipmapIndex, globalToViewer, viewerToSlice),
					img.cellGrid.cellDimensions,
					img.dimensionsAsLongArray(),
					sliceShape,
					interpolation,
					img.cells.randomAccess()
				)
			} else if (source is SlicedRenderSource && source.isSliced) {
				/* an nD source's getSource is a sliced view; prefetch its backing's cells at the current slice instead */
				val sourceToSlice = sourceToSlice(source, timepoint, mipmapIndex, globalToViewer, viewerToSlice)
				source.prefetchSlice(mipmapIndex, sourceToSlice, sliceShape, interpolation, prefetchCacheHints)
			}
		}
	}
}
