package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.labels.labelset.LabelMultisetType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;

/**
 * This class is responsible for create small subvolumes from a
 * RandomAccessibleInterval. Given the data, the size of each partition and the
 * size of the cube (from marching cubes algorithm), this class return a vector
 * with subvolumes and the offsets of each subvolume.
 * 
 * @author vleite
 *
 */
public class VolumePartitioner
{
	/** logger */
	private static final Logger LOGGER = LoggerFactory.getLogger( VolumePartitioner.class );

	/** number of voxels that must overlap between partitions */
	private static final long OVERLAP = 1;

	/** volume to be partitioned */
	private final RandomAccessibleInterval< LabelMultisetType > volumeLabels;

	/** Minimum size of each partition the partition */
	private final int[] partitionSize;

	/**
	 * dimension of the cube that will be used on the marching cubes algorithm
	 */
	private final int[] cubeSize;

	/**
	 * Constructor - initialize parameters
	 */
	public VolumePartitioner( RandomAccessibleInterval< LabelMultisetType > volumeLabels, int[] partitionSize, int[] cubeSize )
	{
		this.volumeLabels = volumeLabels;
		this.partitionSize = partitionSize;
		this.cubeSize = cubeSize;

		if ( LOGGER.isTraceEnabled() )
		{
			LOGGER.trace( "partition defined as: " + partitionSize[ 0 ] + " " + partitionSize[ 1 ] + " " + partitionSize[ 2 ] );
		}
	}

	/**
	 * Method to partitioning the data in small chunks.
	 * 
	 * @param subvolumes
	 *            list of each subvolume created
	 * @param offsets
	 *            the offset of each subvolume
	 */
	public List< Chunk > dataPartitioning( )
	{
		List< Chunk > chunks = new ArrayList< Chunk >();

		for ( long bx = volumeLabels.min( 0 ); ( bx + partitionSize[ 0 ] ) <= volumeLabels.max( 0 ); bx += partitionSize[ 0 ] )
		{
			for ( long by = volumeLabels.min( 1 ); ( by + partitionSize[ 1 ] ) <= volumeLabels.max( 1 ); by += partitionSize[1 ] )
			{
				for ( long bz = volumeLabels.min( 2 ); ( bz + partitionSize[ 2 ] ) <= volumeLabels.max( 2 ); bz += partitionSize[ 2 ] )
				{
					long[] begin = new long[] { bx, by, bz };
					long[] end = new long[] { begin[ 0 ] + partitionSize[ 0 ],
							begin[ 1 ] + partitionSize[ 1 ],
							begin[ 2 ] + partitionSize[ 2 ] };

					if (LOGGER.isTraceEnabled())
					{
						LOGGER.trace( "begin: " + bx + " " + by + " " + bz );
						LOGGER.trace( "end: " + end[ 0 ] + " " + end[ 1 ] + " " + end[ 2 ] );
					}

					if ( begin[ 0 ] - OVERLAP >= 0 )
					{
						begin[ 0 ] -= OVERLAP;
					}

					if ( begin[ 1 ] - OVERLAP >= 0 )
					{
						begin[ 1 ] -= OVERLAP;
					}

					if ( begin[ 2 ] - OVERLAP >= 0 )
					{
						begin[ 2 ] -= OVERLAP;
					}

					if ( volumeLabels.max( 0 ) - end[ 0 ] < partitionSize[ 0 ] )
					{
						end[ 0 ] = volumeLabels.max( 0 );
					}

					if ( volumeLabels.max( 1 ) - end[ 1 ] < partitionSize[ 1 ] )
					{
						end[ 1 ] = volumeLabels.max( 1 );
					}

					if ( volumeLabels.max( 2 ) - end[ 2 ] < partitionSize[ 2 ] )
					{
						end[ 2 ] = volumeLabels.max( 2 );
					}

					final Chunk chunk = new Chunk();
					chunk.setVolume( Views.interval( volumeLabels, begin, end ) );
					chunk.setOffset( new int[] { ( int ) ( begin[ 0 ] / cubeSize[ 0 ] ), ( int ) ( begin[ 1 ] / cubeSize[ 1 ] ), ( int ) ( begin[ 2 ] / cubeSize[ 2 ] ) } );
					chunks.add( chunk );

					if ( LOGGER.isDebugEnabled() )
					{
						LOGGER.debug( "partition begins at: " + begin[ 0 ] + " " + begin[ 1 ] + " " + begin[ 2 ] );
						LOGGER.debug( "partition ends at: " + end[ 0 ] + " " + end[ 1 ] + " " + end[ 2 ] );
					}
				}
			}
		}

		return chunks;
	}
}
