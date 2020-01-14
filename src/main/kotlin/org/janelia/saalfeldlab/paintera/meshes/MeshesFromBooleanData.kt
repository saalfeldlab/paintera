package org.janelia.saalfeldlab.paintera.meshes

import com.google.common.base.Objects
import javafx.beans.binding.Bindings
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.paint.Color
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.cache.Cache
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.BooleanType
import net.imglib2.util.Intervals
import net.imglib2.util.Pair
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.cache.GenericMeshCacheLoader
import org.janelia.saalfeldlab.util.Colors
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction


class MeshesFromBooleanData<B: BooleanType<B>, T> @JvmOverloads constructor(
    private val numScaleLevels: Int,
    private val data: IntFunction<RandomAccessibleInterval<B>>,
    private val transform: IntFunction<AffineTransform3D>,
    private val blockListCache: IntFunction<Array<Interval>>,
    private val meshGenerationManagers: ExecutorService,
    private val meshGenerationWorkers: ExecutorService,
    meshCache: LoaderCache<ShapeKey<T>, Pair<FloatArray, FloatArray>?> = SoftRefLoaderCache<ShapeKey<T>, Pair<FloatArray, FloatArray>?>()) {

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

    private val loader  = GenericMeshCacheLoader<T, B>(
        CUBE_SIZE,
        data,
        transform)

    // For some reason, need to specify the type of the cache, otherwise get this kind of error:
    // [ERROR] java.lang.IllegalStateException: SimpleTypeImpl should not be created for error type: ErrorScope{Error scope for class <ERROR CLASS> with arguments: org.jetbrains.kotlin.types.IndexedParametersSubstitution@dbdbafe}
    // [ERROR] [ERROR : ShapeKey<Unit?>]
    val cache: Cache<ShapeKey<T>, Pair<FloatArray, FloatArray>?> = meshCache.withLoader(loader)


    private val meshCaches = (0 until numScaleLevels)
        .map { InterruptibleFunction.fromFunction { k: ShapeKey<T> -> this.cache[k] } }
        .toTypedArray()

    private val blockListCaches = (0 until numScaleLevels)
        .map { level -> InterruptibleFunction.fromFunction { _: T -> blockListCache.apply(level) } }
        .toTypedArray()

    private var generator: MeshGenerator<T>? = null
        set(generator) {
            field?.let {
                it.isEnabledProperty?.value = false
                it.interrupt()
                _meshesGroup.children.remove(it.root)
                it.meshSettingsProperty().value = null
            }
            field = generator
                ?.also { _meshesGroup.children.add(it.root) }
                ?.also { it.meshSettingsProperty().value = settings }
        }

    var id: T? = null
        @Synchronized set(id) {
            field = id
            updateGenerator()
        }
        @Synchronized get

    var isEnabled: Boolean = false
        @Synchronized private set(isEnabled) {
            field = isEnabled
            updateGenerator()
        }
        @Synchronized get

    val isDisabled: Boolean
        get() = !isEnabled

    @Synchronized
    fun disable() {
        isEnabled = false
    }

    @Synchronized
    fun enable() {
        isEnabled = true
    }

    @Synchronized
    private fun updateGenerator() {
        val id = this.id
        val isEnabled = this.isEnabled
        if (isDisabled)
            generator = null
        else
            setGeneratorFor(id)
    }

    // For some reason, need to specify the type _generator, otherwise get this kind of error:
    // [ERROR] java.lang.IllegalStateException: SimpleTypeImpl should not be created for error type: ErrorScope{Error scope for class <ERROR CLASS> with arguments: org.jetbrains.kotlin.types.IndexedParametersSubstitution@dbdbafe}
    // [ERROR] [ERROR : ShapeKey<Unit?>]
//    private val _generator: ObjectProperty<MeshGenerator<Unit?>?> = SimpleObjectProperty<MeshGenerator<Unit?>?>(null)
//
//    private var generator: MeshGenerator<Unit?>?
//        @Synchronized get() = _generator.value
//        @Synchronized set(generator) {
//            this.generator
//                ?.also { it.isEnabledProperty.value = false }
//                ?.also { it.interrupt() }
//                ?.also { it.meshSettingsProperty().value = null }
//                ?.also { _meshesGroup.children.remove(it.root) }
//            generator?.isEnabledProperty?.value = true
//            _generator.value = generator
//            generator?.meshSettingsProperty()?.value = this.settings
//            generator?.root?.let { _meshesGroup.children.add(it) }
//        }
//
//    @Synchronized
//    private fun updateGenerator(settings: MeshSettings?) {
//        generator = settings?.let {
//            MeshGenerator<Unit?>(
//                null,
//                blockListCaches,
//                meshCaches,
//                colorAsInt,
//                it.scaleLevelProperty().value,
//                0,
//                it.smoothingLambdaProperty().value,
//                it.smoothingIterationsProperty().value,
//                meshGenerationManagers,
//                meshGenerationWorkers)
//        }
//    }

    private fun setGeneratorFor(id: T?) {
        if (generator == null || !Objects.equal(id, generator?.id))
            generator = MeshGenerator<T>(
                id,
                blockListCaches,
                meshCaches,
                colorAsInt,
                numScaleLevels - 1,
                0,
                0.0,
                0,
                meshGenerationManagers,
                meshGenerationWorkers)
        }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private val CUBE_SIZE = intArrayOf(1, 1, 1)

        @JvmStatic
        @JvmOverloads
        // this assumes that data.apply(level).dimension(dim) will always return the same value
        fun <B: BooleanType<B>, T> fromBlockSize(
            numScaleLevels: Int,
            data: IntFunction<RandomAccessibleInterval<B>>,
            transform: IntFunction<AffineTransform3D>,
            blockSize: IntArray,
            meshGenerationManagers: ExecutorService,
            meshGenerationWorkers: ExecutorService,
            meshCache: LoaderCache<ShapeKey<T>, Pair<FloatArray, FloatArray>?> = SoftRefLoaderCache()): MeshesFromBooleanData<B, T> {
            val blocks = (0 until numScaleLevels)
                .map { data.apply(it) }
                .map { Grids.collectAllContainedIntervals(Intervals.minAsLongArray(it), Intervals.maxAsLongArray(it), blockSize).toTypedArray() }
            val blockListCache = IntFunction { blocks[it] }
            return MeshesFromBooleanData(numScaleLevels, data, transform, blockListCache, meshGenerationManagers, meshGenerationWorkers, meshCache)
        }

        @JvmStatic
        @JvmOverloads
        // this assumes that data.apply(level).dimension(dim) will always return the same value
        fun <B: BooleanType<B>, T> fromSourceAndBlockSize(
            data: DataSource<B, *>,
            blockSize: IntArray,
            meshGenerationManagers: ExecutorService,
            meshGenerationWorkers: ExecutorService,
            meshCache: LoaderCache<ShapeKey<T>, Pair<FloatArray, FloatArray>?> = SoftRefLoaderCache()): MeshesFromBooleanData<B, T> {
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
