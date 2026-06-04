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
	val scope = CoroutineScope(coroutineContext)
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

		override fun <T> submit(task: Callable<T?>): Future<T?> {
			ensureNotShutdown()
			return super.submit(task)
		}

		override fun <T> submit(task: Runnable, result: T?): Future<T?> {
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
                    if (futureInterceptor == null || !isShutdown || command !is Future<*>)
						return@launch

					futureInterceptor(command.get())
				}
			}.invokeOnCompletion { cause ->
				(command as? Future<*>)
					?.takeIf { cause is CancellationException }
					?.cancel(true)
			}
		}
	}
	return es to scope
}