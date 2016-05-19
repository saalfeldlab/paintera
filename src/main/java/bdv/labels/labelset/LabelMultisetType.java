package bdv.labels.labelset;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import bdv.labels.labelset.RefList.RefIterator;
import net.imglib2.img.NativeImg;
import net.imglib2.img.NativeImgFactory;
import net.imglib2.type.AbstractNativeType;
import net.imglib2.util.Fraction;

public class LabelMultisetType extends AbstractNativeType< LabelMultisetType > implements Multiset< Label >
{
	public static final LabelMultisetType type = new LabelMultisetType();

	private final NativeImg< ?, VolatileLabelMultisetArray > img;

	private VolatileLabelMultisetArray access;

	private final LabelMultisetEntryList entries;

	private final Set< Entry< Label > > entrySet;

	// this is the constructor if you want it to read from an array
	public LabelMultisetType( final NativeImg< ?, VolatileLabelMultisetArray > img )
	{
		this( img, null );
	}

	// this is the constructor if you want to specify the dataAccess
	public LabelMultisetType( final VolatileLabelMultisetArray access )
	{
		this( null, access );
	}

	// this is the constructor if you want it to be a variable
	public LabelMultisetType()
	{
		this( null, new VolatileLabelMultisetArray( 1, true ) );
	}

	private LabelMultisetType( final NativeImg< ?, VolatileLabelMultisetArray > img, final VolatileLabelMultisetArray access )
	{
		this.entries = new LabelMultisetEntryList();
		this.img = img;
		this.access = access;
		this.entrySet = new AbstractSet< Entry< Label > >()
		{
			private final RefIterator< Entry< Label > > iterator = new RefIterator< Entry< Label > >()
			{
				private final RefIterator< LabelMultisetEntry > it = entries.iterator();

				@Override
				public boolean hasNext()
				{
					return it.hasNext();
				}

				@Override
				public LabelMultisetEntry next()
				{
					return it.next();
				}

				@Override
				public void release()
				{
					it.release();
				}

				@Override
				public void reset()
				{
					it.reset();
				}
			};

			@Override
			public RefIterator< Entry< Label > > iterator()
			{
				iterator.reset();
				return iterator;
			}

			@Override
			public int size()
			{
				return entries.size();
			}
		};
	}

	@Override
	public Fraction getEntitiesPerPixel()
	{
		return new Fraction();
	}

	@Override
	public void updateContainer( final Object c )
	{
		access = img.update( c );
	}

	@Override
	public LabelMultisetType createVariable()
	{
		return new LabelMultisetType();
	}

	@Override
	public LabelMultisetType copy()
	{
		return new LabelMultisetType( img, access );
	}

	@Override
	public void set( final LabelMultisetType c )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public NativeImg< LabelMultisetType, ? > createSuitableNativeImg( final NativeImgFactory< LabelMultisetType > storageFactory, final long[] dim )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public LabelMultisetType duplicateTypeOnSameNativeImg()
	{
		return new LabelMultisetType( img );
	}

	// ==== Multiset< SuperVoxel > =====

	@Override
	public int size()
	{
		access.getValue( i, entries );
		return entries.multisetSize();
	}

	@Override
	public boolean isEmpty()
	{
		access.getValue( i, entries );
		return entries.isEmpty();
	}

	@Override
	public boolean contains( final Object o )
	{
		access.getValue( i, entries );
		return ( ( o instanceof Label ) && entries.binarySearch( ( ( Label ) o ).id() ) >= 0 );
	}

	public boolean contains( final long id )
	{
		access.getValue( i, entries );
		return entries.binarySearch( id ) >= 0;
	}

	public boolean containsAll( final long[] ids )
	{
		access.getValue( i, entries );
		for ( final long id : ids )
			if ( entries.binarySearch( id ) < 0 )
				return false;
		return true;
	}

	@Override
	public boolean containsAll( final Collection< ? > c )
	{
		access.getValue( i, entries );
		for ( final Object o : c )
			if ( ! ( ( o instanceof Label ) && entries.binarySearch( ( ( Label ) o ).id() ) >= 0 ) )
				return false;
		return true;
	}

	@Override
	public int count( final Object o )
	{
		access.getValue( i, entries );
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
		access.getValue( i, entries );
		return entrySet;
	}

	@Override
	public String toString()
	{
		access.getValue( i, entries );
		return entries.toString();
	}

	// for volatile type
	boolean isValid()
	{
		return access.isValid();
	}

	@Override public Iterator< Label > iterator() { throw new UnsupportedOperationException(); }
	@Override public Object[] toArray() { throw new UnsupportedOperationException(); }
	@Override public < T > T[] toArray( final T[] a ) { throw new UnsupportedOperationException(); }
	@Override public boolean add( final Label e ) { throw new UnsupportedOperationException(); }
	@Override public boolean remove( final Object o ) { throw new UnsupportedOperationException(); }
	@Override public boolean addAll( final Collection< ? extends Label > c ) { throw new UnsupportedOperationException(); }
	@Override public boolean removeAll( final Collection< ? > c ) { throw new UnsupportedOperationException(); }
	@Override public boolean retainAll( final Collection< ? > c ) { throw new UnsupportedOperationException(); }
	@Override public void clear() { throw new UnsupportedOperationException(); }
}