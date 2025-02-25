package org.janelia.saalfeldlab.paintera.state.label.n5

import net.imglib2.Volatile
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.n5.universe.metadata.MultiscaleMetadata
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup
import java.util.concurrent.ExecutorService

interface N5BackendLabel<D, T> : SourceStateBackendN5<D, T>, ConnectomicsLabelBackend<D, T> {

	override fun canWriteToSource() = metadataState.writer != null

	companion object {

		@JvmStatic
		fun <D, T> createFrom(
			container: N5ContainerState,
			dataset: String,
			propagationQueue: ExecutorService,
		): N5BackendLabel<D, T>
				where D : IntegerType<D>,
				      D : NativeType<D>,
				      T : Volatile<D>,
				      T : NativeType<T> {

			val metadataState = MetadataUtils.createMetadataState(container, dataset)!!
			return createFrom(metadataState, propagationQueue)
		}


		@JvmStatic
		fun <D, T> createFrom(
			metadataState: MetadataState,
			propagationQueue: ExecutorService,
		): N5BackendLabel<D, T>
				where D : IntegerType<D>,
				      D : NativeType<D>,
				      T : Volatile<D>,
				      T : NativeType<T> {

			return when (metadataState.metadata) {
				is N5PainteraLabelMultiScaleGroup -> N5BackendPainteraDataset(
					metadataState,
					propagationQueue,
					true
				)

				is MultiscaleMetadata<*> -> N5BackendMultiScaleGroup(
					metadataState,
					propagationQueue
				)

				else -> N5BackendSingleScaleDataset(
					metadataState,
					propagationQueue
				)
			}
		}
	}
}
