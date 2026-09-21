package org.janelia.saalfeldlab.paintera.state.metadata

import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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

	@ParameterizedTest
	@FieldSource("multiscaleCases")
	fun `a multiscale update sets s0 exactly and scales the other levels with it`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val resolution = doubleArrayOf(273.4, 273.4, 3100.0000000000005)
		val dataset = TestData.createRaw(writer, testCase, "raw", resolution = resolution, offset = doubleArrayOf(1.5, 2.5, 3.5))
		val metadataState = createMetadataState(N5ContainerState(writer), dataset) as MultiScaleMetadataState
		val s0 = metadataState.transform.rowPackedCopy
		val s1 = metadataState.sourceToXyzTransforms[1].rowPackedCopy

		/* a reload applies the saved resolution and offset; nothing may move by an ulp */
		metadataState.updateTransform(metadataState.resolution, metadataState.translation)
		assertContentEquals(s0, metadataState.transform.rowPackedCopy, "s0 after an update with its own values for $testCase")
		assertContentEquals(s1, metadataState.sourceToXyzTransforms[1].rowPackedCopy, "s1 after an update with its own values for $testCase")

		/* an edit lands exactly on s0; s1 keeps its scale relative to s0 */
		metadataState.updateTransform(doubleArrayOf(4.0, 5.0, 40.0), doubleArrayOf(0.5, 0.6, 0.7))
		assertContentEquals(doubleArrayOf(4.0, 5.0, 40.0), metadataState.resolution)
		assertContentEquals(doubleArrayOf(0.5, 0.6, 0.7), metadataState.translation)
		val level1 = metadataState.sourceToXyzTransforms[1]
		for (d in 0 until 3) {
			val factor = s1[d * 5] / s0[d * 5]
			assertEquals(factor * metadataState.resolution[d], level1.get(d, d), 1e-9, "s1 scale in $d for $testCase")
		}
	}

	companion object {
		@JvmStatic
		val multiscaleCases = (TestData.n5Scalar + TestData.zarr3Cases).filter { it.scalePyramid == TestData.ScalePyramid.Multi && it.numDimensions == 3 && it.dataType == TestData.DataType.INT8 }
	}
}
