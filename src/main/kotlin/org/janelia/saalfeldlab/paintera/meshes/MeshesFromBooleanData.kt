package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Group
import javafx.scene.paint.Color
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.BooleanType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.cache.GenericMeshCacheLoader
import org.janelia.saalfeldlab.util.Colors
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction
import net.imglib2.util.Pair as ImgLib2Pair

typealias VerticesAndNormals = ImgLib2Pair<FloatArray, FloatArray>

class MeshesFromBooleanData<B: BooleanType<B>> @JvmOverloads constructor(
    private val numScaleLevels: Int,
    private val data: IntFunction<RandomAccessibleInterval<B>>,
    private val transform: IntFunction<AffineTransform3D>,
    blockListCache: IntFunction<Array<Interval>>,
    private val meshGenerationManagers: ExecutorService,
    private val meshGenerationWorkers: ExecutorService,
    meshCache: LoaderCache<ShapeKey<Void>, VerticesAndNormals?> = SoftRefLoaderCache()
) {

    private val _meshesGroup = Group()
    val meshesGroup: Group
        get() = _meshesGroup

    val settings = MeshSettings(numScaleLevels)

    private val _color = SimpleObjectProperty(Color.WHITE)
    var color: Color
        get() = _color.value
        set(color) = _color.set(color)
    fun colorProperty() = _color

    private val colorAsInt = Bindings.createIntegerBinding(Callable { Colors.toARGBType(_color.value).get() }, _color)

    private val loader  = GenericMeshCacheLoader<Void, B>(CUBE_SIZE, data, transform)

    val meshCache = meshCache.withLoader(loader)

    private val meshCaches = (0 until numScaleLevels)
        .map { InterruptibleFunction.fromFunction { k: ShapeKey<Void> -> this.meshCache[k] } }
        .toTypedArray()

    private val blockListCaches = (0 until numScaleLevels)
        .map { level -> InterruptibleFunction.fromFunction { v: Void -> blockListCache.apply(level) } }
        .toTypedArray()

    private val _generator = SimpleObjectProperty<MeshGenerator<Void>?>(null)

    private var generator: MeshGenerator<Void>?
        get() = _generator.value
        set(generator) {
            this.generator
                ?.also { it.isEnabledProperty.value = false }
                ?.also { it.interrupt() }
            generator?.isEnabledProperty?.value = true
            _generator.value = generator
        }

    private fun udpateGenerator(settings: MeshSettings) {
        generator = MeshGenerator<Void>(
            null,
            blockListCaches,
            meshCaches,
            colorAsInt,
            settings.scaleLevelProperty().value,
            0,
            settings.smoothingLambdaProperty().value,
            settings.smoothingIterationsProperty().value,
            meshGenerationManagers,
            meshGenerationWorkers)
    }

    companion object {
        private val CUBE_SIZE = intArrayOf(1, 1, 1)

        @JvmStatic
        @JvmOverloads
        // this assumes that data.apply(level).dimension(dim) will always return the same value
        fun <B: BooleanType<B>> fromBlockSize(
            numScaleLevels: Int,
            data: IntFunction<RandomAccessibleInterval<B>>,
            transform: IntFunction<AffineTransform3D>,
            blockSize: IntArray,
            meshGenerationManagers: ExecutorService,
            meshGenerationWorkers: ExecutorService,
            meshCache: LoaderCache<ShapeKey<Void>, VerticesAndNormals?> = SoftRefLoaderCache()): MeshesFromBooleanData<B> {
            val blocks = (0 until numScaleLevels)
                .map { data.apply(it) }
                .map { Grids.collectAllContainedIntervals(Intervals.minAsLongArray(it), Intervals.maxAsLongArray(it), blockSize).toTypedArray() }
            val blockListCache = IntFunction { blocks[it] }
            return MeshesFromBooleanData(numScaleLevels, data, transform, blockListCache, meshGenerationManagers, meshGenerationWorkers, meshCache)
        }

        @JvmStatic
        @JvmOverloads
        // this assumes that data.apply(level).dimension(dim) will always return the same value
        fun <B: BooleanType<B>> fromSourceAndBlockSize(
            data: DataSource<B, *>,
            blockSize: IntArray,
            meshGenerationManagers: ExecutorService,
            meshGenerationWorkers: ExecutorService,
            meshCache: LoaderCache<ShapeKey<Void>, VerticesAndNormals?> = SoftRefLoaderCache()): MeshesFromBooleanData<B> {
            val blocks = (0 until data.numMipmapLevels)
                .map { data.getDataSource(0, it) }
                .map { Grids.collectAllContainedIntervals(Intervals.minAsLongArray(it), Intervals.maxAsLongArray(it), blockSize).toTypedArray() }
            val blockListCache = IntFunction { blocks[it] }
            return MeshesFromBooleanData(
                data.numMipmapLevels,
                IntFunction { data.getDataSource(0, it) },
                IntFunction{ level ->AffineTransform3D().also  { data.getSourceTransform(0, level, it ) }},
                blockListCache,
                meshGenerationManagers,
                meshGenerationWorkers,
                meshCache)
        }
    }



}
