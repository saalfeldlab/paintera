package org.janelia.saalfeldlab.paintera.data.n5;

import com.pivovarit.function.ThrowingSupplier;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetTypeDownscaler;
import net.imglib2.type.label.LabelUtils;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey;
import org.janelia.saalfeldlab.labels.blocks.n5.IsRelativeToContainer;
import org.janelia.saalfeldlab.labels.downsample.WinnerTakesAll;
import org.janelia.saalfeldlab.n5.ByteArrayDataBlock;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToPersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToUpdateLabelBlockLookup;
import org.janelia.saalfeldlab.paintera.exception.PainteraException;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
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

public class CommitCanvasN5 implements PersistCanvas {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final N5Writer n5Writer;

  private final String dataset;

  private final MetadataState metadataState;

  private final boolean isPainteraDataset;

  private final boolean isMultiscale;

  private final boolean isLabelMultiset;

  private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress", 0.0);

  public CommitCanvasN5(final MetadataState metadataState) throws IOException {

	super();
	this.metadataState = metadataState;
	this.n5Writer = metadataState.getWriter();
	this.dataset = metadataState.getGroup();
	this.isPainteraDataset = N5Helpers.isPainteraDataset(this.n5Writer, this.dataset);
	final String volumetricDataGroup = this.isPainteraDataset ? this.dataset + "/data" : this.dataset;
	this.isMultiscale = N5Helpers.isMultiScale(this.n5Writer, volumetricDataGroup);
	this.isLabelMultiset = N5Helpers.getBooleanAttribute(
			this.n5Writer,
			this.isMultiscale ? N5Helpers.getFinestLevelJoinWithGroup(n5Writer, volumetricDataGroup) : volumetricDataGroup,
			N5Helpers.IS_LABEL_MULTISET_KEY,
			false);
  }

  public final N5Writer n5() {

	return this.n5Writer;
  }

  public final String dataset() {

	return this.dataset;
  }

  @Override
  public boolean supportsLabelBlockLookupUpdate() {

	return isPainteraDataset;
  }

