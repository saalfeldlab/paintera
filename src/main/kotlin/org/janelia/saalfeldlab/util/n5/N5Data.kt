package org.janelia.saalfeldlab.util.n5

import bdv.cache.SharedQueue
import bdv.img.cache.VolatileCachedCellImg
import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.imglib2.Volatile
import net.imglib2.cache.img.*
import net.imglib2.cache.ref.WeakRefVolatileCache
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.cache.volatiles.LoadingStrategy
import net.imglib2.cache.volatiles.UncheckedVolatileCache
import net.imglib2.img.NativeImg
import net.imglib2.img.cell.Cell
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.label.VolatileLabelMultisetArray
import net.imglib2.type.label.VolatileLabelMultisetType
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.paintera.data.n5.openLabelMultiset
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.SingleScaleMetadataState
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.VolatileHelpers.CreateInvalidVolatileLabelMultisetArray
import org.janelia.saalfeldlab.util.TmpVolatileHelpers
import org.janelia.saalfeldlab.util.TmpVolatileHelpers.RaiWithInvalidate
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiscaleGroup
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiscaleGroup.PainteraLabelMultiscaleParser
import java.io.IOException
import org.janelia.saalfeldlab.paintera.state.metadata.transform

object N5Data {

    private val LOG = KotlinLogging.logger {}

