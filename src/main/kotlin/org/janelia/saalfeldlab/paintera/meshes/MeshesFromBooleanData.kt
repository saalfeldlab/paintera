package org.janelia.saalfeldlab.paintera.meshes

import com.google.common.base.Objects
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Group
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
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
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
    private val meshViewUpdateQueue: MeshViewUpdateQueue<T>,
    private val workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    val settings: MeshSettings = MeshSettings(numScaleLevels),
    meshCache: LoaderCache<ShapeKey<T>, Pair<FloatArray, FloatArray>?> = SoftRefLoaderCache<ShapeKey<T>, Pair<FloatArray, FloatArray>?>()) {

    init {
        settings.isVisibleProperty().addListener { _, _, _ -> updateGenerator() }
    }

    private val _meshesGroup = Group()
    val meshesGroup: Group
        get() = _meshesGroup

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

    private val _showBlockBoundaries = SimpleBooleanProperty(false)
    var showBlockBoundaries: Boolean
        get() = _showBlockBoundaries.value
        set(showBlockBoundaries) = _showBlockBoundaries.set(showBlockBoundaries)
    fun showBlockBoundariesProperty(): BooleanProperty = _showBlockBoundaries


    private val meshCaches = (0 until numScaleLevels)
        .map { InterruptibleFunction.fromFunction { k: ShapeKey<T> -> this.cache[k] } }
        .toTypedArray()

    private val blockListCaches = (0 until numScaleLevels)
        .map { level -> InterruptibleFunction.fromFunction { _: T -> blockListCache.apply(level) } }
        .toTypedArray()

    private var generator: MeshGenerator<T>? = null
        @Synchronized set(generator) {
            field?.let {
                it.interrupt()
                _meshesGroup.children.remove(it.root)
                it.meshSettingsProperty().value = null
            }
            field = generator
                ?.also { _meshesGroup.children.add(it.root) }
                ?.also { it.meshSettingsProperty().value = settings }
            // TODO probably need to call MeshGenerator.update(BlockTree, CellGrid) here
//                ?.also { it.update(???, ???) }
        }

    init {
        Thread {
            while (true) {
                println("LOL MG ${(generator?.root as Group?)?.children?.size}")
                Thread.sleep(300)
            }
        }.also { it.isDaemon = true }.start()
    }

    var id: T? = null
        @Synchronized set(id) {
            field = id
            updateGenerator()
        }
        @Synchronized get

    val isEnabled: Boolean
        @Synchronized get() = settings.isVisibleProperty().get()

    val isDisabled: Boolean
        get() = !isEnabled

    @Synchronized
    private fun updateGenerator() {
        val id = this.id
        val isEnabled = this.isEnabled
        if (isDisabled)
            generator = null
        else
            setGeneratorFor(id)
    }

    private fun setGeneratorFor(id: T?) {
        if (generator == null || !Objects.equal(id, generator?.id))
            generator = null
        // TODO
//            generator = MeshGenerator<T>(
//                numScaleLevels,
//                id,
//                blockListCaches,
//                meshCaches,
//                meshViewUpdateQueue,
//                colorAsInt,
//                (0 until numScaleLevels).map { transform.apply(it) }.toTypedArray(),
//                meshGenerationManagers,
//                workers,
//                _showBlockBoundaries)
        }

    @Synchronized
    fun refreshMeshes() {
        generator = null
        cache.invalidateAll()
        updateGenerator()
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
            meshViewUpdateQueue: MeshViewUpdateQueue<T>,
            workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
            settings: MeshSettings = MeshSettings(numScaleLevels),
            meshCache: LoaderCache<ShapeKey<T>, Pair<FloatArray, FloatArray>?> = SoftRefLoaderCache()): MeshesFromBooleanData<B, T> {
            val blocks = (0 until numScaleLevels)
                .map { data.apply(it) }
                .map { Grids.collectAllContainedIntervals(Intervals.minAsLongArray(it), Intervals.maxAsLongArray(it), blockSize).toTypedArray() }
            val blockListCache = IntFunction { blocks[it] }
            return MeshesFromBooleanData(
                numScaleLevels,
                data,
                transform,
                blockListCache,
                meshGenerationManagers,
                meshViewUpdateQueue,
                workers,
                settings,
                meshCache)
        }

        @JvmStatic
        @JvmOverloads
        // this assumes that data.apply(level).dimension(dim) will always return the same value
        fun <B: BooleanType<B>, T> fromSourceAndBlockSize(
            data: DataSource<B, *>,
            blockSize: IntArray,
            meshGenerationManagers: ExecutorService,
            meshViewUpdateQueue: MeshViewUpdateQueue<T>,
            workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
            settings: MeshSettings = MeshSettings(data.numMipmapLevels),
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
                meshViewUpdateQueue,
                workers,
                settings,
                meshCache)
        }
    }


}
