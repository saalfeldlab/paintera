package org.janelia.saalfeldlab.paintera.data.n5;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import io.github.oshai.kotlinlogging.KLogger;
import io.github.oshai.kotlinlogging.KotlinLogging;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.*;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey;
import org.janelia.saalfeldlab.labels.downsample.WinnerTakesAll;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToPersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToUpdateLabelBlockLookup;
import org.janelia.saalfeldlab.paintera.exception.PainteraException;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState;
import org.janelia.saalfeldlab.util.math.ArrayMath;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.SpatialMapping;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static net.imglib2.type.label.LabelMultisetTypeDownscaler.*;

public class CommitCanvasN5 implements PersistCanvas {

	private static final KLogger LOG = KotlinLogging.INSTANCE.logger(() -> null);

	private final MetadataState metadataState;

	private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress", 0.0);

	public CommitCanvasN5(final MetadataState metadataState) {

		super();
		this.metadataState = metadataState;
	}

	public final boolean isPainteraDataset() {

		return metadataState instanceof PainteraDataMultiscaleMetadataState;
	}

	public final boolean isMultiscale() {

		return metadataState instanceof MultiScaleMetadataState;
	}

	public final boolean isLabelMultiset() {

		return metadataState.isLabelMultiset();
	}

	public final N5Writer getN5() {

		return metadataState.getWriter();
	}

	public final String dataset() {

		return metadataState.getDataset();
	}

	@Override
	public boolean supportsLabelBlockLookupUpdate() {

		return isPainteraDataset();
	}

