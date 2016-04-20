package bdv.labels.labelset;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class LabelMultiset implements Multiset< Label >
{
	private final LabelMultisetEntryList entries;

	private final int totalSize;

	private final Set< Entry< Label > > entrySet = new AbstractSet< Entry< Label > >()
	{
		@Override
		public Iterator< Entry< Label > > iterator()
		{
			return new Iterator< Entry< Label > >()
			{
				private int i = 0;

				@Override
				public boolean hasNext()
				{
					return i < size();
				}

				@Override
				public LabelMultisetEntry next()
				{
					return entries.get( i++ );
				}
			};
		}

		@Override
		public int size()
		{
			return entries.size();
		}
	};

	public LabelMultiset( final LabelMultisetEntryList entries )
	{
		this.entries = entries;
		int s = 0;
		for ( final LabelMultisetEntry entry : entries )
			s += entry.getCount();
		this.totalSize = s;
	}

	public LabelMultiset( final LabelMultisetEntryList entries, final int size )
	{
		this.entries = entries;
		this.totalSize = size;
	}

	protected LabelMultiset( final int size )
	{
		this.entries = new LabelMultisetEntryList();
		this.totalSize = size;
	}

	/**
	 * makes this object refer to a different multiset.
	 */
	protected void referToDataAt( final LongMappedAccessData data, final long baseOffset )
	{
		entries.referToDataAt( data, baseOffset );
	}

	@Override
	public int size()
	{
		return totalSize;
	}

	@Override
	public boolean isEmpty()
	{
		return entries.isEmpty();
	}

	@Override
	public boolean contains( final Object o )
	{
		return ( o instanceof Label ) &&
				entries.binarySearch( ( ( Label ) o ).id() ) >= 0;
	}

	@Override
	public boolean containsAll( final Collection< ? > c )
	{
		for ( final Object e : c )
			if ( !contains( e ) )
				return false;
		return true;
	}

	@Override
	public int count( final Object o )
	{
		if ( ! ( o instanceof Label ) )
			return 0;

		final int pos = entries.binarySearch( ( ( Label ) o ).id() );
		if ( pos < 0 )
			return 0;

		return entries.get( pos ).getCount();
	}

	@Override
	public Set< Entry< Label > > entrySet()
	{
		return entrySet;
	}

	@Override
	public String toString()
	{
		return entries.toString();
	}

	@Override
	public Iterator< Label > iterator()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Object[] toArray()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public < T > T[] toArray( final T[] a )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean add( final Label e )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove( final Object o )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll( final Collection< ? extends Label > c )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll( final Collection< ? > c )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll( final Collection< ? > c )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear()
	{
		throw new UnsupportedOperationException();
	}
}