package bdv.labels.labelset;

import java.util.Arrays;
import java.util.Iterator;

import bdv.labels.labelset.Multiset.Entry;
import gnu.trove.impl.Constants;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleNeighborhoodUnsafe;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class Downscale
{
	/**
	 *
	 * @param input
	 * @param factors
	 *            downsampling factors of output block relative to input.
	 * @param dimensions
	 *            dimensions of the output block (in output resolution)
	 * @param min
	 *            minimum coordinate of output block (in output resolution).
	 *            Corresponding input coordinates are <em>min * factors</em>.
	 * @param filename
	 * @return
	 * @throws InterruptedException
	 */
	public static VolatileLabelMultisetArray downscale(
			final RandomAccessibleInterval< LabelMultisetType > input,
			final long[] factors,
			final long[] dimensions,
			final long[] min )
	{
		final int numElements = ( int ) Intervals.numElements( dimensions ); // num elements in output block
		final int[] data = new int[ numElements ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );

		final Cursor< Neighborhood< LabelMultisetType > > inNeighborhoods = Views.offsetInterval(
				Views.subsample(
						new RectangleShape.NeighborhoodsAccessible< LabelMultisetType >(
								input, new FinalInterval( factors ), RectangleNeighborhoodUnsafe.factory() ),
						factors ),
				min, dimensions ).cursor();

		final Cursor< IntType > outData = ArrayImgs.ints( data, dimensions ).cursor();

		final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
		final LabelMultisetEntryListIndex lists = new LabelMultisetEntryListIndex( listData );
		int nextListOffset = 0;
		while ( outData.hasNext() )
		{
			list.createListAt( listData, nextListOffset );
			for ( final LabelMultisetType ms : inNeighborhoods.next() )
				list.mergeWith(	ms );

			int offset = lists.putIfAbsent( list );
			if ( offset == -1 )
			{
				offset = nextListOffset;
				nextListOffset += list.getSizeInBytes();
			}
			outData.next().set( offset );
		}

		return new VolatileLabelMultisetArray( data, listData, nextListOffset, true );
	}


	final static class LabelMultisetEntryListIndex
	{
		private final LongMappedAccessData listData;

		private final LabelMultisetEntryList list2;

		private final TIntIntMap hashToOffset;

		private TIntObjectMap< TIntList > collisions;

		public LabelMultisetEntryListIndex( final LongMappedAccessData listData )
		{
			this.listData = listData;
			list2 = new LabelMultisetEntryList();
			hashToOffset = new TIntIntHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0, -1 );
			collisions = null;
		}

		/**
		 * Puts the given list into the index if the same list doesn't already
		 * exist. Otherwise returns the baseOffset of existing matching list.
		 *
		 * @param list
		 * @return {@code -1} if {@code list was added to the index}. Otherwise
		 *         the baseOffset of existing matching list.
		 *
		 */
		public int putIfAbsent( final LabelMultisetEntryList list )
		{
			final int hash = list.hashCode();
			int offset = hashToOffset.get( hash );
			if ( offset >= 0 )
			{
				list2.referToDataAt( listData, offset );
				if ( list.equals( list2 ) )
					return offset;

				// hash collision
				if ( collisions == null )
					collisions = new TIntObjectHashMap< >( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0 );

				TIntList offsets = collisions.get( hash );
				if ( offsets == null )
				{
					offsets = new TIntArrayList();
					collisions.put( hash, offsets );
				}

				for ( int i = 0; i < offsets.size(); ++i )
				{
					offset = offsets.get( i );
					list2.referToDataAt( listData, offset );
					if ( list.equals( list2 ) )
						return offset;
				}
				offsets.add( ( int ) list.getBaseOffset() );
			}
			else
				hashToOffset.put( hash, ( int ) list.getBaseOffset() );
			return -1;
		}
	}

	/**
	 *
	 * @param input
	 * @param factors
	 *            downsampling factors of output block relative to input.
	 * @param dimensions
	 *            dimensions of the output block (in output resolution)
	 * @param min
	 *            minimum coordinate of output block (in output resolution).
	 *            Corresponding input coordinates are <em>min * factors</em>.
	 * @param filename
	 * @return
	 * @throws InterruptedException
	 */
	public static VolatileLabelMultisetArray downscale3(
			final RandomAccessibleInterval< LabelMultisetType > input,
			final long[] factors,
			final long[] dimensions,
			final long[] min )
	{
		final int numElements = ( int ) Intervals.numElements( dimensions ); // num elements in output block
		final int[] data = new int[ numElements ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );

		final Cursor< Neighborhood< LabelMultisetType > > inNeighborhoods = Views.offsetInterval(
				Views.subsample(
						new RectangleShape.NeighborhoodsAccessible< LabelMultisetType >(
								input, new FinalInterval( factors ), RectangleNeighborhoodUnsafe.factory() ),
						factors ),
				min, dimensions ).cursor();

		final Cursor< IntType > outData = ArrayImgs.ints( data, dimensions ).cursor();


		final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
		final LabelMultisetEntryList list2 = new LabelMultisetEntryList();
		final TIntArrayList listHashesAndOffsets = new TIntArrayList();
		int nextListOffset = 0;

		while ( outData.hasNext() )
		{
			list.createListAt( listData, nextListOffset );
			for ( final LabelMultisetType ms : inNeighborhoods.next() )
				list.mergeWith(	ms );

			// check whether the same list already exists, and commit the list
			// if it doesn't.
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
						outData.next().set( listHashesAndOffsets.get( i + 1 ) );
						break;
					}
				}
			}
			if ( makeNewList )
			{
				outData.next().set( nextListOffset );
				listHashesAndOffsets.add( hash );
				listHashesAndOffsets.add( nextListOffset );
				nextListOffset += list.getSizeInBytes();
			}
		}

		return new VolatileLabelMultisetArray( data, listData, nextListOffset, true );
	}


	/**
	 *
	 * @param input
	 * @param factors
	 *            downsampling factors of output block relative to input.
	 * @param dimensions
	 *            dimensions of the output block (in output resolution)
	 * @param min
	 *            minimum coordinate of output block (in output resolution).
	 *            Corresponding input coordinates are <em>min * factors</em>.
	 * @param filename
	 * @return
	 * @throws InterruptedException
	 */
	public static VolatileLabelMultisetArray downscale2(
			final RandomAccessibleInterval< LabelMultisetType > input,
			final long[] factors,
			final long[] dimensions,
			final long[] min )
	{
		final int n = input.numDimensions();
		final int numElements = ( int ) Intervals.numElements( dimensions ); // num elements in output block
		final int[] data = new int[ numElements ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );

		int numContribs = 1;
		for ( int i = 0; i < n; ++i )
			numContribs *= factors[ i ];

		@SuppressWarnings( "unchecked" )
		final RandomAccess< LabelMultisetType >[] inputs = new RandomAccess[ numContribs ];
		for ( int i = 0; i < numContribs; ++i )
			inputs[ i ] = input.randomAccess();

		final SortedPeekIterator[] iters = new SortedPeekIterator[ numContribs ];
		for ( int i = 0; i < numContribs; ++i )
			iters[ i ] = new SortedPeekIterator();

		// current coordinate in output block
		final long[] outputPos = new long[ n ];

		// input coordinate corresponding to outputPos. outputPos * factors.
		final long[] inputOffset = new long[ n ];

		// input coordinate scanned through a (inputOffset, inputOffset + factors) interval.
		final long[] inputPos = new long[ n ];

		final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
		final LabelMultisetEntryList list2 = new LabelMultisetEntryList();
		final TIntArrayList listHashesAndOffsets = new TIntArrayList();
		final LabelMultisetEntry entry = new LabelMultisetEntry( 0, 1 );
		int nextListOffset = 0;
		for ( int o = 0; o < numElements; ++o )
		{
			IntervalIndexer.indexToPosition( o, dimensions, outputPos );
			for ( int d = 0; d < n; ++d )
				inputOffset[ d ] = ( outputPos[ d ] + min[ d ] ) * factors[ d ];

			for ( int i = 0; i < numContribs; ++i )
			{
				IntervalIndexer.indexToPositionWithOffset( i, factors, inputOffset, inputPos );
				inputs[ i ].setPosition( inputPos );
			}

			// merge lists at inputs[ i ] into new list
			list.createListAt( listData, nextListOffset );
			for ( int i = 0; i < numContribs; ++i )
				iters[ i ].init( inputs[ i ].get().entrySet().iterator() );
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

			// check whether the same list already exists, and commit the list
			// if it doesn't.
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

		return new VolatileLabelMultisetArray( data, listData, nextListOffset, true );
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
}
