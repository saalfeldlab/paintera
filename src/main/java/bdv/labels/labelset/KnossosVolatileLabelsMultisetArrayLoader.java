package bdv.labels.labelset;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import gnu.trove.list.array.TLongArrayList;

/**
 * <p>
 * Loads a full resolution label block from a KNOSSOS uint64 source where each
 * voxel is assigned to a single label, and converts them into a LabelMultiset
 * with one element per voxel.
 * </p>
 * <p>
 * URL format encode the magnification and position of the block.  A typical URL
 * would be
 * </p>
 * <pre>
 * /mag%1$d/x%2$04d/y%3$04d/z%4$04d/%5$s_x%2$04d_y%3$04d_z%4$04d.raw
 * </pre>
 */
public class KnossosVolatileLabelsMultisetArrayLoader implements CacheArrayLoader< VolatileLabelMultisetArray >
{
	private VolatileLabelMultisetArray theEmptyArray;

	final private String urlFormat;

	public KnossosVolatileLabelsMultisetArrayLoader(
			final String baseUrl,
			final String urlFormat,
			final String experiment,
			final String format )
	{
		theEmptyArray = new VolatileLabelMultisetArray( 1, false );
		this.urlFormat = baseUrl + urlFormat.replace( "%5$s", experiment );
	}

	// TODO: unused -- remove.
	@Override
	public int getBytesPerElement()
	{
		return 8;
	}

	static private void readBlock(
			final String urlString,
			final int[] data,
			final LongMappedAccessData listData ) throws IOException
	{
		final byte[] bytes = new byte[ data.length * 8 ];
		final URL url = new URL( urlString );
		final InputStream in = url.openStream();
		int off = 0, l = 0;
		do
		{
			l = in.read( bytes, off, bytes.length - off );
			off += l;
		}
		while ( l > 0 && off < bytes.length );
		in.close();

		final TLongArrayList idAndOffsetList = new TLongArrayList();
		final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
		final LabelMultisetEntry entry = new LabelMultisetEntry( 0, 1 );
		long nextListOffset = 0;
A:		for ( int i = 0, j = -1; i < data.length; ++i )
		{
			final long id =
					( 0xffl & bytes[ ++j ] ) |
					( ( 0xffl & bytes[ ++j ] ) << 8 ) |
					( ( 0xffl & bytes[ ++j ] ) << 16 ) |
					( ( 0xffl & bytes[ ++j ] ) << 24 ) |
					( ( 0xffl & bytes[ ++j ] ) << 32 ) |
					( ( 0xffl & bytes[ ++j ] ) << 40 ) |
					( ( 0xffl & bytes[ ++j ] ) << 48 ) |
					( ( 0xffl & bytes[ ++j ] ) << 56 );

			// does the list [id x 1] already exist?
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
			data[ i ] = ( int ) nextListOffset;
			nextListOffset += list.getSizeInBytes();
		}
	}

	private String makeUrl(
			final long[] min,
			final int[] dimensions )
	{
		return String.format(
				urlFormat,
				1,
				min[ 0 ] / 128,
				min[ 1 ] / 128,
				min[ 2 ] / 128 );
	}

	@Override
	public VolatileLabelMultisetArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
//		final int[] data = new int[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		final int[] data = new int[ 128 * 128 * 128 ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );

		try
		{
			final String urlString = makeUrl( min, dimensions );
			System.out.println( urlString );
			readBlock( urlString, data, listData );
		}
		catch ( final IOException e )
		{
			System.out.println(
					"failed loading min = " +
							Arrays.toString( min ) +
							", dimensions = " +
							Arrays.toString( dimensions ) );
			return emptyArray( dimensions );
		}

		return new VolatileLabelMultisetArray( data, listData, true );
	}

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
