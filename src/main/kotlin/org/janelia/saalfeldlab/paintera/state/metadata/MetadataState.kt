package org.janelia.saalfeldlab.paintera.state.metadata

import bdv.cache.SharedQueue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.NgffSingleScaleAxesMetadata
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.data.XyzView
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState.Companion.isLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.fallbackAxes
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.getAxes
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.isLabelMultiset
import org.janelia.saalfeldlab.util.n5.*
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiscaleGroup
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiscaleGroup
import kotlin.streams.asSequence

interface MetadataState {

    var n5ContainerState: N5ContainerState

    val metadata: SpatialMetadata
    var datasetAttributes: DatasetAttributes
    /** transform of the dataset as provided by the metadata; can be updated with [updateTransform].
     * Downstream properties that depend on the source transform (resolution, translation, etc.) should
     * always be immutable and re-derived from [sourceTransform] after [updateTransform] */
    val sourceTransform: AffineTransform3D
    /** [sourceTransform] seen from the canonical x, y, z axes */
    val sourceToXyz: AffineTransform3D
    var isLabel: Boolean
    var isLabelMultiset: Boolean
    var minIntensity: Double
    var maxIntensity: Double
    var axes: Array<Axis>
    val xyzView: XyzView

    var unit: String
    var labelBlockLookup: LabelBlockLookup?
    val reader: N5Reader

    val writer: N5Writer?
    var group: String
    val dataset: String
        get() = N5URI.normalizeGroupPath(group)

    fun updateTransform(newTransform: AffineTransform3D)
    fun updateTransform(resolution: DoubleArray, offset: DoubleArray)

    fun <D, T> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> where D : NativeType<D>, T : Volatile<D>, T : NativeType<T>

    fun copy(): MetadataState

    companion object {
        fun isLabel(dataType: DataType): Boolean {
            return when (dataType) {
                DataType.UINT64 -> true
                DataType.UINT32 -> true
                DataType.INT16 -> true
                else -> false
            }
        }

        @JvmStatic
        fun <T : MetadataState> setBy(source: T, target: T) {
            target.isLabelMultiset = source.isLabelMultiset
            target.isLabel = source.isLabel
            target.datasetAttributes = source.datasetAttributes
            target.minIntensity = source.minIntensity
            target.maxIntensity = source.maxIntensity
            target.axes = source.axes.copyOf()
            target.updateTransform(source.transform)
            target.unit = source.unit
            target.group = source.group
            source.xyzView.nonSpatialAxes.forEach { target.xyzView.sliceAt(it, source.xyzView.slicePosition(it)) }
            target.xyzView.setCropInterval(source.xyzView.xyzCrop)
        }
    }
}

/** for now MetadataState now tracks the concept of `sourceToXyz` transform separate from `sourceTransform`.
 * This is necessary now that we handle more than just 3D sources; but MetadataState imo is an awkward palce
 * for this. Ideally, MetadataState should reflect the actual metadata, and `toXyz` portion should be
 * in some canonicalization layer, similar to how `SpatialMapping` behaves. But for now, let's try this.
 *
 * It removes the old fields we assumed were 3D, and replaces them with immutable getters that expose the same
 * properties as we previously expectd (so 3D xyz transform, resolution = [x,y,z] and translation = [x,y,z].
 *
 * At some point, we should consider refactoring this. */
val MetadataState.transform: AffineTransform3D
    get() = sourceToXyz
val MetadataState.resolution: DoubleArray
    get() = transform.run { doubleArrayOf(get(0, 0), get(1, 1), get(2, 2)) }
val MetadataState.translation: DoubleArray
    get() = transform.translation

