package org.janelia.saalfeldlab.paintera.cache

import javafx.collections.FXCollections
import javafx.collections.FXCollections.observableMap
import javafx.collections.ObservableMap
import kotlinx.coroutines.*
import net.imglib2.cache.ref.SoftRefLoaderCache
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.util.n5.asyncDiscoverAndParseRecursive
import java.net.URI
import java.util.concurrent.ConcurrentHashMap


internal val SHARED_PARSED_N5_CACHE = SoftRefLoaderCache<N5Reader, Deferred<Map<String, N5TreeNode>?>>()

class ParsedN5LoaderCache(loaderScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) : AsyncCacheWithLoader<N5Reader, Map<String, N5TreeNode>?>(loaderScope) {

	override val cache = SHARED_PARSED_N5_CACHE

	override suspend fun loader(key: N5Reader): Map<String, N5TreeNode>? {
		val path: String
		val map: MutableMap<String, N5TreeNode>
		val statusCallback: ((String) -> Unit)?
		if (key is ParseN5Wrapper) {
			path = key.path
			map = key.list
			statusCallback = key.statusCallback
		} else {
			path = "/"
			map = mutableMapOf<String, N5TreeNode>()
			statusCallback = null
		}

		val (deepListCallback, parseCallback) = statusCallback?.let { callback ->
			val deepList = { path: Any? -> callback("Searching ${path ?: ""}") }
			val parse = { path: String? -> callback("Parsing ${path ?: ""}") }
			deepList to parse
		} ?: (null to null)

		asyncDiscoverAndParseRecursive(key, path, deepListCallback) {
			if (it.path !in map.keys) {
				parseCallback?.invoke(it.path)
				map += getValidDatasets(it)
			}
		}
		return map.takeIf { it.isNotEmpty() }
	}

	fun observableRequest(reader: N5Reader, path: String = "/"): ParseRequest {
		val backingMap = ConcurrentHashMap<String, N5TreeNode>()
		val observableMap = observableMap(backingMap)
		val synchronizedMap = FXCollections.synchronizedObservableMap(observableMap)
		var status = ""
		val wrappedKey = ParseN5Wrapper(
			HashableN5Reader(reader),
			path,
			synchronizedMap
		) { status = it }
		return ParseRequest(synchronizedMap, { status }, request(wrappedKey))
	}

	override fun invalidate(key: N5Reader) {
		super.invalidate(HashableN5Reader(key))
	}

	companion object {

		data class ParseRequest(
			val observableMap: ObservableMap<String, N5TreeNode>,
			val status: () -> String,
			val job: Deferred<Map<String, N5TreeNode>?>
		)

		private data class ParseN5Wrapper(
			val reader: HashableN5Reader,
			val path: String = "/",
			val list: ObservableMap<String, N5TreeNode>,
			val statusCallback: ((String) -> Unit)? = null
		) : N5Reader by reader {
			override fun equals(other: Any?): Boolean {
				return when (other) {
					is ParseN5Wrapper -> reader == other.reader
					is HashableN5Reader -> reader == other
					is N5Reader -> uri == other.uri
					else -> super.equals(other)
				}
			}

			override fun hashCode() = reader.hashCode()
		}

		private data class HashableN5Reader(val reader: N5Reader, val uri: URI = reader.uri) : N5Reader by reader {
			override fun equals(other: Any?) =
				when (other) {
					is N5Reader -> uri == other.uri
					else -> super.equals(other)
				}

			override fun hashCode() = uri.hashCode()
		}

		private tailrec suspend fun getValidDatasets(vararg nodes: N5TreeNode, list: MutableMap<String, N5TreeNode> = mutableMapOf<String, N5TreeNode>()): Map<String, N5TreeNode> {

			val newNodes = nodes.filterNot { node ->
				val validMetadata = MetadataUtils.metadataIsValid(node.metadata)
				if (validMetadata)
					list[node.path] = node

				validMetadata || node.childrenList().isEmpty()
			}
				.flatMap { it.childrenList() }
				.toTypedArray()

			if (newNodes.isEmpty())
				return list

			currentCoroutineContext().ensureActive()
			return getValidDatasets(*newNodes)
		}
	}
}