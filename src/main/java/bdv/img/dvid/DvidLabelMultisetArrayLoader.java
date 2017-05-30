package bdv.img.dvid;

import bdv.img.cache.CacheArrayLoader;
import bdv.labels.labelset.ByteUtils;
import bdv.labels.labelset.LongMappedAccessData;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.util.dvid.DatasetKeyValue;


public class DvidLabelMultisetArrayLoader implements CacheArrayLoader< VolatileLabelMultisetArray >
{
	private VolatileLabelMultisetArray theEmptyArray;

	final private DatasetKeyValue dvidStore;

	public DvidLabelMultisetArrayLoader( final DatasetKeyValue dvidStores )
	{
		theEmptyArray = new VolatileLabelMultisetArray( 1, false );
		this.dvidStore = dvidStores;
	}

	@Override
	public VolatileLabelMultisetArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		final String key = getKey( timepoint, setup, min );
		final VolatileLabelMultisetArray cached = tryLoadCached( dimensions, dvidStore, key );
		if ( cached != null )
			return cached;
		else
			return emptyArray( dimensions );
	}

	@Override
	public int getBytesPerElement()
	{
		return 8;
	}

	private String getKey( final int timepoint, final int setup, final long[] min )
	{
		return String.format( "%d_%d_%d_%d_%d", timepoint, setup, min[0], min[1], min[2] );
	}

	private VolatileLabelMultisetArray tryLoadCached(
			final int[] dimensions,
			final DatasetKeyValue store,
			final String key )
	{
		byte[] bytes;
		try
		{
			// download from data store
			// if key not present, return null
			bytes = store.getKey( key );
		}
		catch ( final Exception e )
		{
			// TODO print stack trace?
//			e.printStackTrace();
			return null;
		}

		final int[] data = new int[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		final int listDataSize = bytes.length - 4 * data.length;
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( listDataSize );
		int j = -1;
		for ( int i = 0; i < data.length; ++i )
		{
			data[ i ] = ( 0xff & bytes[ ++j ] ) |
					( ( 0xff & bytes[ ++j ] ) << 8 ) |
					( ( 0xff & bytes[ ++j ] ) << 16 ) |
					( ( 0xff & bytes[ ++j ] ) << 24 );
		}
		final long[] listDataStorage = listData.getData();
		for ( int i = 0; i < listDataSize; ++i )
			ByteUtils.putByte( bytes[ ++j ], listDataStorage, i );
		return new VolatileLabelMultisetArray( data, listData, true );
	}

	private VolatileLabelMultisetArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileLabelMultisetArray( numEntities, false );
		return theEmptyArray;
	}
}
