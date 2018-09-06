package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converters;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.label.FromIntegerTypeConverter;
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
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitCanvasN5 implements BiConsumer<CachedCellImg<UnsignedLongType, ?>, long[]>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final N5Writer n5;

	private final String dataset;

	public CommitCanvasN5(final N5Writer n5, final String dataset)
	{
		super();
		this.n5 = n5;
		this.dataset = dataset;
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
	public void accept(final CachedCellImg<UnsignedLongType, ?> canvas, final long[] blocks)
	{
		LOG.info("Committing canvas");
		try
		{
			final boolean isPainteraDataset = N5Helpers.isPainteraDataset(n5, this.dataset);
			final String  dataset           = isPainteraDataset
			                                  ? this.dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET
			                                  : this.dataset;
			final boolean isMultiscale      = N5Helpers.isMultiScale(n5, dataset);

			final String uniqueLabelsPath        = this.dataset + "/unique-labels";
			LOG.debug("uniqueLabelsPath {}", uniqueLabelsPath);

			final boolean hasUniqueLabels           = n5.exists(uniqueLabelsPath);
			final boolean updateLabelToBlockMapping = isPainteraDataset && hasUniqueLabels && n5 instanceof N5FSReader;

			final CellGrid canvasGrid = canvas.getCellGrid();

			final String highestResolutionDataset             = isMultiscale ? Paths.get(
					dataset,
					N5Helpers.listAndSortScaleDatasets(n5, dataset)[0]
			                                                                            ).toString() : dataset;
			final String highestResolutionDatasetUniqueLabels = Paths.get(
					uniqueLabelsPath,
					N5Helpers.listAndSortScaleDatasets(n5, uniqueLabelsPath)[0]
			                                                             ).toString();
			LOG.debug("highestResolutionDatasetUniqueLabels {}", highestResolutionDatasetUniqueLabels);
			final LabelBlockLookup labelBlockLoader = N5Helpers.getLabelBlockLookup(n5, this.dataset);

			if (!Optional.ofNullable(n5.getAttribute(
					highestResolutionDataset,
					N5Helpers.LABEL_MULTISETTYPE_KEY,
					Boolean.class
			                                        )).orElse(false))
			{
				throw new RuntimeException("Only label multiset type accepted currently!");
			}

			final DatasetAttributes highestResolutionAttributes             = n5.getDatasetAttributes(
					highestResolutionDataset);
			final DatasetAttributes highestResolutionAttributesUniqueLabels = updateLabelToBlockMapping
			                                                                  ? n5.getDatasetAttributes(
					highestResolutionDatasetUniqueLabels)
			                                                                  : null;
			final CellGrid          highestResolutionGrid                   = new CellGrid(
					highestResolutionAttributes.getDimensions(),
					highestResolutionAttributes.getBlockSize()
			);

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

			final int[]  highestResolutionBlockSize  = highestResolutionAttributes.getBlockSize();
			final long[] highestResolutionDimensions = highestResolutionAttributes.getDimensions();

			final long[] gridPosition = new long[highestResolutionBlockSize.length];
			final long[] min          = new long[highestResolutionBlockSize.length];
			final long[] max          = new long[highestResolutionBlockSize.length];

			final RandomAccessibleInterval<LabelMultisetType> highestResolutionData = LabelUtils.openVolatile(
					n5,
					highestResolutionDataset
			                                                                                                 );

			LOG.debug("Persisting canvas with grid={} into background with grid={}", canvasGrid,
					highestResolutionGrid);

			for (final long blockId : blocks)
			{
				highestResolutionGrid.getCellGridPositionFlat(blockId, gridPosition);
				Arrays.setAll(min, d -> gridPosition[d] * highestResolutionBlockSize[d]);
				Arrays.setAll(
						max,
						d -> Math.min(min[d] + highestResolutionBlockSize[d], highestResolutionDimensions[d]) - 1
				             );

				final RandomAccessibleInterval<LabelMultisetType> convertedToMultisets = Converters.convert(
						(RandomAccessibleInterval<UnsignedLongType>) canvas,
						new FromIntegerTypeConverter<>(),
						FromIntegerTypeConverter.geAppropriateType()
				                                                                                           );

				final IntervalView<Pair<LabelMultisetType, LabelMultisetType>> blockWithBackground =
						Views.interval(Views.pair(convertedToMultisets, highestResolutionData), min, max);

				final int numElements = (int) Intervals.numElements(blockWithBackground);

				final Iterable<LabelMultisetType> pairIterable = () -> new Iterator<LabelMultisetType>()
				{

					Iterator<Pair<LabelMultisetType, LabelMultisetType>> iterator = Views.flatIterable(
							blockWithBackground).iterator();

					@Override
					public boolean hasNext()
					{
						return iterator.hasNext();
					}

					@Override
					public LabelMultisetType next()
					{
						final Pair<LabelMultisetType, LabelMultisetType> p = iterator.next();
						final LabelMultisetType                          a = p.getA();
						if (a.entrySet().iterator().next().getElement().id() == Label.INVALID)
						{
							return p.getB();
						}
						else
						{
							return a;
						}
					}

				};

				final byte[]             byteData  = LabelUtils.serializeLabelMultisetTypes(pairIterable, numElements);
				final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(Intervals.dimensionsAsIntArray(
						blockWithBackground), gridPosition, byteData);
				n5.writeBlock(highestResolutionDataset, highestResolutionAttributes, dataBlock);

				if (updateLabelToBlockMapping)
				{
					updateHighestResolutionLabelMapping(
							n5,
							highestResolutionDatasetUniqueLabels,
							highestResolutionAttributesUniqueLabels,
							gridPosition,
							Views.interval(Views.pair(canvas, highestResolutionData), min, max),
							labelBlockLoader
					                                   );
				}

			}

			if (isMultiscale)
			{
				final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(n5, dataset);
				for (int level = 1; level < scaleDatasets.length; ++level)
				{
					final String targetDataset   = Paths.get(dataset, scaleDatasets[level]).toString();
					final String previousDataset = Paths.get(dataset, scaleDatasets[level - 1]).toString();

					final DatasetAttributes targetAttributes   = n5.getDatasetAttributes(targetDataset);
					final DatasetAttributes previousAttributes = n5.getDatasetAttributes(previousDataset);

					final String                 datasetUniqueLabelsPrevious        = Paths.get(
							uniqueLabelsPath,
							N5Helpers.listAndSortScaleDatasets(n5, uniqueLabelsPath)[level - 1]
					                                                                           ).toString();
					final String                 datasetUniqueLabels                = Paths.get(
							uniqueLabelsPath,
							N5Helpers.listAndSortScaleDatasets(n5, uniqueLabelsPath)[level]
					                                                                           ).toString();
					final DatasetAttributes      attributesUniqueLabelsPrevious     = updateLabelToBlockMapping
					                                                                  ? n5.getDatasetAttributes(
							datasetUniqueLabelsPrevious)
					                                                                  : null;
					final DatasetAttributes      attributesUniqueLabels             = updateLabelToBlockMapping
					                                                                  ? n5.getDatasetAttributes(
							datasetUniqueLabels)
					                                                                  : null;

					final double[] targetDownsamplingFactors   = n5.getAttribute(
							targetDataset,
							N5Helpers.DOWNSAMPLING_FACTORS_KEY,
							double[].class
					                                                            );
					final double[] previousDownsamplingFactors = Optional.ofNullable(n5.getAttribute(
							previousDataset,
							N5Helpers.DOWNSAMPLING_FACTORS_KEY,
							double[].class
					                                                                                )).orElse(new
							double[] {1, 1, 1});
					final double[] relativeDownsamplingFactors = new double[targetDownsamplingFactors.length];
					Arrays.setAll(
							relativeDownsamplingFactors,
							d -> targetDownsamplingFactors[d] / previousDownsamplingFactors[d]
					             );

					final CellGrid targetGrid   = new CellGrid(
							targetAttributes.getDimensions(),
							targetAttributes.getBlockSize()
					);
					final CellGrid previousGrid = new CellGrid(
							previousAttributes.getDimensions(),
							previousAttributes.getBlockSize()
					);

					final long[] affectedBlocks = MaskedSource.scaleBlocksToHigherLevel(
							blocks,
							highestResolutionGrid,
							targetGrid,
							targetDownsamplingFactors
					                                                                   ).toArray();

					final CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray> previousData        =
							LabelUtils.openVolatile(
							n5,
							previousDataset
					                                                                                                                );
					final RandomAccess<Cell<VolatileLabelMultisetArray>>               previousCellsAccess =
							previousData.getCells().randomAccess();

					final int[] targetBlockSize   = targetAttributes.getBlockSize();
					final int[] previousBlockSize = previousAttributes.getBlockSize();

					final long[] targetDimensions   = targetAttributes.getDimensions();
					final long[] previousDimensions = previousAttributes.getDimensions();

					final Scale3D targetToPrevious = new Scale3D(relativeDownsamplingFactors);

					final int targetMaxNumEntries = Optional.ofNullable(n5.getAttribute(
							targetDataset,
							N5Helpers.MAX_NUM_ENTRIES_KEY,
							Integer.class
					                                                                   )).orElse(-1);

					final int[] relativeFactors = DoubleStream.of(relativeDownsamplingFactors).mapToInt(d -> (int) d)
							.toArray();

					final int[] ones = {1, 1, 1};

					LOG.debug("level={}: Got {} blocks", level, affectedBlocks.length);

					for (final long targetBlock : affectedBlocks)
					{
						final long[] blockPositionInTargetGrid = new long[targetGrid.numDimensions()];
						targetGrid.getCellGridPositionFlat(targetBlock, blockPositionInTargetGrid);

						final long[]   blockMinInTargetGrid = multiplyElementwise3(
								blockPositionInTargetGrid,
								targetBlockSize,
								new long[3]
						                                                          );
						final double[] blockMinDouble       = asDoubleArray3(blockMinInTargetGrid, new double[3]);
						final double[] blockMaxDouble       = add3(blockMinDouble, targetBlockSize, new double[3]);

						LOG.debug(
								"level={}: blockMinDouble={} blockMaxDouble={}",
								level,
								blockMinDouble,
								blockMaxDouble
						         );

						LOG.debug(
								"level={}: Downsampling block {} with min={} max={} in tarspace.",
								level,
								blockPositionInTargetGrid,
								blockMinDouble,
								blockMaxDouble
						         );

						final int[] size = {
								(int) (Math.min(blockMaxDouble[0], targetDimensions[0]) - blockMinDouble[0]),
								(int) (Math.min(blockMaxDouble[1], targetDimensions[1]) - blockMinDouble[1]),
								(int) (Math.min(blockMaxDouble[2], targetDimensions[2]) - blockMinDouble[2])};

						final long[] blockMaxInTargetGrid = add3(blockMinInTargetGrid, size, new long[3]);

						targetToPrevious.apply(blockMinDouble, blockMinDouble);
						targetToPrevious.apply(blockMaxDouble, blockMaxDouble);

						LOG.debug(
								"level={}: blockMinDouble={} blockMaxDouble={}",
								level,
								blockMinDouble,
								blockMaxDouble
						         );

						final long[] blockMin = minOf3(blockMinDouble, previousDimensions, new long[3]);
						final long[] blockMax = minOf3(blockMaxDouble, previousDimensions, new long[3]);

						final long[] previousRelevantIntervalMin = blockMin.clone();
						final long[] previousRelevantIntervalMax = add3(blockMax, -1, new long[3]);

						divide3(blockMin, previousBlockSize, blockMin);
						divide3(blockMax, previousBlockSize, blockMax);
						add3(blockMax, -1, blockMax);
						minOf3(blockMax, blockMin, blockMax);

						LOG.debug(
								"level={}: Downsampling contained label lists for block {} with min={} max={} in " +
										"previous space.",
								level,
								blockPositionInTargetGrid,
								blockMin,
								blockMax
						         );

						LOG.debug(
								"level={}: Creating downscaled for interval=({} {})",
								level,
								previousRelevantIntervalMin,
								previousRelevantIntervalMax
						         );

						final VolatileLabelMultisetArray updatedAccess = LabelMultisetTypeDownscaler
								.createDownscaledCell(
								Views.zeroMin(Views.interval(
										previousData,
										previousRelevantIntervalMin,
										previousRelevantIntervalMax
								                            )),
								relativeFactors,
								targetMaxNumEntries
						                                                                                                 );

						final byte[] serializedAccess = new byte[LabelMultisetTypeDownscaler
								.getSerializedVolatileLabelMultisetArraySize(
								updatedAccess)];
						LabelMultisetTypeDownscaler.serializeVolatileLabelMultisetArray(
								updatedAccess,
								serializedAccess
						                                                               );

						LOG.debug("level={}: Writing block of size {} at {}.", level, size, blockPositionInTargetGrid);

						n5.writeBlock(
								targetDataset,
								targetAttributes,
								new ByteArrayDataBlock(size, blockPositionInTargetGrid, serializedAccess)
						             );

						if (updateLabelToBlockMapping)
						{
							final TLongHashSet mergedContainedLabels = new TLongHashSet();
							// TODO find better way of iterating here
							LOG.warn(
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
								LOG.warn("level={}: offset={}: got contained labels: {}", level, offset, cl.length);
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

							final TLongHashSet wasAdded   = containedInFirstButNotInSecond(
									mergedContainedLabels,
									containedLabels
							                                                              );
							final TLongHashSet wasRemoved = containedInFirstButNotInSecond(
									containedLabels,
									mergedContainedLabels
							                                                              );

							LOG.warn(
									"level={}: Updating label to block mapping for {}. Added:   {}",
									level,
									blockMinInTargetGrid,
									wasAdded.size()
							        );
							LOG.warn(
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

					}

				}

				//					throw new RuntimeException( "multi-scale export not implemented yet!" );
			}

			//				if ( isIntegerType() )
			//					commitForIntegerType( n5, dataset, canvas );
		} catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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

	private static final TLongHashSet containedInFirstButNotInSecond(
			final TLongHashSet first,
			final TLongHashSet second)
	{
		final TLongHashSet notInSecond = new TLongHashSet();
		for (final TLongIterator fIt = first.iterator(); fIt.hasNext(); )
		{
			final long p = fIt.next();
			if (!second.contains(p))
			{
				notInSecond.add(p);
			}
		}
		return notInSecond;
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
		labelBlockLookup.write(level, id, containedIntervals.stream().toArray(Interval[]::new));
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
		final TLongHashSet wasAdded          = containedInFirstButNotInSecond(currentDataAsSet, previousDataAsSet);
		final TLongHashSet wasRemoved        = containedInFirstButNotInSecond(previousDataAsSet, currentDataAsSet);

		final int[] size = uniqueLabelsAttributes.getBlockSize();
		n5.writeBlock(
				uniqueLabelsDataset,
				uniqueLabelsAttributes,
				new LongArrayDataBlock(size, gridPosition, currentDataAsSet.toArray())
		             );

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

	private static long[] multiplyElementwise3(final long[] factor1, final long[] factor2, final long[] product)
	{
		product[0] = factor1[0] * factor2[0];
		product[1] = factor1[1] * factor2[1];
		product[2] = factor1[2] * factor2[2];
		return product;
	}

	private static long[] multiplyElementwise3(final long[] factor1, final int[] factor2, final long[] product)
	{
		product[0] = factor1[0] * factor2[0];
		product[1] = factor1[1] * factor2[1];
		product[2] = factor1[2] * factor2[2];
		return product;
	}

	private static double[] asDoubleArray3(final long[] source, final double[] target)
	{
		target[0] = source[0];
		target[1] = source[1];
		target[2] = source[2];
		return target;
	}

	private static double[] add3(final double[] summand1, final int[] summand2, final double[] sum)
	{
		sum[0] = summand1[0] + summand2[0];
		sum[1] = summand1[1] + summand2[1];
		sum[2] = summand1[2] + summand2[2];
		return sum;
	}

	private static long[] add3(final long[] summand1, final int[] summand2, final long[] sum)
	{
		sum[0] = summand1[0] + summand2[0];
		sum[1] = summand1[1] + summand2[1];
		sum[2] = summand1[2] + summand2[2];
		return sum;
	}

	private static long[] add3(final long[] summand1, final long summand2, final long[] sum)
	{
		sum[0] = summand1[0] + summand2;
		sum[1] = summand1[1] + summand2;
		sum[2] = summand1[2] + summand2;
		return sum;
	}

	private static long[] divide3(final long[] divident, final long[] divisor, final long[] quotient)
	{
		quotient[0] = divident[0] / divisor[0];
		quotient[1] = divident[1] / divisor[1];
		quotient[2] = divident[2] / divisor[2];
		return quotient;
	}

	private static long[] divide3(final long[] divident, final int[] divisor, final long[] quotient)
	{
		quotient[0] = divident[0] / divisor[0];
		quotient[1] = divident[1] / divisor[1];
		quotient[2] = divident[2] / divisor[2];
		return quotient;
	}

	private static long[] minOf3(final double[] arr1, final long[] arr2, final long[] min)
	{
		min[0] = Math.min((long) arr1[0], arr2[0]);
		min[1] = Math.min((long) arr1[1], arr2[1]);
		min[2] = Math.min((long) arr1[2], arr2[2]);
		return min;
	}

	private static long[] minOf3(final long[] arr1, final long[] arr2, final long[] min)
	{
		min[0] = Math.min(arr1[0], arr2[0]);
		min[1] = Math.min(arr1[1], arr2[1]);
		min[2] = Math.min(arr1[2], arr2[2]);
		return min;
	}

}
