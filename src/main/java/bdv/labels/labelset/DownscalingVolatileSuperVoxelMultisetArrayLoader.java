package bdv.labels.labelset;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import gnu.trove.list.array.TIntArrayList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.IntervalIndexer;
import bdv.img.cache.CacheArrayLoader;
import bdv.labels.labelset.DvidLabels64MultisetSetupImageLoader.MultisetSource;
import bdv.labels.labelset.Multiset.Entry;


public class DownscalingVolatileSuperVoxelMultisetArrayLoader implements CacheArrayLoader< VolatileSuperVoxelMultisetArray >
{
	private VolatileSuperVoxelMultisetArray theEmptyArray;

	private final MultisetSource multisetSource;

	public DownscalingVolatileSuperVoxelMultisetArrayLoader( final MultisetSource multisetSource )
	{
		theEmptyArray = new VolatileSuperVoxelMultisetArray( 1, false );
		this.multisetSource = multisetSource;
	}

	@Override
	public VolatileSuperVoxelMultisetArray loadArray( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
//		System.out.println( "DownscalingVolatileSuperVoxelMultisetArrayLoader.loadArray(\n"
//				+ "   timepoint = " + timepoint + "\n"
//				+ "   setup = " + setup + "\n"
//				+ "   level = " + level + "\n"
//				+ "   dimensions = " + Util.printCoordinates( dimensions ) + "\n"
//				+ "   min = " + Util.printCoordinates( min ) + "\n"
//				+ ")"
//				);
		final String filename = getFilename( timepoint, setup, level, min );
		final VolatileSuperVoxelMultisetArray cached = tryLoadCached( dimensions, filename );
		if ( cached != null )
			return cached;

		final RandomAccessibleInterval< SuperVoxelMultisetType > input = multisetSource.getSource( timepoint, level - 1 );
		final int[] factors = new int[] { 2, 2, 2 };
		return downscale( input, factors, dimensions, min, filename );
	}

	@Override
	public int getBytesPerElement()
	{
		return 8;
	}

	private String getFilename( final int timepoint, final int setup, final int level, final long[] min )
	{
		return String.format( "/tmp/labelcache/%d_%d_%d/%d_%d_%d", timepoint, setup, level, min[ 0 ], min[ 1 ], min[ 2 ] );
	}

