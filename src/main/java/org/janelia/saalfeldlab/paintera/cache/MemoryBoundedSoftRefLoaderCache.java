package org.janelia.saalfeldlab.paintera.cache;

import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.LoaderCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * A cache that forwards to some other (usually {@link WeakRefLoaderCache}) cache and
 * additionally keeps {@link SoftReference}s to the <em>N</em> most recently
 * accessed values.
 *
 * @param <K>
 * @param <V>
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 * @author Philipp Hanslovsky
 */
public class MemoryBoundedSoftRefLoaderCache<K, V, LC extends LoaderCache<K, V> & Invalidate<K>> implements LoaderCache<K, V>, Invalidate<K>{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final LC cache;

	private SoftRefs softRefs;

	private final ToLongFunction<V> memoryUsageInBytes;

	private MemoryBoundedSoftRefLoaderCache(final LC cache, final long maxSizeInBytes, final ToLongFunction<V> memoryUsageInBytes) {
		this.cache = cache;
		this.softRefs = new SoftRefs(maxSizeInBytes);
		this.memoryUsageInBytes = memoryUsageInBytes;
	}

	public static <K, V> MemoryBoundedSoftRefLoaderCache<K, V, WeakRefLoaderCache<K, V>> withWeakRefs(final long maxSizeInBytes, final ToLongFunction<V> memoryUsageInBytes)
	{
		return new MemoryBoundedSoftRefLoaderCache<>(new WeakRefLoaderCache<>(), maxSizeInBytes, memoryUsageInBytes);
	}

	public void restrictToMaxSize()
	{
		LOG.debug("Restricting to max size");
		final List<K> toBeInvalidated = softRefs.restrictToMaxSize();
		if (toBeInvalidated != null) {
			LOG.debug("Invalidated {} keys: Soft refs size {}", toBeInvalidated.size(), softRefs.size());
			this.cache.invalidate(toBeInvalidated);
		}
		else
			LOG.debug("Did not invalidate any keys");
	}

	public long getMaxSize()
	{
		return softRefs.maxSizeInBytes;
	}

	public void setMaxSize(long maxSizeInBytes)
	{
		final List<K> toBeInvalidated = this.softRefs.setMaxSize(maxSizeInBytes);
		this.invalidate(toBeInvalidated);
	}

	public long getCurrentMemoryUsageInBytes()
	{
		synchronized(softRefs)
		{
			return this.softRefs.currentSizeInBytes;
		}
	}

	@Override
	public V getIfPresent(final K key) {
		final V value = cache.getIfPresent(key);
		if (value != null)
			softRefs.touch(key, value);
		return value;
	}

	@Override
	public V get(final K key, final CacheLoader<? super K, ? extends V> loader) throws ExecutionException {
		final V value = cache.get(key, loader);
		softRefs.touch(key, value);
		return value;
	}

	@Override
	public void invalidateAll() {
		softRefs.clear();
		cache.invalidateAll();
	}

	@Override
	public Collection<K> invalidateMatching(Predicate<K> test) {
		final Collection<K> removedKeys = cache.invalidateMatching(test);
		final List<K> toBeRemoved = new ArrayList<>(removedKeys);
		synchronized (softRefs)
		{
			softRefs.keySet().stream().filter(test).forEach(toBeRemoved::add);
		}
		// this removes removedKeys from backing cache twice but makes sure additional keys get removed, as well
		invalidate(toBeRemoved);
		return toBeRemoved;
	}

	@Override
	public void invalidate(Collection<K> keys) {
		cache.invalidate(keys);
		synchronized (softRefs)
		{
			for (K key : keys) {
				final SoftRef<V> v = softRefs.remove(key);
				if (v != null)
					v.clear();
			}
		}
	}

	@Override
	public void invalidate(K key) {
		cache.invalidate(key);
		synchronized (softRefs)
		{
			final SoftRef<V> v = softRefs.remove(key);
			if (v != null)
				v.clear();
		}
	}

	class SoftRefs extends LinkedHashMap<K, SoftRef<V>> {
		private static final long serialVersionUID = 1L;

		private long maxSizeInBytes;

		private long currentSizeInBytes = 0;

		private final Consumer<V> onConstruction = v -> this.addToCurrentSizeInBytes(memoryUsageInBytes.applyAsLong(v));

		private final Consumer<V> onClear = v -> this.subtractFromCurrentSizeInBytes(memoryUsageInBytes.applyAsLong(v));

		public SoftRefs(final long maxSizeInBytes) {
			// 262144 = 8 * 8 * 8 * 1byte
			super(100, 0.75f, true);
			this.maxSizeInBytes = maxSizeInBytes;
		}

		public List<K> restrictToMaxSize()
		{
			final boolean needsUpdate = currentSizeInBytes > maxSizeInBytes;
			LOG.debug("Needs update? {} ({}/{})", needsUpdate, currentSizeInBytes, maxSizeInBytes);
			if (!needsUpdate)
				return null;
			synchronized(this)
			{
				List<K> keys = new ArrayList<>(keySet());
				List<K> removedKeys = new ArrayList<>();
				for (K key : keys)
				{
					if (currentSizeInBytes <= maxSizeInBytes)
						break;
					SoftRef<V> ref = remove(key);
					ref.clear();
					removedKeys.add(key);
				}
				LOG.debug("Returning {} keys that were removed", removedKeys.size());
				return removedKeys;
			}
		}

		public synchronized List<K> setMaxSize(long maxSizeInBytes)
		{
			this.maxSizeInBytes = maxSizeInBytes;
			return restrictToMaxSize();
		}


		@Override
		protected boolean removeEldestEntry(final Entry<K, SoftRef<V>> eldest) {
			if (currentSizeInBytes > maxSizeInBytes) {
				eldest.getValue().clear();
				return true;
			} else
				return false;
		}

		synchronized void touch(final K key, final V value) {
			final SoftRef<V> ref = get(key);
			if (ref == null || ref.get() == null)
				put(key, new SoftRef<>(value, onConstruction, onClear, this));
		}

		@Override
		public synchronized void clear() {
			for (final SoftReference<V> ref : values())
				ref.clear();
			super.clear();
		}

		private void subtractFromCurrentSizeInBytes(long sizeInBytes)
		{
			LOG.trace("Subtracting {}", sizeInBytes);
			setCurrentSizeInBytes(this.currentSizeInBytes - sizeInBytes);
		}

		private void addToCurrentSizeInBytes(long sizeInBytes)
		{
			LOG.trace("Adding {}", sizeInBytes);
			setCurrentSizeInBytes(this.currentSizeInBytes + sizeInBytes);
		}

		private void setCurrentSizeInBytes(long sizeInBytes)
		{
			LOG.trace("Setting to {}", sizeInBytes);
			this.currentSizeInBytes = sizeInBytes;
		}
	}
}
