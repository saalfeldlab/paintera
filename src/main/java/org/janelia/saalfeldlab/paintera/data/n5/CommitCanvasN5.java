package org.janelia.saalfeldlab.paintera.data.n5;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
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
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.Sets;
import org.janelia.saalfeldlab.util.math.ArrayMath;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CommitCanvasN5 implements PersistCanvas
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int[] ONES_3_INT = {1, 1, 1};

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
	public void updateLabelBlockLookup(CachedCellImg<UnsignedLongType, ?> canvas, long[] blockIds) throws UnableToUpdateLabelBlockLookup
	{
		try {
			final String uniqueLabelsPath = this.dataset + "/unique-labels";
			LOG.debug("uniqueLabelsPath {}", uniqueLabelsPath);
			final DatasetSpec highestResolutionDataset = DatasetSpec.of(n5, N5Helpers.highestResolutionDataset(n5, uniqueLabelsPath, true));
			final LabelBlockLookup labelBlockLoader = N5Helpers.getLabelBlockLookup(n5, this.dataset);
			final BlockSpec highestResolutionBlockSpec = new BlockSpec(highestResolutionDataset.grid);

			// we know that it is paintera data set here
			final RandomAccessibleInterval<LabelMultisetType> highestResolutionData = LabelUtils.openVolatile(n5, this.dataset + "/data/s0");

			for (final long blockId : blockIds) {
				highestResolutionBlockSpec.fromLinearIndex(blockId);
				updateHighestResolutionLabelMapping(
						n5,
						highestResolutionDataset.dataset,
						highestResolutionDataset.attributes,
						highestResolutionBlockSpec.pos,
						Views.interval(Views.pair(canvas, highestResolutionData), highestResolutionBlockSpec.asInterval()),
						labelBlockLoader);
			}

			final String[] scaleUniqueLabels = N5Helpers.listAndSortScaleDatasets(n5, uniqueLabelsPath);

			LOG.debug("Found scale datasets {}", (Object) scaleUniqueLabels);
			for (int level = 1; level < scaleUniqueLabels.length; ++level)
			{
				final DatasetSpec datasetUniqueLabelsPrevious = DatasetSpec.of(n5, Paths.get(uniqueLabelsPath, scaleUniqueLabels[level-1]).toString());
				final DatasetSpec datasetUniqueLabels         = DatasetSpec.of(n5, Paths.get(uniqueLabelsPath, scaleUniqueLabels[level]).toString());

				final double[] targetDownsamplingFactors   = N5Helpers.getDownsamplingFactors(n5, datasetUniqueLabels.dataset);
				final double[] previousDownsamplingFactors = N5Helpers.getDownsamplingFactors(n5, datasetUniqueLabelsPrevious.dataset);
				final double[] relativeDownsamplingFactors = ArrayMath.divide3(targetDownsamplingFactors, previousDownsamplingFactors);

				final long[] affectedBlocks = org.janelia.saalfeldlab.util.grids.Grids.getRelevantBlocksInTargetGrid(
						blockIds,
						highestResolutionDataset.grid,
						datasetUniqueLabels.grid,
						targetDownsamplingFactors).toArray();

				final Scale3D targetToPrevious = new Scale3D(relativeDownsamplingFactors);

				final BlockSpec blockSpec = new BlockSpec(datasetUniqueLabels.grid);

				LOG.debug("Updating label block lookup for level {}", level);
				for (final long targetBlock : affectedBlocks)
				{
					blockSpec.fromLinearIndex(targetBlock);
					final double[] blockMinDouble = ArrayMath.asDoubleArray3(blockSpec.min);
					final double[] blockMaxDouble = ArrayMath.asDoubleArray3(ArrayMath.add3(blockSpec.max, 1));
					targetToPrevious.apply(blockMinDouble, blockMinDouble);
					targetToPrevious.apply(blockMaxDouble, blockMaxDouble);

					final long[] blockMin = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.floor3(blockMinDouble, blockMinDouble)), datasetUniqueLabelsPrevious.dimensions);
					final long[] blockMax = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.ceil3(blockMaxDouble, blockMaxDouble)), datasetUniqueLabelsPrevious.dimensions);
					final int[] size = Intervals.dimensionsAsIntArray(new FinalInterval(blockMin, blockMax));

					ArrayMath.divide3(blockMin, datasetUniqueLabelsPrevious.blockSize, blockMin);
					ArrayMath.divide3(blockMax, datasetUniqueLabelsPrevious.blockSize, blockMax);
					ArrayMath.add3(blockMax, -1, blockMax);
					ArrayMath.minOf3(blockMax, blockMin, blockMax);

					updateLabelToBlockMapping(
								n5,
								labelBlockLoader,
								datasetUniqueLabels.dataset,
								datasetUniqueLabelsPrevious.dataset,
								level,
								blockSpec.pos,
								blockSpec.min,
								blockSpec.max,
								size,
								blockMin,
								blockMax,
								ONES_3_INT);
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
	public void persistCanvas(final CachedCellImg<UnsignedLongType, ?> canvas, final long[] blocks) throws UnableToPersistCanvas {
		LOG.info("Committing canvas");
		try
		{
			final String dataset = isPainteraDataset ? this.dataset + "/data" : this.dataset;

			final CellGrid canvasGrid = canvas.getCellGrid();

			final DatasetSpec highestResolutionDataset = DatasetSpec.of(n5, N5Helpers.highestResolutionDataset(n5, dataset));
			final LabelBlockLookup labelBlockLoader = N5Helpers.getLabelBlockLookup(n5, this.dataset);

			checkLabelMultisetTypeOrFail(n5, highestResolutionDataset.dataset);

			checkGridsCompatibleOrFail(canvasGrid, highestResolutionDataset.grid);

			final BlockSpec highestResolutionBlockSpec = new BlockSpec(highestResolutionDataset.grid);

			final RandomAccessibleInterval<LabelMultisetType> highestResolutionData = LabelUtils.openVolatile(n5, highestResolutionDataset.dataset);

			LOG.debug("Persisting canvas with grid={} into background with grid={}", canvasGrid, highestResolutionDataset.grid);

			for (final long blockId : blocks)
			{
				highestResolutionBlockSpec.fromLinearIndex(blockId);
				final IntervalView<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas = Views.interval(Views.pair(highestResolutionData, canvas), highestResolutionBlockSpec.asInterval());
				final int numElements = (int) Intervals.numElements(backgroundWithCanvas);
				final byte[]             byteData  = LabelUtils.serializeLabelMultisetTypes(new BackgroundCanvasIterable(Views.flatIterable(backgroundWithCanvas)), numElements);
				final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(Intervals.dimensionsAsIntArray(backgroundWithCanvas), highestResolutionBlockSpec.pos, byteData);
				n5.writeBlock(highestResolutionDataset.dataset, highestResolutionDataset.attributes, dataBlock);
			}

			if (isMultiscale)
			{
				final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(n5, dataset);
//				final String[] scaleUniqueLabels = N5Helpers.listAndSortScaleDatasets(n5, uniqueLabelsPath);
				for (int level = 1; level < scaleDatasets.length; ++level)
				{
					final DatasetSpec targetDataset = DatasetSpec.of(n5, Paths.get(dataset, scaleDatasets[level]).toString());
					final DatasetSpec previousDataset = DatasetSpec.of(n5, Paths.get(dataset, scaleDatasets[level - 1]).toString());

//					final String datasetUniqueLabelsPrevious = Paths.get(uniqueLabelsPath, scaleUniqueLabels[level-1]).toString();
//					final String datasetUniqueLabels         = Paths.get(uniqueLabelsPath, scaleUniqueLabels[level]).toString();

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
						final int[] size = Intervals.dimensionsAsIntArray(new FinalInterval(blockMin, blockMax));

						final long[] previousRelevantIntervalMin = blockMin.clone();
						final long[] previousRelevantIntervalMax = ArrayMath.add3(blockMax, -1);

						ArrayMath.divide3(blockMin, previousDataset.blockSize, blockMin);
						ArrayMath.divide3(blockMax, previousDataset.blockSize, blockMax);
						ArrayMath.add3(blockMax, -1, blockMax);
						ArrayMath.minOf3(blockMax, blockMin, blockMax);

						downsampleVolatileLabelMultisetArrayAndSerialize(
								n5,
								targetDataset.dataset,
								targetDataset.attributes,
								Views.interval(previousData, previousRelevantIntervalMin, previousRelevantIntervalMax),
								relativeFactors,
								targetMaxNumEntries,
								size,
								blockSpec.pos);
					}

				}

			}

		} catch (final IOException | PainteraException e)
		{
			LOG.error("Unable to commit canvas.", e);
			throw new UnableToPersistCanvas("Unable to commit canvas.", e);
		}
		LOG.info("Finished commiting canvas");
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

	private static void modifyAndWrite(
			final LabelBlockLookup labelBlockLookup,
			final int level,
			final long id,
			final Consumer<Set<HashWrapper<Interval>>> modify) throws FileNotFoundException, IOException
	{
		final Set<HashWrapper<Interval>> containedIntervals = Arrays
				.stream(labelBlockLookup.read(level, id))
				.map(HashWrapper::interval)
				.collect(Collectors.toSet());
		modify.accept(containedIntervals);
		labelBlockLookup.write(level, id, containedIntervals.stream().map(HashWrapper::getData).toArray(Interval[]::new));
	}

	private static void updateHighestResolutionLabelMapping(
			final N5Writer n5,
			final String uniqueLabelsDataset,
			final DatasetAttributes uniqueLabelsAttributes,
			final long[] gridPosition,
			final RandomAccessibleInterval<Pair<UnsignedLongType, LabelMultisetType>> relevantData,
			final LabelBlockLookup labelBlockLookup) throws IOException
	{
		final TLongHashSet previousDataAsSet = readContainedLabelsSet(
				n5,
				uniqueLabelsDataset,
				uniqueLabelsAttributes,
				gridPosition
		                                                             );
		final TLongHashSet currentDataAsSet  = generateContainedLabelsSet(relevantData);
		final TLongHashSet wasAdded          = Sets.containedInFirstButNotInSecond(currentDataAsSet, previousDataAsSet);
		final TLongHashSet wasRemoved        = Sets.containedInFirstButNotInSecond(previousDataAsSet, currentDataAsSet);

		final int[] size = uniqueLabelsAttributes.getBlockSize();
		n5.writeBlock(
				uniqueLabelsDataset,
				uniqueLabelsAttributes,
				new LongArrayDataBlock(size, gridPosition, currentDataAsSet.toArray()));

		LOG.debug("was added {}", wasAdded);
		LOG.debug("was removed {}", wasRemoved);

		final HashWrapper<Interval> wrappedInterval = HashWrapper.interval(new FinalInterval(relevantData));

		for (final TLongIterator wasAddedIt = wasAdded.iterator(); wasAddedIt.hasNext(); )
		{
			modifyAndWrite(
					labelBlockLookup,
					0,
					wasAddedIt.next(),
					set -> set.add(wrappedInterval));
		}

		for (final TLongIterator wasRemovedIt = wasRemoved.iterator(); wasRemovedIt.hasNext(); )
		{
			modifyAndWrite(
					labelBlockLookup,
					0,
					wasRemovedIt.next(),
					set -> set.remove(wrappedInterval));
		}
	}

	private static void updateLabelToBlockMapping(
			final N5Writer n5,
			final LabelBlockLookup labelBlockLoader,
			final String datasetUniqueLabels,
			final String datasetUniqueLabelsPrevious,
			final int level,
			final long[] blockPositionInTargetGrid,
			final long[] blockMinInTargetGrid,
			final long[] blockMaxInTargetGrid,
			final int[] size,
			final long[] blockMin,
			final long[] blockMax,
			final int[] ones
	) throws IOException {
		final TLongHashSet mergedContainedLabels = new TLongHashSet();
		final DatasetAttributes attributesUniqueLabels = n5.getDatasetAttributes(datasetUniqueLabels);
		final DatasetAttributes attributesUniqueLabelsPrevious = n5.getDatasetAttributes(datasetUniqueLabelsPrevious);
		// TODO find better way of iterating here
		LOG.debug(
				"level={}: Fetching contained labels for previous level at {} {} {}",
				level,
				blockMin,
				blockMax,
				ones
		);
		for (final long[] offset : Grids.collectAllOffsets(blockMin, blockMax, ones))
		{
			final long[] cl = readContainedLabels(
					n5,
					datasetUniqueLabelsPrevious,
					attributesUniqueLabelsPrevious,
					offset
			);
			LOG.debug("level={}: offset={}: got contained labels: {}", level, offset, cl.length);
			mergedContainedLabels.addAll(cl);
		}
		final TLongHashSet containedLabels = readContainedLabelsSet(
				n5,
				datasetUniqueLabels,
				attributesUniqueLabels,
				blockPositionInTargetGrid
		);
		n5.writeBlock(
				datasetUniqueLabels,
				attributesUniqueLabels,
				new LongArrayDataBlock(size,
						blockPositionInTargetGrid,
						mergedContainedLabels.toArray()
				)
		);

		final TLongHashSet wasAdded   = Sets.containedInFirstButNotInSecond(
				mergedContainedLabels,
				containedLabels);
		final TLongHashSet wasRemoved = Sets.containedInFirstButNotInSecond(
				containedLabels,
				mergedContainedLabels);

		LOG.debug(
				"level={}: Updating label to block mapping for {}. Added:   {}",
				level,
				blockMinInTargetGrid,
				wasAdded.size()
		);
		LOG.debug(
				"level={}: Updating label to block mapping for {}. Removed: {}",
				level,
				blockMinInTargetGrid,
				wasRemoved.size()
		);

		final HashWrapper<Interval> wrappedInterval = HashWrapper.interval(new FinalInterval(
				blockMinInTargetGrid,
				blockMaxInTargetGrid
		));

		for (final TLongIterator wasAddedIt = wasAdded.iterator(); wasAddedIt.hasNext(); )
		{
			modifyAndWrite(
					labelBlockLoader,
					level,
					wasAddedIt.next(),
					set -> set.add(wrappedInterval)
			);
		}

		for (final TLongIterator wasRemovedIt = wasRemoved.iterator(); wasRemovedIt.hasNext(); )
		{
			modifyAndWrite(
					labelBlockLoader,
					level,
					wasRemovedIt.next(),
					set -> set.remove(wrappedInterval)
			);
		}
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

	private static void downsampleVolatileLabelMultisetArrayAndSerialize(
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
	}

}
