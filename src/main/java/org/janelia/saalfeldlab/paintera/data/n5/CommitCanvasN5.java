package org.janelia.saalfeldlab.paintera.data.n5;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.label.LabelMultisetTypeDownscaler;
import net.imglib2.type.label.LabelUtils;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.ByteArrayDataBlock;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToPersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToUpdateLabelBlockLookup;
import org.janelia.saalfeldlab.paintera.exception.PainteraException;
import org.janelia.saalfeldlab.util.math.ArrayMath;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class CommitCanvasN5 implements PersistCanvas
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final N5Writer n5;

	private final String dataset;

	private final boolean isPainteraDataset;

	private final boolean isMultiscale;

	public CommitCanvasN5(final N5Writer n5, final String dataset) throws IOException {
		super();
		this.n5 = n5;
		this.dataset = dataset;
		this.isPainteraDataset = N5Helpers.isPainteraDataset(this.n5, this.dataset);
		this.isMultiscale = N5Helpers.isMultiScale(this.n5, this.isPainteraDataset ? this.dataset + "/data" : this.dataset);
	}

	public final N5Writer n5()
	{
		return this.n5;
	}

	public final String dataset()
	{
		return this.dataset;
	}

	@Override
	public boolean supportsLabelBlockLookupUpdate()
	{
		return isPainteraDataset;
	}

	@Override
	public void updateLabelBlockLookup(final List<TLongObjectMap<BlockDiff>> blockDiffsByLevel) throws UnableToUpdateLabelBlockLookup
	{
		LOG.debug("Updating label block lookup with {}", blockDiffsByLevel);
		try {
			final String uniqueLabelsPath = this.dataset + "/unique-labels";
			LOG.debug("uniqueLabelsPath {}", uniqueLabelsPath);

			final LabelBlockLookup labelBlockLoader = N5Helpers.getLabelBlockLookup(n5, this.dataset);

			final String[] scaleUniqueLabels = N5Helpers.listAndSortScaleDatasets(n5, uniqueLabelsPath);

			LOG.debug("Found scale datasets {}", (Object) scaleUniqueLabels);
			for (int level = 0; level < scaleUniqueLabels.length; ++level)
			{
				final DatasetSpec datasetUniqueLabels = DatasetSpec.of(n5, Paths.get(uniqueLabelsPath, scaleUniqueLabels[level]).toString());
				final TLongObjectMap<TLongHashSet> removedById = new TLongObjectHashMap<>();
				final TLongObjectMap<TLongHashSet> addedById = new TLongObjectHashMap<>();
				final TLongObjectMap<BlockDiff> blockDiffs = blockDiffsByLevel.get(level);
				final BlockSpec blockSpec = new BlockSpec(datasetUniqueLabels.grid);

				for (final TLongObjectIterator<BlockDiff> blockDiffIt = blockDiffs.iterator(); blockDiffIt.hasNext(); )
				{
					blockDiffIt.advance();
					final long blockId = blockDiffIt.key();
					final BlockDiff blockDiff = blockDiffIt.value();

					blockSpec.fromLinearIndex(blockId);

					LOG.trace("Unique labels for block ({}: {} {}): {}", blockId, blockSpec.min, blockSpec.max, blockDiff);

					n5.writeBlock(
							datasetUniqueLabels.dataset,
							datasetUniqueLabels.attributes,
							new LongArrayDataBlock(
									Intervals.dimensionsAsIntArray(new FinalInterval(blockSpec.min, blockSpec.max)),
									blockSpec.pos,
									blockDiff.getNewUniqueIds()));

					final long[] removedInBlock = blockDiff.getRemovedIds();
					final long[] addedInBlock = blockDiff.getAddedIds();

					for (final long removed : removedInBlock)
						computeIfAbsent(removedById, removed, TLongHashSet::new).add(blockId);

					for (final long added : addedInBlock)
						computeIfAbsent(addedById, added, TLongHashSet::new).add(blockId);

				}

				final TLongSet modifiedIds = new TLongHashSet();
				modifiedIds.addAll(removedById.keySet());
				modifiedIds.addAll(addedById.keySet());
				LOG.debug("Removed by id: {}", removedById);
				LOG.debug("Added by id: {}", addedById);
				for (final long modifiedId : modifiedIds.toArray())
				{
					final Interval[] blockList = labelBlockLoader.read(level, modifiedId);
					final TLongSet blockListLinearIndices = new TLongHashSet();
					for (final Interval block : blockList)
					{
						blockSpec.fromInterval(block);
						blockListLinearIndices.add(blockSpec.asLinearIndex());
					}

					final TLongSet removed = removedById.get(modifiedId);
					final TLongSet added = addedById.get(modifiedId);

					LOG.debug("Removed for id {}: {}", modifiedId, removed);
					LOG.debug("Added for id {}: {}", modifiedId, added);

					if (removed != null)
						blockListLinearIndices.removeAll(removed);

					if (added != null)
						blockListLinearIndices.addAll(added);

					final Interval[] updatedIntervals = new Interval[blockListLinearIndices.size()];
					final TLongIterator blockIt = blockListLinearIndices.iterator();
					for (int index = 0; blockIt.hasNext(); ++index)
					{
						final long blockId = blockIt.next();
						blockSpec.fromLinearIndex(blockId);
						final Interval interval = blockSpec.asInterval();
						updatedIntervals[index] = interval;
						LOG.trace("Added interval {} for linear index {} and block spec {}", interval, blockId, blockSpec);
					}
					labelBlockLoader.write(level, modifiedId, updatedIntervals);
				}

			}


		}
		catch (IOException e)
		{
			throw new UnableToUpdateLabelBlockLookup("Unable to update label block lookup for " + this.dataset, e);
		}
		LOG.info("Finished updating label-block-lookup");
	}

	@Override
	public List<TLongObjectMap<BlockDiff>> persistCanvas(final CachedCellImg<UnsignedLongType, ?> canvas, final long[] blocks) throws UnableToPersistCanvas {
		LOG.info("Committing canvas: {} blocks", blocks.length);
		LOG.debug("Affected blocks in grid {}: {}", canvas.getCellGrid(), blocks);
		try
		{
			final String dataset = isPainteraDataset ? this.dataset + "/data" : this.dataset;

			final CellGrid canvasGrid = canvas.getCellGrid();

			final DatasetSpec highestResolutionDataset = DatasetSpec.of(n5, N5Helpers.highestResolutionDataset(n5, dataset));

			checkLabelMultisetTypeOrFail(n5, highestResolutionDataset.dataset);

			checkGridsCompatibleOrFail(canvasGrid, highestResolutionDataset.grid);

			final BlockSpec highestResolutionBlockSpec = new BlockSpec(highestResolutionDataset.grid);

			final RandomAccessibleInterval<LabelMultisetType> highestResolutionData = LabelUtils.openVolatile(n5, highestResolutionDataset.dataset);

			LOG.debug("Persisting canvas with grid={} into background with grid={}", canvasGrid, highestResolutionDataset.grid);

			final List<TLongObjectMap<BlockDiff>> blockDiffs = new ArrayList<>();
			final TLongObjectHashMap<BlockDiff> blockDiffsAtHighestLevel = new TLongObjectHashMap<>();
			blockDiffs.add(blockDiffsAtHighestLevel);

			for (final long blockId : blocks)
			{
				highestResolutionBlockSpec.fromLinearIndex(blockId);
				final IntervalView<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas = Views.interval(Views.pair(highestResolutionData, canvas), highestResolutionBlockSpec.asInterval());
				final int numElements = (int) Intervals.numElements(backgroundWithCanvas);
				final byte[]             byteData  = LabelUtils.serializeLabelMultisetTypes(new BackgroundCanvasIterable(Views.flatIterable(backgroundWithCanvas)), numElements);
				final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(Intervals.dimensionsAsIntArray(backgroundWithCanvas), highestResolutionBlockSpec.pos, byteData);
				n5.writeBlock(highestResolutionDataset.dataset, highestResolutionDataset.attributes, dataBlock);
				blockDiffsAtHighestLevel.put(blockId, createBlockDiffFromCanvas(backgroundWithCanvas));
			}

			if (isMultiscale)
			{
				final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(n5, dataset);
				for (int level = 1; level < scaleDatasets.length; ++level)
				{

					final TLongObjectHashMap<BlockDiff> blockDiffsAt = new TLongObjectHashMap<>();
					blockDiffs.add(blockDiffsAt);
					final DatasetSpec targetDataset = DatasetSpec.of(n5, Paths.get(dataset, scaleDatasets[level]).toString());
					final DatasetSpec previousDataset = DatasetSpec.of(n5, Paths.get(dataset, scaleDatasets[level - 1]).toString());

					final double[] targetDownsamplingFactors   = N5Helpers.getDownsamplingFactors(n5, targetDataset.dataset);
					final double[] previousDownsamplingFactors = N5Helpers.getDownsamplingFactors(n5, previousDataset.dataset);
					final double[] relativeDownsamplingFactors = ArrayMath.divide3(targetDownsamplingFactors, previousDownsamplingFactors);

					final long[] affectedBlocks = org.janelia.saalfeldlab.util.grids.Grids.getRelevantBlocksInTargetGrid(
							blocks,
							highestResolutionDataset.grid,
							targetDataset.grid,
							targetDownsamplingFactors).toArray();
                    LOG.debug("Affected blocks at higher level: {}", affectedBlocks);

					final CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray> previousData = LabelUtils.openVolatile(n5, previousDataset.dataset);

					final Scale3D targetToPrevious = new Scale3D(relativeDownsamplingFactors);

					final int targetMaxNumEntries = N5Helpers.getIntegerAttribute(n5, targetDataset.dataset, N5Helpers.MAX_NUM_ENTRIES_KEY, -1);

					final int[] relativeFactors = ArrayMath.asInt3(relativeDownsamplingFactors, true);

					LOG.debug("level={}: Got {} blocks", level, affectedBlocks.length);

					final BlockSpec blockSpec = new BlockSpec(targetDataset.grid);

					for (final long targetBlock : affectedBlocks)
					{
						blockSpec.fromLinearIndex(targetBlock);
						final double[] blockMinDouble = ArrayMath.asDoubleArray3(blockSpec.min);
						final double[] blockMaxDouble = ArrayMath.asDoubleArray3(ArrayMath.add3(blockSpec.max, 1));
						targetToPrevious.apply(blockMinDouble, blockMinDouble);
						targetToPrevious.apply(blockMaxDouble, blockMaxDouble);

						LOG.debug("level={}: blockMinDouble={} blockMaxDouble={}", level, blockMinDouble, blockMaxDouble);

						final long[] blockMin = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.floor3(blockMinDouble, blockMinDouble)), previousDataset.dimensions);
						final long[] blockMax = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.ceil3(blockMaxDouble, blockMaxDouble)), previousDataset.dimensions);
						final int[] size = Intervals.dimensionsAsIntArray(new FinalInterval(blockSpec.min, blockSpec.max));

						final long[] previousRelevantIntervalMin = blockMin.clone();
						final long[] previousRelevantIntervalMax = ArrayMath.add3(blockMax, -1);

						ArrayMath.divide3(blockMin, previousDataset.blockSize, blockMin);
						ArrayMath.divide3(blockMax, previousDataset.blockSize, blockMax);
						ArrayMath.add3(blockMax, -1, blockMax);
						ArrayMath.minOf3(blockMax, blockMin, blockMax);

						LOG.trace("Reading old access at position {} and size {}. ({} {})", blockSpec.pos, size, blockSpec.min, blockSpec.max);
						VolatileLabelMultisetArray oldAccess = LabelUtils.fromBytes(
								(byte[]) n5.readBlock(targetDataset.dataset, targetDataset.attributes, blockSpec.pos).getData(),
								(int) Intervals.numElements(size));

						VolatileLabelMultisetArray newAccess = downsampleVolatileLabelMultisetArrayAndSerialize(
								n5,
								targetDataset.dataset,
								targetDataset.attributes,
								Views.interval(previousData, previousRelevantIntervalMin, previousRelevantIntervalMax),
								relativeFactors,
								targetMaxNumEntries,
								size,
								blockSpec.pos);
						blockDiffsAt.put(targetBlock, createBlockDiff(oldAccess, newAccess, (int) Intervals.numElements(size)));
					}

				}

			}
			LOG.info("Finished commiting canvas");
			return blockDiffs;

		} catch (final IOException | PainteraException e)
		{
			LOG.error("Unable to commit canvas.", e);
			throw new UnableToPersistCanvas("Unable to commit canvas.", e);
		}
	}

	private static long[] readContainedLabels(
			final N5Reader n5,
			final String uniqueLabelsDataset,
			final DatasetAttributes uniqueLabelsAttributes,
			final long[] gridPosition) throws IOException
	{
		final long[] previousData = Optional
				.ofNullable(n5.readBlock(uniqueLabelsDataset, uniqueLabelsAttributes, gridPosition))
				.map(b -> (LongArrayDataBlock) b)
				.map(LongArrayDataBlock::getData)
				.orElse(new long[] {});
		return previousData;
	}

	private static TLongHashSet readContainedLabelsSet(
			final N5Reader n5,
			final String uniqueLabelsDataset,
			final DatasetAttributes uniqueLabelsAttributes,
			final long[] gridPosition) throws IOException
	{
		final long[] previousData = readContainedLabels(n5, uniqueLabelsDataset, uniqueLabelsAttributes, gridPosition);
		return new TLongHashSet(previousData);
	}

	private static TLongHashSet generateContainedLabelsSet(
			final RandomAccessibleInterval<Pair<UnsignedLongType, LabelMultisetType>> relevantData)
	{
		final TLongHashSet currentDataAsSet = new TLongHashSet();
		for (final Pair<UnsignedLongType, LabelMultisetType> p : Views.iterable(relevantData))
		{
			final UnsignedLongType  pa  = p.getA();
			final LabelMultisetType pb  = p.getB();
			final long              pav = pa.getIntegerLong();
			if (pav == Label.INVALID)
			{
				pb
						.entrySet()
						.stream()
						.map(Entry::getElement)
						.mapToLong(Label::id)
						.forEach(currentDataAsSet::add);
			}
			else
			{
				currentDataAsSet.add(pav);
			}
		}
		return currentDataAsSet;
	}

	private static void checkLabelMultisetTypeOrFail(
			final N5Reader n5,
			final String dataset
	) throws IOException {

		LOG.debug("Checking if dataset {} is label multiset type.", dataset);
		if (!N5Helpers.getBooleanAttribute(n5, dataset, N5Helpers.LABEL_MULTISETTYPE_KEY, false))
		{
			throw new RuntimeException("Only label multiset type accepted currently!");
		}
	}

	private static void checkGridsCompatibleOrFail(
			final CellGrid canvasGrid,
			final CellGrid highestResolutionGrid
	)
	{


		if (!highestResolutionGrid.equals(canvasGrid))
		{
			LOG.error(
					"Canvas grid {} and highest resolution dataset grid {} incompatible!",
					canvasGrid,
					highestResolutionGrid
			);
			throw new RuntimeException(String.format(
					"Canvas grid %s and highest resolution dataset grid %s incompatible!",
					canvasGrid,
					highestResolutionGrid
			));
		}
	}

	private static VolatileLabelMultisetArray downsampleVolatileLabelMultisetArrayAndSerialize(
			final N5Writer n5,
			final String dataset,
			final DatasetAttributes attributes,
			final RandomAccessibleInterval<LabelMultisetType> data,
			final int[] relativeFactors,
			final int maxNumEntries,
			final int[] size,
			final long[] blockPosition
	) throws IOException {
		final VolatileLabelMultisetArray updatedAccess = LabelMultisetTypeDownscaler.createDownscaledCell(
				Views.isZeroMin(data) ? data : Views.zeroMin(data),
				relativeFactors,
				maxNumEntries
		);

		final byte[] serializedAccess = new byte[LabelMultisetTypeDownscaler
				.getSerializedVolatileLabelMultisetArraySize(
						updatedAccess)];
		LabelMultisetTypeDownscaler.serializeVolatileLabelMultisetArray(
				updatedAccess,
				serializedAccess
		);

		n5.writeBlock(
				dataset,
				attributes,
				new ByteArrayDataBlock(size, blockPosition, serializedAccess)
		);
		return updatedAccess;
	}

	private static BlockDiff createBlockDiffFromCanvas(final Iterable<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas)
	{
		return createBlockDiffFromCanvas(backgroundWithCanvas, new BlockDiff());
	}

	private static BlockDiff createBlockDiffFromCanvas(
			final Iterable<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas,
			final BlockDiff blockDiff)
	{
		for (final Pair<LabelMultisetType, UnsignedLongType> p : backgroundWithCanvas)
		{
			final long newLabel = p.getB().getIntegerLong();
			if (newLabel == Label.INVALID)
			{
				for (Entry<Label> entry : p.getA().entrySet())
				{
					final long id = entry.getElement().id();
					blockDiff.addToOldUniqueLabels(id);
					blockDiff.addToNewUniqueLabels(id);
				}
			}
			else
			{
				for (Entry<Label> entry : p.getA().entrySet())
				{
					final long id = entry.getElement().id();
					blockDiff.addToOldUniqueLabels(id);
				}
				blockDiff.addToNewUniqueLabels(newLabel);
			}
		}
		return blockDiff;
	}

	private static BlockDiff createBlockDiff(
			final VolatileLabelMultisetArray oldAccess,
			final VolatileLabelMultisetArray newAccess,
			final int numElements)
	{
		return createBlockDiff(oldAccess, newAccess, numElements, new BlockDiff());
	}

	private static BlockDiff createBlockDiff(
			final VolatileLabelMultisetArray oldAccess,
			final VolatileLabelMultisetArray newAccess,
			final int numElements,
			final BlockDiff blockDiff)
	{
		ArrayImg<LabelMultisetType, VolatileLabelMultisetArray> oldImg = new ArrayImg<>(oldAccess, new long[]{numElements}, new LabelMultisetType().getEntitiesPerPixel());
		ArrayImg<LabelMultisetType, VolatileLabelMultisetArray> newImg = new ArrayImg<>(newAccess, new long[]{numElements}, new LabelMultisetType().getEntitiesPerPixel());
		oldImg.setLinkedType(new LabelMultisetType(oldImg));
		newImg.setLinkedType(new LabelMultisetType(newImg));
		return createBlockDiff(oldImg, newImg, blockDiff);
	}

	private static BlockDiff createBlockDiff(
			final Iterable<LabelMultisetType> oldLabels,
			final Iterable<LabelMultisetType> newLabels,
			final BlockDiff blockDiff)
	{
		for (final Iterator<LabelMultisetType> oldIterator = oldLabels.iterator(), newIterator = newLabels.iterator(); oldIterator.hasNext();)
		{
			for (Entry<Label> entry : oldIterator.next().entrySet())
				blockDiff.addToOldUniqueLabels(entry.getElement().id());

			for (Entry<Label> entry : newIterator.next().entrySet())
				blockDiff.addToNewUniqueLabels(entry.getElement().id());

		}
		return blockDiff;
	}

	private static <T> T computeIfAbsent(TLongObjectMap<T> map, long key, Supplier<T> fallback)
	{
		T t = map.get(key);
		if (t == null)
		{
			t = fallback.get();
			map.put(key, t);
		}
		return t;
	}

}
