package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheUtils
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Create cascade of caches that produce list of containing blocks for a label at each scale level.
	 *
	 * @param source
	 * @param uniqueLabelLoaders
	 * 		A cascade of cache loaders that produce a unique list of contained labels at each scale level.
	 * @param blockSizes
	 * 		block size per dimension. Note that this need not be the same as a potential blocking for {@code source}.
	 * @param scalingFactors
	 * 		scaling factors for each scale level, relative to a common baseline. Usually,
	 * 		{@link scalingFactors[ 0 ] == 1}
	 * 		should be the case.
	 * @param makeCache
	 * 		Build a {@link Cache} from a {@link CacheLoader}
	 * @param es
	 * 		{@link ExecutorService} for parallel execution of retrieval of lists of unique labels. The task is
	 * 		parallelized
	 * 		over blocks.
	 *
	 * @return Cascade of {@link Cache} that produce list of containing blocks for a label (key) at each scale level.
	 */
	public static <D, T> InterruptibleFunctionAndCache<Long, Interval[]>[] blocksForLabelCachesLongKeys(
			final DataSource<D, T> source,
			final InterruptibleFunction<HashWrapper<long[]>, long[]>[] uniqueLabelLoaders,
			final int[][] blockSizes,
			final double[][] scalingFactors,
			final Function<CacheLoader<Long, Interval[]>, Cache<Long, Interval[]>> makeCache)
	{
		final int numMipmapLevels = source.getNumMipmapLevels();
		assert uniqueLabelLoaders.length == numMipmapLevels;

		@SuppressWarnings("unchecked") final InterruptibleFunctionAndCache<Long, Interval[]>[] caches = new
				InterruptibleFunctionAndCache[numMipmapLevels];

		LOG.debug("Number of mipmap levels for source {}: {}", source.getName(), source.getNumMipmapLevels());
		LOG.debug("Provided {} block sizes and {} scaling factors", blockSizes.length, scalingFactors.length);

		for (int level = numMipmapLevels - 1; level >= 0; --level)
		{
			LOG.debug("Adding loader for level {} (out of {} total)", level, numMipmapLevels);
			final Interval interval   = source.getDataSource(0, level);
			final long[]   dims       = Intervals.dimensionsAsLongArray(interval);
			final long[]   max        = Arrays.stream(dims).map(v -> v - 1).toArray();
			final int[]    bs         = blockSizes[level];
			final CellGrid grid       = new CellGrid(dims, bs);
			final int      finalLevel = level;
			final BlocksForLabelCacheLoader<Long> loader = BlocksForLabelCacheLoader.longKeys(
					grid,
					level == numMipmapLevels - 1
					? InterruptibleFunction.fromFunction(l -> new Interval[] {new FinalInterval(dims.clone())})
					: caches[level + 1],
					level == numMipmapLevels - 1
					? l -> collectAllOffsets(dims, bs, b -> fromMin(b, max, bs))
					: relevantBlocksFromLowResInterval(grid, scalingFactors[level + 1], scalingFactors[level]),
					key -> uniqueLabelLoaders[finalLevel].apply(HashWrapper.longArray(key))
			                                                                                 );
			caches[level] = new InterruptibleFunctionAndCache<>(makeCache.apply(loader).unchecked(), loader);
		}

		return caches;
	}

	/**
	 * Create cascade of caches that produce list of containing blocks for a label at each scale level.
	 *
	 * @param source
	 * @param uniqueLabelLoaders
	 * 		A cascade of cache loaders that produce a unique list of contained labels at each scale level.
	 * @param blockSizes
	 * 		block size per dimension. Note that this need not be the same as a potential blocking for {@code source}.
	 * @param scalingFactors
	 * 		scaling factors for each scale level, relative to a common baseline. Usually,
	 * 		{@link scalingFactors[ 0 ] == 1}
	 * 		should be the case.
	 * @param makeCache
	 * 		Build a {@link Cache} from a {@link CacheLoader}
	 * @param es
	 * 		{@link ExecutorService} for parallel execution of retrieval of lists of unique labels. The task is
	 * 		parallelized
	 * 		over blocks.
	 *
	 * @return Cascade of {@link Cache} that produce list of containing blocks for a label (key) at each scale level.
	 */
	public static <D, T> InterruptibleFunction<TLongHashSet, Interval[]>[] blocksForLabelCachesHashSetKeys(
			final DataSource<D, T> source,
			final InterruptibleFunction<HashWrapper<long[]>, long[]>[] uniqueLabelLoaders,
			final int[][] blockSizes,
			final double[][] scalingFactors,
			final Function<CacheLoader<TLongHashSet, Interval[]>, Cache<TLongHashSet, Interval[]>> makeCache)
	{
		final int numMipmapLevels = source.getNumMipmapLevels();
		assert uniqueLabelLoaders.length == numMipmapLevels;

		@SuppressWarnings("unchecked") final InterruptibleFunction<TLongHashSet, Interval[]>[] caches = new
				InterruptibleFunction[numMipmapLevels];

		LOG.debug("Number of mipmap levels for source {}: {}", source.getName(), source.getNumMipmapLevels());
		LOG.debug("Provided {} block sizes and {} scaling factors", blockSizes.length, scalingFactors.length);

		for (int level = numMipmapLevels - 1; level >= 0; --level)
		{
			LOG.debug("Adding loader for level {} (out of {} total)", level, numMipmapLevels);
			final Interval interval   = source.getDataSource(0, level);
			final long[]   dims       = Intervals.dimensionsAsLongArray(interval);
			final long[]   max        = Arrays.stream(dims).map(v -> v - 1).toArray();
			final int[]    bs         = blockSizes[level];
			final CellGrid grid       = new CellGrid(dims, bs);
			final int      finalLevel = level;
			final BlocksForLabelCacheLoader<TLongHashSet> loader = BlocksForLabelCacheLoader.hashSetKeys(
					grid,
					level == numMipmapLevels - 1
					? InterruptibleFunction.fromFunction(l -> new Interval[] {new FinalInterval(dims.clone())})
					: caches[level + 1],
					level == numMipmapLevels - 1
					? l -> collectAllOffsets(dims, bs, b -> fromMin(b, max, bs))
					: relevantBlocksFromLowResInterval(grid, scalingFactors[level + 1], scalingFactors[level]),
					key -> uniqueLabelLoaders[finalLevel].apply(HashWrapper.longArray(key))
			                                                                                            );
			caches[level] = fromCache(makeCache.apply(loader).unchecked(), (Interruptible<TLongHashSet>) loader);
		}

		return caches;
	}

	/**
	 * @param grid
	 * 		{@link CellGrid} defining higher resolution block sizes and dimensions.
	 * @param lowerResScalingFactors
	 * 		Scaling factors for lower resolution.
	 * @param higherResScalingFactors
	 * 		Scaling factors for higher resolution.
	 * 		<p>
	 * 		The scaling factors need to be with respect to a common baseline.
	 *
	 * @return
	 */
	public static Function<Interval, List<Interval>> relevantBlocksFromLowResInterval(
			final CellGrid grid,
			final double[] lowerResScalingFactors,
			final double[] higherResScalingFactors)
	{

		final int nDim = grid.numDimensions();

		// factors to go from higher res to lower res (ignoring any offset)
		final double[] scalingFactors = IntStream.range(
				0,
				nDim
		                                               ).mapToDouble(d -> lowerResScalingFactors[d] /
				higherResScalingFactors[d]).toArray();

		return interval -> {

			LOG.debug(
					"Using scaling factors: low-res={}, high-res={}, relative={}",
					Arrays.toString(lowerResScalingFactors),
					Arrays.toString(higherResScalingFactors),
					Arrays.toString(scalingFactors)
			         );
			// min and max of low res interval
			final long[] min = Intervals.minAsLongArray(interval);
			final long[] max = Intervals.maxAsLongArray(interval);

			// max possible value for high res interval
			final long[] intervalMax = grid.getImgDimensions();
			final int[]  blockSize   = new int[min.length];

			// map min and max into high res
			for (int d = 0; d < min.length; ++d)
			{
				min[d] = (long) Math.floor(min[d] * scalingFactors[d] / grid.cellDimension(d)) * grid.cellDimension(d);
				max[d] = (long) Math.ceil(max[d] * scalingFactors[d] / grid.cellDimension(d)) * grid.cellDimension(d);
				blockSize[d] = grid.cellDimension(d);
				intervalMax[d] -= 1;
			}
			LOG.debug(
					"{} -- mapped low res interval {} into {}",
					grid,
					toString(interval),
					toString(new FinalInterval(min, max))
			         );
			final Interval completeInterval = new FinalInterval(grid.getImgDimensions());
			return collectAllOffsets(min, max, blockSize, b -> fromMin(b, intervalMax, blockSize))
					.stream()
					.filter(i -> Intervals.contains(completeInterval, Point.wrap(Intervals.minAsLongArray(i))))
					.collect(Collectors.toList());
		};
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
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<Long>, Pair<float[], float[]>>[]
	meshCacheLoaders(
			final DataSource<D, T> source,
			final LongFunction<Converter<D, BoolType>> getMaskGenerator,
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
			final LongFunction<Converter<D, BoolType>> getMaskGenerator,
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
			final Function<TLongHashSet, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Cache<ShapeKey<TLongHashSet>,
					Pair<float[], float[]>>> makeCache)
	{
		return segmentMeshCacheLoaders(
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
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[]
	segmentMeshCacheLoaders(
			final DataSource<D, T> source,
			final int[][] cubeSizes,
			final Function<TLongHashSet, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Cache<ShapeKey<TLongHashSet>,
					Pair<float[], float[]>>> makeCache)
	{
		final int numMipmapLevels = source.getNumMipmapLevels();
		@SuppressWarnings("unchecked") final InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[],
				float[]>>[] caches = new InterruptibleFunctionAndCache[numMipmapLevels];

		for (int i = 0; i < numMipmapLevels; ++i)
		{
			final AffineTransform3D transform = new AffineTransform3D();
			source.getSourceTransform(0, i, transform);
			final SegmentMeshCacheLoader<D> loader = new SegmentMeshCacheLoader<>(
					cubeSizes[i],
					source.getDataSource(0, i),
					getMaskGenerator,
					transform
			);
			final Cache<ShapeKey<TLongHashSet>, Pair<float[], float[]>> cache = makeCache.apply(loader);
			caches[i] = new InterruptibleFunctionAndCache<>(cache.unchecked(), loader);
		}

		return caches;

	}

	/**
	 * Utility method that wraps {@link Cache#get(Object)} as {@link Function}, catches any {@link Exception}, and
	 * re-throws as {@link RuntimeException}
	 *
	 * @param throwingCache
	 *
	 * @return
	 */
	public static <T, U> Function<T, U> wrapAsFunction(final Cache<T, U> throwingCache)
	{
		return t -> {
			throwingCache.getIfPresent(t);
			try
			{
				return throwingCache.get(t);
			} catch (final Exception e)
			{
				throw new RuntimeException(e);
			}
		};
	}

	/**
	 * Utility method that wraps {@link CacheLoader#get(Object)} as {@link Function}, catches any {@link Exception},
	 * and
	 * re-throws as {@link RuntimeException}
	 *
	 * @param throwingCache
	 *
	 * @return
	 */
	public static <T, U> Function<T, U> wrapAsFunction(final CacheLoader<T, U> throwingLoader)
	{
		return t -> {
			try
			{
				return throwingLoader.get(t);
			} catch (final Exception e)
			{
				throw new RuntimeException(e);
			}
		};
	}

	/**
	 * Utility method to collect all blocks of specified size contained within an interval {@code[0, dimensions)}.
	 * Blocks are identified by their minimum.
	 *
	 * @param dimensions
	 * @param blockSize
	 *
	 * @return
	 */
	public static List<long[]> collectAllOffsets(final long[] dimensions, final int[] blockSize)
	{
		return collectAllOffsets(dimensions, blockSize, block -> block);
	}

	/**
	 * Utility method to collect all blocks of specified size contained within an interval {@code [0, dimensions)}.
	 * Blocks are mapped into arbitrary object as specified by {@code func}.
	 *
	 * @param dimensions
	 * @param blockSize
	 * @param func
	 *
	 * @return
	 */
	public static <T> List<T> collectAllOffsets(final long[] dimensions, final int[] blockSize, final Function<
			long[], T> func)
	{
		return collectAllOffsets(new long[dimensions.length], Arrays.stream(dimensions).map(d -> d - 1).toArray
				(), blockSize, func);
	}

	public static List<long[]> collectAllOffsets(final long[] min, final long[] max, final int[] blockSize)
	{
		return collectAllOffsets(min, max, blockSize, block -> block);
	}

	/**
	 * Utility method to collect all blocks of specified size contained within an interval {@code [min, max]}. Blocks
	 * are mapped into arbitrary object as specified by {@code func}.
	 *
	 * @param dimensions
	 * @param blockSize
	 * @param func
	 *
	 * @return
	 */
	public static <T> List<T> collectAllOffsets(final long[] min, final long[] max, final int[] blockSize, final
	Function<long[], T> func)
	{
		final List<T> blocks = new ArrayList<>();
		final int     nDim   = min.length;
		final long[]  offset = min.clone();
		for (int d = 0; d < nDim; )
		{
			final long[] target = offset.clone();
			blocks.add(func.apply(target));
			for (d = 0; d < nDim; ++d)
			{
				offset[d] += blockSize[d];
				if (offset[d] <= max[d])
				{
					break;
				}
				else
				{
					offset[d] = 0;
				}
			}
		}
		return blocks;
	}

	/**
	 * Convert collection to array
	 *
	 * @param collection
	 * @param generator
	 * 		Array constructor
	 *
	 * @return
	 */
	public static <T> T[] asArray(final Collection<T> collection, final IntFunction<T[]> generator)
	{
		return collection.stream().toArray(generator);
	}

	/**
	 * Create {@link Interval} for a block defined by {@code min} and {@code blockSize}. For the returned interval,
	 * {@code interval.max <= intervalMax} holds true.
	 *
	 * @param min
	 * 		Top left corner of block
	 * @param intervalMax
	 * 		Maximum value for top right corner. The top right corner will be capped at this: {@code min + blockSize <=
	 * 		intervalMax}.
	 * @param blockSize
	 * 		Size of block
	 *
	 * @return {@link Interval} for a block defined by {@code min} and {@code blockSize}. For the returned interval,
	 * {@code interval.max <= intervalMax} holds true.
	 */
	public static Interval fromMin(final long[] min, final long[] intervalMax, final int[] blockSize)
	{
		final long[] max = new long[min.length];
		for (int d = 0; d < max.length; ++d)
		{
			max[d] = Math.min(min[d] + blockSize[d] - 1, intervalMax[d]);
		}
		return new FinalInterval(min, max);
	}

	public static String toString(final Interval interval)
	{
		return "(" + Point.wrap(Intervals.minAsLongArray(interval)) + " " + Point.wrap(Intervals.maxAsLongArray
				(interval)) + ")";
	}

	public static <K, V> Cache<K, V> toCacheSoftRefLoaderCache(final CacheLoader<K, V> loader)
	{
		return new SoftRefLoaderCache<K, V>().withLoader(loader);
	}

	public static <T, R> InterruptibleFunction<T, R> fromCache(final UncheckedCache<T, R> cache, final
	Interruptible<T> interruptible)
	{
		return InterruptibleFunction.fromFunctionAndInterruptible(cache::get, interruptible);
	}

	public static <T, R> InterruptibleFunction<T, R> fromCache(final Cache<T, R> cache, final Interruptible<T>
			interruptible)
	{
		return InterruptibleFunction.fromFunctionAndInterruptible(MakeUnchecked.function(cache::get), interruptible);
	}

}
