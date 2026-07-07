package org.janelia.saalfeldlab.paintera.testdata

import com.google.common.collect.Iterators
import com.google.gson.GsonBuilder
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter
import org.janelia.saalfeldlab.n5.Compression
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.GzipCompression
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser
import org.janelia.saalfeldlab.n5.zarr.v3.ZarrV3DatasetAttributes
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataShape.Companion.ALL
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataShape.Companion.ALL_UNSHARDED
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataShape.Companion.SMALL_UNSHARDED_2D
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataShape.Companion.SMALL_UNSHARDED_5D
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataType.Companion.SCALAR
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataType.LabelMultiset
import org.janelia.saalfeldlab.paintera.testdata.TestData.Metadata.OME_NGFF_04
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import org.janelia.saalfeldlab.n5.DataType as N5DataType

/**
 * Test cases to verify Paintera actions against.
 *
 * A [TestCase] is a single result of the cross product of storage [format], [metadata], [dataType], [shape]
 * (dimensionality + block/chunk/sharding), and [scalePyramid] (single vs multi-scale). The named collections
 * ([allCases], [rawSources], ...) are subsets of the full cross product that are valid for a given action.
 */
object TestData {

    enum class Formats(val storageFormat: StorageFormat) : Sequence<Formats> {
        ZARR(StorageFormat.ZARR),
        ZARR3(StorageFormat.ZARR3),
        ZARR2(StorageFormat.ZARR2),
        N5(StorageFormat.N5),
        HDF5(StorageFormat.HDF5);

        override fun iterator() = Iterators.singletonIterator(this)

        companion object {
            val ALL = entries.asSequence()
        }
    }

    enum class Metadata : Sequence<Metadata> {
        NONE,
        OME_NGFF_04,
        OME_NGFF_05;

        /** OME-NGFF version string, or `null` for non-OME metadata. */
        val omeZarrVersion: String?
            get() = when (this) {
                NONE -> null
                OME_NGFF_04 -> "0.4"
                OME_NGFF_05 -> "0.5"
            }

        override fun iterator() = Iterators.singletonIterator(this)

        companion object {
            val ALL = entries.asSequence()
        }
    }

    enum class DataType : Sequence<DataType> {
        LabelMultiset,
        INT8,
        UINT64;

        /** On-disk n5 data type; [LabelMultiset] is serialized into raw `UINT8` byte blocks. */
        val n5DataType: N5DataType
            get() = when (this) {
                LabelMultiset -> N5DataType.UINT8
                INT8 -> N5DataType.INT8
                UINT64 -> N5DataType.UINT64
            }

        override fun iterator() = Iterators.singletonIterator(this)

        companion object {

            val ALL = entries.asSequence()
            val SCALAR = entries.asSequence().filterNot { it == LabelMultiset }
        }
    }

    enum class ScalePyramid : Sequence<ScalePyramid> {
        Single,
        Multi;

        override fun iterator() = Iterators.singletonIterator(this)

        companion object {
            val ALL = entries.asSequence()
        }
    }

    class DataShape(val blockSize: LongArray, val chunkSize: LongArray, val sharded: Boolean) : Sequence<DataShape> {

        init {
            if (!sharded) {
                require(blockSize.contentEquals(chunkSize)) { "blockSize and chunkSize must be equal for unsharded datasets" }
            }
            require(blockSize.size == chunkSize.size) { "blockSize and chunkSize must have the same number of dimensions" }
            if (sharded) {
                blockSize.zip(chunkSize) { block, chunk ->
                    require(block >= chunk) { "blockSize must be greater than or equal to chunkSize for sharded datasets" }
                    require(block % chunk == 0L) { "blockSize must be a multiple of chunkSize for sharded datasets" }
                }
            }
        }

        val numDimensions get() = blockSize.size

        override fun iterator() = Iterators.singletonIterator(this)

