package org.janelia.saalfeldlab.paintera.cache;

import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.LoaderCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WeakRefLoaderCache< K, V > implements LoaderCache< K, V >, Invalidate<K>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	final ConcurrentHashMap< K, Entry > map = new ConcurrentHashMap<>();

	final ReferenceQueue< V > queue = new ReferenceQueue<>();

	final class CacheWeakReference extends WeakReference< V >
	{
		private final Entry entry;

		public CacheWeakReference()
		{
			super( null );
			this.entry = null;
		}

		public CacheWeakReference( final V referent, final Entry entry )
		{
			super( referent, queue );
			this.entry = entry;
		}

		public void clean()
		{
			map.remove( entry.key, entry );
		}
	}

	final class Entry
	{
		final K key;

		private CacheWeakReference ref;

		boolean loaded;

		public Entry( final K key )
		{
			this.key = key;
			this.ref = new CacheWeakReference();
			this.loaded = false;
		}

		public V getValue()
		{
			return ref.get();
		}

		public void setValue( final V value )
		{
			this.loaded = true;
			this.ref = new CacheWeakReference( value, this );
		}
	}

	@Override
	public V getIfPresent( final K key )
	{
		cleanUp();
		final Entry entry = map.get( key );
		return entry == null ? null : entry.getValue();
	}

	@Override
	public void invalidateAll() {
		invalidateMatching(k -> true);
	}

	@Override
	public Collection<K> invalidateMatching(Predicate<K> test) {
		synchronized (map)
		{
			final List<K> toBeRemoved = map.keySet().stream().filter(test).collect(Collectors.toList());
			invalidate(toBeRemoved);
			return toBeRemoved;
		}
	}

	@Override
	public void invalidate(Collection<K> keys) {
		LOG.debug("Invalidating keys {}", keys);
		if (keys == null)
			return;
		synchronized (map)
		{
			keys.forEach(map::remove);
			cleanUp();
			keys.forEach(map::remove);
			LOG.debug("map size {}", map.size());
		}
	}

	@Override
	public void invalidate(K key) {
		synchronized (map)
		{
			map.remove(key);
			cleanUp();
			map.remove(key);
		}
	}

	@Override
	public V get( final K key, final CacheLoader< ? super K, ? extends V > loader ) throws ExecutionException
	{
		cleanUp();
		final Entry entry = map.computeIfAbsent( key, ( k ) -> new Entry( k ) );
		V value = entry.getValue();
		if ( value == null )
		{
			synchronized ( entry )
			{
				if ( entry.loaded )
				{
					value = entry.getValue();
					if ( value == null )
					{
						/*
						 * The entry was already loaded, but its value has been
						 * garbage collected. We need to create a new entry
						 */
						map.remove( key, entry );
						value = get( key, loader );
					}
				}
				else
				{
					try
					{
						value = loader.get( key );
						entry.setValue( value );
					}
					catch ( final InterruptedException e )
					{
						Thread.currentThread().interrupt();
						throw new ExecutionException( e );
					}
					catch ( final Exception e )
					{
						throw new ExecutionException( e );
					}
				}
			}
		}
		return value;
	}

	/**
	 * Remove entries from the cache whose references have been
	 * garbage-collected.
	 */
	public void cleanUp()
	{
		while ( true )
		{
			@SuppressWarnings( "unchecked" )
			final CacheWeakReference poll = ( CacheWeakReference ) queue.poll();
			if ( poll == null )
				break;
			poll.clean();
		}
	}
}
