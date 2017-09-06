package bdv.bigcat.viewer;

import java.util.Set;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import net.imglib2.type.numeric.ARGBType;

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
			long maxCount = Long.MIN_VALUE;
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

}
