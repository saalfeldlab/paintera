package bdv.img.h5;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.img.cache.CacheArrayLoader;
import bdv.labels.labelset.LabelMultisetEntry;
import bdv.labels.labelset.LabelMultisetEntryList;
import bdv.labels.labelset.LongMappedAccessData;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5ShortReader;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TLongIntHashMap;

/**
 * {@link CacheArrayLoader} for simple HDF5 files
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class H5ShortLabelMultisetArrayLoader extends AbstractH5LabelMultisetArrayLoader
{

	private final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	final private IHDF5ShortReader reader;

	public H5ShortLabelMultisetArrayLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset )
	{
		super( scaleReader, dataset );
		this.reader = reader.int16();
	}

	@Override
	public int getBytesPerElement()
	{
		return 2;
	}

	@Override
	public VolatileLabelMultisetArray loadArrayLevel0(
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		short[] data = null;

		final MDShortArray block = reader.readMDArrayBlockWithOffset(
				dataset,
				new int[] { dimensions[ 2 ], dimensions[ 1 ], dimensions[ 0 ] },
				new long[] { min[ 2 ], min[ 1 ], min[ 0 ] } );

		data = block.getAsFlatArray();

		if ( data == null )
		{
			LOG.warn( "H5 short label multiset array loader failed loading min = {}, dimensions = {}", Arrays.toString( min ), Arrays.toString( dimensions ) );

			data = new short[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		}

		final int[] offsets = new int[ dimensions[ 2 ] * dimensions[ 1 ] * dimensions[ 0 ] ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );
		final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
		final LabelMultisetEntry entry = new LabelMultisetEntry( 0, 1 );
		int nextListOffset = 0;
		final TLongIntHashMap idOffsetHash = new TLongIntHashMap(
				Constants.DEFAULT_CAPACITY,
				Constants.DEFAULT_LOAD_FACTOR,
				-1,
				-1 );
		A: for ( int i = 0; i < data.length; ++i )
		{
			final long id = data[ i ] & 0xffffL;

//			does the list [id x 1] already exist?
			final int offset = idOffsetHash.get( id );
			if ( offset == idOffsetHash.getNoEntryValue() )
			{
				list.createListAt( listData, nextListOffset );
				entry.setId( id );
				list.add( entry );
				offsets[ i ] = nextListOffset;
				idOffsetHash.put( id, nextListOffset );
				nextListOffset += list.getSizeInBytes();
			}
			else
			{
				offsets[ i ] = offset;
				continue A;
			}
		}

		return new VolatileLabelMultisetArray( offsets, listData, true );
	}
}
