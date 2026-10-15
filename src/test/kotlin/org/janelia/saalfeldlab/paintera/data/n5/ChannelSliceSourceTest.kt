package org.janelia.saalfeldlab.paintera.data.n5

import bdv.cache.SharedQueue
import bdv.viewer.Interpolation
import bdv.viewer.SourceAndConverter
import bdv.viewer.render.MipmapOrdering.Level
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.cache.volatiles.LoadingStrategy
import org.janelia.saalfeldlab.bdv.fx.viewer.render.SliceMipmapFactory
import net.imglib2.FinalDimensions
import net.imglib2.FinalInterval
import net.imglib2.RandomAccessible
import net.imglib2.img.array.ArrayImgs
import net.imglib2.parallel.TaskExecutors
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.type.mask.Masked
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.volatiles.VolatileUnsignedShortType
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlur
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.FilteredProjector
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImage
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImageProvider
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenView
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilter
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ChannelSliceSourceTest {

	/* value = x + 10 y + 100 z + 1000 c */
	private fun encode(x: Long, y: Long, z: Long, c: Long) = (x + 10 * y + 100 * z + 1000 * c).toInt()

	@Test
	fun `a channel slice is filtered as a plain source`(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(tmp.toAbsolutePath().toString())
		val dataset = "xyzc"
		val dimensions = longArrayOf(4, 3, 2, 3)
		val img = ArrayImgs.unsignedShorts(*dimensions)
		img.localizingCursor().let { cursor ->
			val position = LongArray(4)
			while (cursor.hasNext()) {
				cursor.fwd(); cursor.localize(position)
				cursor.get().set(encode(position[0], position[1], position[2], position[3]))
			}
		}
		writer.createDataset(dataset, DatasetAttributes(dimensions, intArrayOf(4, 3, 2, 1), DataType.UINT16, RawCompression()))
		N5Utils.save(img, writer, dataset, intArrayOf(4, 3, 2, 1), RawCompression())
		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), dataset)!!
		metadataState.axes = arrayOf(Axis(Axis.SPACE, "x"), Axis(Axis.SPACE, "y"), Axis(Axis.SPACE, "z"), Axis(Axis.CHANNEL, "c"))
		val source = N5DataSource<UnsignedShortType, VolatileUnsignedShortType>(metadataState, dataset, SharedQueue(1), 0)
		val slice = ChannelSliceSource<UnsignedShortType, VolatileUnsignedShortType>(source, 3, 2)

		var blurred: ScreenImage? = null
		val capture = RenderFilter { input, _ -> ScreenImageProvider { screenInterval, sliceToScreen -> input.image(screenInterval, sliceToScreen).also { blurred = it } } }
		/* a mean of z - 1, z, z + 1; on the first section only z and z + 1 are data */
		val filters = listOf(DepthBlur(1, 1.0, DepthBlur.Blend.MEAN), capture)
		val converter = ARGBColorConverter.InvertingImp0<VolatileUnsignedShortType>(0.0, 1.0)
		val level = Level(0, 0, 0, CacheHints(LoadingStrategy.VOLATILE, 0, false), CacheHints(LoadingStrategy.DONTLOAD, 0, false))
		val projector = FilteredProjector.create(
			SourceAndConverter(slice, converter), filters, SliceMipmapFactory(false), listOf(level), 0,
			AffineTransform3D(), AffineTransform3D(), Interpolation.NLINEAR, FinalDimensions(4, 3), ArrayImgs.argbs(4, 3), TaskExecutors.singleThreaded(), false
		)

		var passes = 0
		while (!projector.isValid && passes++ < 200) {
			assertTrue(projector.map(false))
			Thread.sleep(10)
		}
		assertTrue(projector.isValid, "did not load")

		val plane = blurred!!
		assertEquals((encode(1, 2, 0, 2) + encode(1, 2, 1, 2)) / 2.0, plane.rasterImg.getAt(1, 2).get().toDouble(), 1e-3)
		assertEquals(2.0 / 3, plane.mask.getAt(1, 2).get().toDouble(), 1e-6)
	}
}
