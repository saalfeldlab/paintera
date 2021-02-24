package org.janelia.saalfeldlab.paintera.state.label.n5

import javafx.scene.Node
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import net.imglib2.Volatile
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

interface N5Backend<D, T> : ConnectomicsLabelBackend<D, T>, SourceStateBackendN5<D, T> {

    override fun createMetaDataNode(): Node {
        val containerLabel = Labels.withTooltip("Container", "N5 container of source dataset `$dataset'")
        val datasetLabel = Labels.withTooltip("Dataset", "Dataset path inside container `${container.urlRepresentation()}'")
        val container = TextField(this.container.urlRepresentation()).also { it.isEditable = false }
        val dataset = TextField(this.dataset).also { it.isEditable = false }
        GridPane.setHgrow(container, Priority.ALWAYS)
        GridPane.setHgrow(dataset, Priority.ALWAYS)
        return GridPane()
            .also { it.hgap = 10.0 }
            .also { it.add(containerLabel, 0, 0) }
            .also { it.add(datasetLabel, 0, 1) }
            .also { it.add(container, 1, 0) }
            .also { it.add(dataset, 1, 1) }
    }

    companion object {

        private fun N5Reader.urlRepresentation() = when (this) {
            is N5FSReader -> "n5://$basePath"
            is N5HDF5Reader -> "h5://${filename.absolutePath}"
            else -> "??://${toString()}"
        }

        @JvmStatic
        fun <D, T> createFrom(
            container: N5Writer,
            dataset: String,
            projectDirectory: Supplier<String>,
            propagationQueue: ExecutorService
        ): N5Backend<D, T>
            where D : IntegerType<D>,
                  D : NativeType<D>,
                  T : Volatile<D>,
                  T : NativeType<T> {
            return if (N5Helpers.isPainteraDataset(container, dataset))
            // Paintera data format
                N5BackendPainteraDataset(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue,
                    true
                )
            else if (N5Helpers.isMultiScale(container, dataset))
            // not paintera data, assuming multiscale data
                N5BackendMultiScaleGroup(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue
                )
            else
            // not multi-scale or paintera, assuming regular dataset
                N5BackendSingleScaleDataset(
                    container,
                    dataset,
                    projectDirectory,
                    propagationQueue
                )
        }
    }

}
