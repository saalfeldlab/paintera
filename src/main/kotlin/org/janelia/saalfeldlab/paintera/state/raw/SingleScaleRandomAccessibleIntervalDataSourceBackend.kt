package org.janelia.saalfeldlab.paintera.state.raw

import bdv.util.volatiles.SharedQueue
import bdv.util.volatiles.VolatileViews
import javafx.scene.Node
import net.imglib2.Volatile
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.cache.volatiles.LoadingStrategy
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.RealType
import net.imglib2.util.Util
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.metadata.SingleScaleMetadataState
import org.janelia.saalfeldlab.util.TmpVolatileHelpers
import org.janelia.saalfeldlab.util.TmpVolatileHelpers.Companion.createVolatileCachedCellImgWithInvalidate
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform
import java.lang.reflect.Type

class SingleScaleRandomAccessibleIntervalDataSourceBackend<D, T, A>(
    override val name: String,
    val dimensions: LongArray,
    val blockSize: IntArray,
    val data: CachedCellImg<D, A>
) : ConnectomicsRawBackend<D, T>
    where D : NativeType<D>, D : RealType<D>, T : NativeType<T>, T : Volatile<D>, A : VolatileArrayDataAccess<A> {

    val dataType: DataType = N5Utils.dataType(Util.getTypeFromInterval(data))

    val datasetAttributes = DatasetAttributes(dimensions, blockSize, dataType, RawCompression())

    private val metadata = N5SingleScaleMetadata(
        "Virtual RAI Backend",
        AffineTransform3D(),
        doubleArrayOf(1.0, 1.0, 1.0),
        doubleArrayOf(1.0, 1.0, 1.0),
        doubleArrayOf(0.0, 0.0, 0.0),
        "pixel",
        datasetAttributes
    )

    val nullReader = object : N5Reader {
        override fun <T : Any?> getAttribute(pathName: String, key: String, clazz: Class<T>?): T? = null

        override fun <T : Any?> getAttribute(pathName: String, key: String, type: Type?): T? = null

        override fun getDatasetAttributes(pathName: String): DatasetAttributes = datasetAttributes

        override fun readBlock(pathName: String, datasetAttributes: DatasetAttributes, vararg gridPosition: Long): DataBlock<*>? = null

        override fun exists(pathName: String): Boolean = false

        override fun list(pathName: String): Array<String> = arrayOf()

        override fun listAttributes(pathName: String): MutableMap<String, Class<*>> = mutableMapOf()

    }

    private val metadataState = object : SingleScaleMetadataState(N5ContainerState("Virtual", nullReader, null), metadata) {
        override fun <DD : NativeType<DD>, TT : Volatile<DD>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<DD, TT>> {


            val test = VolatileViews.wrapAsVolatile(data)
            val raiWithInvalidate: TmpVolatileHelpers.RaiWithInvalidate<T> = createVolatileCachedCellImgWithInvalidate(
                data,
                queue,
                CacheHints(LoadingStrategy.VOLATILE, 0, true)
            )

            return arrayOf(ImagesWithTransform(data, raiWithInvalidate.rai, transform, data.cache, raiWithInvalidate.invalidate) as ImagesWithTransform<DD, TT>)
        }
    }

    override fun canWriteToSource() = false

    override fun createSource(queue: SharedQueue, priority: Int, name: String): DataSource<D, T> {

        val imagesWithTransforms = metadataState.getData(queue, priority) as Array<ImagesWithTransform<D, T>>

        val dataWithInvalidate = RandomAccessibleIntervalDataSource.asDataWithInvalidate(imagesWithTransforms)

        return RandomAccessibleIntervalDataSource(
            dataWithInvalidate,
            { NearestNeighborInterpolatorFactory() },
            { NearestNeighborInterpolatorFactory() },
            "Test"
        )
    }

    override fun createMetaDataNode(): Node {
        TODO("Not yet implemented")
    }

    override fun getMetadataState(): MetadataState = metadataState;
}
