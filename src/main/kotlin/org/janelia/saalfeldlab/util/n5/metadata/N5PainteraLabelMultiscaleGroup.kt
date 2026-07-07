package org.janelia.saalfeldlab.util.n5.metadata

import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.GzipCompression
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataWriter
import org.janelia.saalfeldlab.n5.universe.metadata.N5MultiScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata.OmeNgffDataset
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.CoordinateTransformation
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.ScaleCoordinateTransformation
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.TranslationCoordinateTransformation
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.N5Helpers.MAX_ID_KEY
import org.janelia.saalfeldlab.util.n5.N5Helpers.PAINTERA_NAMESPACE
import java.util.Optional
import kotlin.math.ceil

class N5PainteraLabelMultiscaleGroup @JvmOverloads constructor(
    basePath: String,
    dataGroup: N5PainteraDataMultiscaleMetadata,
    val uniqueLabelsMetadata: N5MultiScaleMetadata? = null,
    val fragmentSegmentAssignment: N5SingleScaleMetadata? = null,
    val labelToBlockLookup: N5MultiScaleMetadata? = null,
    val maxId: Long? = null,
    val isLabelMultisetType: Boolean = false,
    /**  Max entries for a label multiset per scale level, `-1` for unbounded; null when not a label multiset */
    val maxNumEntries: IntArray? = null
) : N5PainteraDataMultiscaleGroup(basePath, dataGroup){

    val isLabel: Boolean
        get() = true

    companion object {

        /**
         * Build the metadata for a new, empty paintera label group, ready to be written via [PainteraLabelMultiscaleParser.writeMetadata].
         * The children carry the [DatasetAttributes] of each scale level; downsampling applies to the
         * spatial axes only, so the stored factors are 3D and non-spatial axis sizes are constant across levels.
         * The style is based on OME-NGFF multiscales.
         *
         * @param group the paintera group path
         * @param dimensions the dimensions at s0
         * @param blockSize the block size, shared by all levels
         * @param resolution the per-axis resolution
         * @param offset the per-axis offset
         * @param relativeScaleFactors per-level scale factors relative to the previous level
         * @param unit the spatial unit
         * @param maxNumEntries per downsampled level (index 0 = s1) cap on multiset entries, `<= 0` for unbounded; ignored unless [labelMultisetType]
         * @param labelMultisetType whether the data is stored as label multisets (UINT8) or scalar labels (UINT64)
         * @param axes the source axes in storage order, or null for the canonical x, y, z, c, t, ...
         */
        @JvmStatic
        fun buildForWriting(
            group: String,
            dimensions: LongArray,
            blockSize: IntArray,
            resolution: DoubleArray,
            offset: DoubleArray,
            relativeScaleFactors: Array<DoubleArray>,
            unit: String,
            maxNumEntries: IntArray?,
            labelMultisetType: Boolean,
            axes: Array<Axis>?
        ): N5PainteraLabelMultiscaleGroup {

            val normalizedGroup = N5URI.normalizeGroupPath(group)
            val dataGroupPath = N5URI.normalizeGroupPath("$group/data")
            val uniqueLabelsPath = N5URI.normalizeGroupPath("$group/unique-labels")

            val numLevels = relativeScaleFactors.size + 1
            val dataType = if (labelMultisetType) DataType.UINT8 else DataType.UINT64

            val scaledDimensions = dimensions.clone()
            val accumulatedFactors = DoubleArray(dimensions.size) { 1.0 }

            val dataChildrenAttributes = mutableListOf<DatasetAttributes>()
            val ngffDatasets = mutableListOf<OmeNgffDataset>()
            val uniqueLabelsChildren = mutableListOf<N5SingleScaleMetadata>()
            val perLevelMaxNumEntries = if (labelMultisetType) IntArray(numLevels) else null
            val perLevelScales = Array(numLevels) { DoubleArray(dimensions.size) }
            val perLevelTranslations = Array(numLevels) { DoubleArray(dimensions.size) }

            /* level 0 is full resolution; levels 1..N accumulate relativeScaleFactors[level - 1] over the spatial axes */
            for (level in 0 until numLevels) {
                if (level > 0) {
                    val relativeFactors = relativeScaleFactors[level - 1]
                    for (dim in dimensions.indices) {
                        val factor = relativeFactors.getOrElse(dim) { 1.0 }
                        scaledDimensions[dim] = ceil(scaledDimensions[dim] / factor).toLong()
                        accumulatedFactors[dim] *= factor
                    }
                }
                /* the per-level transform; non-spatial axes keep their resolution/offset across levels */
                for (dim in dimensions.indices) {
                    perLevelScales[level][dim] = resolution.getOrElse(dim) { 1.0 } * accumulatedFactors[dim]
                    perLevelTranslations[level][dim] = offset.getOrElse(dim) { 0.0 }
                }

                val pixelResolution = perLevelScales[level].copyOf(3)
                val transform = AffineTransform3D().apply {
                    set(
                        pixelResolution[0], 0.0, 0.0, offset[0],
                        0.0, pixelResolution[1], 0.0, offset[1],
                        0.0, 0.0, pixelResolution[2], offset[2]
                    )
                }
                /* downsampling factors describe the 3D spatial pyramid; only spatial axes are downsampled */
                val downsamplingFactors = accumulatedFactors.copyOf(3)

                dataChildrenAttributes += DatasetAttributes(scaledDimensions.clone(), blockSize, dataType, GzipCompression())
                ngffDatasets += OmeNgffDataset().apply {
                    this.path = "s$level"
                    coordinateTransformations = arrayOf<CoordinateTransformation<*>>(
                        ScaleCoordinateTransformation(perLevelScales[level]),
                        TranslationCoordinateTransformation(perLevelTranslations[level])
                    )
                }
                uniqueLabelsChildren += N5SingleScaleMetadata(
                    "$uniqueLabelsPath/s$level",
                    transform.copy(), downsamplingFactors.clone(), pixelResolution.clone(), offset.copyOf(3), unit,
                    DatasetAttributes(scaledDimensions.clone(), blockSize, DataType.UINT64, GzipCompression()),
                    false
                )
                perLevelMaxNumEntries?.set(level, maxNumEntries?.getOrNull(level - 1) ?: -1)
            }

            val groupAxes = axes ?: N5Helpers.canonicalAxes(dimensions.size)

            val dataMultiscale = OmeNgffMultiScaleMetadata(
                dimensions.size,
                dataGroupPath,
                normalizedGroup.substringAfterLast('/'),
                "",
                "0.5",
                groupAxes,
                ngffDatasets.toTypedArray(),
                null,
                dataChildrenAttributes.toTypedArray(),
                null
            )
            /* data and uniqueLabels are created at initialization; fragment-segment-assignment and label-to-block-mapping are created when first written to */
            val dataGroup = N5PainteraDataMultiscaleMetadata(dataGroupPath, arrayOf(dataMultiscale))
            val uniqueLabelsGroup = N5MultiScaleMetadata(uniqueLabelsPath, uniqueLabelsChildren.toTypedArray())

            return N5PainteraLabelMultiscaleGroup(
                basePath = normalizedGroup,
                dataGroup = dataGroup,
                uniqueLabelsMetadata = uniqueLabelsGroup,
                fragmentSegmentAssignment = null,
                labelToBlockLookup = null,
                maxId = 0L,
                isLabelMultisetType = labelMultisetType,
                maxNumEntries = perLevelMaxNumEntries
            )
        }
    }

    class PainteraLabelMultiscaleParser : N5MetadataParser<N5PainteraLabelMultiscaleGroup>, N5MetadataWriter<N5PainteraLabelMultiscaleGroup> {

        /**
         * Create the groups and empty scale datasets for [t], write the `ome/multiscales` block, and derive the
         * legacy attributes this parser also accepts.
         */
        override fun writeMetadata(t: N5PainteraLabelMultiscaleGroup, n5: N5Writer, path: String) {

            if (!n5.exists(path))
                n5.createGroup(path)
            n5.setAttribute(path, N5Helpers.PAINTERA_DATA_KEY, mapOf("type" to "label"))
            n5.setAttribute(path, N5Helpers.MAX_ID_KEY, t.maxId ?: 0L)

            /* the legacy resolution/offset attributes are spatial-only, taken from the highest-res scale/translation */
            val dataGroup = t.dataGroupMetadata
            val highestRes = dataGroup.childrenMetadata[0]
            val highestResTranslation = highestRes.translation ?: DoubleArray(highestRes.scale.size)
            n5.createGroup(dataGroup.path)
            n5.setAttribute(dataGroup.path, N5Helpers.MULTI_SCALE_KEY, true)
            n5.setAttribute(dataGroup.path, N5Helpers.RESOLUTION_KEY, highestRes.scale.copyOf(3))
            n5.setAttribute(dataGroup.path, N5Helpers.OFFSET_KEY, highestResTranslation.copyOf(3))
            n5.setAttribute(dataGroup.path, N5Helpers.IS_LABEL_MULTISET_KEY, t.isLabelMultisetType)

            dataGroup.childrenMetadata.forEachIndexed { level, child ->
                n5.createDataset(child.path, child.attributes)
                if (t.isLabelMultisetType) {
                    n5.setAttribute(child.path, N5Helpers.MAX_NUM_ENTRIES_KEY, t.maxNumEntries?.get(level) ?: -1)
                    n5.setAttribute(child.path, N5Helpers.IS_LABEL_MULTISET_KEY, true)
                }
                n5.setAttribute(child.path, N5Helpers.UNIT_KEY, child.unit() ?: "pixel")
                /* the legacy spatial downsampling factors, relative to the highest-res level; only declared on
                 * downscaled levels, not s0 */
                if (level > 0)
                    n5.setAttribute(child.path, N5Helpers.DOWNSAMPLING_FACTORS_KEY, DoubleArray(3) { child.scale[it] / highestRes.scale[it] })
            }

            /* write the ome/multiscales block after the scale datasets exist, so zarr3 containers also get their dimension_names */
            OmeNgffMetadataParser(n5).writeMetadata(dataGroup, n5, dataGroup.path)

            t.uniqueLabelsMetadata?.let { uniqueLabels ->
                n5.createGroup(uniqueLabels.path)
                n5.setAttribute(uniqueLabels.path, N5Helpers.MULTI_SCALE_KEY, true)
                uniqueLabels.childrenMetadata.forEachIndexed { level, child ->
                    n5.createDataset(child.path, child.attributes)
                    if (level > 0)
                        n5.setAttribute(child.path, N5Helpers.DOWNSAMPLING_FACTORS_KEY, child.downsamplingFactors)
                }
            }
        }

        /**
         * Called by the [N5DatasetDiscoverer]
         * while discovering the N5 tree and filling the metadata for datasets or groups.
         *
         * @param node the node
         * @return the metadata
         */
        override fun parseMetadata(n5: N5Reader, node: N5TreeNode): Optional<N5PainteraLabelMultiscaleGroup> {

            return Optional.ofNullable(parseMetadataNullable(n5, node))
        }

        private fun parseMetadataNullable(n5: N5Reader, node: N5TreeNode): N5PainteraLabelMultiscaleGroup? {

            /* if we're a dataset, we're not a multiscale group; group nodes have no metadata yet at this point */
            if (node.metadata is N5DatasetMetadata)
                return null

            /* must be paintera data label type */
            n5.get<String>(node.path, "painteraData/type")
                ?.takeIf { it == "label" }
                ?: return null

            /* read maxId if present */
            val maxId = listOf(MAX_ID_KEY, "$PAINTERA_NAMESPACE/$MAX_ID_KEY")
                .firstNotNullOfOrNull {
                    n5.get<Long>(node.path, it)
                }

            var dataGroup: N5PainteraDataMultiscaleMetadata? = null
            var uniqueLabelsGroup: N5MultiScaleMetadata? = null
            var fragmentSegmentAssignment: N5SingleScaleMetadata? = null
            var labelToBlockLookupGroup: N5MultiScaleMetadata? = null
            for (child in node.childrenList()) {
                when (val metadata = child.metadata) {
                    is N5PainteraDataMultiscaleMetadata -> dataGroup = metadata
                    is N5SingleScaleMetadata -> if (child.nodeName == "fragment-segment-assignment") fragmentSegmentAssignment = metadata
                    is N5MultiScaleMetadata -> when (child.nodeName) {
                        "unique-labels" -> uniqueLabelsGroup = metadata
                        "label-to-block-mapping" -> labelToBlockLookupGroup = metadata
                    }
                }
            }

            return dataGroup?.let { dg ->
                /* NGFF-typed children carry no label-multiset flag, so fall back to the s0 attribute */
                val firstChild = dg.childrenMetadata[0]
                val isLabelMultiset = (firstChild as? N5SingleScaleMetadata)?.isLabelMultiset
                    ?: n5.get<Boolean>(firstChild.path, N5Helpers.IS_LABEL_MULTISET_KEY)
                    ?: false
                N5PainteraLabelMultiscaleGroup(
                    basePath = node.path,
                    dataGroup = dg,
                    uniqueLabelsMetadata = uniqueLabelsGroup,
                    fragmentSegmentAssignment = fragmentSegmentAssignment,
                    labelToBlockLookup = labelToBlockLookupGroup,
                    maxId = maxId,
                    isLabelMultisetType = isLabelMultiset
                )
            }
        }
    }
}
