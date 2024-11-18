package org.janelia.saalfeldlab.util.n5

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.n5.N5Exception
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataGroup
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.util.n5.N5Helpers.GROUP_PARSERS
import org.janelia.saalfeldlab.util.n5.N5Helpers.METADATA_PARSERS
import org.janelia.saalfeldlab.util.n5.N5Helpers.parseMetadata
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrNull

class DatasetDiscovery(
	val metadataParsers: List<N5MetadataParser<*>>,
	val groupParsers: List<N5MetadataParser<*>>
) {

	private val supervisor = SupervisorJob()
	private val scope = CoroutineScope(Dispatchers.IO + supervisor)

	fun parseMetadata(n5Reader: N5Reader, rootNode: N5TreeNode, callback: (N5TreeNode) -> Unit = {}) : Deferred<N5TreeNode> {
		with(scope) {
			return this.async {
				parseMetadata(n5Reader, rootNode, metadataParsers, groupParsers, callback)
				rootNode
			}
		}
	}

	fun parseMetadata(n5Reader: N5Reader, initialGroup: String = "/", filter: (String) -> Boolean = { true }, callback: (N5TreeNode) -> Unit = {}) : Deferred<N5TreeNode> {
		val rootNode = N5TreeNode(initialGroup)
		val datasets = runBlocking { deepList(n5Reader, initialGroup, filter = filter).await() }
		N5TreeNode.fromFlatList(rootNode, datasets, n5Reader.groupSeparator)
		return parseMetadata(n5Reader, rootNode, callback)
	}

	private fun deepListRecursive(n5Reader: N5Reader, initialGroup: String = "/", datasetsOnly: Boolean = false, filter: (String) -> Boolean = { true }) : Array<String> {
		return runBlocking {
			with(scope) {
				async { Companion.deepList(n5Reader, initialGroup, datasetsOnly, filter) }.await()
			}
		}
	}

	fun deepList(n5Reader: N5Reader, initialGroup: String = "/", datasetsOnly: Boolean = false, filter: (String) -> Boolean = { true }) : Deferred<Array<String>> {
		return with(scope){
			async { deepListRecursive(n5Reader, initialGroup, datasetsOnly, filter) }
		}
	}

	companion object {

		val LOG = KotlinLogging.logger {  }

		@JvmStatic
		@JvmOverloads
		fun parseMetadata(n5Reader: N5Reader, initialGroup: String = "/", callback: (N5TreeNode) -> Unit = {}) : Deferred<N5TreeNode> {
			val discoverer = DatasetDiscovery(METADATA_PARSERS, GROUP_PARSERS)
			val datasetPaths = runBlocking { discoverer.deepList(n5Reader, initialGroup).await() }
			val rootNode = N5TreeNode(initialGroup)
			N5TreeNode.fromFlatList(rootNode, datasetPaths, n5Reader.groupSeparator)
			return discoverer.parseMetadata(n5Reader, rootNode, callback)
		}

		suspend fun deepList(
			n5Reader: N5Reader,
			rootPath: String = "/",
			datasetsOnly : Boolean = false,
			filter : (String) -> Boolean = { true }
		) : Array<String> {
			return coroutineScope {
				val deferredResults = mutableListOf<Deferred<Array<String>>>()
				val normalPath = N5URI.normalizeGroupPath(rootPath)
				val isDataset = try {
					n5Reader.datasetExists(normalPath)
				} catch (e : N5Exception) {
					LOG.debug(e) { "Error querying dataset exists for $normalPath" }
					false
				}

				if (!isDataset) {
					val children = try {
						n5Reader.list(normalPath)
					} catch (e : N5Exception) {
						LOG.debug(e) { "Error listing children for $normalPath" }
						null
					}
					children?.forEach { child ->
						val childPath = normalPath + n5Reader.groupSeparator + child
						deferredResults += async {
							deepList(n5Reader, childPath, datasetsOnly, filter)
						}
					}
				}
				val passDatasetFilter = !datasetsOnly || isDataset
				val passFilters = passDatasetFilter && filter(normalPath)
				val results = deferredResults.awaitAll().flatMap { it.toList() }.toMutableList()
				if (passFilters) results += normalPath

				return@coroutineScope results.toTypedArray()
			}
		}

		suspend fun parseMetadata(
			n5Reader: N5Reader,
			rootNode: N5TreeNode,
			metadataParsers: List<N5MetadataParser<*>>,
			groupParsers: List<N5MetadataParser<*>>,
			callback: (N5TreeNode) -> Unit = {},
		) {
			coroutineScope {
				for (child in rootNode.childrenList()) {
					launch {
						parseMetadata(n5Reader, child, metadataParsers, groupParsers, callback)
					}
				}
			}
			N5DatasetDiscoverer.parseMetadata(
				n5Reader,
				rootNode,
				metadataParsers,
				groupParsers
			)
			(rootNode.metadata as? N5MetadataGroup<*>)?.let { group ->
				group.childrenMetadata
					.mapNotNull { child -> rootNode.getDescendant(child.path).getOrNull() }
					.forEach { child ->
						currentCoroutineContext().ensureActive()
						callback(child)
					}
			}
			currentCoroutineContext().ensureActive()
			callback(rootNode)
		}
	}


}

fun main() {

	val n5 = Paintera.n5Factory.openReader("s3://janelia-cosem-datasets/jrc_mus-pancreas-4/jrc_mus-pancreas-4.zarr")

	parseMetadata(n5, Executors.newWorkStealingPool())

	val kotlin = {
		val discoverer = DatasetDiscovery(METADATA_PARSERS, GROUP_PARSERS)
		val root = "/"
		val datasetPaths = n5.deepList(root, Executors.newWorkStealingPool()).also {
			it.sort()
			it.forEach { println("jv: $it") }
		}
		val datasetPathsKt = runBlocking { DatasetDiscovery.deepList(n5, root, false) }.also {
			it.sort()
			it.forEach { println("kt: $it") }
		}
		val rootNode = N5TreeNode(root)
		N5TreeNode.fromFlatList(rootNode, datasetPaths, n5.groupSeparator)
		discoverer.parseMetadata(n5, rootNode) {
			println("1:\t ${it.path}")
		}
	}

	val java = {
		val discoverer = N5DatasetDiscoverer(n5, Executors.newWorkStealingPool(), METADATA_PARSERS, GROUP_PARSERS)
		discoverer.discoverAndParseRecursive("/") {
			println("2:\t ${it.path}")
		}
	}

	kotlin()
//	java()

}