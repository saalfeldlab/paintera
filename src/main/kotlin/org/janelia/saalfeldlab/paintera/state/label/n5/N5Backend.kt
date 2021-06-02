package org.janelia.saalfeldlab.paintera.state.label.n5

import net.imglib2.Volatile
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

interface N5Backend<D, T> : SourceStateBackendN5<D, T>, ConnectomicsLabelBackend<D, T> {

    companion object {

        @JvmStatic
        fun <D, T> createFrom(
            container: N5Reader,
            dataset: String,
            projectDirectory: Supplier<String>,
            propagationQueue: ExecutorService
        ): N5Backend<D, T>
            where D : IntegerType<D>,
                  D : NativeType<D>,
                  T : Volatile<D>,
                  T : NativeType<T> {

            (container as? N5Writer)?.let {
                return when {
                    // Paintera data format
                    N5Helpers.isPainteraDataset(container, dataset) -> N5BackendPainteraDataset(
                        container,
                        dataset,
                        projectDirectory,
                        propagationQueue,
                        true
                    )
                    // not paintera data, assuming multiscale data
                    N5Helpers.isMultiScale(container, dataset) -> N5BackendMultiScaleGroup(
                        container,
                        dataset,
                        projectDirectory,
                        propagationQueue
                    )
                    // not multi-scale or paintera, assuming regular dataset
                    else -> N5BackendSingleScaleDataset(
                        container,
                        dataset,
                        projectDirectory,
                        propagationQueue
                    )
                }
            }
            return when {

                // Paintera data format
                N5Helpers.isPainteraDataset(container, dataset) -> ReadOnlyN5BackendPainteraDataset(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue,
                    true
                )
                // not paintera data, assuming multiscale data
                N5Helpers.isMultiScale(container, dataset) -> ReadOnlyN5BackendMultiScaleGroup(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue
                )
                // not multi-scale or paintera, assuming regular dataset
                else -> ReadOnlyN5BackendSingleScaleDataset(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue
                )
            }
        }


        @JvmStatic
        fun <D, T> createFrom(
            metadataState: MetadataState<*>,
            projectDirectory: Supplier<String>,
            propagationQueue: ExecutorService
        ): N5Backend<D, T>
            where D : IntegerType<D>,
                  D : NativeType<D>,
                  T : Volatile<D>,
                  T : NativeType<T> {

            metadataState.n5ContainerState.writer?.let {
                return when (metadataState.metadata) {
                    // Paintera data format
                    is N5PainteraLabelMultiScaleGroup -> N5BackendPainteraDataset(
                        it,
                        metadataState.group,
                        projectDirectory,
                        propagationQueue,
                        true,
                        metadataState
                    )
                    // not paintera data, assuming multiscale data
                    is MultiscaleMetadata<*> -> N5BackendMultiScaleGroup(
                        it,
                        metadataState.group,
                        projectDirectory,
                        propagationQueue,
                        metadataState
                    )
                    // not multi-scale or paintera, assuming regular dataset
                    else -> N5BackendSingleScaleDataset(
                        it,
                        metadataState.group,
                        projectDirectory,
                        propagationQueue,
                        metadataState
                    )
                }
            }
            return when (metadataState.metadata) {

                // Paintera data format
                is N5PainteraLabelMultiScaleGroup -> ReadOnlyN5BackendPainteraDataset(
                    metadataState.reader,
                    metadataState.group,
                    projectDirectory,
                    propagationQueue,
                    true,
                    metadataState
                )
                // not paintera data, assuming multiscale data
                is MultiscaleMetadata<*> -> ReadOnlyN5BackendMultiScaleGroup(
                    metadataState.reader,
                    metadataState.group,
                    projectDirectory,
                    propagationQueue,
                    metadataState
                )
                // not multi-scale or paintera, assuming regular dataset
                else -> ReadOnlyN5BackendSingleScaleDataset(
                    metadataState.reader,
                    metadataState.group,
                    projectDirectory,
                    propagationQueue,
                    metadataState
                )
            }
        }
    }
}
