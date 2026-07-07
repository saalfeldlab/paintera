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
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.paintera.data.n5.openLabelMultiset
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.SingleScaleMetadataState
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.VolatileHelpers.CreateInvalidVolatileLabelMultisetArray
import org.janelia.saalfeldlab.util.TmpVolatileHelpers
import org.janelia.saalfeldlab.util.TmpVolatileHelpers.RaiWithInvalidate
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiscaleGroup
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiscaleGroup.PainteraLabelMultiscaleParser
import java.io.IOException

object N5Data {

    private val LOG = KotlinLogging.logger {}

    /** [openRaw] building the source transform from [resolution] and [offset]. */
    suspend fun <T : NativeType<T>, V> openRaw(
        reader: N5Reader,
        dataset: String,
        resolution: DoubleArray,
        offset: DoubleArray,
        xyzSourceAxes: IntArray,
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<T, V> where V : Volatile<T>, V : NativeType<V> {
        val transform = AffineTransform3D()
        transform.set(
            resolution[0], 0.0, 0.0, offset[0],
            0.0, resolution[1], 0.0, offset[1],
            0.0, 0.0, resolution[2], offset[2]
        )
        return openRaw<T, V>(reader, dataset, transform, xyzSourceAxes, queue, priority)
    }

    /** [openRaw] for the single-scale source described by [metadataState]. */
    suspend fun <T : NativeType<T>, V> openRaw(
        metadataState: SingleScaleMetadataState,
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<T, V> where V : Volatile<T>, V : NativeType<V> {
        val xyzSourceAxes = getXyzSourceAxes(metadataState)
        return with(metadataState) {
            openRaw<T, V>(reader, group, transform, xyzSourceAxes, queue, priority, isLabel, slicePositions)
        }
    }

    /** Source axis supplying each canonical dimension x, y, z from [metadataState]'s axis metadata (`-1` when absent). */
    private fun getXyzSourceAxes(metadataState: MetadataState): IntArray = SpatialMapping.xyzSourceAxes(metadataState.axes)

    /** Open the dataset as a raw volatile source; higher-dimensional data is reduced to a 3D view, and [forceSlice3D] forces 4D to reduce too. */
    suspend fun <T : NativeType<T>, V> openRaw(
        reader: N5Reader,
        dataset: String,
        transform: AffineTransform3D,
        xyzSourceAxes: IntArray,
        queue: SharedQueue,
        priority: Int,
        forceSlice3D: Boolean = false,
        slicePositions: LongArray? = null
    ): ImagesWithTransform<T, V>
            where V : Volatile<T>, V : NativeType<V> = withContext(Dispatchers.IO) {

        @Suppress("UNCHECKED_CAST")
        val raw = N5Utils.openVolatile<T>(reader, dataset) as CachedCellImg<T, Nothing>
        val cacheHint = CacheHints(LoadingStrategy.VOLATILE, priority, true)
        val vraw: RaiWithInvalidate<V> = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(raw, queue, cacheHint)

        //TODO: We should make 4D channel vs slice selectable during open;
        //  We should also support opening 4D+ as channels where one of the dimensions is configurably the channel dim.

        /* 4D is treated as channels unless forced; 5D+ always reduces. Keep the data nD and let the source project to
         * a 3D (x, y, z) view live at the current slice positions (N5DataSource), so moving the slider just re-views
         * the same backing - no re-open, no cache invalidation. The grid is the projected 3D grid for a reduced source */
        val positions = slicePositions ?: LongArray(raw.numDimensions())
        val mapping = spatialMappingFor(raw.numDimensions(), xyzSourceAxes, forceSlice3D, positions)
        val grid = gridFor(N5Helpers.getDatasetAttributes(reader, dataset)!!, canonical = mapping.isIdentity, mapping)
        ImagesWithTransform<T, V>(raw, vraw.rai, transform, raw.getCache(), vraw.invalidate, grid)
    }

    /**
     * The cell grid carried with the source. Derived from the dataset [attributes] so it matches the grid the commit
     * uses ([N5Helpers.asCellGrid]): for sharded data the block is the shard size, not the inner chunk that the
     * volatile cell image reports. An un-sliced source keeps its full nD grid; a sliced/embedded one is
     * projected to its 3D (x, y, z) spatial grid. Always carried - a sliced source is a view, not a cell image, so its
     * block grid can't be recovered downstream.
     */
    private fun gridFor(attributes: DatasetAttributes, canonical: Boolean, mapping: SpatialMapping): CellGrid =
        if (canonical) CellGrid(attributes.dimensions, attributes.blockSize)
        else CellGrid(mapping.spatialProjection(attributes.dimensions), mapping.spatialProjection(attributes.blockSize))

    /**
     * A [SpatialMapping] mapping an [numDimensions]-D source to a canonical 3D view (slicing non-spatial axes at 0,
     * adding singleton dimensions up to 3 spatial dims)
     *
     * If no mapping is needed, returns an identity mapping.
     *
     * 4D will not slice, in favor of treating it as a channel source, but can be overriden with [forceSlice3D].
     */
    private fun spatialMappingFor(numDimensions: Int, xyzSourceAxes: IntArray, forceSlice3D: Boolean, slicePositions: LongArray): SpatialMapping {
        val allThreeSpatial = xyzSourceAxes.none { it < 0 }
        val canonical3D = numDimensions == 3 && xyzSourceAxes.contentEquals(intArrayOf(0, 1, 2))
        val channels4D = allThreeSpatial && numDimensions == 4 && !forceSlice3D
        return when {
            canonical3D -> SpatialMapping.identity()
            channels4D -> SpatialMapping.identity()
            else -> SpatialMapping(numDimensions, xyzSourceAxes, slicePositions)
        }
    }

    /** Multi-scale [openRaw] for the source described by [metadataState], opening all levels in parallel. */
    suspend fun <T : NativeType<T>, V> openRawMultiscale(
        metadataState: MultiScaleMetadataState,
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<T, V>> where V : Volatile<T>, V : NativeType<V> {
        val scalePaths = metadataState.metadata.paths
        LOG.debug { "Opening groups ${scalePaths.contentToString()} as multi-scale in ${metadataState.group} " }

        val scaleTransform: Array<AffineTransform3D> = metadataState.scaleTransforms
        val reader = metadataState.reader
        val xyzSourceAxes = getXyzSourceAxes(metadataState)
        val isLabel = metadataState.isLabel

        val imagesWithInvalidate = coroutineScope {
            scalePaths.indices.map { scaleIdx ->
                async {
                    /* get the metadata state for the respective child */
                    LOG.debug { "Populating scale level $scaleIdx" }
                    val scaleImgWithInvalidate = openRaw<T, V>(reader, scalePaths[scaleIdx], scaleTransform[scaleIdx], xyzSourceAxes, queue, priority, isLabel, metadataState.slicePositions)
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
        return openLabelMultiset(metadataState.reader, metadataState.group, metadataState.transform, queue, priority, getXyzSourceAxes(metadataState), metadataState.slicePositions)
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
        xyzSourceAxes: IntArray = intArrayOf(0, 1, 2),
        slicePositions: LongArray? = null
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
        val positions = slicePositions ?: LongArray(cachedLabelMultisetImage.numDimensions())
        val mapping = spatialMappingFor(cachedLabelMultisetImage.numDimensions(), xyzSourceAxes, forceSlice3D = true, positions)
        val grid = gridFor(N5Helpers.getDatasetAttributes(n5, dataset)!!, canonical = mapping.isIdentity, mapping)
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

        val scaleTransforms: Array<AffineTransform3D> = metadataState.scaleTransforms
        val reader = metadataState.reader
        val xyzSourceAxes = getXyzSourceAxes(metadataState)
        val imagesWithInvalidate = coroutineScope {
            scalePaths.indices.map { scaleIdx ->
                async {
                    LOG.debug { "Populating scale level $scaleIdx" }
                    val img = openLabelMultiset(reader, scalePaths[scaleIdx]!!, scaleTransforms[scaleIdx], queue, priority, xyzSourceAxes, metadataState.slicePositions)
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
            offset = offset,
            relativeScaleFactors = relativeScaleFactors,
            unit = unit,
            maxNumEntries = maxNumEntries,
            labelMultisetType = labelMultisetType,
            axes = axes
        )
        PainteraLabelMultiscaleParser().writeMetadata(painteraDataLabelGroup, writer, painteraDataLabelGroup.path)
    }
}