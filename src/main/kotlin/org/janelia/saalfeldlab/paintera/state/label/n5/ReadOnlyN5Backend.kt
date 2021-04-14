package org.janelia.saalfeldlab.paintera.state.label.n5

import net.imglib2.Volatile
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

interface ReadOnlyN5Backend<D, T> : SourceStateBackendN5<D, T> {

    companion object {

        @JvmStatic
        fun <D, T> createFrom(
            container: N5Reader,
            dataset: String,
            projectDirectory: Supplier<String>,
            propagationQueue: ExecutorService
        ): ReadOnlyN5Backend<D, T>
            where D : IntegerType<D>,
                  D : NativeType<D>,
                  T : Volatile<D>,
                  T : NativeType<T> {
            return if (N5Helpers.isPainteraDataset(container, dataset))
            // Paintera data format
                ReadOnlyN5BackendPainteraDataset(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue,
                    true
                )
            else if (N5Helpers.isMultiScale(container, dataset))
            // not paintera data, assuming multiscale data
                ReadOnlyN5BackendMultiScaleGroup(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue
                )
            else
            // not multi-scale or paintera, assuming regular dataset
                ReadOnlyN5BackendSingleScaleDataset(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue
                )
        }
    }

}
