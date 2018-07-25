package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.cache.CacheLoader;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlocksForLabelCacheLoader<T> implements CacheLoader<T, Interval[]>, Interruptible<T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final CellGrid grid;

	private final InterruptibleFunction<T, Interval[]> getRelevantIntervalsFromLowerResolution;

	private final Function<Interval, List<Interval>> getRelevantBlocksIntersectingWithLowResInterval;

	private final Function<long[], long[]> getUniqueLabelListForBlock;

	private final BiPredicate<T, long[]> checkIfLabelsAreContained;

	private final List<Consumer<T>> interruptionListeners = new ArrayList<>();

	/**
	 * @param grid
	 * @param getRelevantIntervalsFromLowerResolution
	 * 		Get all blocks in lower resolution that contain requested labels. Blocks are defined by min and max in the
	 * 		lower resolution coordinate system.
	 * @param getRelevantBlocksIntersectingWithLowResInterval
	 * 		for a block defined by min and max in lower resolution coordinate system, find all blocks that intersect
	 * 		with
	 * 		it at this resolution.
	 * @param getUniqueLabelListForBlock
	 * 		Given a block for this resolution defined by its position in the cell grid, retrieve a unique list of
	 * 		labels
	 * 		present in this block.
	 * @param es
	 * 		{@link ExecutorService} for parallel execution of retrieval of lists of unique labels. The task is
	 * 		parallelized
	 * 		over blocks.
	 */
	public BlocksForLabelCacheLoader(
			final CellGrid grid,
			final InterruptibleFunction<T, Interval[]> getRelevantIntervalsFromLowerResolution,
			final Function<Interval, List<Interval>> getRelevantBlocksIntersectingWithLowResInterval,
			final Function<long[], long[]> getUniqueLabelListForBlock,
			final BiPredicate<T, long[]> checkIfLabelsAreContained)
	{
		super();
		this.grid = grid;
		this.getRelevantIntervalsFromLowerResolution = getRelevantIntervalsFromLowerResolution;
		this.getRelevantBlocksIntersectingWithLowResInterval = getRelevantBlocksIntersectingWithLowResInterval;
		this.getUniqueLabelListForBlock = getUniqueLabelListForBlock;
		this.checkIfLabelsAreContained = checkIfLabelsAreContained;
	}

	public static BlocksForLabelCacheLoader<Long> longKeys(
			final CellGrid grid,
			final InterruptibleFunction<Long, Interval[]> getRelevantIntervalsFromLowerResolution,
			final Function<Interval, List<Interval>> getRelevantBlocksIntersectingWithLowResInterval,
			final Function<long[], long[]> getUniqueLabelListForBlock)
	{
		return new BlocksForLabelCacheLoader<>(
				grid,
				getRelevantIntervalsFromLowerResolution,
				getRelevantBlocksIntersectingWithLowResInterval,
				getUniqueLabelListForBlock,
				(id, block) -> Arrays.stream(block).filter(b -> b == id).count() > 0
		);
	}

	public static BlocksForLabelCacheLoader<TLongHashSet> hashSetKeys(
			final CellGrid grid,
			final InterruptibleFunction<TLongHashSet, Interval[]> getRelevantIntervalsFromLowerResolution,
			final Function<Interval, List<Interval>> getRelevantBlocksIntersectingWithLowResInterval,
			final Function<long[], long[]> getUniqueLabelListForBlock)
	{
		return new BlocksForLabelCacheLoader<>(
				grid,
				getRelevantIntervalsFromLowerResolution,
				getRelevantBlocksIntersectingWithLowResInterval,
				getUniqueLabelListForBlock,
				(ids, block) -> Arrays.stream(block).filter(ids::contains).count() > 0
		);
	}

	@Override
	public Interval[] get(final T key) throws Exception
	{
		final boolean[] isInterrupted = {false};
		final Consumer<T> listener = interruptedKey -> {
			if (interruptedKey.equals(key))
			{
				isInterrupted[0] = true;
				this.getRelevantIntervalsFromLowerResolution.interruptFor(key);
			}
		};
		synchronized (this.interruptionListeners)
		{
			this.interruptionListeners.add(listener);
		}

		try
		{
			final Interval[]                     relevantLowResBlocks = getRelevantIntervalsFromLowerResolution.apply(
					key);
			final HashSet<HashWrapper<Interval>> blocks               = new HashSet<>();
			Arrays
					.stream(relevantLowResBlocks)
					.map(getRelevantBlocksIntersectingWithLowResInterval::apply)
					.flatMap(List::stream)
					.map(HashWrapper::interval)
					.forEach(blocks::add);
			LOG.debug("key={} grid={} -- got {} block candidates", key, grid, blocks.size());

			final List<Interval> results = new ArrayList<>();
			for (final Iterator<HashWrapper<Interval>> blockIt = blocks.iterator(); blockIt.hasNext() &&
					!isInterrupted[0]; )
			{
				final HashWrapper<Interval> block   = blockIt.next();
				final long[]                cellPos = new long[grid.numDimensions()];
				grid.getCellPosition(Intervals.minAsLongArray(block.getData()), cellPos);
				final long[] uniqueLabels = getUniqueLabelListForBlock.apply(cellPos);
				LOG.trace("key={} grid ={} -- Unique labels: {}", key, grid, uniqueLabels);
				if (checkIfLabelsAreContained.test(key, uniqueLabels))
				{
					results.add(block.getData());
				}
			}
			LOG.debug("key={} grid={} -- still {} blocks after filtering", key, grid, results.size());
			return isInterrupted[0] ? null : results.toArray(new Interval[results.size()]);
		} finally
		{
			synchronized (this.interruptionListeners)
			{
				this.interruptionListeners.remove(listener);
			}
		}
	}

	private static List<String> toString(final Interval[] intervals)
	{
		final List<String> strings = Arrays
				.stream(intervals)
				.map(ival -> String.format(
						"(%s %s)",
						Point.wrap(Intervals.minAsLongArray(ival)),
						Point.wrap(Intervals.maxAsLongArray(ival))
				                          ))
				.collect(Collectors.toList());
		return strings;

	}

	private static List<Interval> doubleStep(final Interval interval)
	{
		final long[] min = Intervals.minAsLongArray(interval);
		return Arrays.asList(
				new FinalInterval(
						Arrays.stream(min).map(m -> m * 2 + 0).toArray(),
						Arrays.stream(min).map(m -> m * 2 + 1).toArray()
				),
				new FinalInterval(
						Arrays.stream(min).map(m -> m * 2 + 2).toArray(),
						Arrays.stream(min).map(m -> m * 2 + 3).toArray()
				)
		                    );
	}

	public static String toString(final Collection<HashWrapper<Interval>> list)
	{
		return list
				.stream()
				.map(HashWrapper::getData)
				.map(i -> "(" + Point.wrap(Intervals.minAsLongArray(i)) + " " + Point.wrap(Intervals.maxAsLongArray(i)
				                                                                          ) + ")")
				.collect(Collectors.toList()).toString();
	}

	@Override
	public void interruptFor(final T t)
	{
		synchronized (this.interruptionListeners)
		{
			this.interruptionListeners.forEach(l -> l.accept(t));
		}
	}

}
