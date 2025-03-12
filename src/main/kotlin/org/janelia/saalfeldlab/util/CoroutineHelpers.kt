package org.janelia.saalfeldlab.util

import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

internal fun coroutineBackedExecutorService(
	coroutineContext: CoroutineContext,
	futureInterceptor: ((Any?) -> Unit)? = null
): Pair<ExecutorService, CoroutineScope> {
	var scope = CoroutineScope(coroutineContext)
	val es = object : AbstractExecutorService() {
		private val shutdown = AtomicBoolean(false)
		override fun shutdown() {
			shutdown.getAndSet(true)
		}

		override fun shutdownNow(): List<Runnable> {
			shutdown()
			return scope.cancel("Shutdown requested").let { emptyList() }
		}

		override fun isShutdown(): Boolean = shutdown.get() || !scope.isActive
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
			scope.launch {
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
					is CancellationException -> (command as Future<*>).cancel(true)
					else -> throw cause
				}
			}
		}
	}
	return es to scope
}