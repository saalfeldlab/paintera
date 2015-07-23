package bdv.labels.labelset;

import static bdv.labels.labelset.ByteUtils.INT_SIZE;

import java.util.AbstractList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;

// TOOD: make unmodifiable version
public class MappedObjectArrayList< O extends MappedObject< O, T >, T extends MappedAccess< T > >
		extends AbstractList< O >
		implements RefList< O >
{
	int numRefs = 0;

	private final O type;

	private MappedAccessData< T > data;

	private long baseOffset;

	private long elementBaseOffset;

	private final T access;

	private final ConcurrentLinkedQueue< O > tmpObjRefs = new ConcurrentLinkedQueue< O >();

	// TODO: fix confusing constructor overloads

	// creates underlying data array
	public MappedObjectArrayList( final O type, final int capacity )
	{
		this( type, type.storageFactory.createStorage( INT_SIZE + capacity * type.getSizeInBytes() ), 0 );
	}

	// doesn't create underlying data array
	protected MappedObjectArrayList( final O type, final MappedAccessData< T > data, final long baseOffset )
	{
		this( type );
		referToDataAt( data, baseOffset );
		ensureCapacity( 0 );
	}

	// doesn't create underlying data array
	protected MappedObjectArrayList( final O type )
	{
		this.type = type;
		this.access = type.storageFactory.createAccess();
	}

	/**
	 * make this object refer to a different list.
	 */
	protected void referToDataAt( final MappedAccessData< T > data, final long baseOffset )
	{
		this.data = data;
		this.baseOffset = baseOffset;
		this.elementBaseOffset = baseOffset + INT_SIZE;
		data.updateAccess( access, baseOffset );
	}

	protected void createListAt( final MappedAccessData< T > data, final long baseOffset )
	{
		referToDataAt( data, baseOffset );
		clear();
	}

	/**
	 * Ensure capacity for size field and set size to 0.
	 */
	@Override
	public void clear()
	{
		ensureCapacity( 0 );
		setSize( 0 );
	}

	private void setSize( final int size )
	{
		access.putInt( size, 0 );
	}

	static Object lock = new Object();

	@Override
	public O createRef()
	{
		++numRefs;
//		System.out.println( "createRef (" + numRefs + ")" );
		synchronized( lock )
		{
			if ( numRefs > 10 )
			{
				System.out.println( "createRef (" + numRefs + ")" );
				for ( final StackTraceElement e : Thread.currentThread().getStackTrace() )
					System.out.println( e );
				System.out.println();
			}
		}


		final O obj = tmpObjRefs.poll();
		return obj == null ? type.createRef() : obj;
	}

	private O createRefAt( final int index )
	{
		final O ref = createRef();
		data.updateAccess( ref.access, elementBaseOffset + index * elementSizeInBytes() );
		return ref;
	}

	@Override
	public void releaseRef( final O ref )
	{
		--numRefs;
//		System.out.println( "releaseRef (" + numRefs + ")" );

		tmpObjRefs.add( ref );
	}

	private int elementSizeInBytes()
	{
		return type.getSizeInBytes();
	}

	private void ensureCapacity( final int size )
	{
		final int required = ( size + 1 ) * elementSizeInBytes();
		if ( data.size() < elementBaseOffset + required )
			data.resize( 2 * ( elementBaseOffset + required ) );
	}

	public long getBaseOffset()
	{
		return baseOffset;
	}

	public long getSizeInBytes()
	{
		return INT_SIZE + size() * type.getSizeInBytes();
	}

	@Override
	public int size()
	{
		return access.getInt( 0 );
	}

	@Override
	public O get( final int index )
	{
		if ( index < 0 || index >= size() )
			throw new IndexOutOfBoundsException();
		return createRefAt( index );
	}

	@Override
	public O get( final int index, final O ref )
	{
		if ( index < 0 || index >= size() )
			throw new IndexOutOfBoundsException();
		data.updateAccess( ref.access, elementBaseOffset + index * elementSizeInBytes() );
		return ref;
	}

	@Override
	public O set( final int index, final O element )
	{
		if ( index < 0 || index >= size() )
			throw new IndexOutOfBoundsException();
		final O ref = createRefAt( index );
		ref.set( element );
		releaseRef( ref );
		return null;
	}

	@Override
	public boolean add( final O obj )
	{
		final int size = size();
		ensureCapacity( size + 1 );
		setSize( size + 1 );

		final O ref = createRefAt( size );
		ref.set( obj );
		releaseRef( ref );

		return true;
	}

	@Override
	public RefIterator< O > iterator()
	{
		return new RefIterator< O >()
		{
			private O ref = createRef();

			private int i = 0;

			@Override
			public boolean hasNext()
			{
				if ( i < size() )
					return true;
				else
				{
					release();
					return false;
				}
			}

			@Override
			public O next()
			{
				return get( i++, ref );
			}

			@Override
			public void release()
			{
				if ( ref != null )
				{
					releaseRef( ref );
					ref = null;
				}
			}

			@Override
			public void reset()
			{
				if ( ref == null )
					ref = createRef();
				i = 0;
			}
		};
	}

	@Override
	public boolean equals( final Object o )
	{
		if ( o == this )
			return true;
		if ( !( o instanceof List ) )
			return false;

		if ( o instanceof RefList )
		{
			final RefIterator< O > e1 = iterator();
			final RefIterator< ? > e2 = ( ( RefList< ? > ) o ).iterator();
			while ( e1.hasNext() && e2.hasNext() )
			{
				final O o1 = e1.next();
				final Object o2 = e2.next();
				if ( ! o1.equals( o2 ) )
				{
					e1.release();
					e2.release();
					return false;
				}
			}
			return !( e1.hasNext() || e2.hasNext() );
		}

		final ListIterator< O > e1 = listIterator();
		final ListIterator< ? > e2 = ( ( List< ? > ) o ).listIterator();
		while ( e1.hasNext() && e2.hasNext() )
		{
			final O o1 = e1.next();
			final Object o2 = e2.next();
			if ( !( o1 == null ? o2 == null : o1.equals( o2 ) ) )
				return false;
		}
		return !( e1.hasNext() || e2.hasNext() );
	}
}
