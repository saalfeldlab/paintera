package org.janelia.saalfeldlab.util.n5.metadata

import com.google.gson.JsonObject
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata.OmeNgffDataset
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.CoordinateTransformation
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.ScaleCoordinateTransformation
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.TranslationCoordinateTransformation
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.util.Optional
import java.util.function.Predicate
import java.util.regex.Pattern

/**
 * The paintera `data` group: an [OmeNgffMetadata] multiscale dataset.
 * The in-memory representation is always NGFF. If the metadat ais present it will be parsed and used directly,
 * but if only the legacy metadata is present, it will be parsed to OmeNgffMetadata for construction.
 */
class N5PainteraDataMultiscaleMetadata(
    basePath: String,
    multiscales: Array<OmeNgffMultiScaleMetadata>
) : OmeNgffMetadata(basePath, multiscales) {

    class PainteraDataMultiscaleParser : N5MetadataParser<N5PainteraDataMultiscaleMetadata> {

        override fun parseMetadata(n5: N5Reader, node: N5TreeNode) = Optional.ofNullable(parseMetadataNullable(n5, node))

        @JvmSynthetic
        private fun parseMetadataNullable(n5: N5Reader, node: N5TreeNode): N5PainteraDataMultiscaleMetadata? {

            if (node.nodeName != "data")
                return null


            /* Since PainteraDataMultiscale groups ARE OmeNgffMetadata, we need to be sure to only consider them paintera data metadata if
            * the parent group contains the `painteraData` attribute. */
            val parentPath = node.path.substringBeforeLast('/', "")
            val parentIsPainteraDataset = runCatching { n5.getAttribute(parentPath, N5Helpers.PAINTERA_DATA_KEY, JsonObject::class.java) }.getOrNull() != null
            if (!parentIsPainteraDataset)
                return null

            /* when present, return the wrapped OmeNgffMetadata directly */
            runCatching {
                OmeNgffMetadataParser(n5).parseMetadata(n5, node).orElse(null)
            }.getOrNull()?.let { ngffMetadata ->
                return N5PainteraDataMultiscaleMetadata(ngffMetadata.path, ngffMetadata.multiscales)
            }

            val childMetadata = node.childrenList()
                .filter { childNode ->
                    childNode.matchesScalePredicate
                            && childNode.isDataset
                            && childNode.metadata is N5SpatialDatasetMetadata
                }
                .map { it.metadata as N5SingleScaleMetadata }
                .toTypedArray()
                .takeIf { it.isNotEmpty() }
                ?.takeIf { sortScaleMetadata(it) }
                ?: return null

            return buildFromLegacyAttributes(n5, node, childMetadata)
        }

        /**
         * build the [OmeNgffMultiScaleMetadata] equivalent from the legacy attributes.
         */
        private fun buildFromLegacyAttributes(n5: N5Reader, node: N5TreeNode, childMetadata: Array<N5SingleScaleMetadata>): N5PainteraDataMultiscaleMetadata? {


            val resolution = n5.get<DoubleArray>(node.path, N5Helpers.RESOLUTION_KEY) ?: doubleArrayOf(1.0, 1.0, 1.0)
            val offset = n5.get<DoubleArray>(node.path, N5Helpers.OFFSET_KEY) ?: doubleArrayOf(0.0, 0.0, 0.0)
            if (resolution.size != 3 || offset.size != 3)
                return null

            /* legacy datasets carry no axis metadata and are always 3D; the canonical x, y, z axes are the only possibility */
            val numDims = childMetadata[0].attributes.numDimensions
            val axes = N5Helpers.canonicalAxes(numDims)

            val datasets = childMetadata.map { child ->
                val scale = DoubleArray(numDims) { idx ->
                    val dimRes = resolution.getOrElse(idx) { 1.0 }
                    val dimDownsampleFactor = child.downsamplingFactors.getOrElse(idx) { 1.0 }
                    dimRes * dimDownsampleFactor
                }
                val translation = DoubleArray(numDims) { dim -> offset.getOrElse(dim) { 0.0 } }
                OmeNgffDataset().apply {
                    path = child.path.substringAfterLast('/')
                    coordinateTransformations = arrayOf<CoordinateTransformation<*>>(
                        ScaleCoordinateTransformation(scale),
                        TranslationCoordinateTransformation(translation)
                    )
                }
            }.toTypedArray()

            val childrenAttributes = childMetadata.map { it.attributes }.toTypedArray()
            val multiscale = OmeNgffMultiScaleMetadata(
                numDims,
                node.path,
                node.nodeName,
                "",
                "0.5",
                axes,
                datasets,
                null,
                childrenAttributes,
                null
            )
            return N5PainteraDataMultiscaleMetadata(node.path, arrayOf(multiscale))
        }

        companion object {

            private val SCALE_LEVEL_PREDICATE: Predicate<String> = Pattern.compile("^s\\d+$").asPredicate()

            val N5TreeNode.matchesScalePredicate: Boolean
                get() = SCALE_LEVEL_PREDICATE.test(nodeName)
        }
    }
}
