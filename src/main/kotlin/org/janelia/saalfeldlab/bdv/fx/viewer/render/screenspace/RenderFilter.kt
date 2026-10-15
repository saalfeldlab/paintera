package org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace

import bdv.util.Affine3DHelpers
import net.imglib2.Dimensions
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.Scale3D
import net.imglib2.type.mask.MaskedType
import net.imglib2.type.numeric.real.FloatType
import org.janelia.saalfeldlab.util.convertWith

/**
 * One screen z slice of a source, in screen-scale pixels. Read only.
 *
 * @param rasterImg screen raster over the source, in the source's units
 * @param mask 0 where there is no data, 1 where there is
 * @param complete false while any sample is still loading
 */
class ScreenImage(
    val rasterImg: RandomAccessibleInterval<FloatType>,
    val mask: RandomAccessibleInterval<FloatType>,
    val complete: Boolean
) {

	/** [rasterImg] and [mask] as one image */
	val masked: RandomAccessibleInterval<MaskedType<FloatType>>
		get() = rasterImg.convertWith(mask, MaskedType(FloatType())) { pixel, mask, masked ->
            masked.setValue(pixel)
            masked.setMask(mask.realDouble)
        }
}

/**
 * The screen image of a source, sampled anywhere in screen space: the frame's pixels, with z along the viewer normal
 * and z = 0 the viewer plane.
 */
fun interface ScreenImageProvider {

	/**
	 * @param screenInterval the slice's pixels, 2D, in frame pixels
	 * @param sliceToScreen where each pixel of the slice samples the screen, with screen space measured in voxels of
	 *   the displayed level; the identity is the viewer plane itself
	 * @return the slice over [screenInterval]
	 */
	fun image(screenInterval: Interval, sliceToScreen: AffineTransform3D): ScreenImage

	fun image(screenInterval: Interval) = image(screenInterval, AffineTransform3D())
}

/**
 * The screen space a [RenderFilter] works in, and how its units map to it.
 *
 * @param size the size of the whole render target, of which a [ScreenImageProvider.image] region is a part
 * @param sourceToScreenScale from voxels of the displayed level to screen pixels; it follows the screen transform, so
 *   the voxels are whole only when the view is axis aligned
 * @param viewerDepth the global length of one z unit, along the viewer normal
 */
class ScreenView(val size: Dimensions, private val sourceToScreenScale: Scale3D, private val viewerDepth: Double = 1.0) {

	private val screenToSourceScale = sourceToScreenScale.inverse()

	/** [voxels] of the displayed level along screen axis [axis], in screen pixels */
	fun screenPixels(voxels: Double, axis: Int) = voxels * sourceToScreenScale.get(axis, axis)

	/** [globalLength] along the viewer normal, in screen z units */
	fun screenDepth(globalLength: Double) = globalLength / viewerDepth

	/** [sliceToScreen] with screen space in pixels instead of voxels of the displayed level */
	fun toScreenPixels(sliceToScreen: AffineTransform3D): AffineTransform3D {
		val sliceToScreenPixels = AffineTransform3D()
		sliceToScreenPixels.concatenate(sourceToScreenScale)
		sliceToScreenPixels.concatenate(sliceToScreen)
		sliceToScreenPixels.concatenate(screenToSourceScale)
		return sliceToScreenPixels
	}

	companion object {

		/**
		 * @param bestLevelToScreen voxels of the level the viewer displays to screen pixels
		 * @param globalToScreenScale screen pixels per global unit
		 */
		fun of(size: Dimensions, bestLevelToScreen: AffineTransform3D, globalToScreenScale: Double): ScreenView {
			val screenToBestLevel = bestLevelToScreen.inverse()
			val pixelsPerVoxel = DoubleArray(3) { 1.0 / Affine3DHelpers.extractScale(screenToBestLevel, it) }
			return ScreenView(size, Scale3D(*pixelsPerVoxel), pixelsPerVoxel[2] / globalToScreenScale)
		}
	}
}

fun interface RenderFilter {

	fun apply(imgProvider: ScreenImageProvider, view: ScreenView): ScreenImageProvider
}
