package bdv.labels.labelset;

import gnu.trove.impl.Constants;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleNeighborhoodUnsafe;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.IntType;
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
}
