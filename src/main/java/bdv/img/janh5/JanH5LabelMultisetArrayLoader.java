package bdv.img.janh5;

import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import bdv.labels.labelset.LabelMultisetEntry;
import bdv.labels.labelset.LabelMultisetEntryList;
import bdv.labels.labelset.LongMappedAccessData;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import ch.systemsx.cisd.base.mdarray.MDLongArray;
import ch.systemsx.cisd.hdf5.IHDF5LongReader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.list.array.TLongArrayList;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class JanH5LabelMultisetArrayLoader implements CacheArrayLoader< VolatileLabelMultisetArray >
{
	private VolatileLabelMultisetArray theEmptyArray;

	final private IHDF5LongReader reader;

	final private String dataset;

	public JanH5LabelMultisetArrayLoader(
			final IHDF5Reader reader,
			final String dataset )
	{
		theEmptyArray = new VolatileLabelMultisetArray( 1, false );
		this.reader = reader.uint64();
		this.dataset = dataset;
	}

	@Override
	public int getBytesPerElement()
	{
		return 4;
	}

	@Override
	public VolatileLabelMultisetArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		long[] data = null;

		final MDLongArray block = reader.readMDArrayBlockWithOffset(
				dataset,
				new int[]{ dimensions[ 2 ], dimensions[ 1 ], dimensions[ 0 ] },
				new long[]{ min[ 2 ], min[ 1 ], min[ 0 ] } );

		data = block.getAsFlatArray();

		if ( data == null )
		{
			System.out.println(
					"JanH5 label multiset array loader failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );

			data = new long[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		}

		final int[] offsets = new int[ dimensions[ 2 ] * dimensions[ 1 ] * dimensions[ 0 ] ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );
		final TLongArrayList idAndOffsetList = new TLongArrayList();
		final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
		final LabelMultisetEntry entry = new LabelMultisetEntry( 0, 1 );
		long nextListOffset = 0;
A:		for ( int i = 0; i < data.length; ++i )
		{
			final long id = data[ i ];

//			does the list [id x 1] already exist?
//			for ( int k = 0; k < idAndOffsetList.size(); k += 2 )
//			{
//				if ( idAndOffsetList.getQuick( k ) == id )
//				{
//					final long offset = idAndOffsetList.getQuick( k + 1 );
//					data[ i ] = ( int ) offset;
//					System.out.println( "Continuing A " + i + " " + data.length );
//					continue A;
//				}
//			}

			list.createListAt( listData, nextListOffset );
			entry.setId( id );
			list.add( entry );
			idAndOffsetList.add( id );
			idAndOffsetList.add( nextListOffset );
			offsets[ i ] = ( int ) nextListOffset;
			nextListOffset += list.getSizeInBytes();
		}

		return new VolatileLabelMultisetArray( offsets, listData, true );
	}

	/**
	 * Reuses the existing empty array if it already has the desired size.
	 */
	@Override
	public VolatileLabelMultisetArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileLabelMultisetArray( numEntities, false );
		return theEmptyArray;
	}
}
