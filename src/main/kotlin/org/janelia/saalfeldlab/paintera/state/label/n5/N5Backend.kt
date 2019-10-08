package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.util.volatiles.SharedQueue
import net.imglib2.Volatile
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

interface N5Backend<D, T> : ConnectomicsLabelBackend<D, T> {

	val container: N5Writer
	val dataset: String

	companion object {
		@JvmStatic
		fun <D, T> createFrom(
				container: N5Writer,
				dataset: String,
				queue: SharedQueue,
				priority: Int,
				name: String,
				projectDirectory: Supplier<String>,
				propagationQueue: ExecutorService,
				resolution: DoubleArray = DoubleArray(3) { 1.0 },
				offset: DoubleArray = DoubleArray(3) { 0.0 }): N5Backend<D, T>
				where D: IntegerType<D>,
					  D: NativeType<D>,
					  T: Volatile<D>,
					  T: NativeType<T> {
			return if (N5Helpers.isPainteraDataset(container, dataset))
				// Paintera data format
				N5BackendPainteraDataset(
						container,
						dataset,
						resolution,
						offset,
						queue,
						priority,
						name,
						projectDirectory,
						propagationQueue)
			else if (!N5Helpers.isMultiScale(container, dataset))
				// not paintera data, assuming multiscale data
				N5BackendMultiScaleGroup(
						container,
						dataset, null,
						resolution,
						offset,
						queue,
						priority,
						name,
						projectDirectory,
						propagationQueue)

			else
				// not multi-scale or paintera, assuming regular dataset
				N5BackendSingleScaleDataset(
					container,
					dataset,
					null,
					resolution,
					offset,
					queue,
					priority,
					name,
					projectDirectory,
					propagationQueue)
		}
	}

}
