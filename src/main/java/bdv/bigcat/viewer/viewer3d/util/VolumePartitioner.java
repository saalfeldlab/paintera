package bdv.bigcat.viewer.viewer3d.util;

import java.util.HashMap;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

/**
 * This class is responsible for create small subvolumes from a
 * RandomAccessibleInterval.
 *
 * @author vleite
 * @param <T>
 *
 */
public class VolumePartitioner< T >
{
	/** logger */
	private static final Logger LOGGER = LoggerFactory.getLogger( VolumePartitioner.class );

	/** number of voxels that must overlap between partitions */
	private final int[] OVERLAP;

	/** volume to be partitioned */
	private final RandomAccessibleInterval< T > volumeLabels;

	/** Minimum size of each partition the partition */
	private final int[] partitionSize;

	private final HashMap< Long, Chunk< T > > chunks;

	private final CellGrid grid;

	private final long[] gridDimensions;

	private final Dimensions dimensions;

	/**
	 * Constructor - initialize parameters
	 */
	public VolumePartitioner( final RandomAccessibleInterval< T > volumeLabels, final int[] partitionSize, final int[] cubeSize )
	{
		this.volumeLabels = volumeLabels;
		this.partitionSize = partitionSize;
		this.OVERLAP = cubeSize;
		this.chunks = new HashMap<>();
		this.grid = new CellGrid( Intervals.dimensionsAsLongArray( volumeLabels ), partitionSize );
		this.gridDimensions = this.grid.getGridDimensions();
		this.dimensions = new FinalDimensions( this.gridDimensions );

		if ( LOGGER.isTraceEnabled() )
			LOGGER.trace( "partition size: " + partitionSize[ 0 ] + " " + partitionSize[ 1 ] + " " + partitionSize[ 2 ] );
	}

	/**
	 * Given a specific position, return the chunk were this position is. If the
	 * chunk already exists, just return the chunk. If not, create a chunk for
	 * the informed position.
	 *
	 * @param position
	 *            x, y, z coordinates
	 * @return chunk were this position belongs to.
	 */
	public Pair< Chunk< T >, Boolean > getChunk( final Localizable gridLocation )
	{
		final long[] gridPosition = new long[ gridLocation.numDimensions() ];
		gridLocation.localize( gridPosition );
//		for ( int d = 0; d < gridPosition.length; ++d )
//			gridPosition[ d ] /= this.partitionSize[ d ];

		final long index = IntervalIndexer.positionToIndex( gridPosition, gridDimensions );

		if ( chunks.containsKey( index ) )
			return new ValuePair<>( chunks.get( index ), false );

		final long[] offset = IntStream.range( 0, gridPosition.length ).mapToLong( d -> gridPosition[ d ] * partitionSize[ d ] ).toArray();
		final long[] lower = IntStream.range( 0, offset.length ).mapToLong( d -> Math.max( offset[ d ] - OVERLAP[ d ], 0 ) ).toArray();
		final long[] upper = IntStream.range( 0, offset.length ).mapToLong( d -> Math.min( offset[ d ] + partitionSize[ d ], this.volumeLabels.dimension( d ) ) - 1 ).toArray();

		final Chunk< T > chunk = new Chunk<>( Views.interval( volumeLabels, lower, upper ), offset, ( int ) index );
		chunks.put( index, chunk );

		if ( LOGGER.isDebugEnabled() )
		{
			LOGGER.debug( "partition begins at: " + lower[ 0 ] + " " + lower[ 1 ] + " " + lower[ 2 ] );
			LOGGER.debug( "partition ends at: " + upper[ 0 ] + " " + upper[ 1 ] + " " + upper[ 2 ] );
		}

		return new ValuePair<>( chunk, true );
	}

	public void getVolumeOffset( final long[] offset )
	{
		offset[ 0 ] /= partitionSize[ 0 ];
		offset[ 1 ] /= partitionSize[ 1 ];
		offset[ 2 ] /= partitionSize[ 2 ];
	}

	public boolean isGridOffsetContained( final long offset, final int dimension )
	{
		return offset > 0 && offset < gridDimensions[ dimension ];
	}

	public boolean isGridOffsetContained( final long[] offset )
	{
		boolean isContained = true;
		for ( int d = 0; d < offset.length; ++d )
			isContained &= isGridOffsetContained( offset[ d ], d );
		return isContained;
	}

	public void indexToGridOffset( final long index, final long[] offset )
	{
		IntervalIndexer.indexToPosition( index, gridDimensions, offset );
	}

	public long gridOffsetToIndex( final Localizable offset )
	{
		return IntervalIndexer.positionToIndex( offset, dimensions );
	}

	public boolean isChunkPresent( final Localizable location )
	{
		return this.chunks.containsKey( gridOffsetToIndex( location ) );
	}
}