	@Override
	public void updateLabelBlockLookup(final List<TLongObjectMap<BlockDiff>> blockDiffsByLevel) throws UnableToUpdateLabelBlockLookup {

		LOG.debug(() -> "Updating label block lookup with " + blockDiffsByLevel);
		final ThreadFactory build = new ThreadFactoryBuilder().setNameFormat("update-unique-labels-%d").build();
		final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), build);
		try {
			final String uniqueLabelsPath = N5URI.normalizeGroupPath("%s/unique-labels".formatted(dataset()));
			LOG.debug(() -> "uniqueLabelsPath %s".formatted(uniqueLabelsPath));

			final LabelBlockLookup labelBlockLoader;
			try {
				labelBlockLoader = N5Helpers.getLabelBlockLookup(metadataState);
			} catch (N5Helpers.NotAPainteraDataset e) {
				throw new RuntimeException(e);
			}

			final String[] scaleUniqueLabels = N5Helpers.listAndSortScaleDatasets(getN5(), uniqueLabelsPath);

			LOG.debug(() -> "Found scale datasets %s".formatted((Object[])scaleUniqueLabels));
			for (int level = 0; level < scaleUniqueLabels.length; ++level) {
				final String uniqueLabelScalePath = N5URI.normalizeGroupPath("%s/%s".formatted(uniqueLabelsPath, scaleUniqueLabels[level]));
				final DatasetSpec datasetUniqueLabels = DatasetSpec.of(getN5(), uniqueLabelScalePath);
				final TLongObjectMap<TLongHashSet> removedById = new TLongObjectHashMap<>();
				final TLongObjectMap<TLongHashSet> addedById = new TLongObjectHashMap<>();
				final TLongObjectMap<BlockDiff> blockDiffs = blockDiffsByLevel.get(level);
				final BlockSpec blockSpec = new BlockSpec(datasetUniqueLabels.grid);

				/* unique-labels blocks are independent; write them in parallel while accumulating the mapping patch */
				final List<Future<?>> uniqueLabelsWrites = new ArrayList<>();
				for (final TLongObjectIterator<BlockDiff> blockDiffIt = blockDiffs.iterator(); blockDiffIt.hasNext(); ) {
					blockDiffIt.advance();
					final long blockId = blockDiffIt.key();
					final BlockDiff blockDiff = blockDiffIt.value();

					uniqueLabelsWrites.add(threadPool.submit(() -> {
						final BlockSpec spec = new BlockSpec(datasetUniqueLabels.grid);
						spec.fromLinearIndex(blockId);
						LOG.trace(() -> "Unique labels for block (%d: %s %s): %s".formatted(blockId, spec.min, spec.max, blockDiff));
						getN5().writeBlock(
								datasetUniqueLabels.dataset,
								datasetUniqueLabels.attributes,
								new LongArrayDataBlock(
										Intervals.dimensionsAsIntArray(new FinalInterval(spec.min, spec.max)),
										spec.pos,
										blockDiff.getNewUniqueIds()));
					}));

					final long[] removedInBlock = blockDiff.getRemovedIds();
					final long[] addedInBlock = blockDiff.getAddedIds();

					for (final long removed : removedInBlock) {
						computeIfAbsent(removedById, removed, TLongHashSet::new).add(blockId);
					}

					for (final long added : addedInBlock) {
						computeIfAbsent(addedById, added, TLongHashSet::new).add(blockId);
					}

				}

				for (final Future<?> uniqueLabelsWrite : uniqueLabelsWrites)
					uniqueLabelsWrite.get();

				final TLongSet modifiedIds = new TLongHashSet();
				modifiedIds.addAll(removedById.keySet());
				modifiedIds.addAll(addedById.keySet());
				LOG.debug(() -> "Removed by id: " + removedById);
				LOG.debug(() -> "Added by id: " + addedById);
				for (final long modifiedId : modifiedIds.toArray()) {
					final Interval[] blockList = labelBlockLoader.read(new LabelBlockLookupKey(level, modifiedId));
					final TLongSet blockListLinearIndices = new TLongHashSet();
					for (final Interval block : blockList) {
						blockSpec.fromInterval(block);
						blockListLinearIndices.add(blockSpec.asLinearIndex());
					}

					final TLongSet removed = removedById.get(modifiedId);
					final TLongSet added = addedById.get(modifiedId);

					LOG.debug(() -> "Removed for id %d: %s".formatted(modifiedId, removed));
					LOG.debug(() -> "Added for id %d: %s".formatted(modifiedId, added));

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
						LOG.trace(() -> "Added interval %s for linear index %d and block spec %s".formatted(interval, blockId, blockSpec));
					}
					labelBlockLoader.write(new LabelBlockLookupKey(level, modifiedId), updatedIntervals);
				}

			}

		} catch (final IOException | InterruptedException | ExecutionException e) {
			LOG.error(e, () -> null);
			throw new UnableToUpdateLabelBlockLookup("Unable to update label block lookup for %s".formatted(dataset()), e);
		} finally {
			threadPool.shutdown();
		}
		LOG.info(() -> "Finished updating label-block-lookup");
	}

	@Override
	public List<TLongObjectMap<BlockDiff>> persistCanvas(final CachedCellImg<UnsignedLongType, ?> canvas, final long[] blocks) throws UnableToPersistCanvas {

		LOG.info(() -> "Committing canvas: %d blocks".formatted(blocks.length));
		LOG.debug(() -> "Affected blocks in grid %s: %s".formatted(canvas.getCellGrid(), blocks));
		progress.set(0.1);
		try {
			final String highestResDatasetPath = getHighestResolutionDatasetPath();
			DatasetSpec highestResolutionDataset = DatasetSpec.of(getN5(), highestResDatasetPath);

			final CellGrid canvasGrid = canvas.getCellGrid();

            checkGridsCompatibleOrFail(canvasGrid, highestResolutionDataset.grid);

            LOG.debug(() -> "Persisting canvas into dataset grid %s".formatted(highestResolutionDataset.grid));
            final List<TLongObjectMap<BlockDiff>> result = new ArrayList<>();

            /* highest resolution: merge the canvas straight into the dataset block-for-block */
            final BlockSpec blockSpec = new BlockSpec(highestResolutionDataset.grid);
            final TLongObjectHashMap<BlockDiff> blockDiffs = new TLongObjectHashMap<>();
            if (isLabelMultiset())
                writeBlocksLabelMultisetType(canvas, blocks, highestResolutionDataset, blockSpec, blockDiffs);
            else
                writeBlocksLabelIntegerType(canvas, blocks, highestResolutionDataset, blockSpec, blockDiffs);
            result.add(blockDiffs);
            progress.set(0.4);

            /* lower scales: downsample the just-committed level into each lower level, spatially, per non-spatial slice */
            if (metadataState instanceof MultiScaleMetadataState multiscaleMetadataState) {
                final AffineTransform3D[] scaleTransforms = multiscaleMetadataState.getScaleTransforms();
                final String[] scalePaths = multiscaleMetadataState.getMetadata().getPaths();
                final int[] xyzSourceAxes = SpatialMapping.xyzSourceAxes(metadataState.getAxes());

                final long[] modifiedBlocks = blockDiffs.keys();
                /* only revisit the slices (timepoints/channels) the commit actually touched; null => fall back to all */
                final List<long[]> affectedSlices = affectedBlockPositions(modifiedBlocks, highestResolutionDataset.grid, highestResolutionDataset.blockSize, xyzSourceAxes);
                final List<long[]> slices = affectedSlices != null ? affectedSlices : fixedNonSpatialBlocks(highestResolutionDataset.dimensions, xyzSourceAxes);

                final List<long[]> committedSpatialBlocksPerSlice = new ArrayList<>();
                for (final long[] slicePositions : slices)
                    committedSpatialBlocksPerSlice.add(spatialBlocksInSlice(modifiedBlocks, highestResolutionDataset, xyzSourceAxes, slicePositions));

                for (int targetLevel = 1; targetLevel < scalePaths.length; ++targetLevel) {
                    final TLongObjectHashMap<BlockDiff> blockDiffsAt = new TLongObjectHashMap<>();
                    result.add(blockDiffsAt);

                    final int sourceLevel = targetLevel - 1;
                    final DatasetSpec sourceDataset = DatasetSpec.of(getN5(), N5URI.normalizeGroupPath(scalePaths[sourceLevel]));
                    final DatasetSpec targetDataset = DatasetSpec.of(getN5(), N5URI.normalizeGroupPath(scalePaths[targetLevel]));

                    final AffineTransform3D previousToTarget = scaleTransforms[targetLevel].copy().concatenate(scaleTransforms[sourceLevel].inverse());
                    final double[] relativeDownsamplingFactors = {previousToTarget.get(0, 0), previousToTarget.get(1, 1), previousToTarget.get(2, 2)};
                    final Scale3D targetToPrevious = new Scale3D(relativeDownsamplingFactors);
                    final int[] relativeFactors = ArrayMath.asInt3(relativeDownsamplingFactors, true);
                    final int targetMaxNumEntries = N5Helpers.getIntegerAttribute(getN5(), targetDataset.dataset, N5Helpers.MAX_NUM_ENTRIES_KEY, -1);

                    final AffineTransform3D highestResToTarget = scaleTransforms[targetLevel].copy().concatenate(scaleTransforms[0].inverse());
                    final double[] highestResToTargetFactors = {highestResToTarget.get(0, 0), highestResToTarget.get(1, 1), highestResToTarget.get(2, 2)};

                    for (int sliceIndex = 0; sliceIndex < slices.size(); ++sliceIndex) {
                        /* nothing committed in this slice */
                        if (committedSpatialBlocksPerSlice.get(sliceIndex).length == 0)
                            continue;
                        final long[] slicePositions = slices.get(sliceIndex);
                        final SpatialMapping mapping = new SpatialMapping(targetDataset.dimensions.length, xyzSourceAxes, slicePositions);
                        final CellGrid highestResSpatialGrid = new CellGrid(
                                mapping.spatialProjection(highestResolutionDataset.dimensions),
                                mapping.spatialProjection(highestResolutionDataset.blockSize));
                        final CellGrid targetSpatialGrid = new CellGrid(
                                mapping.spatialProjection(targetDataset.dimensions),
                                mapping.spatialProjection(targetDataset.blockSize));
                        final BlockSpec targetBlockSpec = new BlockSpec(targetSpatialGrid);
                        /* only downsample the target blocks affected by the committed s0 blocks  */
                        final long[] affectedTargetBlocks = relevantTargetBlocks(
                                committedSpatialBlocksPerSlice.get(sliceIndex),
                                highestResSpatialGrid,
                                targetSpatialGrid,
                                highestResToTargetFactors);

                        if (isLabelMultiset())
                            downsampleAndWriteBlocksLabelMultisetType(
                                    affectedTargetBlocks,
                                    getN5(),
                                    sourceDataset,
                                    targetDataset,
                                    targetBlockSpec,
                                    targetToPrevious,
                                    relativeFactors,
                                    targetMaxNumEntries,
                                    targetLevel,
                                    mapping,
                                    blockDiffsAt,
                                    Optional.empty());
                        else
                            downsampleAndWriteBlocksIntegerType(
                                    affectedTargetBlocks,
                                    getN5(),
                                    sourceDataset,
                                    targetDataset,
                                    targetBlockSpec,
                                    targetToPrevious,
                                    relativeFactors,
                                    targetLevel,
                                    mapping,
                                    blockDiffsAt);
                    }
                }
            }

            progress.set(1.0);
            return result;

        } catch (final IOException | PainteraException e) {
            LOG.error(e, () -> "Unable to commit canvas.");
            throw new UnableToPersistCanvas("Unable to commit canvas.", e);
        } catch (final Exception e) {
            LOG.error(e, () -> "Unable to commit canvas.");
            throw new UnableToPersistCanvas("Unable to commit canvas.", e);
        }
	}

	private String getHighestResolutionDatasetPath() {

		final String highestResScalePath = getScaleLevelDatasetPath(0);
		return highestResScalePath != null ? highestResScalePath : metadataState.getMetadata().getPath();
	}

	private String getScaleLevelDatasetPath(int level) {

		if (metadataState instanceof MultiScaleMetadataState)
			return ((MultiScaleMetadataState)metadataState).getMetadata().getPaths()[level];
		else return null;
	}

	private static long[] readContainedLabels(
			final N5Reader n5,
			final String uniqueLabelsDataset,
			final DatasetAttributes uniqueLabelsAttributes,
			final long[] gridPosition) throws IOException {

		return Optional.ofNullable(n5.<long[]>readBlock(uniqueLabelsDataset, uniqueLabelsAttributes, gridPosition))
				.map(DataBlock::getData)
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
		for (final Pair<UnsignedLongType, LabelMultisetType> p : relevantData) {
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

		LOG.debug(() -> "Checking if dataset %s is label multiset type.".formatted(dataset));
		if (!N5Helpers.getBooleanAttribute(n5, dataset, N5Helpers.IS_LABEL_MULTISET_KEY, false)) {
			throw new RuntimeException("Only label multiset type accepted currently!");
		}
	}

	private static void checkGridsCompatibleOrFail(
			final CellGrid canvasGrid,
			final CellGrid highestResolutionGrid
	) {

		if (!highestResolutionGrid.equals(canvasGrid)) {
			final RuntimeException error = new RuntimeException("Canvas grid %s and highest resolution dataset grid %s incompatible!".formatted(canvasGrid, highestResolutionGrid));
			LOG.error(error, () -> null);
			throw error;
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

		final VolatileLabelMultisetArray updatedAccess = createDownscaledCell(
				Views.isZeroMin(data) ? data : Views.zeroMin(data),
				relativeFactors,
				maxNumEntries
		);

		boolean currentEmptyBehavior = updatedAccess.getArrayLength() == 0;
		if (updatedAccess.isValid() && currentEmptyBehavior) {
			n5.deleteBlock(dataset, attributes, blockPosition);
			return null;
		}

		final byte[] serializedAccess = new byte[getSerializedVolatileLabelMultisetArraySize(updatedAccess)];
		serializeVolatileLabelMultisetArray(updatedAccess, serializedAccess);

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
			final SpatialMapping mapping
	) {

		final I i = data.getType().createVariable();
		i.setInteger(Label.OUTSIDE);
		final RandomAccessibleInterval<I> input = Views.isZeroMin(data) ? data : Views.zeroMin(data);
		final RandomAccessibleInterval<I> output = new ArrayImgFactory<>(i).create(size);
		WinnerTakesAll.downsample(Views.extendMirrorDouble(input), output, relativeFactors);

		/* read the existing 3D spatial slice to diff against; write the new block back into the nD slice */
		final RandomAccessibleInterval<I> openedTarget = N5Utils.open(n5, dataset);
		final RandomAccessibleInterval<I> previousContents = Views.offsetInterval(mapping.to3D(openedTarget), blockInterval);
		final BlockDiff blockDiff = createBlockDiffInteger(previousContents, output);

		N5Utils.saveBlock(mapping.toSourceView(Views.translate(output, Intervals.minAsLongArray(blockInterval))), n5, dataset, attributes);
		return blockDiff;
	}

	private static BlockDiff createBlockDiffFromCanvas(final Iterable<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas) {

		return createBlockDiffFromCanvas(backgroundWithCanvas, new BlockDiff());
	}

	private static BlockDiff createBlockDiffFromCanvas(
			final Iterable<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas,
			final BlockDiff blockDiff) {

		final var entryRef = new LabelMultisetEntry();
		for (final Pair<LabelMultisetType, UnsignedLongType> sourceAndCanvas : backgroundWithCanvas) {
			final long canvasLabel = sourceAndCanvas.getB().getIntegerLong();
			if (canvasLabel == Label.INVALID) {
				for (LabelMultisetType.Entry<Label> entry : sourceAndCanvas.getA().entrySetWithRef(entryRef)) {
					final long id = entry.getElement().id();
					blockDiff.addToOldUniqueLabels(id);
					blockDiff.addToNewUniqueLabels(id);
				}
			} else {
				for (LabelMultisetType.Entry<Label> entry : sourceAndCanvas.getA().entrySetWithRef(entryRef)) {
					final long id = entry.getElement().id();
					blockDiff.addToOldUniqueLabels(id);
				}
				blockDiff.addToNewUniqueLabels(canvasLabel);
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

		final ArrayImg<LabelMultisetType, VolatileLabelMultisetArray> oldImg = new ArrayImg<>(oldAccess, new long[]{numElements}, new LabelMultisetType().getEntitiesPerPixel());
		oldImg.setLinkedType(new LabelMultisetType(oldImg));
		if (newAccess == null)
			return createBlockDiff(oldImg, null, blockDiff);

		final ArrayImg<LabelMultisetType, VolatileLabelMultisetArray> newImg = new ArrayImg<>(newAccess, new long[]{numElements}, new LabelMultisetType().getEntitiesPerPixel());
		newImg.setLinkedType(new LabelMultisetType(newImg));
		return createBlockDiff(oldImg, newImg, blockDiff);
	}

	private static BlockDiff createBlockDiff(
			final Iterable<LabelMultisetType> oldLabelImg,
			final Iterable<LabelMultisetType> newLabelImg,
			final BlockDiff blockDiff) {

		final var entry = new LabelMultisetEntry();

		for (LabelMultisetType oldLabelMultiset : oldLabelImg) {
			for (LabelMultisetType.Entry<Label> oldEntry : oldLabelMultiset.entrySetWithRef(entry)) {
				blockDiff.addToOldUniqueLabels(oldEntry.getElement().id());
			}
		}
		if (newLabelImg == null)
			return blockDiff;

		for (LabelMultisetType newLabelMultiset : newLabelImg) {
			for (LabelMultisetType.Entry<Label> newEntry : newLabelMultiset.entrySetWithRef(entry)) {
				blockDiff.addToNewUniqueLabels(newEntry.getElement().id());
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

		if (access == null)
			return blockDiff;

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
		for (LabelMultisetType lmt : labels) {
			for (LabelMultisetType.Entry<Label> labelEntry : lmt.entrySetWithRef(entry)) {
				blockDiff.addToNewUniqueLabels(labelEntry.getElement().id());
			}
		}

		return blockDiff;
	}

	/* only regular canvas values override the background at merge, so INVALID/TRANSPARENT/OUTSIDE-only blocks are no-ops */
	private static boolean hasPaintedVoxel(final RandomAccessibleInterval<UnsignedLongType> canvasOverBlock) {
		//TODO Caleb: We alredy generate BlockDiff. either re-use this for block diff, or remove and use BlockDiff instead
		for (final UnsignedLongType value : Views.flatIterable(canvasOverBlock))
			if (Label.regular(value.getIntegerLong()))
				return true;
		return false;
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

		return createBlockDiffInteger(
				(Iterable<I>)Views.flatIterable(oldAccess),
				(Iterable<I>)Views.flatIterable(newAccess),
				blockDiff);
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

	/**
	 * The distinct non-spatial slice positions actually touched by [blocks] (the painted level-0 nD blocks), so the
	 * downsample only revisits painted timepoints/channels instead of every slice. Returns null when a non-spatial
	 * block spans more than one position (cell != voxel), signalling the caller to fall back to every slice.
	 */
	private static List<long[]> affectedBlockPositions(final long[] blocks, final CellGrid s0Grid, final int[] s0BlockSize, final int[] xyzSourceAxes) {

		final boolean[] spatial = new boolean[s0Grid.numDimensions()];

        for (final int axis : xyzSourceAxes)
			if (axis >= 0)
                spatial[axis] = true;
		for (int axis = 0; axis < spatial.length; ++axis)
			if (!spatial[axis] && s0BlockSize[axis] != 1)
                return null;

		final long[] cellPosition = new long[s0Grid.numDimensions()];
		final Set<List<Long>> seen = new HashSet<>();
		final List<long[]> slices = new ArrayList<>();
		for (final long block : blocks) {
			s0Grid.getCellGridPositionFlat(block, cellPosition);
			final long[] slicePositions = new long[s0Grid.numDimensions()];
			for (int axis = 0; axis < slicePositions.length; ++axis)
				slicePositions[axis] = spatial[axis] ? 0 : cellPosition[axis];
			final List<Long> key = new ArrayList<>();
			for (final long position : slicePositions)
				key.add(position);
			if (seen.add(key))
				slices.add(slicePositions);
		}
		return slices;
	}

	/** The highest-resolution spatial blocks among [blocks] that lie in the slice at [slicePositions]. */
	private static long[] spatialBlocksInSlice(
			final long[] blocks,
			final DatasetSpec highestResolutionDataset,
			final int[] xyzSourceAxes,
			final long[] slicePositions) {

		final CellGrid grid = highestResolutionDataset.grid;
		final int[] blockSize = highestResolutionDataset.blockSize;
		final boolean[] spatial = new boolean[grid.numDimensions()];
		for (final int axis : xyzSourceAxes)
			if (axis >= 0)
				spatial[axis] = true;

		final long[] spatialGridDimensions = new long[3];
		for (int slot = 0; slot < 3; ++slot)
			spatialGridDimensions[slot] = xyzSourceAxes[slot] >= 0 ? grid.gridDimension(xyzSourceAxes[slot]) : 1;

		final long[] cellPosition = new long[grid.numDimensions()];
		final long[] spatialPosition = new long[3];
		final TLongHashSet spatialBlocks = new TLongHashSet();
		for (final long block : blocks) {
			grid.getCellGridPositionFlat(block, cellPosition);
			boolean inSlice = true;
			for (int axis = 0; axis < cellPosition.length && inSlice; ++axis)
				if (!spatial[axis])
					inSlice = cellPosition[axis] == slicePositions[axis] / blockSize[axis];
			if (!inSlice)
				continue;
			for (int slot = 0; slot < 3; ++slot)
				spatialPosition[slot] = xyzSourceAxes[slot] >= 0 ? cellPosition[xyzSourceAxes[slot]] : 0;
			spatialBlocks.add(IntervalIndexer.positionToIndex(spatialPosition, spatialGridDimensions));
		}
		return spatialBlocks.toArray();
	}

	/* the target blocks whose voxels downsample from the given source blocks; the scaled bounds are voxel-precise,
	 * unlike Grids.getRelevantBlocksInTargetGrid whose ceil'd real interval bleeds into the adjacent block layer */
	private static long[] relevantTargetBlocks(
			final long[] sourceSpatialBlocks,
			final CellGrid sourceGrid,
			final CellGrid targetGrid,
			final double[] sourceToTargetFactors) {

		final long[] sourceBlockPosition = new long[3];
		final long[] targetMinBlock = new long[3];
		final long[] targetMaxBlock = new long[3];
		final int[] ones = {1, 1, 1};
		final long[] targetGridDimensions = targetGrid.getGridDimensions();
		final TLongHashSet targetBlocks = new TLongHashSet();
		for (final long block : sourceSpatialBlocks) {
			sourceGrid.getCellGridPositionFlat(block, sourceBlockPosition);
			for (int d = 0; d < 3; ++d) {
				final long sourceMin = sourceBlockPosition[d] * sourceGrid.cellDimension(d);
				final long sourceMax = Math.min(sourceMin + sourceGrid.cellDimension(d), sourceGrid.imgDimension(d)) - 1;
				final long targetMin = (long)Math.floor(sourceMin / sourceToTargetFactors[d]);
				final long targetMax = (long)Math.ceil((sourceMax + 1) / sourceToTargetFactors[d]) - 1;
				targetMinBlock[d] = Math.max(targetMin / targetGrid.cellDimension(d), 0);
				targetMaxBlock[d] = Math.min(targetMax / targetGrid.cellDimension(d), targetGrid.gridDimension(d) - 1);
			}
			Grids.forEachOffset(targetMinBlock, targetMaxBlock, ones,
					offset -> targetBlocks.add(IntervalIndexer.positionToIndex(offset, targetGridDimensions)));
		}
		return targetBlocks.toArray();
	}

	/** Every combination of fixed non-spatial block positions.
     *  - spatial axes left at 0
     *  - one entry per non-spatial axis position
     *  - single all-zero entry for a 3D source. */
	private static List<long[]> fixedNonSpatialBlocks(final long[] dimensions, final int[] xyzSourceAxes) {

		final boolean[] spatial = new boolean[dimensions.length];
		for (final int axis : xyzSourceAxes)
			if (axis >= 0) spatial[axis] = true;
		final List<Integer> nonSpatialAxes = new ArrayList<>();
		for (int d = 0; d < dimensions.length; ++d)
			if (!spatial[d]) nonSpatialAxes.add(d);

		final List<long[]> positions = new ArrayList<>();
		enumerateNonSpatialAxisPositions(nonSpatialAxes, 0, dimensions, new long[dimensions.length], positions);
		return positions;
	}

	private static void enumerateNonSpatialAxisPositions(final List<Integer> nonSpatialAxes, final int index, final long[] dimensions, final long[] current, final List<long[]> out) {

		if (index == nonSpatialAxes.size()) {
			out.add(current.clone());
			return;
		}
		final int axis = nonSpatialAxes.get(index);
		for (long position = 0; position < dimensions[axis]; ++position) {
			current[axis] = position;
			enumerateNonSpatialAxisPositions(nonSpatialAxes, index + 1, dimensions, current, out);
		}
	}

	private static void writeBlocksLabelMultisetType(
			final RandomAccessibleInterval<UnsignedLongType> canvas,
			final long[] blocks,
			final DatasetSpec datasetSpec,
			final BlockSpec blockSpec,
			final TLongObjectHashMap<BlockDiff> blockDiff) throws IOException {

		final RandomAccessibleInterval<LabelMultisetType> background =
				N5LabelMultisets.openLabelMultiset(datasetSpec.container, datasetSpec.dataset);

		final ThreadFactory build = new ThreadFactoryBuilder().setNameFormat("write-blocks-label-multiset-nd-%d").build();
		final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), build);
		final int chunkSize = Math.max(1, Math.min(blocks.length / (Runtime.getRuntime().availableProcessors() * 2), 100));
		final ArrayList<Future<?>> futures = new ArrayList<>();

		for (int chunkStart = 0; chunkStart < blocks.length; chunkStart += chunkSize) {
			final int chunkEnd = Math.min(chunkStart + chunkSize, blocks.length);
			final int finalChunkStart = chunkStart;
			futures.add(threadPool.submit(() -> {
				final TLongObjectHashMap<BlockDiff> localBlockDiffs = new TLongObjectHashMap<>();
				for (int i = finalChunkStart; i < chunkEnd; i++) {
					final long blockId = blocks[i];
					try {
						final var blockSpecCopy = new BlockSpec(blockSpec);
						blockSpecCopy.fromLinearIndex(blockId);
						/* leave the block untouched if the canvas has no painted voxel in it */
						if (!hasPaintedVoxel(Views.interval(canvas, blockSpecCopy.asInterval())))
							continue;
						final IntervalView<Pair<LabelMultisetType, UnsignedLongType>> backgroundWithCanvas =
								Views.interval(Views.pair(background, canvas), blockSpecCopy.asInterval());
						final int numElements = (int) Intervals.numElements(backgroundWithCanvas);
						final byte[] byteData = LabelUtils.serializeLabelMultisetTypes(
								new BackgroundCanvasIterable(Views.flatIterable(backgroundWithCanvas)), numElements);

						if (byteData == null) {
							datasetSpec.container.deleteBlock(datasetSpec.dataset, datasetSpec.attributes, blockSpecCopy.pos);
						} else {
							final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(
									Intervals.dimensionsAsIntArray(backgroundWithCanvas), blockSpecCopy.pos, byteData);
							datasetSpec.container.writeBlock(datasetSpec.dataset, datasetSpec.attributes, dataBlock);
						}

						localBlockDiffs.put(blockId, createBlockDiffFromCanvas(backgroundWithCanvas));
					} catch (Exception e) {
						LOG.error("Error processing block {}", blockId, e);

					}
				}

				if (!localBlockDiffs.isEmpty()) {
					synchronized (blockDiff) {
						localBlockDiffs.forEachEntry((id, diff) -> {
							blockDiff.put(id, diff);
							return true;
						});
					}
				}
			}));
		}

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		threadPool.shutdown();
	}

	// TODO the integer type implementation does not need to iterate over all pixels per block but could intersect with bounding box first
	private static <I extends IntegerType<I> & NativeType<I>> void writeBlocksLabelIntegerType(
			final RandomAccessibleInterval<UnsignedLongType> canvas,
			final long[] blocks,
			final DatasetSpec datasetSpec,
			final BlockSpec blockSpec,
			final TLongObjectHashMap<BlockDiff> blockDiff) throws IOException {

		final RandomAccessibleInterval<I> background = N5Utils.open(datasetSpec.container, datasetSpec.dataset);
		final I type = background.getType();

		final ThreadFactory build = new ThreadFactoryBuilder().setNameFormat("write-blocks-integer-%d").build();
		final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), build);
		final int chunkSize = Math.max(1, Math.min(blocks.length / (Runtime.getRuntime().availableProcessors() * 2), 100));
		final ArrayList<Future<?>> futures = new ArrayList<>();

		for (int chunkStart = 0; chunkStart < blocks.length; chunkStart += chunkSize) {
			final int chunkEnd = Math.min(chunkStart + chunkSize, blocks.length);
			final int finalChunkStart = chunkStart;
			futures.add(threadPool.submit(() -> {
				final TLongObjectHashMap<BlockDiff> localBlockDiffs = new TLongObjectHashMap<>();
				for (int i = finalChunkStart; i < chunkEnd; i++) {
					final long blockId = blocks[i];
					try {
						final var blockSpecCopy = new BlockSpec(blockSpec);
						blockSpecCopy.fromLinearIndex(blockId);
						/* leave the block untouched if the canvas has no painted voxel in it */
						if (!hasPaintedVoxel(Views.interval(canvas, blockSpecCopy.asInterval())))
							continue;
						final RandomAccessibleInterval<Pair<I, UnsignedLongType>> backgroundWithCanvas = Views
								.interval(Views.pair(background, canvas), blockSpecCopy.asInterval());
						final RandomAccessibleInterval<I> mergedData = Converters
								.convert(backgroundWithCanvas, (s, t) -> pickFirstIfSecondIsInvalid(s.getA(), s.getB(), t),
										type.createVariable());

						/* canvas and dataset share the grid, so the merged block is written back unchanged */
						N5Utils.saveBlock(mergedData, datasetSpec.container, datasetSpec.dataset, datasetSpec.attributes);
						localBlockDiffs.put(blockId, createBlockDiffFromCanvasIntegerType(backgroundWithCanvas));
					} catch (Exception e) {
						LOG.error("Error processing block {}", blockId, e);
					}
				}
				if (!localBlockDiffs.isEmpty()) {
					synchronized (blockDiff) {
						localBlockDiffs.forEachEntry((id, diff) -> {
							blockDiff.put(id, diff);
							return true;
						});
					}
				}
			}));
		}

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		threadPool.shutdown();
	}

	private static void downsampleAndWriteBlocksLabelMultisetType(
			final long[] affectedTargetBlocks,
			final N5Writer n5,
			final DatasetSpec sourceDataset,
			final DatasetSpec targetDataset,
			final BlockSpec targetBlockSpec,
			final Scale3D targetToPrevious,
			final int[] relativeFactors,
			final int targetMaxNumEntries,
			final int level,
			final SpatialMapping mapping,
			final TLongObjectHashMap<BlockDiff> blockDiffsAt,
			Optional<ReadOnlyDoubleWrapper> progressOpt) throws IOException {

		// In older converted data the "isLabelMultiset" attribute may not be present in s1,s2,... datasets.
		// Make sure the attribute is set to avoid "is not a label multiset" exception.
		n5.setAttribute(sourceDataset.dataset, N5Helpers.IS_LABEL_MULTISET_KEY, true);
		n5.setAttribute(targetDataset.dataset, N5Helpers.IS_LABEL_MULTISET_KEY, true);

		/* the levels may be nD; downsample on the 3D spatial slice and map block I/O back to nD */
		final RandomAccessibleInterval<LabelMultisetType> openedSource = LabelMultisetUtilsKt.openLabelMultiset(n5, sourceDataset.dataset);
		final RandomAccessibleInterval<LabelMultisetType> sourceData = mapping.to3D(openedSource);
		final long[] sourceSpatialDimensions = mapping.spatialProjection(sourceDataset.dimensions);
		final int[] sourceSpatialBlockSize = mapping.spatialProjection(sourceDataset.blockSize);

		final double increment;
		if (progressOpt.isPresent()) {
			final var progress = progressOpt.get();
			final var delta = Math.max(0.0, .9 - progress.get());
			increment = delta / affectedTargetBlocks.length;
		} else {
			increment = 0.0;
		}

		final ThreadFactory build = new ThreadFactoryBuilder().setNameFormat("downsample-and-write-blocks-label-multiset-%d").build();
		try (ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), build)) {
			final ArrayList<Future<?>> futures = new ArrayList<>();
			for (final long targetBlock : affectedTargetBlocks) {
				var future = threadPool.submit(() -> {
					final var blockSpecCopy = new BlockSpec(targetBlockSpec);
					blockSpecCopy.fromLinearIndex(targetBlock);
					final double[] realSourceMin = ArrayMath.asDoubleArray3(blockSpecCopy.min);
					final double[] realSourceMax = ArrayMath.asDoubleArray3(ArrayMath.add3(blockSpecCopy.max, 1));
					targetToPrevious.apply(realSourceMin, realSourceMin);
					targetToPrevious.apply(realSourceMax, realSourceMax);

					LOG.debug(() -> "level=%d: realSourceMin=%s realSourceMax=%s".formatted(level, realSourceMin, realSourceMax));

					final long[] sourceMin = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.floor3(realSourceMin, realSourceMin)), sourceSpatialDimensions);
					final long[] sourceMax = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.ceil3(realSourceMax, realSourceMax)), sourceSpatialDimensions);
					final int[] size = Intervals.dimensionsAsIntArray(new FinalInterval(blockSpecCopy.min, blockSpecCopy.max));

					final long[] previousRelevantIntervalMin = sourceMin.clone();
					final long[] previousRelevantIntervalMax = ArrayMath.add3(sourceMax, -1);

					ArrayMath.divide3(sourceMin, sourceSpatialBlockSize, sourceMin);
					ArrayMath.divide3(sourceMax, sourceSpatialBlockSize, sourceMax);
					ArrayMath.add3(sourceMax, -1, sourceMax);
					ArrayMath.minOf3(sourceMax, sourceMin, sourceMax);

					/* the spatial block position addresses the nD dataset at the fixed non-spatial slice */
					final long[] targetSourcePos = mapping.toSourcePosition(blockSpecCopy.pos[0], blockSpecCopy.pos[1], blockSpecCopy.pos[2]);
					LOG.trace(() -> "Reading existing access at position %s and size %s. (%s %s)".formatted(targetSourcePos, size, blockSpecCopy.min, blockSpecCopy.max));
					VolatileLabelMultisetArray oldAccess = null;
					try {
						final DataBlock<?> block = n5.readBlock(targetDataset.dataset, targetDataset.attributes, targetSourcePos);
						oldAccess = block != null && block.getData() instanceof byte[]
								? LabelUtils.fromBytes((byte[]) block.getData(), (int) Intervals.numElements(size))
								: null;
					} catch (N5Exception.N5IOException e) {
						LOG.debug(e, () -> "");
						LOG.warn(() -> String.format("Could not read block %s of dataset %s during downsample. Regenerating block.", Arrays.toString(targetSourcePos), targetDataset.dataset));
					}

					final VolatileLabelMultisetArray newAccess;
					try {
						newAccess = downsampleVolatileLabelMultisetArrayAndSerialize(
								n5,
								targetDataset.dataset,
								targetDataset.attributes,
								Views.interval(sourceData, previousRelevantIntervalMin, previousRelevantIntervalMax),
								relativeFactors,
								targetMaxNumEntries,
								mapping.toSourceBlockSize(size),
								targetSourcePos);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					final int numElements = (int)Intervals.numElements(size);
					synchronized (blockDiffsAt) {
						blockDiffsAt.put(
								targetBlock,
								oldAccess == null
										? createBlockDiffOldDoesNotExist(newAccess, numElements)
										: createBlockDiff(oldAccess, newAccess, numElements));
						progressOpt.ifPresent(prop -> prop.set(prop.get() + increment));
					}
				});
				futures.add(future);
			}
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e);
				}
			}
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
			final SpatialMapping mapping,
			final TLongObjectHashMap<BlockDiff> blockDiffsAt
	) throws IOException {

		/* the levels may be nD; downsample on the 3D spatial slice and map block I/O back to nD */
		final RandomAccessibleInterval<I> openedPrevious = N5Utils.open(n5, previousDataset.dataset);
		final RandomAccessibleInterval<I> previousData = mapping.to3D(openedPrevious);
		final long[] previousSpatialDimensions = mapping.spatialProjection(previousDataset.dimensions);
		final int[] previousSpatialBlockSize = mapping.spatialProjection(previousDataset.blockSize);

		final ThreadFactory build = new ThreadFactoryBuilder().setNameFormat("downsample-and-write-blocks-integer-%d").build();
		final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), build);
		final ArrayList<Future<?>> futures = new ArrayList<>();

		for (final long targetBlock : affectedBlocks) {
			futures.add(threadPool.submit(() -> {
				final BlockSpec blockSpecCopy = new BlockSpec(blockSpec);
				blockSpecCopy.fromLinearIndex(targetBlock);
				final double[] blockMinDouble = ArrayMath.asDoubleArray3(blockSpecCopy.min);
				final double[] blockMaxDouble = ArrayMath.asDoubleArray3(ArrayMath.add3(blockSpecCopy.max, 1));
				targetToPrevious.apply(blockMinDouble, blockMinDouble);
				targetToPrevious.apply(blockMaxDouble, blockMaxDouble);

				final long[] blockMin = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.floor3(blockMinDouble, blockMinDouble)), previousSpatialDimensions);
				final long[] blockMax = ArrayMath.minOf3(ArrayMath.asLong3(ArrayMath.ceil3(blockMaxDouble, blockMaxDouble)), previousSpatialDimensions);
				final Interval targetInterval = new FinalInterval(blockSpecCopy.min, blockSpecCopy.max);
				final int[] size = Intervals.dimensionsAsIntArray(targetInterval);

				final long[] previousRelevantIntervalMin = blockMin.clone();
				final long[] previousRelevantIntervalMax = ArrayMath.add3(blockMax, -1);

				ArrayMath.divide3(blockMin, previousSpatialBlockSize, blockMin);
				ArrayMath.divide3(blockMax, previousSpatialBlockSize, blockMax);
				ArrayMath.add3(blockMax, -1, blockMax);
				ArrayMath.minOf3(blockMax, blockMin, blockMax);

				final BlockDiff blockDiff = downsampleIntegerTypeAndSerialize(
						n5,
						targetDataset.dataset,
						targetDataset.attributes,
						Views.interval(previousData, previousRelevantIntervalMin, previousRelevantIntervalMax),
						relativeFactors,
						size,
						targetInterval,
						mapping);
				synchronized (blockDiffsAt) {
					blockDiffsAt.put(targetBlock, blockDiff);
				}
			}));
		}
		for (final Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		threadPool.shutdown();
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
