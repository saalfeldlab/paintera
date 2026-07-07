package org.janelia.saalfeldlab.util.n5.metadata

import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import java.util.Optional

class N5PainteraRawMultiscaleGroup(
    basePath: String,
    dataGroup: N5PainteraDataMultiscaleMetadata
) : N5PainteraDataMultiscaleGroup(basePath, dataGroup) {

    class PainteraRawMultiscaleParser : N5MetadataParser<N5PainteraRawMultiscaleGroup> {

        /**
         * Called by the [N5DatasetDiscoverer]
         * while discovering the N5 tree and filling the metadata for datasets or groups.
         *
         * @param node the node
         * @return the metadata
         */
        override fun parseMetadata(n5: N5Reader, node: N5TreeNode) = Optional.ofNullable(parseMetadataNullable(n5, node))

        private fun parseMetadataNullable(n5: N5Reader, node: N5TreeNode): N5PainteraRawMultiscaleGroup? {

            /* if we're a dataset, we're not a multiscale group; group nodes have no metadata yet at this point */
            if (node.metadata is N5DatasetMetadata)
                return null

            /* must be paintera data raw type */
            n5.get<String>(node.path, "painteraData/type")
                ?.takeIf { it == "raw" }
                ?: return null

            val dataGroup = node.childrenList().firstNotNullOfOrNull { it.metadata as? N5PainteraDataMultiscaleMetadata } ?: return null
            return N5PainteraRawMultiscaleGroup(node.path, dataGroup)
        }
    }
}
