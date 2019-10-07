package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.util.volatiles.SharedQueue
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.label.VolatileLabelMultisetType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.LongType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileLongType
import net.imglib2.type.volatiles.VolatileUnsignedIntType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.exception.PainteraException
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.N5Types
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.function.Supplier

class N5BackendSingleScaleDataset<D, T>(
	val container: N5Writer,
	val dataset: String,
	idService: IdService?,
	labelBlockLookup: LabelBlockLookup?,
	private val resolution: DoubleArray,
	private val offset: DoubleArray,
	queue: SharedQueue,
	priority: Int,
	name: String,
	projectDirectory: Supplier<String>,
	propagationExecutorService: ExecutorService) : ConnectomicsLabelBackend<D, T>
		where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {

	private val transform = N5Helpers.fromResolutionAndOffset(resolution, offset)
	override val source: DataSource<D, T> = makeSource(container, dataset, transform, queue, priority, name, projectDirectory, propagationExecutorService)
	override val lockedSegments: LockedSegmentsState = LockedSegmentsOnlyLocal(Consumer {})
	override val fragmentSegmentAssignment = FragmentSegmentAssignmentOnlyLocal(
		FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE,
		FragmentSegmentAssignmentOnlyLocal.doesNotPersist(persistError(dataset)))

	override val idService = idService ?: N5Helpers.idService(container, dataset, Supplier { PainteraAlerts.getN5IdServiceFromData(container, dataset, source) })!!

	override val labelBlockLookup = labelBlockLookup ?: PainteraAlerts.getLabelBlockLookupFromN5DataSource(container, dataset, source)!!

	override fun setResolution(x: Double, y: Double, z: Double) {
		resolution[0] = x
		resolution[1] = y
		resolution[2] = z
		updateTransform()
	}

	override fun setOffset(x: Double, y: Double, z: Double) {
		offset[0] = x
		offset[1] = y
		offset[2] = z
		updateTransform()
	}

	override fun getResolution() = resolution.clone()

	override fun getOffset() = offset.clone()

	private fun updateTransform() = this.transform.set(N5Helpers.fromResolutionAndOffset(resolution, offset))

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun persistError(dataset: String) = "Persisting assignments not supported for non Paintera dataset $dataset."

		private fun <D, T> makeSource(
			container: N5Reader,
			dataset: String,
			transform: AffineTransform3D,
			queue: SharedQueue,
			priority: Int,
			name: String,
			projectDirectory: Supplier<String>,
			propagationExecutorService: ExecutorService): DataSource<D, T>
				where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {
			val dataSource = N5DataSource<D, T>(N5Meta.fromReader(container, dataset), transform, name, queue, priority)
			return if (container is N5Writer) {
				val tmpDir = Masks.canvasTmpDirDirectorySupplier(projectDirectory)
				Masks.mask(dataSource, queue, tmpDir.get(), tmpDir, CommitCanvasN5(container, dataset), propagationExecutorService)
			} else
				dataSource
		}

		fun createBackend(
			container: N5Writer,
			dataset: String,
			idService: IdService?,
			labelBlockLookup: LabelBlockLookup?,
			resolution: DoubleArray,
			offset: DoubleArray,
			queue: SharedQueue,
			priority: Int,
			name: String,
			projectDirectory: Supplier<String>,
			propagationExecutorService: ExecutorService): N5BackendSingleScaleDataset<*, *>  {
			val dataType = N5Types.getDataType(container, dataset)
			val isLabelMultisetType = N5Types.isLabelMultisetType(container, dataset, false)
			if (isLabelMultisetType)
				return N5BackendSingleScaleDataset<LabelMultisetType, VolatileLabelMultisetType>(container, dataset, idService, labelBlockLookup, resolution, offset, queue, priority, name, projectDirectory, propagationExecutorService)
			return when (dataType) {
					DataType.INT64 -> N5BackendSingleScaleDataset<LongType, VolatileLongType>(container, dataset, idService, labelBlockLookup, resolution, offset, queue, priority, name, projectDirectory, propagationExecutorService)
					DataType.UINT32 -> N5BackendSingleScaleDataset<UnsignedIntType, VolatileUnsignedIntType>(container, dataset, idService, labelBlockLookup, resolution, offset, queue, priority, name, projectDirectory, propagationExecutorService)
					DataType.UINT64 -> N5BackendSingleScaleDataset<UnsignedLongType, VolatileUnsignedLongType>(container, dataset, idService, labelBlockLookup, resolution, offset, queue, priority, name, projectDirectory, propagationExecutorService)
					else -> throw IncompatibleDataType(container, dataset, dataType)
			}
		}

		private val LEGAL_DATATYPES = listOf(DataType.INT64, DataType.UINT32, DataType.UINT64)
	}

	class IncompatibleDataType(container: N5Reader, dataset: String, dataType: DataType): PainteraException(
		"Expected one of $LEGAL_DATATYPES or label multiset type but found $dataType for dataset `$dataset' in container `$container'.")

}