        companion object {

            private val SMALL_BLOCK_2D = longArrayOf(50, 50)

            private val SMALL_BLOCK_3D = longArrayOf(50, 50, 50)
            private val SMALL_CHUNK_3D = longArrayOf(10, 10, 10)

            private val SMALL_BLOCK_4D = longArrayOf(50, 50, 50, 1)
            private val SMALL_CHUNK_4D = longArrayOf(10, 10, 10, 1)

            private val SMALL_BLOCK_5D = longArrayOf(50, 50, 50, 1, 1)
            private val SMALL_CHUNK_5D = longArrayOf(10, 10, 10, 1, 1)

            val SMALL_UNSHARDED_2D = DataShape(SMALL_BLOCK_2D, SMALL_BLOCK_2D, false)

            val SMALL_UNSHARDED_3D = DataShape(SMALL_BLOCK_3D, SMALL_BLOCK_3D, false)
            val SMALL_SHARDED_3D = DataShape(SMALL_BLOCK_3D, SMALL_CHUNK_3D, true)

            val SMALL_UNSHARDED_4D = DataShape(SMALL_BLOCK_4D, SMALL_BLOCK_4D, false)
            val SMALL_SHARDED_4D = DataShape(SMALL_BLOCK_4D, SMALL_CHUNK_4D, true)

            val SMALL_UNSHARDED_5D = DataShape(SMALL_BLOCK_5D, SMALL_BLOCK_5D, false)
            val SMALL_SHARDED_5D = DataShape(SMALL_BLOCK_5D, SMALL_CHUNK_5D, true)

            val ALL_UNSHARDED = sequenceOf(SMALL_UNSHARDED_3D, SMALL_UNSHARDED_4D, SMALL_UNSHARDED_5D)
            val ALL_SHARDED = sequenceOf(SMALL_SHARDED_3D, SMALL_SHARDED_4D, SMALL_SHARDED_5D)

            val ALL = ALL_UNSHARDED + ALL_SHARDED

            val ALL_3D = SMALL_UNSHARDED_3D + SMALL_SHARDED_3D
            val ALL_4D = SMALL_UNSHARDED_4D + SMALL_SHARDED_4D
            val ALL_5D = SMALL_UNSHARDED_5D + SMALL_SHARDED_5D
        }
    }

    class TestCase(val format: StorageFormat, val metadata: Metadata, val dataType: DataType, val shape: DataShape, val scalePyramid: ScalePyramid) {

        val extension get() = when (format) {
            StorageFormat.N5 -> ".n5"
            StorageFormat.HDF5 -> ".h5"
            else -> ".zarr"
        }
        val sharded get() = shape.sharded
        val numDimensions get() = shape.numDimensions
        val omeZarrVersion get() = metadata.omeZarrVersion
        val isLabelMultiset get() = dataType == LabelMultiset

        override fun toString() =
            "$format/$metadata/$dataType/${numDimensions}D${if (sharded) "-sharded" else ""}/$scalePyramid"
    }

    private fun matrixOf(
        format: Sequence<Formats>,
        metadata: Sequence<Metadata>,
        dataType: Sequence<DataType>,
        shape: Sequence<DataShape>,
        scalePyramid: Sequence<ScalePyramid>,
    ): List<TestCase> = format.flatMap { format -> metadata.flatMap { metadata -> dataType.flatMap { dataType -> shape.flatMap { shape ->
        scalePyramid.map { scalePyramid -> TestCase(format.storageFormat, metadata, dataType, shape, scalePyramid) }
    } } } }.toList()

    /*
     * The named collections below are exposed as static List fields to be used via JUnit's @FieldSource
     * for parameteraized tests; e.g.
     *   @FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#rawSources")
     */

    @JvmField
    val n5Scalar = matrixOf(Formats.N5, Metadata.ALL, SCALAR, ALL_UNSHARDED, ScalePyramid.ALL)

    @JvmField
    val n5LabelMultiset = matrixOf(Formats.N5, Metadata.ALL, LabelMultiset, ALL_UNSHARDED, ScalePyramid.ALL)

    @JvmField
    val n5Cases = n5Scalar + n5LabelMultiset

    @JvmField
    val zarr2Cases = matrixOf(Formats.ZARR2, Metadata.NONE, SCALAR, ALL_UNSHARDED, ScalePyramid.ALL) +
            matrixOf(Formats.ZARR2, OME_NGFF_04, SCALAR, SMALL_UNSHARDED_5D, ScalePyramid.ALL)


    @JvmField
    val zarr3Cases = matrixOf(Formats.ZARR3, Metadata.ALL, SCALAR, ALL, ScalePyramid.ALL)

