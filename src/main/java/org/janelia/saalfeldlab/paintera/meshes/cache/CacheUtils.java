package org.janelia.saalfeldlab.paintera.meshes.cache;

import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class CacheUtils
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * @param source
	 * @param getMaskGenerator
	 * 		Turn data into binary mask usable in marching cubes.
	 * @param makeCache
	 * 		Build a {@link Cache} from a {@link CacheLoader}
	 *
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<Long>, Pair<float[], float[]>>[]
	meshCacheLoaders(
			final DataSource<D, T> source,
			final BiFunction<Long, Double, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<Long>, Pair<float[], float[]>>, Cache<ShapeKey<Long>, Pair<float[],
					float[]>>> makeCache)
	{
		return meshCacheLoaders(
				source,
				Stream.generate(() -> new int[] {1, 1, 1}).limit(source.getNumMipmapLevels()).toArray(int[][]::new),
				getMaskGenerator,
				makeCache
			);
	}

	/**
	 * @param source
	 * @param cubeSizes
	 * 		cube sizes for marching cubes
	 * @param getMaskGenerator
	 * 		Turn data into binary mask usable in marching cubes.
	 * @param makeCache
	 * 		Build a {@link Cache} from a {@link CacheLoader}
	 *
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<Long>, Pair<float[], float[]>>[]
	meshCacheLoaders(
			final DataSource<D, T> source,
			final int[][] cubeSizes,
			final BiFunction<Long, Double, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<Long>, Pair<float[], float[]>>, Cache<ShapeKey<Long>, Pair<float[],
					float[]>>> makeCache)
	{
		final int numMipmapLevels = source.getNumMipmapLevels();
		@SuppressWarnings("unchecked") final InterruptibleFunctionAndCache<ShapeKey<Long>, Pair<float[], float[]>>[]
				caches = new InterruptibleFunctionAndCache[numMipmapLevels];

		for (int i = 0; i < numMipmapLevels; ++i)
		{
			final AffineTransform3D transform = new AffineTransform3D();
			source.getSourceTransform(0, i, transform);
			final MeshCacheLoader<D> loader = new MeshCacheLoader<>(
					cubeSizes[i],
					source.getDataSource(0, i),
					getMaskGenerator,
					transform
			);
			final Cache<ShapeKey<Long>, Pair<float[], float[]>> cache = makeCache.apply(loader);
			caches[i] = new InterruptibleFunctionAndCache<>(cache.unchecked(), loader);
		}

		return caches;
	}

	/**
	 * @param source
	 * @param getMaskGenerator
	 * 		Turn data into binary mask usable in marching cubes.
	 * @param makeCache
	 * 		Build a {@link Cache} from a {@link CacheLoader}
	 *
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] segmentMeshCacheLoaders(
			final DataSource<D, T> source,
			final BiFunction<TLongHashSet, Double, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Cache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>> makeCache)
	{
		return segmentMeshCacheLoaders(
				source,
				Stream.generate(() -> new int[] {1, 1, 1}).limit(source.getNumMipmapLevels()).toArray(int[][]::new),
				getMaskGenerator,
				makeCache);
	}

	/**
	 * @param source
	 * @param cubeSizes
	 * 		cube sizes for marching cubes
	 * @param getMaskGenerator
	 * 		Turn data into binary mask usable in marching cubes.
	 * @param makeCache
	 * 		Build a {@link Cache} from a {@link CacheLoader}
	 *
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[]
	segmentMeshCacheLoaders(
			final DataSource<D, T> source,
			final int[][] cubeSizes,
			final BiFunction<TLongHashSet, Double, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Cache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>> makeCache)
	{
		final int numMipmapLevels = source.getNumMipmapLevels();
		@SuppressWarnings("unchecked") InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] caches = new InterruptibleFunctionAndCache[numMipmapLevels];

		LOG.debug("source is type {}", source.getClass());
		for (int i = 0; i < numMipmapLevels; ++i)
		{
			final int fi = i;
			final AffineTransform3D transform = new AffineTransform3D();
			source.getSourceTransform(0, i, transform);
			final SegmentMeshCacheLoader<D> loader = new SegmentMeshCacheLoader<>(
					cubeSizes[i],
					() -> source.getDataSource(0, fi),
					getMaskGenerator,
					transform
			);
			final Cache<ShapeKey<TLongHashSet>, Pair<float[], float[]>> cache = makeCache.apply(loader);
			caches[i] = new InterruptibleFunctionAndCache<>(cache.unchecked(), loader);
		}

		return caches;
	}

}