    /** [openRaw] building the source transform from [resolution] and [offset]. */
    suspend fun <T : NativeType<T>, V> openRaw(
        reader: N5Reader,
        dataset: String,
        resolution: DoubleArray,
        offset: DoubleArray,
        mapping: SpatialMapping,
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<T, V> where V : Volatile<T>, V : NativeType<V> {
        val transform = AffineTransform3D()
        transform.set(
            resolution[0], 0.0, 0.0, offset[0],
            0.0, resolution[1], 0.0, offset[1],
            0.0, 0.0, resolution[2], offset[2]
        )
        return openRaw(reader, dataset, transform, mapping, queue, priority)
    }

    /** [openRaw] for the single-scale source described by [metadataState]. */
    suspend fun <T : NativeType<T>, V> openRaw(
        metadataState: SingleScaleMetadataState,
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<T, V> where V : Volatile<T>, V : NativeType<V> {
        return with(metadataState) {
            openRaw(reader, group, transform, xyzView.spatialMapping(), queue, priority)
        }
    }

    /** Open the dataset as a raw volatile source, presented through [mapping]. */
    suspend fun <T : NativeType<T>, V> openRaw(
        reader: N5Reader,
        dataset: String,
        transform: AffineTransform3D,
        mapping: SpatialMapping,
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<T, V>
            where V : Volatile<T>, V : NativeType<V> = withContext(Dispatchers.IO) {

        @Suppress("UNCHECKED_CAST")
        val raw = N5Utils.openVolatile<T>(reader, dataset) as CachedCellImg<T, Nothing>
        val cacheHint = CacheHints(LoadingStrategy.VOLATILE, priority, true)
        val vraw: RaiWithInvalidate<V> = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(raw, queue, cacheHint)

        val grid = gridFor(N5Helpers.getDatasetAttributes(reader, dataset)!!, mapping)
        ImagesWithTransform<T, V>(raw, vraw.rai, transform, raw.getCache(), vraw.invalidate, grid)
    }

    /**
     * The cell grid carried with the source. Derived from the dataset [attributes] so it matches the grid the commit
     * uses ([N5Helpers.asCellGrid]): for sharded data the block is the shard size, not the inner chunk that the
     * volatile cell image reports. An un-sliced source keeps its full nD grid; a sliced/embedded one is
     * projected to its 3D (x, y, z) spatial grid. Always carried - a sliced source is a view, not a cell image, so its
     * block grid can't be recovered downstream.
     */
    private fun gridFor(attributes: DatasetAttributes, mapping: SpatialMapping): CellGrid =
        if (mapping.isIdentity) CellGrid(attributes.dimensions, attributes.blockSize)
        else CellGrid(mapping.toSpatial(attributes.dimensions, 1L), mapping.toSpatial(attributes.blockSize, 1))

    /** Multi-scale [openRaw] for the source described by [metadataState], opening all levels in parallel. */
    suspend fun <T : NativeType<T>, V> openRawMultiscale(
        metadataState: MultiScaleMetadataState,
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<T, V>> where V : Volatile<T>, V : NativeType<V> {
        val scalePaths = metadataState.metadata.paths
        LOG.debug { "Opening groups ${scalePaths.contentToString()} as multi-scale in ${metadataState.group} " }

        val scaleTransform: Array<AffineTransform3D> = metadataState.sourceToXyzTransforms
        val reader = metadataState.reader
        val mapping = metadataState.xyzView.spatialMapping()

        val imagesWithInvalidate = coroutineScope {
            scalePaths.indices.map { scaleIdx ->
                async {
                    /* get the metadata state for the respective child */
                    LOG.debug { "Populating scale level $scaleIdx" }
                    val scaleImgWithInvalidate = openRaw<T, V>(reader, scalePaths[scaleIdx], scaleTransform[scaleIdx], mapping, queue, priority)
                    LOG.debug { "Populated scale level $scaleIdx" }
                    scaleImgWithInvalidate
                }
            }.awaitAll()
        }.toTypedArray()

        return imagesWithInvalidate
    }



    /** [openLabelMultiset] for the single-scale source described by [metadataState]. */
    suspend fun openLabelMultiset(
        metadataState: SingleScaleMetadataState,
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<LabelMultisetType, VolatileLabelMultisetType> {
        return openLabelMultiset(metadataState.reader, metadataState.group, metadataState.transform, queue, priority, metadataState.xyzView.spatialMapping())
    }

    /** [openLabelMultiset] building the source transform from [resolution] and [offset]. */
    suspend fun openLabelMultiset(
        reader: N5Reader,
        dataset: String,
        resolution: DoubleArray,
        offset: DoubleArray,
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<LabelMultisetType, VolatileLabelMultisetType> {
        val transform = AffineTransform3D()
        transform.set(
            resolution[0], 0.0, 0.0, offset[0],
            0.0, resolution[1], 0.0, offset[1],
            0.0, 0.0, resolution[2], offset[2]
        )
        return openLabelMultiset(reader, dataset, transform, queue, priority)
    }

    /** Open the dataset as a volatile [LabelMultisetType] source; higher-dimensional data is reduced to a 3D view. */
    suspend fun openLabelMultiset(
        n5: N5Reader,
        dataset: String,
        transform: AffineTransform3D,
        queue: SharedQueue,
        priority: Int,
        mapping: SpatialMapping = SpatialMapping.identity()
    ): ImagesWithTransform<LabelMultisetType, VolatileLabelMultisetType> = withContext(Dispatchers.IO) {

        val cachedLabelMultisetImage: CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray> = openLabelMultiset(n5, dataset)
        val backingCache = cachedLabelMultisetImage.getCache()
        val invalidateLabelMultisetArray = CreateInvalidVolatileLabelMultisetArray(cachedLabelMultisetImage.cellGrid)
        val volatileCache = WeakRefVolatileCache(backingCache, queue, invalidateLabelMultisetArray)

        val unchecked: UncheckedVolatileCache<Long, Cell<VolatileLabelMultisetArray>> = volatileCache.unchecked()
        val cacheHints = CacheHints(LoadingStrategy.VOLATILE, priority, true)

        /* LabelMultiset isn't a standard native type; use the entities-per-pixel + generator constructor
         * and link the type explicitly. The type-argument constructor calls getNativeTypeFactory(), which
         * VolatileLabelMultisetType doesn't support. For now, ignore the deprecation warning */
        @Suppress("DEPRECATION")
        val vimg = VolatileCachedCellImg<VolatileLabelMultisetType, VolatileLabelMultisetArray>(
            cachedLabelMultisetImage.cellGrid,
            VolatileLabelMultisetType().entitiesPerPixel,
            { img -> VolatileLabelMultisetType(img as NativeImg<*, VolatileLabelMultisetArray>) },
            cacheHints,
            unchecked::get
        )
        vimg.setLinkedType(VolatileLabelMultisetType(vimg))

        /* multiset is a label; keep the data nD and let the source project to a 3D view live at the slice positions */
        val grid = gridFor(N5Helpers.getDatasetAttributes(n5, dataset)!!, mapping)
        ImagesWithTransform(cachedLabelMultisetImage, vimg, transform, backingCache, unchecked, grid)
    }

    /** Multi-scale [openLabelMultiset] for the source described by [metadataState], opening all levels in parallel. */
    suspend fun openLabelMultisetMultiscale(
        metadataState: MultiScaleMetadataState,
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<LabelMultisetType, VolatileLabelMultisetType>> {
        val metadata: SpatialMultiscaleMetadata<N5SpatialDatasetMetadata> = metadataState.metadata
        val scalePaths = metadata.paths

        LOG.debug { "Opening groups ${scalePaths.contentToString()} as multi-scale in ${metadata.path} " }

        val scaleTransforms: Array<AffineTransform3D> = metadataState.sourceToXyzTransforms
        val reader = metadataState.reader
        val mapping = metadataState.xyzView.spatialMapping()
        val imagesWithInvalidate = coroutineScope {
            scalePaths.indices.map { scaleIdx ->
                async {
                    LOG.debug { "Populating scale level $scaleIdx" }
                    val img = openLabelMultiset(reader, scalePaths[scaleIdx]!!, scaleTransforms[scaleIdx], queue, priority, mapping)
                    LOG.debug { "Populated scale level $scaleIdx" }
                    img
                }
            }.awaitAll()
        }.toTypedArray()

        return imagesWithInvalidate
    }

    /**
     * Create an empty Paintera label dataset at [group]; a multi-scale `data` group plus an adjacent
     * `unique-labels` group, with `s0` at full resolution and one level per [relativeScaleFactors] entry.
     *
     * @param relativeScaleFactors per-level factors relative to the previous level, e.g. `[[2,2,1],[2,2,2]]` produces absolute `[1,1,1],[2,2,1],[4,4,2]`
     * @param maxNumEntries per-level cap on label-multiset entries, `<= 0` for unbounded; only used when [labelMultisetType]
     * @param overwrite overwrite instead of throwing when [group] already exists
     * @throws UnsupportedOperationException if [writer] is not an N5 container
     */
    fun createPainteraLabelDataset(
        writer: N5Writer,
        group: String,
        dimensions: LongArray,
        blockSize: IntArray,
        resolution: DoubleArray,
        offset: DoubleArray,
        relativeScaleFactors: Array<DoubleArray>,
        unit: String = "pixel",
        maxNumEntries: IntArray? = null,
        labelMultisetType: Boolean,
        overwrite: Boolean = false,
        axes: Array<Axis>? = null
    ) {
        /* `unique-labels` and `label-to-block-mapping` have variable length blocks.
        * Currently, only N5 can support them.  */
        if (writer !is N5KeyValueWriter)
            throw UnsupportedOperationException("Paintera label datasets are only supported in N5 containers, not ${StorageFormat.guessStorageFromUri(writer.uri) ?: writer.javaClass.simpleName}")

        if (!overwrite) {
            val n5Uri = writer.uri
            if (writer.datasetExists(group))
                throw IOException("Dataset `$group' already exists in container `$n5Uri'")
            if (writer.get<JsonObject>(group, N5Helpers.PAINTERA_DATA_KEY) != null)
                throw IOException("Group '$group' already exists in container '$n5Uri' and is a Paintera dataset")
            if (writer.exists(N5URI.normalizeGroupPath("$group/unique-labels")))
                throw IOException("Unique labels group '$group/unique-labels' already exists in container '$n5Uri' -- conflict likely.")
        }

        val painteraDataLabelGroup = N5PainteraLabelMultiscaleGroup.buildForWriting(
            group = group,
            dimensions = dimensions,
            blockSize = blockSize,
            resolution = resolution,
            translation = offset,
            relativeScaleFactors = relativeScaleFactors,
            unit = unit,
            maxNumEntries = maxNumEntries,
            labelMultisetType = labelMultisetType,
            axes = axes
        )
        PainteraLabelMultiscaleParser().writeMetadata(painteraDataLabelGroup, writer, painteraDataLabelGroup.path)
    }
}