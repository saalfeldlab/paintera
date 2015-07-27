package bdv.labels.labelset;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class SuperVoxelMultiset implements Multiset< SuperVoxel >
{
	private final SuperVoxelMultisetEntryList entries;

	private final int totalSize;

	private final Set< Entry< SuperVoxel > > entrySet = new AbstractSet< Entry< SuperVoxel > >()
	{
		@Override
		public Iterator< Entry< SuperVoxel > > iterator()
		{
			return new Iterator< Entry< SuperVoxel > >()
			{
				private int i = 0;

				@Override
				public boolean hasNext()
				{
					return i < size();
				}

				@Override
				public SuperVoxelMultisetEntry next()
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

	public SuperVoxelMultiset( final SuperVoxelMultisetEntryList entries )
	{
		this.entries = entries;
		int s = 0;
		for ( final SuperVoxelMultisetEntry entry : entries )
			s += entry.getCount();
		this.totalSize = s;
	}

	public SuperVoxelMultiset( final SuperVoxelMultisetEntryList entries, final int size )
	{
		this.entries = entries;
		this.totalSize = size;
	}

	protected SuperVoxelMultiset( final int size )
	{
		this.entries = new SuperVoxelMultisetEntryList();
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
		return ( o instanceof SuperVoxel ) &&
				entries.binarySearch( ( ( SuperVoxel ) o ).id() ) >= 0;
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
		if ( ! ( o instanceof SuperVoxel ) )
			return 0;

		final int pos = entries.binarySearch( ( ( SuperVoxel ) o ).id() );
		if ( pos < 0 )
			return 0;

		return entries.get( pos ).getCount();
	}

	@Override
	public Set< Entry< SuperVoxel > > entrySet()
	{
		return entrySet;
	}

	@Override
	public String toString()
	{
		return entries.toString();
	}

	@Override
	public Iterator< SuperVoxel > iterator()
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
	public boolean add( final SuperVoxel e )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove( final Object o )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll( final Collection< ? extends SuperVoxel > c )
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