  @Override
  public void updateLabelBlockLookup(final List<TLongObjectMap<BlockDiff>> blockDiffsByLevel) throws UnableToUpdateLabelBlockLookup {

	LOG.debug("Updating label block lookup with {}", blockDiffsByLevel);
	try {
	  final String uniqueLabelsPath = this.dataset + "/unique-labels";
	  LOG.debug("uniqueLabelsPath {}", uniqueLabelsPath);

	  final LabelBlockLookup labelBlockLoader = ThrowingSupplier.unchecked(() -> N5Helpers.getLabelBlockLookup(metadataState)).get();
	  if (labelBlockLoader instanceof IsRelativeToContainer)
		((IsRelativeToContainer)labelBlockLoader).setRelativeTo(n5Writer, this.dataset);

	  final String[] scaleUniqueLabels = N5Helpers.listAndSortScaleDatasets(n5Writer, uniqueLabelsPath);

	  LOG.debug("Found scale datasets {}", (Object)scaleUniqueLabels);
	  for (int level = 0; level < scaleUniqueLabels.length; ++level) {
		final DatasetSpec datasetUniqueLabels = DatasetSpec.of(n5Writer, Paths.get(uniqueLabelsPath, scaleUniqueLabels[level]).toString());
		final TLongObjectMap<TLongHashSet> removedById = new TLongObjectHashMap<>();
		final TLongObjectMap<TLongHashSet> addedById = new TLongObjectHashMap<>();
		final TLongObjectMap<BlockDiff> blockDiffs = blockDiffsByLevel.get(level);
		final BlockSpec blockSpec = new BlockSpec(datasetUniqueLabels.grid);

		for (final TLongObjectIterator<BlockDiff> blockDiffIt = blockDiffs.iterator(); blockDiffIt.hasNext(); ) {
		  blockDiffIt.advance();
		  final long blockId = blockDiffIt.key();
		  final BlockDiff blockDiff = blockDiffIt.value();

		  blockSpec.fromLinearIndex(blockId);

		  LOG.trace("Unique labels for block ({}: {} {}): {}", blockId, blockSpec.min, blockSpec.max, blockDiff);

		  n5Writer.writeBlock(
				  datasetUniqueLabels.dataset,
				  datasetUniqueLabels.attributes,
				  new LongArrayDataBlock(
						  Intervals.dimensionsAsIntArray(new FinalInterval(blockSpec.min, blockSpec.max)),
						  blockSpec.pos,
						  blockDiff.getNewUniqueIds()));

		  final long[] removedInBlock = blockDiff.getRemovedIds();
		  final long[] addedInBlock = blockDiff.getAddedIds();

		  for (final long removed : removedInBlock) {
			computeIfAbsent(removedById, removed, TLongHashSet::new).add(blockId);
		  }

		  for (final long added : addedInBlock) {
			computeIfAbsent(addedById, added, TLongHashSet::new).add(blockId);
		  }

		}

		final TLongSet modifiedIds = new TLongHashSet();
		modifiedIds.addAll(removedById.keySet());
		modifiedIds.addAll(addedById.keySet());
		LOG.debug("Removed by id: {}", removedById);
		LOG.debug("Added by id: {}", addedById);
		for (final long modifiedId : modifiedIds.toArray()) {
		  final Interval[] blockList = labelBlockLoader.read(new LabelBlockLookupKey(level, modifiedId));
		  final TLongSet blockListLinearIndices = new TLongHashSet();
		  for (final Interval block : blockList) {
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
		  for (int index = 0; blockIt.hasNext(); ++index) {
			final long blockId = blockIt.next();
			blockSpec.fromLinearIndex(blockId);
			final Interval interval = blockSpec.asInterval();
			updatedIntervals[index] = interval;
			LOG.trace("Added interval {} for linear index {} and block spec {}", interval, blockId, blockSpec);
		  }
		  labelBlockLoader.write(new LabelBlockLookupKey(level, modifiedId), updatedIntervals);
		}

	  }

	} catch (final IOException e) {
	  throw new UnableToUpdateLabelBlockLookup("Unable to update label block lookup for " + this.dataset, e);
	}
	LOG.info("Finished updating label-block-lookup");
  }

  @Override
  public List<TLongObjectMap<BlockDiff>> persistCanvas(final CachedCellImg<UnsignedLongType, ?> canvas, final long[] blocks) throws UnableToPersistCanvas {

	LOG.info("Committing canvas: {} blocks", blocks.length);
	LOG.debug("Affected blocks in grid {}: {}", canvas.getCellGrid(), blocks);
	InvokeOnJavaFXApplicationThread.invoke(() -> progress.set(0.1));
	try {
	  final String dataset = isPainteraDataset ? this.dataset + "/data" : this.dataset;

	  final CellGrid canvasGrid = canvas.getCellGrid();

	  final DatasetSpec highestResolutionDataset = DatasetSpec.of(n5Writer, this.isMultiscale ? N5Helpers.getFinestLevelJoinWithGroup(n5Writer, dataset) : dataset);

	  if (this.isLabelMultiset)
		checkLabelMultisetTypeOrFail(n5Writer, highestResolutionDataset.dataset);

	  checkGridsCompatibleOrFail(canvasGrid, highestResolutionDataset.grid);

	  final BlockSpec highestResolutionBlockSpec = new BlockSpec(highestResolutionDataset.grid);

	  LOG.debug("Persisting canvas with grid={} into background with grid={}", canvasGrid, highestResolutionDataset.grid);

	  final List<TLongObjectMap<BlockDiff>> blockDiffs = new ArrayList<>();
	  final TLongObjectHashMap<BlockDiff> blockDiffsAtHighestLevel = new TLongObjectHashMap<>();
	  blockDiffs.add(blockDiffsAtHighestLevel);

	  /* Writer the highest resolution first*/
	  if (this.isLabelMultiset)
		writeBlocksLabelMultisetType(canvas, blocks, highestResolutionDataset, highestResolutionBlockSpec, blockDiffsAtHighestLevel);
	  else {
		writeBlocksLabelIntegerType(canvas, blocks, highestResolutionDataset, highestResolutionBlockSpec, blockDiffsAtHighestLevel);
	  }

	  InvokeOnJavaFXApplicationThread.invoke(() -> progress.set(0.4));

	  /* If multiscale, downscale and write the lower scales*/
	  if (isMultiscale) {
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(n5Writer, dataset);

		for (int level = 1; level < scaleDatasets.length; ++level) {

		  final TLongObjectHashMap<BlockDiff> blockDiffsAt = new TLongObjectHashMap<>();
		  blockDiffs.add(blockDiffsAt);
		  final DatasetSpec targetDataset = DatasetSpec.of(n5Writer, Paths.get(dataset, scaleDatasets[level]).toString());
		  final DatasetSpec previousDataset = DatasetSpec.of(n5Writer, Paths.get(dataset, scaleDatasets[level - 1]).toString());

		  final double[] targetDownsamplingFactors = N5Helpers.getDownsamplingFactors(n5Writer, targetDataset.dataset);
		  final double[] previousDownsamplingFactors = N5Helpers.getDownsamplingFactors(n5Writer, previousDataset.dataset);
		  final double[] relativeDownsamplingFactors = ArrayMath.divide3(targetDownsamplingFactors, previousDownsamplingFactors);

		  final long[] affectedBlocks = org.janelia.saalfeldlab.util.grids.Grids.getRelevantBlocksInTargetGrid(
				  blocks,
				  highestResolutionDataset.grid,
				  targetDataset.grid,
				  targetDownsamplingFactors).toArray();
		  LOG.debug("Affected blocks at higher level: {}", affectedBlocks);

		  final Scale3D targetToPrevious = new Scale3D(relativeDownsamplingFactors);

		  final int targetMaxNumEntries = N5Helpers.getIntegerAttribute(n5Writer, targetDataset.dataset, N5Helpers.MAX_NUM_ENTRIES_KEY, -1);

		  final int[] relativeFactors = ArrayMath.asInt3(relativeDownsamplingFactors, true);

		  LOG.debug("level={}: Got {} blocks", level, affectedBlocks.length);

		  final BlockSpec blockSpec = new BlockSpec(targetDataset.grid);

		  if (this.isLabelMultiset)
			downsampleAndWriteBlocksLabelMultisetType(
					affectedBlocks,
					n5Writer,
					previousDataset,
					targetDataset,
					blockSpec,
					targetToPrevious,
					relativeFactors,
					targetMaxNumEntries,
					level,
					blockDiffsAt,
					Optional.of(progress));
		  else
			downsampleAndWriteBlocksIntegerType(
					affectedBlocks,
					n5Writer,
					previousDataset,
					targetDataset,
					blockSpec,
					targetToPrevious,
					relativeFactors,
					level,
					blockDiffsAt);

		}
		InvokeOnJavaFXApplicationThread.invoke(() -> progress.set(1.0));

	  }
	  LOG.info("Finished commiting canvas");
	  return blockDiffs;

	} catch (final IOException | PainteraException e) {
	  LOG.error("Unable to commit canvas.", e);
	  throw new UnableToPersistCanvas("Unable to commit canvas.", e);
	}
  }

  private static long[] readContainedLabels(
		  final N5Reader n5,
		  final String uniqueLabelsDataset,
		  final DatasetAttributes uniqueLabelsAttributes,
		  final long[] gridPosition) throws IOException {

	return Optional.ofNullable(n5.readBlock(uniqueLabelsDataset, uniqueLabelsAttributes, gridPosition))
			.map(b -> (LongArrayDataBlock)b)
			.map(LongArrayDataBlock::getData)
			.orElse(new long[]{});
  }

  private static TLongHashSet readContainedLabelsSet(
		  final N5Reader n5,
		  final String uniqueLabelsDataset,
		  final DatasetAttributes uniqueLabelsAttributes,
		  final long[] gridPosition) throws IOException {

	final long[] previousData = readContainedLabels(n5, uniqueLabelsDataset, uniqueLabelsAttributes, gridPosition);
	return new TLongHashSet(previousData);
  }

  private static TLongHashSet generateContainedLabelsSet(
		  final RandomAccessibleInterval<Pair<UnsignedLongType, LabelMultisetType>> relevantData) {

	final TLongHashSet currentDataAsSet = new TLongHashSet();
	final var entry = new LabelMultisetEntry();
	for (final Pair<UnsignedLongType, LabelMultisetType> p : Views.iterable(relevantData)) {
	  final UnsignedLongType pa = p.getA();
	  final LabelMultisetType pb = p.getB();
	  final long pav = pa.getIntegerLong();
	  if (pav == Label.INVALID) {
		for (final var iterEntry : pb.entrySetWithRef(entry)) {
		  currentDataAsSet.add(iterEntry.getElement().id());
		}
	  } else {
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
	if (!N5Helpers.getBooleanAttribute(n5, dataset, N5Helpers.LABEL_MULTISETTYPE_KEY, false)) {
	  throw new RuntimeException("Only label multiset type accepted currently!");
	}
  }

  private static void checkGridsCompatibleOrFail(
		  final CellGrid canvasGrid,
		  final CellGrid highestResolutionGrid
  ) {

	if (!highestResolutionGrid.equals(canvasGrid)) {
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

  private static <I extends IntegerType<I> & NativeType<I>> BlockDiff downsampleIntegerTypeAndSerialize(
		  final N5Writer n5,
		  final String dataset,
		  final DatasetAttributes attributes,
		  final RandomAccessibleInterval<I> data,
		  final int[] relativeFactors,
		  final int[] size,
		  final Interval blockInterval,
		  final long[] blockPosition
  ) throws IOException {

	final I i = Util.getTypeFromInterval(data).createVariable();
	i.setInteger(Label.OUTSIDE);
	final RandomAccessibleInterval<I> input = Views.isZeroMin(data) ? data : Views.zeroMin(data);
	final RandomAccessibleInterval<I> output = new ArrayImgFactory<>(i).create(size);
	WinnerTakesAll.downsample(input, output, relativeFactors);

	final RandomAccessibleInterval<I> previousContents = Views.offsetInterval(N5Utils.<I>open(n5, dataset), blockInterval);
	final BlockDiff blockDiff = createBlockDiffInteger(previousContents, output);

	N5Utils.saveBlock(output, n5, dataset, attributes, blockPosition);
	return blockDiff;
  }

  private static BlockDiff createBlockDiffFromCanvas(final Iterable<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas) {

	return createBlockDiffFromCanvas(backgroundWithCanvas, new BlockDiff());
  }

  private static BlockDiff createBlockDiffFromCanvas(
		  final Iterable<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas,
		  final BlockDiff blockDiff) {

	final var entry = new LabelMultisetEntry();
	for (final Pair<LabelMultisetType, UnsignedLongType> p : backgroundWithCanvas) {
	  final long newLabel = p.getB().getIntegerLong();
	  if (newLabel == Label.INVALID) {
		for (LabelMultisetEntry iterEntry : p.getA().entrySetWithRef(entry)) {
		  final long id = iterEntry.getElement().id();
		  blockDiff.addToOldUniqueLabels(id);
		  blockDiff.addToNewUniqueLabels(id);
		}
	  } else {
		for (LabelMultisetEntry iterEntry : p.getA().entrySetWithRef(entry)) {
		  final long id = iterEntry.getElement().id();
		  blockDiff.addToOldUniqueLabels(id);
		}
		blockDiff.addToNewUniqueLabels(newLabel);
	  }
	}
	return blockDiff;
  }

  private static <T extends IntegerType<T>> BlockDiff createBlockDiffFromCanvasIntegerType(final Iterable<Pair<T, UnsignedLongType>> backgroundWithCanvas) {

	return createBlockDiffFromCanvasIntegerType(backgroundWithCanvas, new BlockDiff());
  }

  private static <T extends IntegerType<T>> BlockDiff createBlockDiffFromCanvasIntegerType(
		  final Iterable<Pair<T, UnsignedLongType>> backgroundWithCanvas,
		  final BlockDiff blockDiff) {

	for (final Pair<T, UnsignedLongType> p : backgroundWithCanvas) {
	  final long newLabel = p.getB().getIntegerLong();
	  if (newLabel == Label.INVALID) {
		final long id = p.getA().getIntegerLong();
		blockDiff.addToOldUniqueLabels(id);
		blockDiff.addToNewUniqueLabels(id);
	  } else {
		final long id = p.getA().getIntegerLong();
		blockDiff.addToOldUniqueLabels(id);
		blockDiff.addToNewUniqueLabels(newLabel);
	  }
	}
	return blockDiff;
  }

  private static BlockDiff createBlockDiff(
		  final VolatileLabelMultisetArray oldAccess,
		  final VolatileLabelMultisetArray newAccess,
		  final int numElements) {

	return createBlockDiff(oldAccess, newAccess, numElements, new BlockDiff());
  }

  private static BlockDiff createBlockDiff(
		  final VolatileLabelMultisetArray oldAccess,
		  final VolatileLabelMultisetArray newAccess,
		  final int numElements,
		  final BlockDiff blockDiff) {

	final ArrayImg<LabelMultisetType, VolatileLabelMultisetArray> oldImg = new ArrayImg<>(oldAccess, new long[]{numElements},
			new LabelMultisetType().getEntitiesPerPixel());
	final ArrayImg<LabelMultisetType, VolatileLabelMultisetArray> newImg = new ArrayImg<>(newAccess, new long[]{numElements},
			new LabelMultisetType().getEntitiesPerPixel());
	oldImg.setLinkedType(new LabelMultisetType(oldImg));
	newImg.setLinkedType(new LabelMultisetType(newImg));
	return createBlockDiff(oldImg, newImg, blockDiff);
  }

  private static BlockDiff createBlockDiff(
		  final Iterable<LabelMultisetType> oldLabels,
		  final Iterable<LabelMultisetType> newLabels,
		  final BlockDiff blockDiff) {

	final var entry = new LabelMultisetEntry();
	for (final Iterator<LabelMultisetType> oldIterator = oldLabels.iterator(), newIterator = newLabels.iterator(); oldIterator.hasNext(); ) {
	  for (LabelMultisetEntry labelMultisetEntry : oldIterator.next().entrySetWithRef(entry)) {
		blockDiff.addToOldUniqueLabels(labelMultisetEntry.getElement().id());
	  }
	  for (LabelMultisetEntry iterEntry : newIterator.next().entrySetWithRef(entry)) {
		blockDiff.addToNewUniqueLabels(iterEntry.getElement().id());
	  }

	}
	return blockDiff;
  }

  private static BlockDiff createBlockDiffOldDoesNotExist(
		  final VolatileLabelMultisetArray access,
		  final int numElements
  ) {

	return createBlockDiffOldDoesNotExist(access, numElements, new BlockDiff());
  }

  private static BlockDiff createBlockDiffOldDoesNotExist(
		  final VolatileLabelMultisetArray access,
		  final int numElements,
		  final BlockDiff blockDiff
  ) {

	final ArrayImg<LabelMultisetType, VolatileLabelMultisetArray> img = new ArrayImg<>(access, new long[]{numElements},
			new LabelMultisetType().getEntitiesPerPixel());
	img.setLinkedType(new LabelMultisetType(img));
	return createBlockDiffOldDoesNotExist(img, blockDiff);
  }

  private static BlockDiff createBlockDiffOldDoesNotExist(
		  final Iterable<LabelMultisetType> labels,
		  final BlockDiff blockDiff
  ) {

	final var entry = new LabelMultisetEntry();
	labels.forEach(lmt -> {
	  for (LabelMultisetEntry iterEntry : lmt.entrySetWithRef(entry)) {
		blockDiff.addToNewUniqueLabels(iterEntry.getElement().id());
	  }
	});
	return blockDiff;
  }

  private static <I extends IntegerType<I>> BlockDiff createBlockDiffInteger(
		  final RandomAccessibleInterval<I> oldAccess,
		  final RandomAccessibleInterval<I> newAccess) {

	return createBlockDiffInteger(oldAccess, newAccess, new BlockDiff());
  }

  private static <I extends IntegerType<I>> BlockDiff createBlockDiffInteger(
		  final RandomAccessibleInterval<I> oldAccess,
		  final RandomAccessibleInterval<I> newAccess,
		  final BlockDiff blockDiff) {

	return createBlockDiffInteger(Views.flatIterable(oldAccess), Views.flatIterable(newAccess), blockDiff);
  }

  private static <I extends IntegerType<I>> BlockDiff createBlockDiffInteger(
		  final Iterable<I> oldLabels,
		  final Iterable<I> newLabels,
		  final BlockDiff blockDiff) {

	for (final Iterator<I> oldIterator = oldLabels.iterator(), newIterator = newLabels.iterator(); oldIterator.hasNext(); ) {
	  blockDiff.addToOldUniqueLabels(oldIterator.next().getIntegerLong());
	  blockDiff.addToNewUniqueLabels(newIterator.next().getIntegerLong());
	}
	return blockDiff;
  }

  private static <T> T computeIfAbsent(final TLongObjectMap<T> map, final long key, final Supplier<T> fallback) {

	T t = map.get(key);
	if (t == null) {
	  t = fallback.get();
	  map.put(key, t);
	}
	return t;
  }

  // TODO: switch to N5LabelMultisets for writing label multiset data
  private static void writeBlocksLabelMultisetType(
		  final RandomAccessibleInterval<UnsignedLongType> canvas,
		  final long[] blocks,
		  final DatasetSpec datasetSpec,
		  final BlockSpec blockSpec,
		  final TLongObjectHashMap<BlockDiff> blockDiff) throws IOException {

	final RandomAccessibleInterval<LabelMultisetType> highestResolutionData = N5LabelMultisets.openLabelMultiset(datasetSpec.container, datasetSpec.dataset);
	for (final long blockId : blocks) {
	  blockSpec.fromLinearIndex(blockId);
	  final IntervalView<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas = Views
			  .interval(Views.pair(highestResolutionData, canvas), blockSpec.asInterval());
	  final int numElements = (int)Intervals.numElements(backgroundWithCanvas);
	  final byte[] byteData = LabelUtils.serializeLabelMultisetTypes(new BackgroundCanvasIterable(Views.flatIterable(backgroundWithCanvas)), numElements);
	  final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(Intervals.dimensionsAsIntArray(backgroundWithCanvas), blockSpec.pos, byteData);
	  datasetSpec.container.writeBlock(datasetSpec.dataset, datasetSpec.attributes, dataBlock);
	  blockDiff.put(blockId, createBlockDiffFromCanvas(backgroundWithCanvas));
	}
  }

  // TODO the integer type implementation does not need to iterate over all pixels per block but could intersect with bounding box first
  private static <I extends IntegerType<I> & NativeType<I>> void writeBlocksLabelIntegerType(
		  final RandomAccessibleInterval<UnsignedLongType> canvas,
		  final long[] blocks,
		  final DatasetSpec datasetSpec,
		  final BlockSpec blockSpec,
		  final TLongObjectHashMap<BlockDiff> blockDiff) throws IOException {

	final RandomAccessibleInterval<I> highestResolutionData = N5Utils.open(datasetSpec.container, datasetSpec.dataset);
	final I i = Util.getTypeFromInterval(highestResolutionData).createVariable();
	for (final long blockId : blocks) {
	  blockSpec.fromLinearIndex(blockId);
	  final RandomAccessibleInterval<Pair<I, UnsignedLongType>> backgroundWithCanvas = Views
			  .interval(Views.pair(highestResolutionData, canvas), blockSpec.asInterval());
	  final RandomAccessibleInterval<I> mergedData = Converters
			  .convert(backgroundWithCanvas, (s, t) -> pickFirstIfSecondIsInvalid(s.getA(), s.getB(), t), i.createVariable());
	  N5Utils.saveBlock(mergedData, datasetSpec.container, datasetSpec.dataset, datasetSpec.attributes, blockSpec.pos);
	  blockDiff.put(blockId, createBlockDiffFromCanvasIntegerType(Views.iterable(backgroundWithCanvas)));
	}
  }

  // TODO: switch to N5LabelMultisets for writing label multiset data
  private static void downsampleAndWriteBlocksLabelMultisetType(
		  final long[] affectedBlocks,
		  final N5Writer n5,
		  final DatasetSpec previousDataset,
		  final DatasetSpec targetDataset,
		  final BlockSpec blockSpec,
		  final Scale3D targetToPrevious,
		  final int[] relativeFactors,
		  final int targetMaxNumEntries,
		  final int level,
		  final TLongObjectHashMap<BlockDiff> blockDiffsAt,
		  Optional<ReadOnlyDoubleWrapper> progressOpt) throws IOException {

	// In older converted data the "isLabelMultiset" attribute may not be present in s1,s2,... datasets.
	// Make sure the attribute is set to avoid "is not a label multiset" exception.
	n5.setAttribute(previousDataset.dataset, N5Helpers.IS_LABEL_MULTISET_KEY, true);
	n5.setAttribute(targetDataset.dataset, N5Helpers.IS_LABEL_MULTISET_KEY, true);

	final RandomAccessibleInterval<LabelMultisetType> previousData = N5LabelMultisets.openLabelMultiset(n5, previousDataset.dataset);

	final double increment;
	if (progressOpt.isPresent()) {
	  final var progress = progressOpt.get();
	  final var delta = Math.max(0.0, .9 - progress.get());
	  increment = delta / affectedBlocks.length;
	} else {
	  increment = 0.0;
	}

	for (final long targetBlock : affectedBlocks) {
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
	  final DataBlock<?> block = n5.readBlock(targetDataset.dataset, targetDataset.attributes, blockSpec.pos);
	  final VolatileLabelMultisetArray oldAccess = block != null && block.getData() instanceof byte[]
			  ? LabelUtils.fromBytes(
			  (byte[])block.getData(),
			  (int)Intervals.numElements(size))
			  : null;

	  final VolatileLabelMultisetArray newAccess = downsampleVolatileLabelMultisetArrayAndSerialize(
			  n5,
			  targetDataset.dataset,
			  targetDataset.attributes,
			  Views.interval(previousData, previousRelevantIntervalMin, previousRelevantIntervalMax),
			  relativeFactors,
			  targetMaxNumEntries,
			  size,
			  blockSpec.pos);
	  final int numElements = (int)Intervals.numElements(size);
	  blockDiffsAt.put(
			  targetBlock,
			  oldAccess == null
					  ? createBlockDiffOldDoesNotExist(newAccess, numElements)
					  : createBlockDiff(oldAccess, newAccess, numElements));
	  progressOpt.ifPresent(prop -> prop.set(prop.get() + increment));
	}
  }

  //TODO add progress updates to this when data is available to test against
  private static <I extends IntegerType<I> & NativeType<I>> void downsampleAndWriteBlocksIntegerType(
		  final long[] affectedBlocks,
		  final N5Writer n5,
		  final DatasetSpec previousDataset,
		  final DatasetSpec targetDataset,
		  final BlockSpec blockSpec,
		  final Scale3D targetToPrevious,
		  final int[] relativeFactors,
		  final int level,
		  final TLongObjectHashMap<BlockDiff> blockDiffsAt
  ) throws IOException {

	final RandomAccessibleInterval<I> previousData = N5Utils.open(n5, previousDataset.dataset);

	for (final long targetBlock : affectedBlocks) {
	  blockSpec.fromLinearIndex(targetBlock);
	  final double[] blockMinDouble = ArrayMath.asDoubleArray3(blockSpec.min);
	  final double[] blockMaxDouble = ArrayMath.asDoubleArray3(ArrayMath.add3(blockSpec.max, 1));
	  targetToPrevious.apply(blockMinDouble, blockMinDouble);
	  targetToPrevious.apply(blockMaxDouble, blockMaxDouble);

	  LOG.debug("level={}: blockMinDouble={} blockMaxDouble={}", level, blockMinDouble, blockMaxDouble);

	  final long[] blockMin = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.floor3(blockMinDouble, blockMinDouble)), previousDataset.dimensions);
	  final long[] blockMax = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.ceil3(blockMaxDouble, blockMaxDouble)), previousDataset.dimensions);
	  final Interval targetInterval = new FinalInterval(blockSpec.min, blockSpec.max);
	  final int[] size = Intervals.dimensionsAsIntArray(targetInterval);

	  final long[] previousRelevantIntervalMin = blockMin.clone();
	  final long[] previousRelevantIntervalMax = ArrayMath.add3(blockMax, -1);

	  ArrayMath.divide3(blockMin, previousDataset.blockSize, blockMin);
	  ArrayMath.divide3(blockMax, previousDataset.blockSize, blockMax);
	  ArrayMath.add3(blockMax, -1, blockMax);
	  ArrayMath.minOf3(blockMax, blockMin, blockMax);

	  LOG.trace("Reading old access at position {} and size {}. ({} {})", blockSpec.pos, size, blockSpec.min, blockSpec.max);

	  final BlockDiff blockDiff = downsampleIntegerTypeAndSerialize(
			  n5,
			  targetDataset.dataset,
			  targetDataset.attributes,
			  Views.interval(previousData, previousRelevantIntervalMin, previousRelevantIntervalMax),
			  relativeFactors,
			  size,
			  targetInterval,
			  blockSpec.pos);
	  blockDiffsAt.put(targetBlock, blockDiff);
	}
  }

  private static <I extends IntegerType<I>, C extends IntegerType<C>> void pickFirstIfSecondIsInvalid(final I s1, final C s2, final I t) {

	final long val = s2.getIntegerLong();
	if (Label.regular(val))
	  t.setInteger(val);
	else
	  t.set(s1);
  }

  @Override public ReadOnlyDoubleProperty getProgressProperty() {

	return progress.getReadOnlyProperty();
  }

}
