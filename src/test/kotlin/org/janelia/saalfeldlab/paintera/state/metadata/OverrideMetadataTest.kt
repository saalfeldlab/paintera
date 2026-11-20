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

class OverrideMetadataTest {

	@Test
	fun `overriding one axis resolution or translation lands in the metadata state`(@TempDir tmp: Path) {
		val writer = N5FSWriter(tmp.toAbsolutePath().toString())
		writer.createDataset("raw", DatasetAttributes(longArrayOf(8, 6, 4), intArrayOf(8, 6, 4), DataType.UINT8, RawCompression()))
		writer.setAttribute("raw", "pixelResolution", doubleArrayOf(4.0, 5.0, 40.0))
		val metadataState = createMetadataState(N5ContainerState(writer), "raw")!!

		metadataState.updateTransform(resolution = metadataState.resolution.also { it[1] = 7.0 })
		assertContentEquals(doubleArrayOf(4.0, 7.0, 40.0), metadataState.resolution)

		metadataState.updateTransform(translation = metadataState.translation.also { it[2] = 3.5 })
		assertContentEquals(doubleArrayOf(0.0, 0.0, 3.5), metadataState.translation)
		assertContentEquals(doubleArrayOf(4.0, 7.0, 40.0), metadataState.resolution)
	}

	@Test
	fun `ngffMetadataCopy follows swapped axes and unit overrides in source order`(@TempDir tmp: Path) {
		val writer = N5FSWriter(tmp.toAbsolutePath().toString())
		writer.createDataset("raw", DatasetAttributes(longArrayOf(8, 6, 4), intArrayOf(8, 6, 4), DataType.UINT8, RawCompression()))
		writer.setAttribute("raw", "pixelResolution", doubleArrayOf(4.0, 5.0, 40.0))
		val metadataState = createMetadataState(N5ContainerState(writer), "raw") as SingleScaleMetadataState
		metadataState.axes = arrayOf(Axis(Axis.SPACE, "z", "nm"), Axis(Axis.SPACE, "y", "nm"), Axis(Axis.SPACE, "x", "um"))
		metadataState.updateTransform(translation = doubleArrayOf(1.5, 2.5, 3.5))

		val ngffMetadata = metadataState.ngffMetadataCopy()
		/* source order is z, y, x; the file's scale is unchanged there, and the canonical translation maps back reversed */
		assertContentEquals(doubleArrayOf(4.0, 5.0, 40.0), ngffMetadata.scale)
		assertContentEquals(doubleArrayOf(3.5, 2.5, 1.5), ngffMetadata.translation)
		assertContentEquals(listOf("z", "y", "x"), ngffMetadata.axes.map { it.name })
		assertContentEquals(listOf("nm", "nm", "um"), ngffMetadata.axes.map { it.unit })
	}

	@Test
	fun `ngffMetadataCopy keeps non-spatial values from a non-NGFF source`(@TempDir tmp: Path) {
		val writer = N5FSWriter(tmp.toAbsolutePath().toString())
		writer.createDataset("raw", DatasetAttributes(longArrayOf(8, 6, 4, 3, 2), intArrayOf(8, 6, 4, 1, 1), DataType.UINT8, RawCompression()))
		writer.setAttribute("raw", "pixelResolution", doubleArrayOf(4.0, 5.0, 40.0, 1.0, 0.5))
		val metadataState = createMetadataState(N5ContainerState(writer), "raw")!!

		var ngffMetadata = metadataState.ngffMetadataCopy()
		assertContentEquals(doubleArrayOf(4.0, 5.0, 40.0, 1.0, 0.5), ngffMetadata.scale)
		assertContentEquals(DoubleArray(5), ngffMetadata.translation)

		/* an override replaces only the spatial slots */
		metadataState.updateTransform(resolution = doubleArrayOf(8.0, 5.0, 40.0), translation = doubleArrayOf(1.0, 2.0, 9.0))
		ngffMetadata = metadataState.ngffMetadataCopy()
		assertContentEquals(doubleArrayOf(8.0, 5.0, 40.0, 1.0, 0.5), ngffMetadata.scale)
		assertContentEquals(doubleArrayOf(1.0, 2.0, 9.0, 0.0, 0.0), ngffMetadata.translation)
	}

	@Test
	fun `ngffMetadataCopy fills the non-spatial axes when the resolution is spatial-only`(@TempDir tmp: Path) {
		val writer = N5FSWriter(tmp.toAbsolutePath().toString())
		writer.createDataset("raw", DatasetAttributes(longArrayOf(8, 6, 4, 3, 2), intArrayOf(8, 6, 4, 1, 1), DataType.UINT8, RawCompression()))
		writer.setAttribute("raw", "pixelResolution", doubleArrayOf(4.0, 5.0, 40.0))
		val metadataState = createMetadataState(N5ContainerState(writer), "raw")!!

		val ngffMetadata = metadataState.ngffMetadataCopy()
		assertContentEquals(doubleArrayOf(4.0, 5.0, 40.0, 1.0, 1.0), ngffMetadata.scale)
		assertContentEquals(DoubleArray(5), ngffMetadata.translation)
	}

	@Test
	fun `an override is in canonical order and maps back to the source axes`(@TempDir tmp: Path) {
		val writer = N5FSWriter(tmp.toAbsolutePath().toString())
		writer.createDataset("raw", DatasetAttributes(longArrayOf(8, 6, 4), intArrayOf(8, 6, 4), DataType.UINT8, RawCompression()))
		writer.setAttribute("raw", "pixelResolution", doubleArrayOf(4.0, 5.0, 40.0))
		val metadataState = createMetadataState(N5ContainerState(writer), "raw") as SingleScaleMetadataState
		metadataState.axes = arrayOf(Axis(Axis.SPACE, "z"), Axis(Axis.SPACE, "y"), Axis(Axis.SPACE, "x"))

		/* canonical x is the last source axis */
		metadataState.updateTransform(resolution = metadataState.resolution.also { it[0] = 9.0 })
		metadataState.updateTransform(translation = metadataState.translation.also { it[0] = 1.5 })
		assertContentEquals(doubleArrayOf(9.0, 5.0, 4.0), metadataState.resolution)
		assertContentEquals(doubleArrayOf(1.5, 0.0, 0.0), metadataState.translation)
		assertContentEquals(doubleArrayOf(4.0, 5.0, 9.0), metadataState.sourceTransform.run { doubleArrayOf(get(0, 0), get(1, 1), get(2, 2)) })
		assertContentEquals(doubleArrayOf(0.0, 0.0, 1.5), metadataState.sourceTransform.translation)
	}
}