open class SingleScaleMetadataState(
    final override var n5ContainerState: N5ContainerState,
    final override val metadata: N5SpatialDatasetMetadata,
) : MetadataState {

    override var isLabelMultiset: Boolean = isLabelMultiset(n5ContainerState.reader, N5URI.normalizeGroupPath(metadata.path)!!, metadata)
    override var isLabel: Boolean = isLabel(metadata.attributes.dataType) || isLabelMultiset
    override var datasetAttributes: DatasetAttributes = metadata.attributes
    override var minIntensity = metadata.minIntensity()
    override var maxIntensity = metadata.maxIntensity()

    override val sourceTransform: AffineTransform3D = metadata.spatialTransform3d().copy()
    override var axes: Array<Axis> = (getAxes() ?: fallbackAxes()).copyOf()
        set(value) {
            field = value
            cachedSourceToXyz = null
        }
    private var cachedSourceToXyz: AffineTransform3D? = null
    override val sourceToXyz: AffineTransform3D
        get() = cachedSourceToXyz ?: SpatialMapping.of(axes).toXyz(sourceTransform).also { cachedSourceToXyz = it }

    override val xyzView: XyzView by lazy { XyzView.of(axes, datasetAttributes.dimensions) }
    override var unit: String = metadata.unit()
    override var labelBlockLookup: LabelBlockLookup? = null
    override val reader
        get() = n5ContainerState.reader
    override val writer: N5Writer?
        get() = n5ContainerState.writer

    override var group = N5URI.normalizeGroupPath(metadata.path)!!

    override fun copy(): SingleScaleMetadataState {
        return SingleScaleMetadataState(n5ContainerState, metadata).also {
            MetadataState.setBy(this, it)
        }
    }

    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
        updateTransform(newTransform)
    }

    override fun updateTransform(newTransform: AffineTransform3D) {
        val updatedTransforms = SpatialMapping.of(axes).rebase(arrayOf(sourceTransform), newTransform) ?: return
        sourceTransform.set(updatedTransforms[0])
        cachedSourceToXyz = null
    }

    override fun <D, T> getData(
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<D, T>>
            where D : NativeType<D>, T : Volatile<D>, T : NativeType<T> = runBlocking {
        val img = when {
            isLabelMultiset -> N5Data.openLabelMultiset(this@SingleScaleMetadataState, queue, priority)
            else -> N5Data.openRaw<D, T>(this@SingleScaleMetadataState, queue, priority)
        }

        arrayOf(img) as Array<ImagesWithTransform<D, T>>
    }
}


open class MultiScaleMetadataState(
    override var n5ContainerState: N5ContainerState,
    final override val metadata: SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>,
) : MetadataState by SingleScaleMetadataState(n5ContainerState, metadata[0]) {

    val highestResMetadata: N5SpatialDatasetMetadata = metadata[0]
    override var axes: Array<Axis> = (getAxes() ?: fallbackAxes()).copyOf()
        set(value) {
            field = value
            cachedSourceToXyzTransforms = null
        }

    //TODO: xyzView should not live in the MetadataState when migration is done.
    override val xyzView: XyzView by lazy { XyzView.of(axes, datasetAttributes.dimensions) }

    /** Per level in source axis order; [sourceTransform] is level 0 */
    val sourceTransforms: Array<AffineTransform3D> = metadata.spatialTransforms3d().map { it.copy() }.toTypedArray()
    override val sourceTransform: AffineTransform3D
        get() = sourceTransforms[0]

    private var cachedSourceToXyzTransforms: Array<AffineTransform3D>? = null
    /** [sourceTransforms] seen from the canonical x, y, z axes; [sourceToXyz] is level 0 */
    val sourceToXyzTransforms: Array<AffineTransform3D>
        get() = cachedSourceToXyzTransforms ?: SpatialMapping.of(axes).let { mapping -> sourceTransforms.map { mapping.toXyz(it) }.toTypedArray() }.also { cachedSourceToXyzTransforms = it }
    override val sourceToXyz: AffineTransform3D
        get() = sourceToXyzTransforms[0]
    final override var isLabelMultiset: Boolean = isLabelMultiset(n5ContainerState.reader, N5URI.normalizeGroupPath(metadata[0].path)!!, metadata[0])
    override var isLabel: Boolean = when {
        metadata is N5PainteraLabelMultiscaleGroup -> metadata.isLabel
        else -> isLabel(highestResMetadata.attributes.dataType) || isLabelMultiset
    }
    override var group: String = N5URI.normalizeGroupPath(metadata.path)
    override val dataset: String = N5URI.normalizeGroupPath(metadata.path)

    /**
     * Per-level downsampling factors relative to s0, one entry per source axis. Non-spatial axes are included, so a
     * pyramid that downsamples time reports it; [sourceToXyzTransforms] is 3D and cannot.
     */
    val scaleFactors: Array<DoubleArray> by lazy {
        val levelScales = metadata.childrenMetadata.mapIndexed { level, levelMetadata -> levelScale(level, levelMetadata) }
        val highestResScale = levelScales[0]
        Array(levelScales.size) { level -> DoubleArray(highestResScale.size) { levelScales[level][it] / highestResScale[it] } }
    }

    /* the nD scale where the container stores one, otherwise the 3D transform diagonal at the spatial axes and 1 elsewhere */
    private fun levelScale(level: Int, levelMetadata: N5SpatialDatasetMetadata): DoubleArray {
        val numDimensions = levelMetadata.attributes.numDimensions
        (levelMetadata as? NgffSingleScaleAxesMetadata)?.scale?.takeIf { it.size == numDimensions }?.let {
            return it
        }
        val sLevelToXyz = sourceToXyzTransforms[level]
        return DoubleArray(numDimensions) { 1.0 }.also { scale ->
            xyzView.xyzSourceAxes.forEachIndexed { slot, axis ->
                if (axis >= 0)
                    scale[axis] = sLevelToXyz.get(slot, slot)
            }
        }
    }

    override fun copy(): MultiScaleMetadataState {
        return MultiScaleMetadataState(n5ContainerState, metadata).also {
            MetadataState.setBy(this, it)
        }
    }

    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
        updateTransform(newTransform)
    }

    override fun updateTransform(newTransform: AffineTransform3D) {
        val updatedTransforms = SpatialMapping.of(axes).rebase(sourceTransforms, newTransform) ?: return
        updatedTransforms.forEachIndexed { level, sLevelSourceTransform -> sourceTransforms[level].set(sLevelSourceTransform) }
        cachedSourceToXyzTransforms = null
    }


    override fun <D, T> getData(
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<D, T>> where D : NativeType<D>, T : Volatile<D>, T : NativeType<T> = runBlocking {
        when {
            isLabelMultiset -> N5Data.openLabelMultisetMultiscale(this@MultiScaleMetadataState, queue, priority)
            else -> N5Data.openRawMultiscale<D, T>(this@MultiScaleMetadataState, queue, priority)
        } as Array<ImagesWithTransform<D, T>>
    }
}

