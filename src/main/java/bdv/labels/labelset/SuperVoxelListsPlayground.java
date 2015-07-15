package bdv.labels.labelset;

import static bdv.labels.labelset.ByteUtils.INT_SIZE;
import static bdv.labels.labelset.ByteUtils.LONG_SIZE;
import gnu.trove.list.array.TIntArrayList;

import java.util.AbstractList;
import java.util.List;
import java.util.Set;

import bdv.labels.labelset.MappedAccessData.Factory;

public class SuperVoxelListsPlayground
{
	public static void main( final String[] args )
	{
		final SVO svo = new SVO();
		System.out.println( svo );
		svo.setSuperVoxelId( 1L );
		svo.setNumOccurrences( 5 );
		System.out.println( svo );
		System.out.println();

		final LongMappedAccessData data = LongMappedAccessData.factory.createStorage( 1024 );

		final List< SVO > list = new MappedObjectArrayList<>( SVO.type, data, 0 );
		list.add( new SVO( 2, 10 ) );
		list.add( svo );
		svo.setSuperVoxelId( 3L );
		svo.setNumOccurrences( 15 );
		list.add( svo );
		System.out.println( list );
		System.out.println( svo );
		System.out.println( list.get( 1 ) );
		System.out.println();

		for ( int i = 0; i < 20; ++i )
			list.add( new SVO( i, i+1 ) );
		System.out.println( list );
		System.out.println();
		System.out.println();

		final TIntArrayList lists = new TIntArrayList();
		lists.add( 0 );
		for ( int l = 0; l < 10; ++l )
		{
			final long oldOffset = lists.get( lists.size() - 1 );
			final long oldSizeInBytes = new MappedObjectArrayList<>( SVO.type, data, oldOffset ).getSizeInBytes();
			final long baseOffset = oldOffset + oldSizeInBytes;
			final List< SVO > ll = new MappedObjectArrayList<>( SVO.type, data, baseOffset );
			for ( int i = 0; i < 5; ++i )
				ll.add( new SVO( i, l ) );
			lists.add( ( int ) baseOffset );
			for ( int li = 0; li < lists.size(); ++li )
				System.out.println( new MappedObjectArrayList<>( SVO.type, data, lists.get( li ) ) );
			System.out.println();
		}
	}

	public static interface SuperVoxelOccurrences
	{
		public long getSuperVoxelId();

		public int getNumOccurrences();
	}

	public static interface SuperVoxelMultiSet< E extends SuperVoxelOccurrences > extends Set< E >
	{
	}

	public static class SVO extends MappedObject< SVO, LongMappedAccess > implements SuperVoxelOccurrences
	{
		protected static final int SUPERVOXEL_ID_OFFSET = 0;
		protected static final int NUM_OCCURRENCES_OFFSET = SUPERVOXEL_ID_OFFSET + LONG_SIZE;
		protected static final int SIZE_IN_BYTES = NUM_OCCURRENCES_OFFSET + INT_SIZE;

		protected SVO( final LongMappedAccess access )
		{
			super( access, LongMappedAccessData.factory );
		}

		public SVO()
		{
			super(
				LongMappedAccessData.factory.createStorage( SIZE_IN_BYTES ).createAccess(),
				LongMappedAccessData.factory );
		}

		public SVO( final long superVoxelId, final int numOccurrences )
		{
			this();
			setSuperVoxelId( superVoxelId );
			setNumOccurrences( numOccurrences );
		}

		@Override
		public long getSuperVoxelId()
		{
			return access.getLong( SUPERVOXEL_ID_OFFSET );
		}

		@Override
		public int getNumOccurrences()
		{
			return access.getInt( NUM_OCCURRENCES_OFFSET );
		}

		void setSuperVoxelId( final long superVoxelId )
		{
			access.putLong( superVoxelId, SUPERVOXEL_ID_OFFSET );
		}

		void setNumOccurrences( final int numOccurrences )
		{
			access.putInt( numOccurrences, NUM_OCCURRENCES_OFFSET );
		}

		@Override
		public int getSizeInBytes()
		{
			return SIZE_IN_BYTES;
		}

		@Override
		public boolean equals( final Object obj )
		{
			if ( ! ( obj instanceof SVO ) )
				return false;

			final SVO svo = ( SVO ) obj;
			return svo.getSuperVoxelId() == getSuperVoxelId() && svo.getNumOccurrences() == getNumOccurrences();
		}

		@Override
		public int hashCode()
		{
			return Long.hashCode( getSuperVoxelId() ) + Integer.hashCode( getNumOccurrences() );
		}

		@Override
		public String toString()
		{
			return getSuperVoxelId() + "(*" + getNumOccurrences() + ")";
		}

		@Override
		protected SVO createRef()
		{
			return new SVO( new LongMappedAccess( null, 0 ) );
		}

		public static final SVO type = new SVO();
	}

	public static abstract class MappedObject< O extends MappedObject< O, T >, T extends MappedAccess< T > >
	{
		/**
		 * Access to the data.
		 */
		protected final T access;

		protected final Factory< ? extends MappedAccessData< T > > storageFactory;

		@Override
		public boolean equals( final Object obj )
		{
			return obj instanceof MappedObject< ?, ? > &&
					access.equals( ( ( MappedObject< ?, ? > ) obj ).access );
		}

		@Override
		public int hashCode()
		{
			return access.hashCode();
		}

		public abstract int getSizeInBytes();

		protected MappedObject(
				final T access,
				final MappedAccessData.Factory< ? extends MappedAccessData< T > > storageFactory )
		{
			this.access = access;
			this.storageFactory = storageFactory;
		}

		protected void set( final O obj )
		{
			access.copyFrom( obj.access, getSizeInBytes() );
		}

		protected abstract O createRef();
	}

	public static class MappedObjectArrayList< O extends MappedObject< O, T >, T extends MappedAccess< T > >
		extends AbstractList< O >
//		implements List< O >
	{
		private final O type;

		private final MappedAccessData< T > data;

		private final long baseOffset;

		private final long elementBaseOffset;

		private final T access;

		public MappedObjectArrayList( final O type, final int capacity )
		{
			this( type, type.storageFactory.createStorage( INT_SIZE + capacity * type.getSizeInBytes() ), 0 );
			setSize( 0 );
		}

		protected MappedObjectArrayList( final O type, final MappedAccessData< T > data, final long baseOffset )
		{
			this.type = type;
			this.data = data;
			this.baseOffset = baseOffset;
			this.elementBaseOffset = baseOffset + INT_SIZE;
			access = data.createAccess();
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
}