    @JvmField
    val hdf5Cases = matrixOf(Formats.HDF5, Metadata.NONE, SCALAR, ALL_UNSHARDED, ScalePyramid.Single)

    @JvmField
    val allCases = n5Cases + zarr2Cases + zarr3Cases + hdf5Cases

    /* 2 spatial dims (x, y) only; kept out of the broad matrices and used by focused 2D-embedding tests */
    @JvmField
    val twoDimensionalCases = matrixOf(Formats.N5, Metadata.NONE, SCALAR, SMALL_UNSHARDED_2D, ScalePyramid.Single)

    /* 4D is always channelized atm, so only 3D or 5D+ are interpreted as raw sources */
    @JvmField
    val rawSources = allCases.filterNot { it.numDimensions == 4 }

    /* opened as a label source at any supported dimensionality: 2D embeds a singleton z, >3D slices to 3D */
    @JvmField
    val readOnlyAsLabelSourceCases = twoDimensionalCases + allCases

    /* opened as a writable (masked) label source; nD as above, but not sharded (can't write sharded yet) */
    @JvmField
    val writeableLabelSourceCases = readOnlyAsLabelSourceCases.filterNot { it.sharded }

    /* creating a new paintera label dataset is 3D only (nD label-dataset creation is not supported) */
    @JvmField
    val creatableLabelDatasetCases = allCases.filter { it.numDimensions == 3 && !it.sharded }

    /* channel is only supported if explicitly 4D; label multisets are labels, never channel sources */
    @JvmField
    val channelSource4DCases = allCases.filter { it.numDimensions == 4 && !it.isLabelMultiset }

    /* any (n > 4)d source is sliced at timepoint 0 for now */
    @JvmField
    val hyperDimension5DCases = allCases.filter { it.numDimensions == 5 }

    /**
     * Default dataset dimensions for [testCase], derived from its [DataShape.blockSize]: spatial dims span two
     * blocks/shards, with small channel/time tails (the channel/time block size is 1, so these become 2 and 3).
     */
    @JvmStatic
    fun defaultDimensions(testCase: TestCase): LongArray {
        val blockSize = testCase.shape.blockSize
        /* spatial (x, y, z) and channel dims span two blocks; any further (time) dims span three */
        return LongArray(testCase.numDimensions) { d -> if (d < 4) blockSize[d] * 2 else blockSize[d] * 3 }
    }