class PainteraDataMultiscaleMetadataState(
    n5ContainerState: N5ContainerState,
    var painteraDataMultiscaleMetadata: N5PainteraDataMultiscaleGroup,
) : MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata) {

    override var maxIntensity: Double = (painteraDataMultiscaleMetadata as? N5PainteraLabelMultiscaleGroup)?.maxId?.toDouble() ?: super.maxIntensity

    @Suppress("UNCHECKED_CAST")
    val dataMetadataState = MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata.dataGroupMetadata as SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>)

    override val xyzView: XyzView
        get() = dataMetadataState.xyzView

    override var axes: Array<Axis>
        get() = dataMetadataState.axes
        set(value) {
            dataMetadataState.axes = value
            super.axes = value
        }

    override fun updateTransform(newTransform: AffineTransform3D) {
        dataMetadataState.updateTransform(newTransform)
        super.updateTransform(newTransform)
    }

    override fun <D, T> getData(
        queue: SharedQueue,
        priority: Int
    ): Array<ImagesWithTransform<D, T>>
            where D : NativeType<D>, T : Volatile<D>, T : NativeType<T> = runBlocking {
        when {
            isLabelMultiset -> N5Data.openLabelMultisetMultiscale(dataMetadataState, queue, priority)
            else -> N5Data.openRawMultiscale<D, T>(dataMetadataState, queue, priority)
        } as Array<ImagesWithTransform<D, T>>
    }

    override fun copy(): PainteraDataMultiscaleMetadataState {
        return PainteraDataMultiscaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata).also {
            MetadataState.setBy(this, it)
        }
    }
}

operator fun <T> SpatialMultiscaleMetadata<T>.get(index: Int): T where T : N5SpatialDatasetMetadata {
    return childrenMetadata[index]
}

class MetadataUtils {

    enum class SpatialAxes(val label: String) {
        X("x"),
        Y("y"),
        Z("z");

        companion object {
            val labels = SpatialAxes.entries.map { it.label }
            val default = SpatialAxes.entries.associate { Axis(Axis.SPACE, it.name, null) to it.ordinal }
        }
    }