	private VolatileSuperVoxelMultisetArray tryLoadCached(
			final int[] dimensions,
			final String filename )
	{
		if ( ! Paths.get( filename ).toFile().exists() )
			return null;

		byte[] bytes;
		try
		{
			bytes = Files.readAllBytes( Paths.get( filename ) );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
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
		for ( int i = 0; i < listDataSize; ++i )
			ByteUtils.putByte( bytes[ ++j ], listData.data, i );
		return new VolatileSuperVoxelMultisetArray( data, listData, true );
	}

	private static class SortedPeekIterator implements Comparable< SortedPeekIterator >
	{
		Iterator< Entry< SuperVoxel > > iter;

		Entry< SuperVoxel > head;

		void init( final Iterator< Entry< SuperVoxel > > iter )
		{
			this.iter = iter;
			head = iter.hasNext() ? iter.next() : null;
		}

		void fwd()
		{
			head = iter.hasNext() ? iter.next() : null;
		}

		@Override
		public int compareTo( final SortedPeekIterator o )
		{
			if ( head == null )
				return o.head == null ?
					0 :
					1; // o is smaller because it still has elements
			else
				return o.head == null ?
					-1 : // o is greater because we still have elements
					Long.compare( head.getElement().id(), o.head.getElement().id() );
		}
	}

	private VolatileSuperVoxelMultisetArray downscale(
			final RandomAccessibleInterval< SuperVoxelMultisetType > input,
			final int[] factors, // (relative to to input)
			final int[] dimensions,
			final long[] min,
			final String filename ) throws InterruptedException
	{
		final int n = 3;
		final int[] data = new int[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );

		int numEntities = 1;
		for ( int i = 0; i < n; ++i )
			numEntities *= dimensions[ i ];

		int numContribs = 1;
		for ( int i = 0; i < n; ++i )
			numContribs *= factors[ i ];

		@SuppressWarnings( "unchecked" )
		final RandomAccess< SuperVoxelMultisetType >[] inputs = new RandomAccess[ numContribs ];
		for ( int i = 0; i < numContribs; ++i )
			inputs[ i ] = input.randomAccess();

		final SortedPeekIterator[] iters = new SortedPeekIterator[ numContribs ];
		for ( int i = 0; i < numContribs; ++i )
			iters[ i ] = new SortedPeekIterator();

		final int[] outputPos = new int[ n ];
		final int[] inputOffset = new int[ n ];
		final int[] inputPos = new int[ n ];

		final SuperVoxelMultisetEntryList list = new SuperVoxelMultisetEntryList( listData, 0 );
		final SuperVoxelMultisetEntryList list2 = new SuperVoxelMultisetEntryList();
		final TIntArrayList listHashesAndOffsets = new TIntArrayList();
		final SuperVoxelMultisetEntry entry = new SuperVoxelMultisetEntry( 0, 1 );
		int nextListOffset = 0;
		for ( int o = 0; o < numEntities; ++o )
		{
			IntervalIndexer.indexToPosition( o, dimensions, outputPos );
			for ( int d = 0; d < n; ++d )
				inputOffset[ d ] = ( outputPos[ d ] + ( int ) min[ d ] ) * factors[ d ];

			for ( int i = 0; i < numContribs; ++i )
			{
				IntervalIndexer.indexToPositionWithOffset( i, factors, inputOffset, inputPos );
				inputs[ i ].setPosition( inputPos );
				iters[ i ].init( inputs[ i ].get().entrySet().iterator() );
			}

			list.createListAt( listData, nextListOffset );
			Arrays.sort( iters );
			if ( iters[ 0 ].head != null )
			{
				long id = iters[ 0 ].head.getElement().id();
				int count = iters[ 0 ].head.getCount();

				iters[ 0 ].fwd();
				Arrays.sort( iters );

				while ( iters[ 0 ].head != null )
				{
					final long headId = iters[ 0 ].head.getElement().id();
					final int headCount = iters[ 0 ].head.getCount();

					if ( headId == id )
					{
						count += headCount;
					}
					else
					{
						entry.setId( id );
						entry.setCount( count );
						list.add( entry );

						id = headId;
						count = headCount;
					}

					iters[ 0 ].fwd();
					Arrays.sort( iters );
				}

				entry.setId( id );
				entry.setCount( count );
				list.add( entry );
			}

			boolean makeNewList = true;
			final int hash = list.hashCode();
			for ( int i = 0; i < listHashesAndOffsets.size(); i += 2 )
			{
				if ( hash == listHashesAndOffsets.get( i ) )
				{
					list2.referToDataAt( listData, listHashesAndOffsets.get( i + 1 ) );
					if ( list.equals( list2 ) )
					{
						makeNewList = false;
						data[ o ] = listHashesAndOffsets.get( i + 1 ) ;
						break;
					}
				}
			}
			if ( makeNewList )
			{
				data[ o ] = nextListOffset;
				listHashesAndOffsets.add( hash );
				listHashesAndOffsets.add( nextListOffset );
				nextListOffset += list.getSizeInBytes();
			}
		}

		final byte[] bytes = new byte[ 4 * data.length + nextListOffset ];
		int j = -1;
		for ( final int d : data )
		{
			bytes[ ++j ] = ( byte ) d;
			bytes[ ++j ] = ( byte ) ( d >> 8 );
			bytes[ ++j ] = ( byte ) ( d >> 16 );
			bytes[ ++j ] = ( byte ) ( d >> 24 );
		}
		for ( int i = 0; i < nextListOffset; ++i )
			bytes[ ++j ] = ByteUtils.getByte( listData.data, i );
		try
		{
			Paths.get( filename ).getParent().toFile().mkdirs();
			Files.write( Paths.get( filename ), bytes, WRITE, CREATE, TRUNCATE_EXISTING );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}

		return new VolatileSuperVoxelMultisetArray( data, listData, true );
	}

	@Override
	public VolatileSuperVoxelMultisetArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileSuperVoxelMultisetArray( numEntities, false );
		return theEmptyArray;
	}
}
