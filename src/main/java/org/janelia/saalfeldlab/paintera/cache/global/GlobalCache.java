/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.janelia.saalfeldlab.paintera.cache.global;

import bdv.cache.CacheControl;
import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.util.volatiles.VolatileTypeMatcher;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.LoaderCache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.queue.FetcherThreads;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.util.KeyBimap;
import net.imglib2.cache.volatiles.*;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import tmp.bdv.img.cache.VolatileCachedCellImg;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class GlobalCache implements CacheControl {
	/**
	 * Key for a cell identified by timepoint, setup, level, and index
	 * (flattened spatial coordinate).
	 */
	public static class Key<T> {
		public final int setup;

		public final T subKey;

		private final int hashcode;

		/**
		 * @param setup
		 * @param subKey
		 */
		public Key(final int setup, final T subKey) {
			this.setup = setup;
			this.subKey = subKey;
			this.hashcode = 31 * Integer.hashCode(this.setup) + subKey.hashCode();
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Key))
				return false;
			final Key that = (Key) other;
			return this.setup == that.setup && this.subKey.equals(that.subKey);
		}

		@Override
		public int hashCode() {
			return hashcode;
		}
	}

	public static class MipmapLevelAndIndex {
		public final int level;

		public final long index;

		private final int hashCode;

		public MipmapLevelAndIndex(int level, long index) {
			this.level = level;
			this.index = index;
			this.hashCode = 31 * Integer.hashCode(level) + Long.hashCode(index);
		}

		public int hashCode() {
			return this.hashCode;
		}

		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof MipmapLevelAndIndex))
				return false;
			MipmapLevelAndIndex that = (MipmapLevelAndIndex) other;
			return this.level == that.level && this.index == that.index;
		}
	}

	private final BlockingFetchQueues<Callable<?>> queue;

	protected final LoaderCache<Key<?>, ?> backingCache;

	private final AtomicInteger nextSetupId = new AtomicInteger(0);

	/**
	 * Create a new global cache with a new fetch queue served by the specified
	 * number of fetcher threads.
	 *
	 * @param maxNumLevels      the highest occurring mipmap level plus 1.
	 * @param numFetcherThreads how many threads should be created to load data.
	 */
	public GlobalCache(final int maxNumLevels, final int numFetcherThreads, LoaderCache<Key<?>, ?> backingCache) {
		queue = new BlockingFetchQueues<>(maxNumLevels);
		new FetcherThreads(queue, numFetcherThreads);
//		backingCache = new SoftRefLoaderCache<>();
		this.backingCache = backingCache;
	}

	/**
	 * Create a new global cache with the specified fetch queue. (It is the
	 * callers responsibility to create fetcher threads that serve the queue.)
	 *
	 * @param queue queue to which asynchronous data loading jobs are submitted
	 */
	public GlobalCache(final BlockingFetchQueues<Callable<?>> queue, LoaderCache<Key<?>, ?> backingCache) {
		this.queue = queue;
		this.backingCache = backingCache;
	}

	/**
	 * Prepare the cache for providing data for the "next frame",
	 * by moving pending cell request to the prefetch queue
	 * ({@link BlockingFetchQueues#clearToPrefetch()}).
	 */
	@Override
	public void prepareNextFrame() {
		queue.clearToPrefetch();
	}

	/**
	 * Remove all references to loaded data as well as all enqueued requests
	 * from the cache.
	 */
	public void clearCache() {
		backingCache.invalidateAll();
		queue.clear();
		backingCache.invalidateAll();
	}

	public int nextSetupId() {
		return this.nextSetupId.getAndIncrement();
	}

	public int getNumPriorities() {
		return this.queue.getNumPriorities();
	}

	public <T extends NativeType<T>, A extends ArrayDataAccess<A>> CachedCellImg<T, A> createVolatileImg(
			final CellGrid grid,
			final CellLoader<T> loader,
			final T type) {
		return createImg(grid, loader, type, AccessFlags.VOLATILE);
	}

	public <T extends NativeType<T>, A extends ArrayDataAccess<A>> CachedCellImg<T, A> createImg(
			final CellGrid grid,
			final CellLoader<T> loader,
			final T type,
			AccessFlags... accessFlags) {
		final LoadedCellCacheLoader<T, A> cacheLoader = LoadedCellCacheLoader.get(grid, loader, type, AccessFlags.setOf(accessFlags));
		return createImg(grid, cacheLoader, type, accessFlags);
	}

	public <T extends NativeType<T>, A extends ArrayDataAccess<A>> CachedCellImg<T, A> createVolatileImg(
			final CellGrid grid,
			final CacheLoader<Long, Cell<A>> loader,
			final T type) {
		return createImg(grid, loader, type);
	}

	public <T extends NativeType<T>, A extends ArrayDataAccess<A>> CachedCellImg<T, A> createImg(
			final CellGrid grid,
			final CacheLoader<Long, Cell<A>> loader,
			final T type,
			AccessFlags... accessFlags) {
		final int setup = nextSetupId();
		final KeyBimap<Long, Key<Long>> bimap = KeyBimap.build(
				index -> new Key<>(setup, index),
				key -> key.subKey);

		final Cache<Long, Cell<A>> cache = backingCache
				.mapKeys((KeyBimap) bimap)
				.withLoader((CacheLoader) loader);

		final A access = ArrayDataAccessFactory.get(type, AccessFlags.setOf(accessFlags));
		return new CachedCellImg<>(grid, type, cache, access);
	}

	public <T extends NativeType<T>, A> CachedCellImg<T, A> createImg(
			final CellGrid grid,
			final CacheLoader<Long, Cell<A>> loader,
			final Fraction fraction,
			final A accessType) {
		final int setup = nextSetupId();
		final KeyBimap<Long, Key<Long>> bimap = KeyBimap.build(
				index -> new Key<>(setup, index),
				key -> key.subKey);

		final Cache<Long, Cell<A>> cache = backingCache
				.mapKeys((KeyBimap) bimap)
				.withLoader((CacheLoader) loader);
		return new CachedCellImg<>(grid, fraction, cache, accessType);
	}

	public <
			T extends NativeType<T>,
			V extends Volatile<T> & NativeType<V>, A>
	Pair<RandomAccessibleInterval<V>, VolatileCache<Long, Cell<A>>> wrapAsVolatile(
			CachedCellImg<T, A> img,
			final int priority
	) throws InvalidAccessException {
		final A accessType = img.getAccessType();

		if (!(accessType instanceof VolatileAccess))
			throw new InvalidAccessException(accessType, VolatileAccess.class);

		final T type = Util.getTypeFromInterval(img);
		final V vtype = (V) VolatileTypeMatcher.getVolatileTypeForType(type);
		final boolean isDirty = AccessFlags.ofAccess(accessType).contains(AccessFlags.DIRTY);

		CreateInvalid<Long, Cell<A>> createInvalid = (CreateInvalid) CreateInvalidVolatileCell.get(
				img.getCellGrid(),
				type,
				isDirty);
		VolatileCache<Long, Cell<A>> vcache = new WeakRefVolatileCache<>(img.getCache(), queue, createInvalid);
		final UncheckedVolatileCache<Long, Cell<A>> unchecked =
				vcache.unchecked();

		final CacheHints cacheHints = new CacheHints(LoadingStrategy.VOLATILE, priority, true);

		@SuppressWarnings("unchecked") final VolatileCachedCellImg<V, A> vimg = new VolatileCachedCellImg<>(
				img.getCellGrid(),
				vtype,
				cacheHints,
				unchecked::get,
				((WeakRefVolatileCache<Long, Cell<A>>) vcache)::cleanUp);

		return new ValuePair<>(vimg, vcache);
	}

	public <
			T extends NativeType<T>,
			V extends Volatile<T> & NativeType<V>, A>
	Pair<RandomAccessibleInterval<V>, VolatileCache<Long, Cell<A>>> wrapAsVolatile(
			CachedCellImg<T, A> img,
			final Function<NativeImg<V, ? extends A>, V> typeFactory,
			final CreateInvalid<Long, Cell<A>> createInvalid,
			final int priority
	) throws InvalidAccessException {
		final A accessType = img.getAccessType();

		if (!(accessType instanceof VolatileAccess))
			throw new InvalidAccessException(accessType, VolatileAccess.class);

		final T type = Util.getTypeFromInterval(img);
		final boolean isDirty = AccessFlags.ofAccess(accessType).contains(AccessFlags.DIRTY);

		VolatileCache<Long, Cell<A>> vcache = new WeakRefVolatileCache<>(img.getCache(), queue, createInvalid);
		final UncheckedVolatileCache<Long, Cell<A>> unchecked = vcache.unchecked();

		final CacheHints cacheHints = new CacheHints(LoadingStrategy.VOLATILE, priority, true);

		@SuppressWarnings("unchecked") final VolatileCachedCellImg<V, A> vimg = new VolatileCachedCellImg<>(
				img.getCellGrid(),
				type.getEntitiesPerPixel(),
				typeFactory,
				cacheHints,
				unchecked::get,
				((WeakRefVolatileCache<Long, Cell<A>>) vcache)::cleanUp);
		return new ValuePair<>(vimg, vcache);
	}


}
