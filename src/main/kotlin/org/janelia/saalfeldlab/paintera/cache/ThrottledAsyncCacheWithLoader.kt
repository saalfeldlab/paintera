package org.janelia.saalfeldlab.paintera.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * An [AsyncCacheWithLoader] that limits the number of concurrent
 * in-flight loader requests. Requests that exceed that cap are queued,
 * where immediate requests are inserted at the front and pre-fetch
 * requests at the back.
 *
 * When an in-flight request completes, the next pending request is
 * automatically submitted.
 *
 * @param maxConcurrentRequests maximum number of requests in-flight at once
 */
abstract class ThrottledAsyncCacheWithLoader<K : Any, V>(
	private val maxConcurrentRequests: Int = 10
) : AsyncCacheWithLoader<K, V>() {

	private val inFlightRequests = AtomicInteger(0)
	private val pendingQueue = ConcurrentLinkedDeque<PendingRequest<K, V>>()

	private class PendingRequest<K, V>(
		val key: K,
		val deferred: CompletableDeferred<V>,
	)

	private fun submitToLoader(key: K, result: CompletableDeferred<V>) {
		inFlightRequests.incrementAndGet()
		loaderScope.launch {
			try {
				LOG.trace { "Submitting $key" }
				result.complete(loader(key))
			} catch (cancellation: CancellationException) {
				result.cancel(cancellation)
			} catch (exception: Throwable) {
				result.completeExceptionally(exception)
			} finally {
				inFlightRequests.decrementAndGet()
				loaderScope.launch { submitFromQueue() }
			}
		}
	}

	private fun submitFromQueue() {
		while (inFlightRequests.get() < maxConcurrentRequests) {
			val next = pendingQueue.pollFirst() ?: break
			if (next.deferred.isActive) {
				submitToLoader(next.key, next.deferred)
			}
		}
	}

	private fun submitOrEnqueue(key: K, isPrefetch: Boolean): Deferred<V> {
		val deferred = CompletableDeferred<V>()
		if (inFlightRequests.get() < maxConcurrentRequests) {
			submitToLoader(key, deferred)
		} else {
			LOG.trace { "Adding $key to queue at ${if (isPrefetch) "End" else "Start"}" }
			val pending = PendingRequest(key, deferred)
			if (isPrefetch) {
				pendingQueue.addLast(pending)
			} else {
				pendingQueue.addFirst(pending)
			}
			/* re-check in case a slot freed between the check and enqueue */
			submitFromQueue()
		}
		return deferred
	}

	override fun request(key: K, clear: Boolean): Deferred<V> = runBlocking {
		cache.get(key) {
			LOG.trace { "cache miss, trigger new loader request (throttled)" }
			if (clear) cancelUnfinishedRequests()
			submitOrEnqueue(key, isPrefetch = false)
		}.apply {
			invokeOnCompletion { cause ->
				cause?.let { cache.invalidate(key) }
			}
		}
	}

	override fun load(key: K): Job {
		cache.getIfPresent(key)?.invokeOnCompletion { cause ->
			cause?.let { cache.invalidate(key) }
		}

		cache.getIfPresent(key)?.let { return it }
		return loaderQueueScope.async {
			if (!isActive)
				invalidate(key)

			submitOrEnqueue(key, isPrefetch = true).await()
		}
	}

	override fun cancelUnfinishedRequests() {
		LOG.debug { "cancelling unfinished requests (throttled)" }
		/* cancel queued requests that haven't been submitted yet */
		val reason = CancellationException("Unfinished Requests Cancelled")
		pendingQueue.removeAll { pending ->
			pending.deferred.cancel(reason)
			true
		}
		super.cancelUnfinishedRequests()
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}
