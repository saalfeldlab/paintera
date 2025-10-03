package org.janelia.saalfeldlab.util.n5

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Application.launch
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5Factory
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.util.n5.N5Helpers.GROUP_PARSERS
import org.janelia.saalfeldlab.util.n5.N5Helpers.METADATA_PARSERS
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
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

private fun coroutineBackedExecutorService(
	coroutineContext: CoroutineContext,
	futureInterceptor : ((Any?) -> Unit)? = null
): Pair<ExecutorService, CoroutineScope> {
	var parseScope = CoroutineScope(coroutineContext)
	val es = object : AbstractExecutorService() {
		private val shutdown = AtomicBoolean(false)
		override fun shutdown() {
			shutdown.getAndSet(true)
		}

		override fun shutdownNow(): List<Runnable> {
			shutdown()
			return parseScope.cancel("Shutdown requested").let { emptyList() }
		}

		override fun isShutdown(): Boolean = shutdown.get() || !parseScope.isActive
		override fun isTerminated(): Boolean = TODO("Not yet implemented")
		override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = TODO("Not yet implemented")
		override fun submit(task: Runnable): Future<*> {
			ensureNotShutdown()
			return super.submit(task)
		}

		override fun <T : Any?> submit(task: Callable<T?>): Future<T?> {
			ensureNotShutdown()
			return super.submit(task)
		}

		override fun <T : Any?> submit(task: Runnable, result: T?): Future<T?> {
			ensureNotShutdown()
			return super.submit(task, result)
		}

		private fun ensureNotShutdown() {
			if (isShutdown)
				throw RejectedExecutionException("Executor service was shutdown")
		}

		override fun execute(command: Runnable) {
			parseScope.launch {
				if (!isShutdown) {
					command.run()
					futureInterceptor?.let { intercept ->
						(command as? Future<*>)?.let { future ->
							runCatching {
								if (!isShutdown)
								 intercept(future.get())
							}
						}
					}
				}
			}.invokeOnCompletion { cause ->
				@Suppress("RemoveRedundantQualifierName") //ambiguous without qualifying CancellationException
				when (cause) {
					null -> Unit
					is kotlinx.coroutines.CancellationException -> (command as Future<*>).cancel(true)
					else -> throw cause
				}
			}
		}
	}
	return es to parseScope
}

fun main() {
	var waiting = 5
	var n5 = N5Factory.createReader("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")
	var paths = mutableSetOf<String>()
	val parseJob = CoroutineScope(Dispatchers.IO).launch {
			asyncDiscoverAndParseRecursive(n5, "/", { println("deeplist: $it")}) {
				if (paths.add(it.path))
					println("parse: ${it.path}")
			}
	}
	runBlocking {
		delay(1000)
		parseJob.cancel()
		println("cancelled!")
		delay(1000)
	}
}