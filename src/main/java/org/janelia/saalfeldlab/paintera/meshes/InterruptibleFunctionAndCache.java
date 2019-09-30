package org.janelia.saalfeldlab.paintera.meshes;

import net.imglib2.cache.UncheckedCache;

import java.util.function.Predicate;

public class InterruptibleFunctionAndCache<K, V> implements UncheckedCache<K, V>, InterruptibleFunction<K, V>
{

	private final UncheckedCache<K, V> cacheDelegate;

	private final Interruptible<K> interrupt;

	public InterruptibleFunctionAndCache(
			final UncheckedCache<K, V> cacheDelegate,
			final Interruptible<K> interrupt)
	{
		super();
		this.cacheDelegate = cacheDelegate;
		this.interrupt = interrupt;
	}

	@Override
	public void invalidateAll()
	{
		this.cacheDelegate.invalidateAll();
	}

	@Override
	public void invalidateIf(final long parallelismThreshold, final Predicate<K> predicate) {
		this.cacheDelegate.invalidateIf(parallelismThreshold, predicate);
	}

	@Override
	public void invalidateAll(long parallelismThreshold) {
		this.cacheDelegate.invalidateAll(parallelismThreshold);
	}

	@Override
	public void invalidate(K key) {
		this.cacheDelegate.invalidate(key);
	}

	@Override
	public V get(final K key)
	{
		return cacheDelegate.get(key);
	}

	@Override
	public V apply(final K key)
	{
		return this.get(key);
	}

	@Override
	public void interruptFor(final K t)
	{
		interrupt.interruptFor(t);
	}

	@Override
	public V getIfPresent(final K key)
	{
		return cacheDelegate.getIfPresent(key);
	}


}
