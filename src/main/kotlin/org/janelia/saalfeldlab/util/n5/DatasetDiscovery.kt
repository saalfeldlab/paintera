package org.janelia.saalfeldlab.util.n5

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMultiScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5GenericSingleScaleMetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.N5MultiScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadataParser
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser
import org.janelia.saalfeldlab.paintera.cache.ParsedN5LoaderCache.Companion.HashableN5Reader
import org.janelia.saalfeldlab.paintera.cache.ParsedN5LoaderCache.Companion.ParseN5Wrapper
import org.janelia.saalfeldlab.util.coroutineBackedExecutorService
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleMetadata.PainteraDataMultiScaleParser
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup.PainteraLabelMultiScaleParser
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraRawMultiScaleGroup.PainteraRawMultiScaleParser
import java.util.concurrent.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

private object BaseParsers {
    val GROUP = listOf<N5MetadataParser<*>>(
        PainteraRawMultiScaleParser(),
        PainteraLabelMultiScaleParser(),
        PainteraDataMultiScaleParser(),
        N5CosemMultiScaleMetadata.CosemMultiScaleParser(),
        N5MultiScaleMetadata.MultiScaleParser()
    )

    val METADATA = listOf<N5MetadataParser<*>>(
        N5CosemMetadataParser(),
        N5SingleScaleMetadataParser(),
        N5GenericSingleScaleMetadataParser()
    )
}

fun getGroupParsers(n5: N5Reader) = listOf<N5MetadataParser<*>>(OmeNgffMetadataParser(n5)) + BaseParsers.GROUP
fun getMetadataParsers(n5: N5Reader) = BaseParsers.METADATA

private fun getDiscoverer(n5: N5Reader, executorService: ExecutorService): N5DatasetDiscoverer {
	val baseReader = extractBaseReader(n5)

	val metadataParsers = getMetadataParsers(baseReader)
	val groupParsers = getGroupParsers(baseReader)

	return N5DatasetDiscoverer(baseReader, executorService, metadataParsers, groupParsers)
}

private val LOG = KotlinLogging.logger { }

/**
 * Some internal N5Readers are delegate wrappers; Some parsers branch on the N5 type (zarr vs n5) so
 * we need to extract the delegate base.
 *
 * @param n5 to recursively extract the base reader from
 * @return the base reader; may be the same as the input
 */
private fun extractBaseReader(n5: N5Reader) : N5Reader {
	return when (n5) {
		is ParseN5Wrapper -> extractBaseReader(n5.reader)
		is HashableN5Reader -> extractBaseReader(n5.reader)
		else -> n5
	}
}

/**
 * Parses the metadata from a given N5Reader starting at a specified initial group and applies parsing results to nodes
 * using the provided callback function.
 *
 * @param n5Reader The reader to access the N5 container.
 * @param initialGroup The initial group path from which to start parsing, default is "/".
 * @param callback The function to be executed on the root node after parsing is completed, default is an empty function.
 * @return The root node of the parsed metadata tree.
 */
@JvmOverloads
internal fun discoverAndParseRecursive(n5Reader: N5Reader, initialGroup: String = "/", callback: (N5TreeNode) -> Unit = {}): N5TreeNode {

	val (es, _) = coroutineBackedExecutorService(Dispatchers.IO)
	return getDiscoverer(n5Reader, es).discoverAndParseRecursive(initialGroup) {
		callback(it)
	}
}

internal suspend fun asyncDiscoverAndParseRecursive(
	n5Reader: N5Reader,
	initialGroup: String = "/",
	deepListCallback: ((Any?) -> Unit)? = null,
	parseCallback: suspend (N5TreeNode) -> Unit = {}
): N5TreeNode {
	return coroutineBackedExecutorService(coroutineContext, deepListCallback).let { (es, scope) ->
		scope.async {
 			getDiscoverer(n5Reader, es).discoverAndParseRecursive(initialGroup) {
				scope.ensureActive()
				runBlocking { parseCallback(it) }
			}
		}.apply {
			invokeOnCompletion { cause ->
				when (cause) {
					null -> es.shutdown()
					is CancellationException -> {
						LOG.debug(cause) { "discover and parse cancelled" }
						es.shutdownNow()
					}
					else -> {
						LOG.error(cause) { "discover and parse failed" }
						es.shutdownNow()
					}
				}
			}
		}
	}.await()
}
