package org.janelia.saalfeldlab.paintera.cache

import kotlinx.coroutines.*
import net.imglib2.cache.Cache
import net.imglib2.cache.LoaderCache
import java.util.function.Predicate

abstract class AsyncCacheWithLoader<K : Any, V>(
	val loaderScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
	protected val loaderQueueScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Cache<K, V> {

	protected abstract val cache: LoaderCache<K, Deferred<V>>

	protected abstract suspend fun loader(key : K): V

	fun cancelUnfinishedRequests() {
		val reason = CancellationException("Unfinished Requests Cancelled")
		loaderQueueScope.coroutineContext.cancelChildren(reason)
		loaderScope.coroutineContext.cancelChildren(reason)
	}

	override fun getIfPresent(key: K): V? {
		return runBlocking {
			cache.getIfPresent(key)?.await()
		}
	}

	override fun get(key: K): V = runBlocking {
		/* We clear here because the logic is that if we have to actually get the
		* value from the loader, then it's likely that prior optimistic calls to [::load]
		* are probably no longer valid. clear them from the job queue.  */
		request(key, clear = true).await()
	}

	fun request(key: K, clear : Boolean = false): Deferred<V> = runBlocking {
		cache.get(key) {
			if (clear) cancelUnfinishedRequests()
			loaderScope.async { loader(key) }
		}.invalidateOnException(key)
	}

	private fun Deferred<V>.invalidateOnException(key: K) : Deferred<V> {
		invokeOnCompletion { cause ->
			cause?.let { cache.invalidate(key) }
		}
		return this
	}

	open fun load(key: K) : Job {
		/*check invalidate an existing value if it has finished excepptionally */
		cache.getIfPresent(key)?.invalidateOnException(key)
		/* If it's already in the cache, return it */
		cache.getIfPresent(key)?.let { return it }
		/* otherwise, trigger a new non-blocking request */
		return loaderQueueScope.async {
			if (!isActive) invalidate(key)
			else request(key)
		}
	}

	override fun persist(key: K) {
		cache.persist(key)
	}

	override fun persistIf(condition: Predicate<K>?) {
		cache.persistIf(condition)
	}

	override fun persistAll() {
		cache.persistAll()
	}

	override fun invalidate(key: K) {
		cache.invalidate(key)
	}

	override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<K>?) {
		cache.invalidateIf(parallelismThreshold, condition)
	}

	override fun invalidateAll(parallelismThreshold: Long) {
		cache.invalidateAll(parallelismThreshold)
	}
}