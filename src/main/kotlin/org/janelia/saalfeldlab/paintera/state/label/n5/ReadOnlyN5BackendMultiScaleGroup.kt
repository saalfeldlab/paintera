package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.util.volatiles.SharedQueue
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

class ReadOnlyN5BackendMultiScaleGroup<D, T> constructor(
    override val container: N5Reader,
    override val dataset: String,
    private val projectDirectory: Supplier<String>,
    private val propagationExecutorService: ExecutorService
) : ReadOnlyN5Backend<D, T>
    where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

    override fun createSource(
        queue: SharedQueue,
        priority: Int,
        name: String,
        resolution: DoubleArray,
        offset: DoubleArray
    ): DataSource<D, T> {
        return makeSource<D, T>(
            container,
            dataset,
            N5Helpers.fromResolutionAndOffset(resolution, offset),
            queue,
            priority,
            name,
            projectDirectory,
            propagationExecutorService
        )
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun <D, T> makeSource(
            container: N5Reader,
            dataset: String,
            transform: AffineTransform3D,
            queue: SharedQueue,
            priority: Int,
            name: String,
            projectDirectory: Supplier<String>,
            propagationExecutorService: ExecutorService
        ): DataSource<D, T>
            where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
            return N5DataSource<D, T>(N5Meta.fromReader(container, dataset), transform, name, queue, priority)
        }

    }
}
