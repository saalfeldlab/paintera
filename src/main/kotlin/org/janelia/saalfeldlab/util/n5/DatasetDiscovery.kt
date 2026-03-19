package org.janelia.saalfeldlab.util.n5

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5Factory
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.util.coroutineBackedExecutorService
import org.janelia.saalfeldlab.util.n5.N5Helpers.GROUP_PARSERS
import org.janelia.saalfeldlab.util.n5.N5Helpers.METADATA_PARSERS
import java.util.concurrent.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext


private fun getDiscoverer(n5: N5Reader, executorService: ExecutorService): N5DatasetDiscoverer {
	return N5DatasetDiscoverer(n5, executorService, METADATA_PARSERS, GROUP_PARSERS)
}

private val LOG = KotlinLogging.logger { }

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

fun main() {
	var waiting = 5
	var n5 = N5Factory.createReader("/Volumes/cellmap/zouinkhim/c-elegan/predictions/mito_lsds/inference/jrc_c-elegans-op50-1.zarr")
	var paths = mutableSetOf<String>()
	val parseJob = CoroutineScope(Dispatchers.IO).launch {
			asyncDiscoverAndParseRecursive(n5, "/setup16_lsd_400k_all_16/seg_0_55_cleaned", { println("deeplist: $it")}) {
				if (paths.add(it.path)) {
					println("parse: ${it.path}\t${it.metadata}")
				}
			}
	}
	runBlocking {
		parseJob.join()
	}
}