package bdv.labels.labelset;

import static bdv.labels.labelset.ByteUtils.INT_SIZE;

import java.util.AbstractList;

// TOOD: make unmodifiable version
public class MappedObjectArrayList< O extends MappedObject< O, T >, T extends MappedAccess< T > >
		extends AbstractList< O >
//		implements List< O >
	{
		private final O type;

		private MappedAccessData< T > data;

		private long baseOffset;

		private long elementBaseOffset;

		private final T access;

		// TODO: fix confusing constructor overloads

		// creates underlying data array
		public MappedObjectArrayList( final O type, final int capacity )
		{
			this( type, type.storageFactory.createStorage( INT_SIZE + capacity * type.getSizeInBytes() ), 0 );
		}

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

		private void setSize( final int size )
		{
			access.putInt( size, 0 );
		}

		private O createRef()
		{
			return type.createRef();
		}

		private O createRefAt( final int index )
		{
			final O ref = createRef();
			data.updateAccess( ref.access, elementBaseOffset + index * elementSizeInBytes() );
			return ref;
		}

		private void releaseRef( final O ref )
		{
			// TODO
		}

		private int elementSizeInBytes()
		{
			return type.getSizeInBytes();
		}

		private void ensureCapacity( final int size )
		{
			final int required = ( size + 1 ) * elementSizeInBytes();
			if ( data.size() < elementBaseOffset + required )
				data.resize( elementBaseOffset + 2 * required );
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
		public O set( final int index, final O element )
		{
			if ( index < 0 || index >= size() )
				throw new IndexOutOfBoundsException();
			createRefAt( index ).set( element );
			return null; // TODO
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
	}