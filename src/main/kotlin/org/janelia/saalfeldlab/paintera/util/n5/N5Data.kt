package org.janelia.saalfeldlab.paintera.util.n5

import bdv.cache.SharedQueue
import bdv.img.cache.VolatileCachedCellImg
import net.imglib2.RandomAccessible
import net.imglib2.Volatile
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.cache.img.SingleCellArrayImg
import net.imglib2.cache.ref.WeakRefVolatileCache
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.cache.volatiles.LoadingStrategy
import net.imglib2.img.NativeImg
import net.imglib2.type.NativeType
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.label.VolatileLabelMultisetArray
import net.imglib2.type.label.VolatileLabelMultisetType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisUtils
import org.janelia.saalfeldlab.paintera.data.n5.openLabelMultiset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.VolatileHelpers
import org.janelia.saalfeldlab.util.TmpVolatileHelpers
import org.janelia.saalfeldlab.util.hyperSlice
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform

object N5Data {


    @JvmStatic
    fun MultiScaleMetadataState.openLabelMultisetMultiscale(
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<LabelMultisetType, VolatileLabelMultisetType>> {
        TODO()
    }

    @JvmStatic
    fun MetadataState.openLabelMultiset(
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<LabelMultisetType, VolatileLabelMultisetType>> {
        val imgs = (this as? MultiScaleMetadataState)?.openLabelMultisetMultiscale(queue, priority)
        return imgs ?: arrayOf(openLabelMultisetSingleScale(queue, priority))
    }


    private fun MetadataState.openLabelMultisetSingleScale(
        queue: SharedQueue,
        priority: Int
    ): ImagesWithTransform<LabelMultisetType, VolatileLabelMultisetType> {

        val cachedLabelMultisetImage = openLabelMultiset(reader, dataset)
        assert(cachedLabelMultisetImage.numDimensions() == 3) {
            "Label Multiset Type is only supported for 3D data, but $dataset is ${cachedLabelMultisetImage.numDimensions()} dimensional"
        }

        val cellGrid = cachedLabelMultisetImage.cellGrid
        val createInvalid = VolatileHelpers.CreateInvalidVolatileLabelMultisetArray(cellGrid)
        val volatileCache = WeakRefVolatileCache(cachedLabelMultisetImage.cache, queue, createInvalid)
        val uncheckedCache = volatileCache.unchecked()

        val cacheHints = CacheHints(LoadingStrategy.VOLATILE, priority, true)

        val vimg = VolatileCachedCellImg<VolatileLabelMultisetType, VolatileLabelMultisetArray>(
            cellGrid,
            VolatileLabelMultisetType().entitiesPerPixel,
            { img -> VolatileLabelMultisetType(img as NativeImg<*, VolatileLabelMultisetArray?>?) },
            cacheHints,
            { a, b -> uncheckedCache.get(a, b) }
        )
        vimg.setLinkedType(VolatileLabelMultisetType(vimg))
        return ImagesWithTransform(
            cachedLabelMultisetImage,
            vimg,
            transform,
            cachedLabelMultisetImage.cache,
            uncheckedCache
        )
    }

    @JvmStatic
    fun <T, V> MetadataState.openRaw(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<T, V>>
            where T : NativeType<T>, V : Volatile<T>, V : NativeType<V> {
        val multiScaleImgs = (this as? MultiScaleMetadataState)?.openRawMultiscale<T, V>(queue, priority)
        return multiScaleImgs ?: arrayOf(openRawSingleScale(queue, priority))
    }

    @JvmStatic
    fun <T, V> MultiScaleMetadataState.openRawMultiscale(
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<T, V>>
            where T : NativeType<T>, V : Volatile<T>, V : NativeType<V> {

        val perScaleMetadata = metadata.childrenMetadata
        val perScaleImgs = Array(perScaleMetadata.size) { idx ->
            val scaleMetadata = perScaleMetadata[idx]
            val scaleMetadataState = MetadataUtils.createMetadataState(n5ContainerState, scaleMetadata)!!
            scaleMetadataState.axes = axes
            scaleMetadataState.slicePositions = slicePositions
            scaleMetadataState.updateTransform(scaleTransforms[idx])
            scaleMetadataState.openRaw<T, V>(queue, priority)[0]
        }
        return perScaleImgs
    }

    private fun <T, V> MetadataState.openRawSingleScale(queue: SharedQueue, priority: Int): ImagesWithTransform<T, V>
            where T : NativeType<T>, V : Volatile<T>, V : NativeType<V> {

        with(this) {
            val img: CachedCellImg<T, *> = N5Utils.openVolatile(reader, dataset)

            @Suppress("UNCHECKED_CAST")
            val slice3D = slice3D(img) as CachedCellImg<T, Nothing>

            val raiWithInvalidate = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate<T, V, Nothing>(
                slice3D,
                queue,
                CacheHints(LoadingStrategy.VOLATILE, priority, true)
            )
            return ImagesWithTransform(
                slice3D,
                raiWithInvalidate.rai,
                transform,
                slice3D.cache,
                raiWithInvalidate.invalidate
            )
        }
    }

    @JvmStatic
    fun <T> MetadataState.slice3D(
        nDImg: CachedCellImg<T, *>,
    ): CachedCellImg<T, *>
            where T : NativeType<T> {
        val imgSizeND = nDImg.dimensionsAsLongArray()
//        if (imgSizeND.size <= 3)
//            return nDImg

        @Suppress("UNCHECKED_CAST")
                /* expected to be NumericType, but neither Views.extendZero, nor the preceding caller enforce it. */
        val zeroExtended = Views.extendZero(nDImg as CachedCellImg<Nothing, *>) as RandomAccessible<T>

        val axesAndSlices = axes.mapIndexed { idx, axis ->
            axis to (slicePositions?.getOrNull(idx) ?: -1)
        }

        var hyperSliceImg: RandomAccessible<T> = zeroExtended
        axesAndSlices.indices.reversed().forEach { idx ->
            val (_, slicePos) = axesAndSlices[idx]
            if (slicePos >= 0)
                hyperSliceImg = hyperSliceImg.hyperSlice(idx, slicePos.toLong())
        }

        val imgSize3D = LongArray(3)

        val cellSizeND = datasetAttributes.chunkSize
        val cellSize3D = IntArray(3)
        val spatialAxes = axes
            .mapIndexed { dimIdx, axis -> dimIdx to axis }
            .filter { (_, axis) -> axis.type == Axis.SPACE }

        spatialAxes
            .forEachIndexed { idx, (spatialIdx, _) ->
                imgSize3D[idx] = imgSizeND[spatialIdx]
                cellSize3D[idx] = cellSizeND[spatialIdx]
            }

        val hyperSliceRai = Views.interval(hyperSliceImg, Intervals.createMinSize(0, 0, 0, *imgSize3D))

        val initialAxisOrder = spatialAxes.sortedBy { (key, _) -> key }.map { (_, value) -> value }.map { it.name }.toTypedArray()
        val xyzAxisOrder = spatialAxes.map { (_, axis) -> axis.name }.sorted().toTypedArray()

        val permutation = AxisUtils.findPermutation(initialAxisOrder, xyzAxisOrder)
        val affinePermutation = AxisUtils.axisPermutationTransform(permutation)
        val permutedTransform = transform.copy().preConcatenate(affinePermutation)
        updateTransform(permutedTransform)

        val hyperSliceXYZ =
            if (AxisUtils.isIdentityPermutation(permutation))
                hyperSliceRai
            else
                AxisUtils.permute(hyperSliceRai, permutation)

        val options = DiskCachedCellImgOptions().cellDimensions(*cellSize3D)
        return DiskCachedCellImgFactory<T>(
            nDImg.getType(),
            options
        ).create(imgSize3D) { cell: SingleCellArrayImg<T, *> ->
            val hypersliceOverCell = Views.interval(hyperSliceXYZ, cell)
            val sliceCursor = hypersliceOverCell.cursor()
            val cellCursor = cell.cursor()
            while (cellCursor.hasNext()) {
                cellCursor.next().set(sliceCursor.next())
            }
        }
    }
}