    companion object {

        private val LOG = KotlinLogging.logger {}

        val N5SpatialDatasetMetadata.isLabelMultiset
            get() = when (this) {
                is N5SingleScaleMetadata -> isLabelMultiset
                else -> false
            }

        /**
         * Whether [dataset] is a label multiset. Falls back to reading the `isLabelMultiset` attribute directly,
         * because OME-NGFF metadata ([NgffSingleScaleAxesMetadata]) does not expose the flag on its own.
         */
        @JvmStatic
        fun isLabelMultiset(
            reader: N5Reader,
            dataset: String,
            metadata: N5SpatialDatasetMetadata
        ): Boolean {
            val multisetKey = N5Helpers.IS_LABEL_MULTISET_KEY
            val multisetKeyWithNamespace = "${N5Helpers.IMGLIB2_NAMESPACE}/${N5Helpers.IS_LABEL_MULTISET_KEY}"
            return metadata.isLabelMultiset || (reader[dataset, multisetKey] ?: reader[dataset, multisetKeyWithNamespace] ?: false)
        }

        val N5SpatialDatasetMetadata.resolution: DoubleArray
            get() = when (this) {
                is N5SingleScaleMetadata -> pixelResolution!!
                is NgffSingleScaleAxesMetadata -> scale
                else -> DoubleArray(this.spatialTransform().numDimensions()) { 1.0 }
            }

        val N5SpatialDatasetMetadata.offset
            get() = when (this) {
                is N5SingleScaleMetadata -> offset!!
                is NgffSingleScaleAxesMetadata -> translation!!
                else -> DoubleArray(this.spatialTransform().numDimensions()) { 0.0 }
            }

        @JvmStatic
        fun MetadataState.getAxes(): Array<Axis>? = when (this) {
            is SingleScaleMetadataState -> (metadata as? AxisMetadata)?.axes
            is MultiScaleMetadataState -> (metadata as? AxisMetadata)?.axes ?: (highestResMetadata as? AxisMetadata)?.axes
            else if (metadata is AxisMetadata) -> (metadata as AxisMetadata).axes
            else -> null
        }

        /** Canonical x, y, z, [c, t, ...] axes. */
        @JvmStatic
        fun MetadataState.fallbackAxes(): Array<Axis> = N5Helpers.canonicalAxes(datasetAttributes.numDimensions)

        /**
         * If the MetadataState has [N5PainteraLabelMultiscaleGroup], create a [FragmentSegmentAssignmentOnlyLocal]
         * used for fragment-segment lookups and persisting.
         *
         * If the group does not exist, but is otherwise valid, this will create it.
         * If the N5Container is read-only, the existing LUT will be used if found, but no
         *  new persisting can occur.
         */
        @JvmStatic
        val MetadataState.fragmentSegmentAssignmentState: FragmentSegmentAssignmentOnlyLocal
            get() {

                val (lut, persist) = (metadata as? N5PainteraLabelMultiscaleGroup)?.let { painteraLabels ->
                    val lut = painteraLabels.fragmentSegmentAssignment?.let { initialLutMetadata ->
                        if (reader.exists(initialLutMetadata.path))
                            N5FragmentSegmentAssignmentInitialLut(reader, initialLutMetadata.path)
                        else null
                    } ?: NO_INITIAL_LUT_AVAILABLE
                    val persist = writer?.let { n5Writer ->
                        runCatching {
                            N5FragmentSegmentAssignmentPersister(n5Writer, "${painteraLabels.path}/${N5Helpers.PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASET}")
                        }.getOrElse {
                            LOG.error(it) {}
                            FragmentSegmentAssignmentOnlyLocal.doesNotPersist("Cannot Persist: $it")
                        }
                    } ?: FragmentSegmentAssignmentOnlyLocal.doesNotPersist("Persisting assignments not supported for read-only $group")
                    lut to persist
                } ?: let {
                    val reason = "Persisting assignments not supported for non Paintera group/dataset $group"
                    NO_INITIAL_LUT_AVAILABLE to FragmentSegmentAssignmentOnlyLocal.doesNotPersist(reason)
                }

                return FragmentSegmentAssignmentOnlyLocal(lut, persist)

            }


        /**
         * Checks if the given metadata is valid. The metadata is considered valid if it is either
         * a SpatialMultiscaleMetadata with children of type N5SpatialDatasetMetadata, or it is
         * a single scale N5SpatialDatasetMetadata.
         *
         * @param metadata The metadata to validate.
         * @return True if the metadata is valid, false otherwise.
         */
        @JvmStatic
        fun metadataIsValid(metadata: N5Metadata?): Boolean {
            return (metadata as? SpatialMultiscaleMetadata<*>)?.let {
                it.childrenMetadata[0] is N5SpatialDatasetMetadata
            } ?: run {
                metadata is N5SpatialDatasetMetadata
            }
        }

        @JvmStatic
        fun createMetadataState(n5ContainerState: N5ContainerState, metadata: N5Metadata?): MetadataState? {
            @Suppress("UNCHECKED_CAST")
            return when {
                metadata is N5PainteraDataMultiscaleGroup -> PainteraDataMultiscaleMetadataState(n5ContainerState, metadata)
                (metadata as? SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>) != null -> MultiScaleMetadataState(n5ContainerState, metadata)
                metadata is N5SpatialDatasetMetadata -> SingleScaleMetadataState(n5ContainerState, metadata)
                else -> null
            }
        }

        @JvmStatic
        fun createMetadataState(n5Uri: String): MetadataState? {

            val n5URI = N5URI(n5Uri)
            val containerPath = n5URI.containerPath
            val dataset = n5URI.groupPath

            return createMetadataState(containerPath, dataset)
        }

        @JvmStatic
        fun createMetadataState(n5container: String, dataset: String = ""): MetadataState? {
            val container = StorageFormat.parseUri(n5container).b.toString()

            val newContainerState by lazy(LazyThreadSafetyMode.NONE) {
                Paintera.n5Factory.openWriterOrReaderOrNull(n5container)?.let {
                    N5ContainerState(it)
                }
            }
            val containerState = N5ContainerStateCache.cache.getOrPut(container) {
                newContainerState
            } ?: newContainerState ?: return null
            return createMetadataState(containerState, dataset)
        }

        @JvmStatic
        fun createMetadataState(n5ContainerState: N5ContainerState, dataset: String, datasetTreeNode: N5TreeNode? = null): MetadataState? {
            val metadataFromParam = datasetTreeNode?.takeIf { N5URI.normalizeGroupPath(dataset) == N5URI.normalizeGroupPath(it.path) }
            val metadataFromParser by lazy(LazyThreadSafetyMode.NONE) { discoverAndParseRecursive(n5ContainerState.reader, dataset) }
            val metadataRoot = metadataFromParam ?: metadataFromParser

            val normalizedPath = N5URI.normalizeGroupPath(dataset)
            return N5TreeNode.flattenN5Tree(metadataRoot)
                .asSequence()
                .filter { node: N5TreeNode -> (normalizedPath == N5URI.normalizeGroupPath(node.path) || normalizedPath == node.nodeName) && metadataIsValid(node.metadata) }
                .map { obj: N5TreeNode -> obj.metadata }
                .map { md: N5Metadata -> createMetadataState(n5ContainerState, md) }
                .firstOrNull()?.also {
                    LOG.debug { "Metadata State created for $n5ContainerState:$dataset ($it)" }
                }
        }

        /**
         * Creates a metadata state from the given N5Reader and dataset path.
         *
         * @param reader The N5Reader to access the container
         * @param dataset The path to the dataset within the container
         * @return MetadataState if successfully created, null otherwise
         */
        @JvmStatic

        fun createMetadataState(reader: N5Reader, dataset: String): MetadataState? {
            val newContainerState by lazy(LazyThreadSafetyMode.NONE) { N5ContainerState(reader) }
            /* Realistically, the null case should never be triggered, but the return type of `getOrPut` is nullable,
             * so this is the safe thing to do. In either case, it should be the same result. */
            val containerState = N5ContainerStateCache.cache.getOrPut(reader.uri.toString()) { newContainerState }
                ?: newContainerState

            return createMetadataState(containerState, dataset)
        }

        fun transformFromResolutionOffset(resolution: DoubleArray, offset: DoubleArray): AffineTransform3D {
            val newTransform = AffineTransform3D()
            newTransform.set(
                resolution[0], 0.0, 0.0, offset[0],
                0.0, resolution[1], 0.0, offset[1],
                0.0, 0.0, resolution[2], offset[2]
            )
            return newTransform
        }
    }
}

/** Set s0's canonical transform to [newS0ToXyz] and move every other level accordingly */
private fun SpatialMapping.rebase(sourceTransforms: Array<AffineTransform3D>, newS0ToXyz: AffineTransform3D): List<AffineTransform3D>? {
	val sourceToXyzTransforms = sourceTransforms.map { toXyz(it) }
	val s0ToXyz = sourceToXyzTransforms[0]
	if (newS0ToXyz.rowPackedCopy.contentEquals(s0ToXyz.rowPackedCopy))
		return null
	val xyzToS0 = s0ToXyz.inverse()
	val currentXyzToNewXyz = newS0ToXyz.copy().concatenate(xyzToS0)

	val s0SourceTransform = fromXyz(newS0ToXyz)
	val otherSourceTransforms = sourceToXyzTransforms.drop(1).map { sLevelToXyz ->
		val newSLevelToXyz = currentXyzToNewXyz.copy().concatenate(sLevelToXyz)
		fromXyz(newSLevelToXyz)
	}
	return listOf(s0SourceTransform) + otherSourceTransforms
}
