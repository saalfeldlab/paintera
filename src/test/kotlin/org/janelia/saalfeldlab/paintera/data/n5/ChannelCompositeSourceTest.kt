package org.janelia.saalfeldlab.paintera.data.n5

import bdv.cache.SharedQueue
import bdv.viewer.Interpolation
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.volatiles.VolatileUnsignedShortType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class ChannelCompositeSourceTest {

	/* value = x + 10 y + 100 z + 1000 c + 10000 t, so every component is traceable to its source voxel */
	private fun encode(x: Long, y: Long, z: Long, c: Long, t: Long) = (x + 10 * y + 100 * z + 1000 * c + 10000 * t).toInt()

	@Test
	fun `each component is the source voxel at that channel and the view's slice of the other axes`(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(tmp.toAbsolutePath().toString())
		val dataset = "xyzct"
		val dimensions = longArrayOf(4, 3, 2, 3, 2)
		val img = ArrayImgs.unsignedShorts(*dimensions)
		img.localizingCursor().let { cursor ->
			val position = LongArray(5)
			while (cursor.hasNext()) {
				cursor.fwd(); cursor.localize(position)
				cursor.get().set(encode(position[0], position[1], position[2], position[3], position[4]))
			}
		}
		writer.createDataset(dataset, DatasetAttributes(dimensions, intArrayOf(4, 3, 2, 1, 1), DataType.UINT16, RawCompression()))
		N5Utils.save(img, writer, dataset, intArrayOf(4, 3, 2, 1, 1), RawCompression())
		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), dataset)!!
		metadataState.axes = arrayOf(Axis(Axis.SPACE, "x"), Axis(Axis.SPACE, "y"), Axis(Axis.SPACE, "z"), Axis(Axis.CHANNEL, "c"), Axis(Axis.TIME, "t"))
		val source = N5DataSource<UnsignedShortType, VolatileUnsignedShortType>(metadataState, dataset, SharedQueue(1), 0)
		metadataState.xyzView.sliceAt(4, 1L)

		/* channels 0 and 2 active; the composite still holds every dataset channel by its own index */
		val composite = ChannelCompositeSource<UnsignedShortType, VolatileUnsignedShortType>(source, channelAxis = 3, channels = longArrayOf(0, 2))
		assertEquals(2, composite.numChannels)
		assertEquals(3, composite.numDatasetChannels)

		assertEquals(listOf(4L, 3L, 2L), Intervals.dimensionsAsLongArray(composite.getSource(0, 0)).toList(), "the composite is the 3D view")
		/* the volatile view is empty until the queue loads it, so read the values through the data composite */
		val access = composite.getDataSource(0, 0).randomAccess()
		for (x in 0L until 4) for (y in 0L until 3) for (z in 0L until 2) {
			val pixel = access.setPositionAndGet(x, y, z)
			for (c in 0L until 3)
				assertEquals(encode(x, y, z, c, 1), pixel.get(c).get(), "component $c is channel $c at t=1")
		}

		/* the cells are loaded now, so the volatile composite interpolates: halfway between x=1 and x=2, per active channel, rounded to the integer type */
		val linear = composite.getInterpolatedSource(0, 0, Interpolation.NLINEAR).realRandomAccess().setPositionAndGet(1.5, 1.0, 0.0)
		assertEquals(true, linear.isValid, "every weighted neighbor is loaded")
		for (c in longArrayOf(0, 2))
			assertEquals((encode(1, 1, 0, c, 1) + encode(2, 1, 0, c, 1)) / 2.0, linear.get().get(c).realDouble, 0.5, "channel $c halfway")
		assertEquals(0.0, linear.get().get(1L).realDouble, "an inactive channel stays zero")

		/* the data composite follows the same slice; moving the view moves every channel */
		metadataState.xyzView.sliceAt(4, 0L)
		val data = composite.getDataSource(0, 0).randomAccess().setPositionAndGet(1L, 2L, 1L)
		assertEquals(encode(1, 2, 1, 0, 0), data.get(0L).get())
		assertEquals(encode(1, 2, 1, 2, 0), data.get(2L).get())

		/* interpolated access outside the image reads the zero extension, with either interpolation */
		for (interpolation in Interpolation.entries) {
			val outside = composite.getInterpolatedSource(0, 0, interpolation).realRandomAccess().setPositionAndGet(-2.0, 0.0, 0.0)
			for (c in 0L until 3)
				assertEquals(0, outside.get().get(c).get().get(), "$interpolation outside")
			assertEquals(true, outside.isValid, "the extension is valid under $interpolation")
		}
	}
}