    /** Register the [LabelBlockLookup] gson adapter the Paintera factory needs for label datasets. */
    @JvmStatic
    fun registerLabelBlockLookupAdapter() {
        val builder = GsonBuilder()
        builder.registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.getJsonAdapter())
        Paintera.n5Factory.options.gsonBuilder(builder)
    }

    /** Create a writer with the format for [testCase] under [tmp]. */
    @JvmStatic
    fun newWriter(testCase: TestCase, tmp: Path, name: String = "container"): N5Writer {
        val uri = tmp.resolve("$name${testCase.extension}").absolutePathString()
        return Paintera.n5Factory.newWriter(testCase.format, uri)
    }

    /** Dataset attributes for [testCase] at [dimensions], using [dataType] (defaulting to the case's data type). */
    @JvmStatic
    @JvmOverloads
    fun datasetAttributes(
        testCase: TestCase,
        dimensions: LongArray,
        dataType: DataType = testCase.dataType,
        compression: Compression = GzipCompression()
    ): DatasetAttributes {
        val blockSize = testCase.shape.blockSize.toIntArray()
        val n5DataType = dataType.n5DataType
        return if (testCase.sharded) {
            val innerChunk = testCase.shape.chunkSize.toIntArray()
            ZarrV3DatasetAttributes(dimensions, blockSize, innerChunk, n5DataType, compression)
        } else
            DatasetAttributes(dimensions, blockSize, n5DataType, compression)
    }

    /**
     * Create a dataset (or multiscale group) for [testCase] at [dataset], honoring its metadata flavor,
     * scale pyramid, and data type (label multisets are tagged so they re-open as [LabelMultisetType]).
     *
     * @return the path to open as a source.
     */
    @JvmStatic
    @JvmOverloads
    fun createRaw(
        writer: N5Writer,
        testCase: TestCase,
        dataset: String,
        dimensions: LongArray = defaultDimensions(testCase),
        resolution: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0),
        offset: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
        onScale: ScaleDataWriter? = null
    ): String {
        val scaleFactors = if (testCase.scalePyramid == ScalePyramid.Multi) defaultScaleFactors(dimensions.size) else emptyArray()
        return when {
            testCase.omeZarrVersion != null -> createOmeNgffMultiscale(writer, testCase, dataset, dimensions, scaleFactors, resolution, offset, onScale)
            scaleFactors.isNotEmpty() -> createN5ViewerMultiscale(writer, testCase, dataset, dimensions, scaleFactors, onScale)
            else -> createSingleScale(writer, testCase, dataset, dimensions, resolution, offset, onScale)
        }
    }

    /**
     * Optional callback invoked after each scale dataset is created, used by the manual sample-data generator to
     * fill real pixels. Receives the scale dataset path, its dimensions, and its scale index (`0` for `s0`).
     * Auto-tests leave this `null`, so they keep creating empty datasets.
     */
    fun interface ScaleDataWriter {
        fun write(datasetPath: String, dimensions: LongArray, scaleIndex: Int)
    }

    /** Scalar/label data; same on-disk layout as raw, opened as a label by specifying the source type. */
    @JvmStatic
    @JvmOverloads
    fun createScalarLabel(
        writer: N5Writer,
        testCase: TestCase,
        dataset: String,
        dimensions: LongArray = defaultDimensions(testCase)
    ): String = createRaw(writer, testCase, dataset, dimensions)

    /**
     * Create a multiscale scalar-label group with `s0` plus one dataset per entry in [scaleFactors].
     * Each factor is absolute (relative to `s0`); scale `i+1` has dimensions `dimensions / scaleFactors[i]`.
     * OME containers get OME-NGFF metadata; everything else gets the N5-viewer style
     * [N5Helpers.MULTI_SCALE_KEY] / [N5Helpers.DOWNSAMPLING_FACTORS_KEY] layout.
     *
     * @return the group path.
     */
    @JvmStatic
    @JvmOverloads
    fun createMultiscaleScalarLabels(
        writer: N5Writer,
        testCase: TestCase,
        dataset: String,
        dimensions: LongArray,
        scaleFactors: Array<IntArray>,
        dataType: DataType = DataType.UINT64
    ): String {
        val typedCase = testCase.withDataType(dataType)
        return if (typedCase.omeZarrVersion != null)
            createOmeNgffMultiscale(writer, typedCase, dataset, dimensions, scaleFactors)
        else
            createN5ViewerMultiscale(writer, typedCase, dataset, dimensions, scaleFactors)
    }

    private fun createSingleScale(
        writer: N5Writer,
        testCase: TestCase,
        dataset: String,
        dimensions: LongArray,
        resolution: DoubleArray,
        offset: DoubleArray,
        onScale: ScaleDataWriter? = null
    ): String {
        writer.createDataset(dataset, datasetAttributes(testCase, dimensions))
        markLabelMultiset(writer, testCase, dataset)
        /* only 3D spatial metadata is meaningful; high-dim datasets fall back to axis heuristics */
        if (dimensions.size == 3) {
            writer.setAttribute(dataset, N5Helpers.RESOLUTION_KEY, resolution)
            writer.setAttribute(dataset, N5Helpers.OFFSET_KEY, offset)
            writer.setAttribute(dataset, N5Helpers.UNIT_KEY, "pixel")
        }
        onScale?.write(dataset, dimensions, 0)
        return dataset
    }

    private fun createN5ViewerMultiscale(
        writer: N5Writer,
        testCase: TestCase,
        dataset: String,
        dimensions: LongArray,
        scaleFactors: Array<IntArray>,
        onScale: ScaleDataWriter? = null
    ): String {
        val scaleDimensions = scaleFactors.map { factors -> LongArray(dimensions.size) { dimensions[it] / factors[it] } }
        writer.createGroup(dataset)
        writer.setAttribute(dataset, N5Helpers.MULTI_SCALE_KEY, true)
        val s0 = "$dataset/s0"
        writer.createDataset(s0, datasetAttributes(testCase, dimensions))
        markLabelMultiset(writer, testCase, s0)
        onScale?.write(s0, dimensions, 0)
        scaleFactors.forEachIndexed { idx, factors ->
            val sN = "$dataset/s${idx + 1}"
            writer.createDataset(sN, datasetAttributes(testCase, scaleDimensions[idx]))
            markLabelMultiset(writer, testCase, sN)
            writer.setAttribute(sN, N5Helpers.DOWNSAMPLING_FACTORS_KEY, DoubleArray(factors.size) { factors[it].toDouble() })
            onScale?.write(sN, scaleDimensions[idx], idx + 1)
        }
        return dataset
    }

    /**
     * Write an OME-NGFF multiscale group (at the case's [Metadata.omeZarrVersion]) with `s0` plus one dataset
     * per entry in [scaleFactors], using the same metadata path `exportOmeNGFFMetadata` produces.
     *
     * @return the group path.
     */
    private fun createOmeNgffMultiscale(
        writer: N5Writer,
        testCase: TestCase,
        dataset: String,
        dimensions: LongArray,
        scaleFactors: Array<IntArray>,
        resolution: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0),
        offset: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
        onScale: ScaleDataWriter? = null
    ): String {
        val version = testCase.omeZarrVersion!!
        val scaleDimensions = scaleFactors.map { factors -> LongArray(dimensions.size) { dimensions[it] / factors[it] } }
        writer.createGroup(dataset)
        val s0 = "$dataset/s0"
        writer.createDataset(s0, datasetAttributes(testCase, dimensions))
        markLabelMultiset(writer, testCase, s0)
        onScale?.write(s0, dimensions, 0)
        scaleDimensions.forEachIndexed { idx, scaleDims ->
            val sN = "$dataset/s${idx + 1}"
            writer.createDataset(sN, datasetAttributes(testCase, scaleDims))
            markLabelMultiset(writer, testCase, sN)
            onScale?.write(sN, scaleDims, idx + 1)
        }
        val paths = Array(scaleFactors.size + 1) { "s$it" }
        /* pad the base resolution/offset to the dataset dimensionality (defaults beyond x, y, z are 1.0 / 0.0) */
        val baseResolution = DoubleArray(dimensions.size) { resolution.getOrElse(it) { 1.0 } }
        val baseOffset = DoubleArray(dimensions.size) { offset.getOrElse(it) { 0.0 } }
        val resolutions = arrayOf(baseResolution) + scaleFactors.map { factors -> DoubleArray(factors.size) { factors[it].toDouble() } }
        val offsets = Array(scaleFactors.size + 1) { baseOffset }
        val metadata = OmeNgffMetadata.buildForWriting(
            dimensions.size,
            dataset,
            version,
            axesFor(dimensions.size),
            paths,
            resolutions,
            offsets
        )
        OmeNgffMetadataParser(writer).writeMetadata(metadata, writer, dataset)
        return dataset
    }

    private fun markLabelMultiset(writer: N5Writer, testCase: TestCase, dataset: String) {
        if (testCase.isLabelMultiset) writer.setAttribute(dataset, N5Helpers.IS_LABEL_MULTISET_KEY, true)
    }

    /** One extra scale, downsampling spatial (x, y, z) dimensions by 2 and leaving channel/time dimensions untouched. */
    private fun defaultScaleFactors(numDimensions: Int): Array<IntArray> = arrayOf(IntArray(numDimensions) { if (it < 3) 2 else 1 })

    /** OME-NGFF axes for an [numDimensions]-dimensional dataset; the first three are spatial, the rest channel/time. */
    private fun axesFor(numDimensions: Int): Array<Axis> {
        val spatial = listOf(
            Axis(Axis.SPACE, "x", "pixel", false),
            Axis(Axis.SPACE, "y", "pixel", false),
            Axis(Axis.SPACE, "z", "pixel", false)
        )
        val extras = (3 until numDimensions).map { idx ->
            when (idx) {
                3 -> Axis(Axis.CHANNEL, "c", null, false)
                else -> Axis(Axis.TIME, "t", null, false)
            }
        }
        return (spatial + extras).take(numDimensions).toTypedArray()
    }

    private fun TestCase.withDataType(dataType: DataType) = TestCase(format, metadata, dataType, shape, scalePyramid)

    private fun LongArray.toIntArray() = IntArray(size) { this[it].toInt() }
}
