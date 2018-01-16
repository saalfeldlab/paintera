package bdv.bigcat.viewer;

import java.util.Set;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;

public interface ToIdConverter
{

	public long[] allIds( Object o );

	public long biggestFragment( Object o );

	public static FromLabelMultisetType fromLabelMultisetType()
	{
		return new FromLabelMultisetType();
	}

	public static FromARGB fromARGB()
	{
		return new FromARGB();
	}

	public static FromIntegerType fromIntegerType()
	{
		return new FromIntegerType();
	}

	public static FromRealType fromRealType()
	{
		return new FromRealType();
	}

	public static < I extends IntegerType< I >, K extends IntegerType< K > > FromPair< I, K > fromPair()
	{
		return new FromPair<>();
	}

	public static class FromLabelMultisetType implements ToIdConverter
	{

		@Override
		public long[] allIds( final Object o )
		{
			final LabelMultisetType t = ( LabelMultisetType ) o;
			final Set< Entry< Label > > entries = t.entrySet();
			final long[] ids = new long[ entries.size() ];
			int index = 0;
			for ( final Entry< Label > entry : entries )
			{
				ids[ index ] = entry.getElement().id();
				++index;
			}
			return ids;
		}

		@Override
		public long biggestFragment( final Object o )
		{
			final LabelMultisetType t = ( LabelMultisetType ) o;
			long argMaxId = Label.INVALID;
			long maxCount = 0;
			for ( final Entry< Label > entry : t.entrySet() )
			{
				final long id = entry.getElement().id();
				if ( Label.regular( id ) )
				{
					final int count = entry.getCount();
					if ( count > maxCount )
					{
						argMaxId = id;
						maxCount = count;
					}
				}
			}
			return argMaxId;
		}

	}

	public static class FromARGB implements ToIdConverter
	{

		@Override
		public long[] allIds( final Object o )
		{
			return new long[] { biggestFragment( o ) };
		}

		@Override
		public long biggestFragment( final Object o )
		{
			return ( ( ARGBType ) o ).get();
		}

	}

	public static class FromIntegerType implements ToIdConverter
	{

		@Override
		public long[] allIds( final Object o )
		{
			return new long[] { biggestFragment( o ) };
		}

		@Override
		public long biggestFragment( final Object o )
		{
			return ( ( IntegerType< ? > ) o ).getIntegerLong();
		}

	}

	public static class FromRealType implements ToIdConverter
	{

		@Override
		public long[] allIds( final Object o )
		{
			return new long[] { biggestFragment( o ) };
		}

		@Override
		public long biggestFragment( final Object o )
		{
			return ( long ) ( ( RealType< ? > ) o ).getRealDouble();
		}
	}

	public static class FromPair< I extends IntegerType< I >, K extends IntegerType< K > > implements ToIdConverter
	{

		@Override
		public long[] allIds( final Object o )
		{
			return new long[] { biggestFragment( o ) };
		}

		@Override
		public long biggestFragment( final Object o )
		{
			final Pair< I, K > p = ( Pair< I, K > ) o;
			final long k = p.getB().getIntegerLong();
			return Label.regular( k ) ? k : p.getA().getIntegerLong();
		}

	}

}
