package org.janelia.saalfeldlab.paintera.cache

import javafx.beans.property.SimpleIntegerProperty
import kotlinx.coroutines.*
import net.imglib2.cache.Cache
import net.imglib2.cache.LoaderCache
import org.checkerframework.checker.units.qual.K
import org.janelia.saalfeldlab.fx.extensions.nonnull
import java.util.concurrent.ExecutionException
import java.util.function.Predicate

open class AsyncCacheWithLoader<K : Any, V>(
	private val cache: LoaderCache<K, Deferred<V>>,
	private val loader: suspend (K) -> V
) : Cache<K, V> {

	private val loaderContext = Dispatchers.IO + Job()
	private val loaderQueueContext = Dispatchers.IO + Job()

	val cacheSizeProperty = SimpleIntegerProperty()
	var cacheSize by cacheSizeProperty.nonnull()
		private set
	val pendingJobsProperty = SimpleIntegerProperty()
	var pendingJobs by pendingJobsProperty.nonnull()
		private set

	fun cancelUnsubmittedLoadRequests() {
		loaderQueueContext.cancelChildren()
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
			if (clear) cancelUnsubmittedLoadRequests()
			async(loaderContext) {  loader(key) }
		}
	}

	open fun load(key: K) : Job {
		/* If it's already in the cache, deferred or not, just return a completed job and skipp the loader queue */
		if (cache.getIfPresent(key) != null) return Job().apply { complete() }

		return runBlocking {
			async(loaderQueueContext) {
				if (!isActive) invalidate(key)
				else request(key)
			}
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