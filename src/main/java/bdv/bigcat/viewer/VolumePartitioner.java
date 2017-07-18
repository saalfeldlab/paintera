package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.List;

import bdv.labels.labelset.LabelMultisetType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;

public class VolumePartitioner
{
	/** volume to be partitioned */
	private static RandomAccessibleInterval< LabelMultisetType > volumeLabels = null;

	private final int partitionXSize;

	private final int partitionYSize;

	private final int partitionZSize;

	private final long overlap = 1;

	/**
	 * Constructor Initialize parameters
	 */
	public VolumePartitioner( RandomAccessibleInterval< LabelMultisetType > volumeLabels, int[] partitionSize )
	{
		VolumePartitioner.volumeLabels = volumeLabels;
		this.partitionXSize = partitionSize[ 0 ];
		this.partitionYSize = partitionSize[ 1 ];
		this.partitionZSize = partitionSize[ 2 ];

		System.out.println( "partition defined as: " + partitionXSize + " " + partitionYSize + " " + partitionZSize );
	}

	public List< RandomAccessibleInterval< LabelMultisetType > > dataPartitioning( List< int[] > offsets )
	{
		List< RandomAccessibleInterval< LabelMultisetType > > parts = new ArrayList< RandomAccessibleInterval< LabelMultisetType > >();
		int offsetIdx = 0;

		for ( long bx = volumeLabels.min( 0 ); ( bx + partitionXSize ) <= volumeLabels.max( 0 ); bx += partitionXSize )
		{
			for ( long by = volumeLabels.min( 1 ); ( by + partitionYSize ) <= volumeLabels.max( 1 ); by += partitionYSize )
			{
				for ( long bz = volumeLabels.min( 2 ); ( bz + partitionZSize ) <= volumeLabels.max( 2 ); bz += partitionZSize )
				{
					long[] begin = new long[] { bx, by, bz };
					System.out.println( "begin: " + bx + " " + by + " " + bz );

					long[] end = new long[] { begin[ 0 ] + partitionXSize,
							begin[ 1 ] + partitionYSize,
							begin[ 2 ] + partitionZSize };

					System.out.println( "end: " + end[ 0 ] + " " + end[ 1 ] + " " + end[ 2 ] );

					if ( begin[ 0 ] - overlap >= 0 )
						begin[ 0 ] -= overlap;
					if ( begin[ 1 ] - overlap >= 0 )
						begin[ 1 ] -= overlap;
					if ( begin[ 2 ] - overlap >= 0 )
						begin[ 2 ] -= overlap;

					if ( volumeLabels.max( 0 ) - end[ 0 ] < partitionXSize )
						end[ 0 ] = volumeLabels.max( 0 );

					if ( volumeLabels.max( 1 ) - end[ 1 ] < partitionYSize )
						end[ 1 ] = volumeLabels.max( 1 );

					if ( volumeLabels.max( 2 ) - end[ 2 ] < partitionZSize )
						end[ 2 ] = volumeLabels.max( 2 );

					RandomAccessibleInterval< LabelMultisetType > partition = Views.interval( volumeLabels, begin, end );

					offsets.add( new int[] { ( int ) begin[ 0 ], ( int ) begin[ 1 ], ( int ) begin[ 2 ] } );
					System.out.println( " partition begins at: " + begin[ 0 ] + " x " + begin[ 1 ] + " x " + begin[ 2 ] );
					System.out.println( " partition ends at: " + end[ 0 ] + " x " + end[ 1 ] + " x " + end[ 2 ] );
					System.out.println( "offset: " + offsets.get( offsetIdx )[ 0 ] + " x " + offsets.get( offsetIdx )[ 1 ] + " x " + offsets.get( offsetIdx )[ 2 ] );

					offsetIdx++;

					parts.add( partition );
				}
			}
		}
		return parts;
	}
}
