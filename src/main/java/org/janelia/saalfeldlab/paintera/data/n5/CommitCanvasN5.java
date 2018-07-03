package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.janelia.saalfeldlab.n5.ByteArrayDataBlock;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.cache.BlocksForLabelFromFile;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class CommitCanvasN5 implements BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final N5Writer n5;

	private final String dataset;

	public CommitCanvasN5( final N5Writer n5, final String dataset )
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
	public void accept( final CachedCellImg< UnsignedLongType, ? > canvas, final long[] blocks )
	{
		try
		{
			final boolean isPainteraDataset = N5Helpers.isPainteraDataset( n5, this.dataset );
			final String dataset = isPainteraDataset ? this.dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET : this.dataset;
			final boolean isMultiscale = N5Helpers.isMultiScale( n5, dataset );

			final String uniqueLabelsPath = this.dataset + "/unique-labels";
			final String labelToBlockMappingPath = this.dataset + "/label-to-block-mapping";
			LOG.warn( "uniqueLabelsPath {}", uniqueLabelsPath );

			final boolean hasUniqueLabels = n5.exists( uniqueLabelsPath );
			final boolean hasLabelToBlockMapping = n5.exists( labelToBlockMappingPath );
			final boolean updateLabelToBlockMapping = isPainteraDataset && hasUniqueLabels && hasLabelToBlockMapping && n5 instanceof N5FSReader;

			final CellGrid canvasGrid = canvas.getCellGrid();

			final String highestResolutionDataset = isMultiscale ? Paths.get( dataset, N5Helpers.listAndSortScaleDatasets( n5, dataset )[ 0 ] ).toString() : dataset;
			final String highestResolutionDatasetUniqueLabels = Paths.get( uniqueLabelsPath, N5Helpers.listAndSortScaleDatasets( n5, uniqueLabelsPath )[ 0 ] ).toString();
			final String highestResolutionLabelToBlockMapping = updateLabelToBlockMapping ? Paths.get( new N5FSMeta( ( N5FSReader ) n5, dataset ).basePath(), labelToBlockMappingPath, "s0" ).toAbsolutePath().toString() : null;
			LOG.warn( "highestResolutionDatasetUniqueLabels {}", highestResolutionDatasetUniqueLabels );
			LOG.warn( "Label to block mapping at highest resolution {} {}", highestResolutionLabelToBlockMapping, updateLabelToBlockMapping );
			final String highestResolutionLabelToBlockMappingPattern = updateLabelToBlockMapping ? highestResolutionLabelToBlockMapping + "/%d" : null;
			final BlocksForLabelFromFile highestResolutionBlocksForLabelLoader = new BlocksForLabelFromFile( highestResolutionLabelToBlockMappingPattern );

			if ( !Optional.ofNullable( n5.getAttribute( highestResolutionDataset, N5Helpers.LABEL_MULTISETTYPE_KEY, Boolean.class ) ).orElse( false ) ) { throw new RuntimeException( "Only label multiset type accepted currently!" ); }

			final DatasetAttributes highestResolutionAttributes = n5.getDatasetAttributes( highestResolutionDataset );
			final DatasetAttributes highestResolutionAttributesUniqueLabels = updateLabelToBlockMapping ? n5.getDatasetAttributes( highestResolutionDatasetUniqueLabels ) : null;
			final CellGrid highestResolutionGrid = new CellGrid( highestResolutionAttributes.getDimensions(), highestResolutionAttributes.getBlockSize() );

			if ( !highestResolutionGrid.equals( canvasGrid ) )
			{
				LOG.error( "Canvas grid {} and highest resolution dataset grid {} incompatible!", canvasGrid, highestResolutionGrid );
				throw new RuntimeException( String.format( "Canvas grid %s and highest resolution dataset grid %s incompatible!", canvasGrid, highestResolutionGrid ) );
			}

			final int[] highestResolutionBlockSize = highestResolutionAttributes.getBlockSize();
			final long[] highestResolutionDimensions = highestResolutionAttributes.getDimensions();

			final long[] gridPosition = new long[ highestResolutionBlockSize.length ];
			final long[] min = new long[ highestResolutionBlockSize.length ];
			final long[] max = new long[ highestResolutionBlockSize.length ];

			final RandomAccessibleInterval< LabelMultisetType > highestResolutionData = LabelUtils.openVolatile( n5, highestResolutionDataset );

			LOG.debug( "Persisting canvas with grid={} into background with grid={}", canvasGrid, highestResolutionGrid );

			for ( final long blockId : blocks )
			{
				highestResolutionGrid.getCellGridPositionFlat( blockId, gridPosition );
				Arrays.setAll( min, d -> gridPosition[ d ] * highestResolutionBlockSize[ d ] );
				Arrays.setAll( max, d -> Math.min( min[ d ] + highestResolutionBlockSize[ d ], highestResolutionDimensions[ d ] ) - 1 );

				final RandomAccessibleInterval< LabelMultisetType > convertedToMultisets = Converters.convert(
						( RandomAccessibleInterval< UnsignedLongType > ) canvas,
						new FromIntegerTypeConverter<>(),
						FromIntegerTypeConverter.geAppropriateType() );

				final IntervalView< Pair< LabelMultisetType, LabelMultisetType > > blockWithBackground =
						Views.interval( Views.pair( convertedToMultisets, highestResolutionData ), min, max );

				final int numElements = ( int ) Intervals.numElements( blockWithBackground );

				final Iterable< LabelMultisetType > pairIterable = () -> new Iterator< LabelMultisetType >()
				{

					Iterator< Pair< LabelMultisetType, LabelMultisetType > > iterator = Views.flatIterable( blockWithBackground ).iterator();

					@Override
					public boolean hasNext()
					{
						return iterator.hasNext();
					}

					@Override
					public LabelMultisetType next()
					{
						final Pair< LabelMultisetType, LabelMultisetType > p = iterator.next();
						final LabelMultisetType a = p.getA();
						if ( a.entrySet().iterator().next().getElement().id() == Label.INVALID )
						{
							return p.getB();
						}
						else
						{
							return a;
						}
					}

				};

				final byte[] byteData = LabelUtils.serializeLabelMultisetTypes( pairIterable, numElements );
				final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock( Intervals.dimensionsAsIntArray( blockWithBackground ), gridPosition, byteData );
				n5.writeBlock( highestResolutionDataset, highestResolutionAttributes, dataBlock );

				if ( updateLabelToBlockMapping )
				{
					final long[] previousData = Optional
							.ofNullable( n5.readBlock( highestResolutionDatasetUniqueLabels, highestResolutionAttributesUniqueLabels, gridPosition ) )
							.map( b -> ( LongArrayDataBlock ) b )
							.map( LongArrayDataBlock::getData )
							.orElse( new long[] {} );
					final TLongHashSet previousDataAsSet = new TLongHashSet( previousData );
					final TLongHashSet currentDataAsSet = new TLongHashSet();
					final TLongHashSet wasAdded = new TLongHashSet();
					final TLongHashSet wasRemoved = new TLongHashSet();
					final IntervalView< Pair< UnsignedLongType, LabelMultisetType > > relevantData = Views.interval( Views.pair( canvas, highestResolutionData ), min, max );
					for ( final Pair< UnsignedLongType, LabelMultisetType > p : Views.iterable( relevantData ) )
					{
						final UnsignedLongType pa = p.getA();
						final LabelMultisetType pb = p.getB();
						final long pav = pa.getIntegerLong();
						if ( pav == Label.INVALID )
						{
							pb
									.entrySet()
									.stream()
									.map( Entry::getElement )
									.mapToLong( Label::id )
									.forEach( currentDataAsSet::add );
						}
						else
						{
							currentDataAsSet.add( pav );
						}
					}

					for ( final TLongIterator pIt = previousDataAsSet.iterator(); pIt.hasNext(); )
					{
						final long p = pIt.next();
						if ( !currentDataAsSet.contains( p ) )
						{
							wasRemoved.add( p );
						}
					}

					for ( final TLongIterator cIt = currentDataAsSet.iterator(); cIt.hasNext(); )
					{
						final long c = cIt.next();
						if ( !previousDataAsSet.contains( c ) )
						{
							wasAdded.add( c );
						}
					}

					LOG.warn( "was added {}", wasAdded );
					LOG.warn( "was removed {}", wasRemoved );

					final HashWrapper< Interval > wrappedInterval = HashWrapper.interval( new FinalInterval( min, max ) );

					for ( final TLongIterator wasAddedIt = wasAdded.iterator(); wasAddedIt.hasNext(); )
					{
						final long wasAddedId = wasAddedIt.next();
						final Set< HashWrapper< Interval > > containedIntervals = Arrays
								.stream( highestResolutionBlocksForLabelLoader.apply( wasAddedId ) )
								.map( HashWrapper::interval )
								.collect( Collectors.toSet() );
						containedIntervals.add( wrappedInterval );
						final File targetPath = new File( String.format( highestResolutionLabelToBlockMappingPattern, wasAddedId ) );
//						LOG.warn( "Writing {} to {}", containedIntervals, targetPath );
						try (FileOutputStream fos = new FileOutputStream( targetPath ))
						{
							try (DataOutputStream dos = new DataOutputStream( fos ))
							{
								for ( final HashWrapper< Interval > wi : containedIntervals )
								{
									final Interval interval = wi.getData();
									dos.writeLong( interval.min( 0 ) );
									dos.writeLong( interval.min( 1 ) );
									dos.writeLong( interval.min( 2 ) );

									dos.writeLong( interval.max( 0 ) );
									dos.writeLong( interval.max( 1 ) );
									dos.writeLong( interval.max( 2 ) );
								}
							}
						}
					}

					for ( final TLongIterator wasRemovedIt = wasRemoved.iterator(); wasRemovedIt.hasNext(); )
					{
						final long wasRemovedId = wasRemovedIt.next();
						final Set< HashWrapper< Interval > > containedIntervals = Arrays
								.stream( highestResolutionBlocksForLabelLoader.apply( wasRemovedId ) )
								.map( HashWrapper::interval )
								.collect( Collectors.toSet() );
						containedIntervals.remove( wrappedInterval );
						try (FileOutputStream fos = new FileOutputStream( new File( String.format( highestResolutionLabelToBlockMappingPattern, wasRemovedId ) ) ))
						{
							try (DataOutputStream dos = new DataOutputStream( fos ))
							{
								for ( final HashWrapper< Interval > wi : containedIntervals )
								{
									final Interval interval = wi.getData();
									dos.writeLong( interval.min( 0 ) );
									dos.writeLong( interval.min( 1 ) );
									dos.writeLong( interval.min( 2 ) );

									dos.writeLong( interval.max( 0 ) );
									dos.writeLong( interval.max( 1 ) );
									dos.writeLong( interval.max( 2 ) );
								}
							}
						}
					}
				}

			}

			if ( isMultiscale )
			{
				final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets( n5, dataset );
				for ( int level = 1; level < scaleDatasets.length; ++level )
				{
					final String targetDataset = Paths.get( dataset, scaleDatasets[ level ] ).toString();
					final String previousDataset = Paths.get( dataset, scaleDatasets[ level - 1 ] ).toString();

					final DatasetAttributes targetAttributes = n5.getDatasetAttributes( targetDataset );
					final DatasetAttributes previousAttributes = n5.getDatasetAttributes( previousDataset );

					final double[] targetDownsamplingFactors = n5.getAttribute( targetDataset, N5Helpers.DOWNSAMPLING_FACTORS_KEY, double[].class );
					final double[] previousDownsamplingFactors = Optional.ofNullable( n5.getAttribute( previousDataset, N5Helpers.DOWNSAMPLING_FACTORS_KEY, double[].class ) ).orElse( new double[] { 1, 1, 1 } );
					final double[] relativeDownsamplingFactors = new double[ targetDownsamplingFactors.length ];
					Arrays.setAll( relativeDownsamplingFactors, d -> targetDownsamplingFactors[ d ] / previousDownsamplingFactors[ d ] );

					final CellGrid targetGrid = new CellGrid( targetAttributes.getDimensions(), targetAttributes.getBlockSize() );
					final CellGrid previousGrid = new CellGrid( previousAttributes.getDimensions(), previousAttributes.getBlockSize() );

					final long[] affectedBlocks = MaskedSource.scaleBlocksToHigherLevel( blocks, highestResolutionGrid, targetGrid, targetDownsamplingFactors ).toArray();

					final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > previousData = LabelUtils.openVolatile( n5, previousDataset );
					final RandomAccess< Cell< VolatileLabelMultisetArray > > previousCellsAccess = previousData.getCells().randomAccess();

					final long[] blockPosition = new long[ targetGrid.numDimensions() ];
					final double[] blockMinDouble = new double[ blockPosition.length ];
					final double[] blockMaxDouble = new double[ blockPosition.length ];

					final long[] blockMin = new long[ blockMinDouble.length ];
					final long[] blockMax = new long[ blockMinDouble.length ];

					final int[] targetBlockSize = targetAttributes.getBlockSize();
					final int[] previousBlockSize = previousAttributes.getBlockSize();

					final long[] targetDimensions = targetAttributes.getDimensions();
					final long[] previousDimensions = previousAttributes.getDimensions();

					final Scale3D targetToPrevious = new Scale3D( relativeDownsamplingFactors );

					final TLongHashSet relevantBlocksAtPrevious = new TLongHashSet();

					final int targetMaxNumEntries = Optional.ofNullable( n5.getAttribute( targetDataset, N5Helpers.MAX_NUM_ENTRIES_KEY, Integer.class ) ).orElse( -1 );

					final int[] relativeFactors = DoubleStream.of( relativeDownsamplingFactors ).mapToInt( d -> ( int ) d ).toArray();

					final int[] ones = { 1, 1, 1 };

					final long[] previousRelevantIntervalMin = new long[ blockMin.length ];
					final long[] previousRelevantIntervalMax = new long[ blockMin.length ];

					LOG.debug( "level={}: Got {} blocks", level, affectedBlocks.length );

					for ( final long targetBlock : affectedBlocks )
					{
						targetGrid.getCellGridPositionFlat( targetBlock, blockPosition );

						blockMinDouble[ 0 ] = blockPosition[ 0 ] * targetBlockSize[ 0 ];
						blockMinDouble[ 1 ] = blockPosition[ 1 ] * targetBlockSize[ 1 ];
						blockMinDouble[ 2 ] = blockPosition[ 2 ] * targetBlockSize[ 2 ];

						blockMaxDouble[ 0 ] = blockMinDouble[ 0 ] + targetBlockSize[ 0 ];
						blockMaxDouble[ 1 ] = blockMinDouble[ 1 ] + targetBlockSize[ 1 ];
						blockMaxDouble[ 2 ] = blockMinDouble[ 2 ] + targetBlockSize[ 2 ];

						LOG.debug( "level={}: Downsampling block {} with min={} max={} in tarspace.", level, blockPosition, blockMinDouble, blockMaxDouble );

						final int[] size = {
								( int ) ( Math.min( blockMaxDouble[ 0 ], targetDimensions[ 0 ] ) - blockMinDouble[ 0 ] ),
								( int ) ( Math.min( blockMaxDouble[ 1 ], targetDimensions[ 1 ] ) - blockMinDouble[ 1 ] ),
								( int ) ( Math.min( blockMaxDouble[ 2 ], targetDimensions[ 2 ] ) - blockMinDouble[ 2 ] ) };

						targetToPrevious.apply( blockMinDouble, blockMinDouble );
						targetToPrevious.apply( blockMaxDouble, blockMaxDouble );

						blockMin[ 0 ] = Math.min( ( long ) blockMinDouble[ 0 ], previousDimensions[ 0 ] );
						blockMin[ 1 ] = Math.min( ( long ) blockMinDouble[ 1 ], previousDimensions[ 0 ] );
						blockMin[ 2 ] = Math.min( ( long ) blockMinDouble[ 2 ], previousDimensions[ 0 ] );

						blockMax[ 0 ] = Math.min( ( long ) blockMaxDouble[ 0 ], previousDimensions[ 0 ] );
						blockMax[ 1 ] = Math.min( ( long ) blockMaxDouble[ 1 ], previousDimensions[ 1 ] );
						blockMax[ 2 ] = Math.min( ( long ) blockMaxDouble[ 2 ], previousDimensions[ 2 ] );

						previousRelevantIntervalMin[ 0 ] = blockMin[ 0 ];
						previousRelevantIntervalMin[ 1 ] = blockMin[ 1 ];
						previousRelevantIntervalMin[ 2 ] = blockMin[ 2 ];

						previousRelevantIntervalMax[ 0 ] = blockMax[ 0 ] - 1;
						previousRelevantIntervalMax[ 1 ] = blockMax[ 1 ] - 1;
						previousRelevantIntervalMax[ 2 ] = blockMax[ 2 ] - 1;

						blockMin[ 0 ] /= previousBlockSize[ 0 ];
						blockMin[ 1 ] /= previousBlockSize[ 1 ];
						blockMin[ 2 ] /= previousBlockSize[ 2 ];

						blockMax[ 0 ] = Math.max( blockMax[ 0 ] / previousBlockSize[ 0 ] - 1, blockMin[ 0 ] );
						blockMax[ 1 ] = Math.max( blockMax[ 1 ] / previousBlockSize[ 1 ] - 1, blockMin[ 1 ] );
						blockMax[ 2 ] = Math.max( blockMax[ 2 ] / previousBlockSize[ 2 ] - 1, blockMin[ 2 ] );

						LOG.debug( "level={}: Downsampling contained label lists for block {} with min={} max={} in previous space.", level, blockPosition, blockMin, blockMax );

						relevantBlocksAtPrevious.clear();
						Grids.forEachOffset( blockMin, blockMax, ones, offset -> {
							previousCellsAccess.setPosition( offset[ 0 ], 0 );
							previousCellsAccess.setPosition( offset[ 1 ], 1 );
							previousCellsAccess.setPosition( offset[ 2 ], 2 );
//							relevantBlocksAtPrevious.addAll( previousCellsAccess.get().getData().containedLabels() );
						} );

						LOG.debug( "level={}: Creating downscaled for interval=({} {})", level, previousRelevantIntervalMin, previousRelevantIntervalMax );

						final VolatileLabelMultisetArray updatedAccess = LabelMultisetTypeDownscaler.createDownscaledCell(
								Views.zeroMin( Views.interval( previousData, previousRelevantIntervalMin, previousRelevantIntervalMax ) ),
								relativeFactors,
								targetMaxNumEntries );

						final byte[] serializedAccess = new byte[ LabelMultisetTypeDownscaler.getSerializedVolatileLabelMultisetArraySize( updatedAccess ) ];
						LabelMultisetTypeDownscaler.serializeVolatileLabelMultisetArray( updatedAccess, serializedAccess );

						LOG.debug( "level={}: Writing block of size {} at {}.", level, size, blockPosition );

						n5.writeBlock( targetDataset, targetAttributes, new ByteArrayDataBlock( size, blockPosition, serializedAccess ) );

					}

				}

//					throw new RuntimeException( "multi-scale export not implemented yet!" );
			}

//				if ( isIntegerType() )
//					commitForIntegerType( n5, dataset, canvas );
		}
		catch ( final IOException | ReflectionException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
