package org.janelia.saalfeldlab.paintera.control.actions

import bdv.cache.SharedQueue
import kotlinx.coroutines.runBlocking
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.label.Label
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.numeric.integer.AbstractIntegerType
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendPainteraDataset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.util.concurrent.Executors


class ExportSourceStateTest {

	companion object {

		private val EXPORT_TEST_RESOURCES = Paths.get(ExportSourceStateTest::class.java.getResource("/export_tests")!!.toURI()).toAbsolutePath()

		private val EXPORT_TEST_N5 = "$EXPORT_TEST_RESOURCES/export_test.n5"
		private val EXPORT_TEST_ZARR = "$EXPORT_TEST_RESOURCES/export_test.zarr"
		private val TEMP_TEST_N5 = "$EXPORT_TEST_RESOURCES/tmp.n5"
		private val TEMP_TEST_ZARR = "$EXPORT_TEST_RESOURCES/tmp.zarr"
		private const val LABEL_MULTISET_DATASET = "paintera_labels"

		lateinit var tmpN5: N5Writer
		lateinit var tmpZarr: N5Writer

		@BeforeAll
		@JvmStatic
		fun setup() {
			tmpN5 = Paintera.n5Factory.newWriter(TEMP_TEST_N5)
			tmpZarr = Paintera.n5Factory.newWriter(TEMP_TEST_ZARR)
		}

		@AfterAll
		@JvmStatic
		fun cleanup() {
			tmpN5.remove("")
			tmpZarr.remove("")
		}
	}

	val metadataState = MetadataUtils.createMetadataState(EXPORT_TEST_N5, LABEL_MULTISET_DATASET)!! as PainteraDataMultiscaleMetadataState
	val metadata = metadataState.metadata as N5PainteraLabelMultiScaleGroup
	val labelBackend = N5BackendLabel.createFrom<Nothing, Nothing>(metadataState, Executors.newSingleThreadExecutor()) as N5BackendPainteraDataset

	val source = let {
		val queue = SharedQueue(1)
		val dataSource = N5DataSource<Nothing, Nothing>(metadataState, "export_test", queue, 1)
		val canvasPath = "$TEMP_TEST_N5/cache"
		Masks.maskedSource(dataSource, queue, canvasPath, { canvasPath }, CommitCanvasN5(metadataState), Executors.newSingleThreadExecutor()) as MaskedSource
	}

	fun regenerateExpectedScalarData() = runBlocking {
		listOf(EXPORT_TEST_N5, EXPORT_TEST_ZARR).forEach {
			listOf("s0", "s2").forEach { scaleLevel ->
				newExportState("scalar_export_$scaleLevel", 0).apply {
					exportLocationProperty.value = it
				}.exportSource()!!.join()
			}
		}
	}

	private fun newExportState(dataset: String, scaleLevel: Int): ExportSourceState {
		val exportState = ExportSourceState().apply {

			backendProperty.set(labelBackend)
			maxIdProperty.set(metadata.maxId)
			sourceProperty.set(source)
			scaleLevelProperty.set(scaleLevel)
			dataTypeProperty.set(DataType.UINT8)
			exportLocationProperty.set(TEMP_TEST_N5)
			datasetProperty.set(dataset)
		}
		return exportState
	}

	private fun regressionTestAtScaleLevel(scaleLevel: Int, exportPath: String, exportWriter: N5Writer) = runBlocking {
		val expectedImg = N5Utils.open(labelBackend.container, "scalar_export_s$scaleLevel/s$scaleLevel") as RandomAccessibleInterval<AbstractIntegerType<*>>

		newExportState("actual_s$scaleLevel", scaleLevel).apply {
			exportLocationProperty.value = exportPath
		}.exportSource()!!.join()

		val actualImg = N5Utils.open(exportWriter, "actual_s$scaleLevel/s$scaleLevel") as RandomAccessibleInterval<AbstractIntegerType<*>>
		val actualIter = actualImg.view().cursor()
		val expectedIter = expectedImg.view().cursor()
		for (expected in expectedIter) {
			assertEquals(expected.integerLong, actualIter.next().integerLong)
		}
		assertFalse({ actualIter.hasNext() }) { " export source regression failed for s$scaleLevel " }
	}

	@Test
	fun `export equals LabelMultisetType argMax`() {

		runBlocking {
			newExportState("argMaxComparisonTest", 2).exportSource()!!.join()
		}

		val exportedImg = N5Utils.open(tmpN5, "argMaxComparisonTest/s2") as RandomAccessibleInterval<AbstractIntegerType<*>>
		val lmtImg = N5Utils.open<LabelMultisetType>(labelBackend.container, "${labelBackend.dataset}/data/s2")
		val exportIter = exportedImg.view().cursor()
		val lmtIter = lmtImg.view().cursor()
		for (lmt in lmtIter) {
			assertEquals(lmt.argMax().takeUnless { it == Label.INVALID }?: 0L, exportIter.next().integerLong)
		}
		assertFalse { exportIter.hasNext() }
	}

	@Test
	fun `N5 export s0 regression test`() {

		regressionTestAtScaleLevel(0, TEMP_TEST_N5, tmpN5)
	}

	@Test
	fun `N5 export s2 regression test`() {
		regressionTestAtScaleLevel(2, TEMP_TEST_N5, tmpN5)
	}

	@Test
	fun `Zarr export s0 regression test`() {

		regressionTestAtScaleLevel(0, TEMP_TEST_ZARR, tmpZarr)
	}

	@Test
	fun `Zarr export s2 regression test`() {
		regressionTestAtScaleLevel(2, TEMP_TEST_ZARR, tmpZarr)
	}

}