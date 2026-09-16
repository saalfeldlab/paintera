package org.janelia.saalfeldlab.paintera.state.metadata

import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContentEquals

class CanonicalTransformTest {

	@Test
	fun `resolution and translation follow the axis roles`(@TempDir tmp: Path) {
		val writer = N5FSWriter(tmp.toAbsolutePath().toString())
		writer.createDataset("raw", DatasetAttributes(longArrayOf(8, 6, 4), intArrayOf(8, 6, 4), DataType.UINT8, RawCompression()))
		writer.setAttribute("raw", "pixelResolution", doubleArrayOf(4.0, 5.0, 40.0))
		val metadataState = createMetadataState(N5ContainerState(writer), "raw") as SingleScaleMetadataState

		assertContentEquals(doubleArrayOf(4.0, 5.0, 40.0), metadataState.resolution)

		/* the first dim becomes z and the last x: each canonical slot shows its source axis' own scale */
		metadataState.axes = arrayOf(Axis(Axis.SPACE, "z"), Axis(Axis.SPACE, "y"), Axis(Axis.SPACE, "x"))
		assertContentEquals(doubleArrayOf(40.0, 5.0, 4.0), metadataState.resolution)
		assertContentEquals(doubleArrayOf(40.0, 5.0, 4.0), metadataState.transform.run { doubleArrayOf(get(0, 0), get(1, 1), get(2, 2)) })

		/* an edit writes back to the source axes through the current roles */
		metadataState.updateTransform(doubleArrayOf(10.0, 20.0, 30.0), doubleArrayOf(0.5, 0.6, 0.7))
		assertContentEquals(doubleArrayOf(30.0, 20.0, 10.0), metadataState.sourceTransform.run { doubleArrayOf(get(0, 0), get(1, 1), get(2, 2)) })
		assertContentEquals(doubleArrayOf(0.7, 0.6, 0.5), metadataState.sourceTransform.translation)

		metadataState.axes = arrayOf(Axis(Axis.SPACE, "x"), Axis(Axis.SPACE, "y"), Axis(Axis.SPACE, "z"))
		assertContentEquals(doubleArrayOf(30.0, 20.0, 10.0), metadataState.resolution)
		assertContentEquals(doubleArrayOf(0.7, 0.6, 0.5), metadataState.translation)
	}
}
