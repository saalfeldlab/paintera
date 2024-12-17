package org.janelia.saalfeldlab.util.n5

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.util.n5.N5Helpers.GROUP_PARSERS
import org.janelia.saalfeldlab.util.n5.N5Helpers.METADATA_PARSERS
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

private val IO_EXECUTOR by lazy {
	val count = AtomicInteger()
	val factory = ForkJoinPool.ForkJoinWorkerThreadFactory { pool ->
		ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply {
			isDaemon = true
			priority = 4
			name = "propagation-queue-${count.getAndIncrement()}"
		}
	}
	val exceptionHandler: (Thread, Throwable) -> Unit = { _, throwable -> throwable.printStackTrace() }
	val parallelism = Runtime.getRuntime().availableProcessors()
	ForkJoinPool(parallelism, factory, exceptionHandler, true)
}


private fun getDiscoverer(n5: N5Reader): N5DatasetDiscoverer {
	return object : N5DatasetDiscoverer(n5, IO_EXECUTOR, METADATA_PARSERS, GROUP_PARSERS) {

		val groupSeparator = n5.groupSeparator
		val executor = IO_EXECUTOR

		override fun discoverAndParseRecursive(root: N5TreeNode, callback: Consumer<N5TreeNode?>): N5TreeNode? {
			println("List Nodes - Before Shallow")
			var nodes = mutableListOf(root)
			while (nodes.isNotEmpty()) {
				for (node in nodes.toList()) {
					nodes += node.childrenList()
					println("\tnode: ${node.path}\tmetadata: ${node.metadata}")
					nodes.remove(node)
				}
			}
			discoverShallow(root, callback);
			println("List Nodes - After Shallow")
			nodes = mutableListOf(root)
			while (nodes.isNotEmpty()) {
				for (node in nodes.toList()) {
					nodes += node.childrenList()
					println("\tnode: ${node.path}\tmetadata: ${node.metadata}")
					nodes.remove(node)
				}
			}
			callback.accept(root);
			sortAndTrimRecursive(root, callback);
			println("List Nodes - After Sort and Trim")
			nodes = mutableListOf(root)
			while (nodes.isNotEmpty()) {
				for (node in nodes.toList()) {
					nodes += node.childrenList()
					println("\tnode: ${node.path}\tmetadata: ${node.metadata}")
					nodes.remove(node)
				}
			}

			val datasetPaths : Array<String>
			try {
				datasetPaths = n5.deepList(root.getPath(), executor);
				println("Deep List Paths")
				datasetPaths.forEach { println("\t $it")}
				N5TreeNode.fromFlatList(root, datasetPaths, groupSeparator);
			} catch (ignore : Exception) {
				ignore.printStackTrace();
				return root;
			}
			callback.accept(root);
			println("List Nodes - Before Parse Metadata")
			nodes = mutableListOf(root)
			while (nodes.isNotEmpty()) {
				for (node in nodes.toList()) {
					nodes += node.childrenList()
					println("\tnode: ${node.path}\tmetadata: ${node.metadata}")
					nodes.remove(node)
				}
			}
			parseMetadataRecursive(root, callback)
			println("List Nodes - After Parse Metadata")
			nodes = mutableListOf(root)
			while (nodes.isNotEmpty()) {
				for (node in nodes.toList()) {
					nodes += node.childrenList()
					println("\tnode: ${node.path}\tmetadata: ${node.metadata}")
					nodes.remove(node)
				}
			}
			sortAndTrimRecursive(root, callback)
			println("List Nodes - After Sort and Trim 2")
			nodes = mutableListOf(root)
			while (nodes.isNotEmpty()) {
				for (node in nodes.toList()) {
					nodes += node.childrenList()
					println("\tnode: ${node.path}\tmetadata: ${node.metadata}")
					nodes.remove(node)
				}
			}
			return root
		}
	}
}

private val LOG = KotlinLogging.logger {  }

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
	val discoverer = getDiscoverer(n5Reader)
	return discoverer.discoverAndParseRecursive(initialGroup) {
		callback(it)
	}
}