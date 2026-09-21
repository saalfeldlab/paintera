package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.cache.SharedQueue
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class CreateDatasetModelTest {

	/** The stored block size is in source axis order; the dialog's x, y, z must follow the axis mapping, not the position. */
	@Test
	fun `block size and dimensions follow the axis roles of a permuted source`(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(tmp.toAbsolutePath().toString())
		val dataset = "cxyzt"
		/* c=3, x=32, y=16, z=8, t=4 with distinct block sizes */
        val dimensions = longArrayOf(3, 32, 16, 8, 4)
        val blockSize = intArrayOf(1, 32, 16, 8, 2)
        writer.createDataset(dataset, DatasetAttributes(dimensions, blockSize, DataType.UINT8, RawCompression()))
        val n5ContainerState = N5ContainerState(writer)
        val metadataState = MetadataUtils.createMetadataState(n5ContainerState, dataset)!!
		metadataState.axes = arrayOf(Axis(Axis.CHANNEL, "c"), Axis(Axis.SPACE, "x"), Axis(Axis.SPACE, "y"), Axis(Axis.SPACE, "z"), Axis(Axis.TIME, "t"))
		val source = N5DataSource<UnsignedByteType, VolatileUnsignedByteType>(metadataState, dataset, SharedQueue(1), 0)

		val model = DefaultCreateDatasetModel()
		model.populateFrom(source)

		assertEquals(listOf(32L, 16L, 8L), model.dimensions.asLongArray().toList())
		assertEquals(listOf(32, 16, 8), model.blockSize.asIntArray().toList())
		assertEquals(listOf(3L, 4L), model.additionalAxes.map { it.sizeProperty.value })
		assertEquals(listOf(1, 2), model.additionalAxes.map { it.blockSizeProperty.value })
	}
}
