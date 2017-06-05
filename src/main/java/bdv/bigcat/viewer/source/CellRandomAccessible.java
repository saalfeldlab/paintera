package bdv.bigcat.viewer.source;

import java.util.Arrays;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;

public class CellRandomAccessible< T > implements RandomAccessible< T >
{

	public static class HashableLongArray
	{

		private final long[] data;

		public HashableLongArray( final long[] data )
		{
			super();
			this.data = data;
		}

		@Override
		public boolean equals( final Object o )
		{
			return o instanceof HashableLongArray && Arrays.equals( ( ( HashableLongArray ) o ).data, data );
		}

		@Override
		public int hashCode()
		{
			return Arrays.hashCode( data );
		}

	}

	@Override
	public int numDimensions()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RandomAccess< T > randomAccess( final Interval interval )
	{
		// TODO Auto-generated method stub
		return null;
	}

}
