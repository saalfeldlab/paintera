package bdv.labels.labelset;

import static bdv.labels.labelset.ByteUtils.INT_SIZE;
import static bdv.labels.labelset.ByteUtils.LONG_SIZE;

public class SuperVoxelMultisetEntry
		extends MappedObject< SuperVoxelMultisetEntry, LongMappedAccess >
		implements Multiset.Entry< SuperVoxel >
{
	public static final SuperVoxelMultisetEntry type = new SuperVoxelMultisetEntry();

	protected static final int SUPERVOXEL_ID_OFFSET = 0;
	protected static final int COUNT_OFFSET = SUPERVOXEL_ID_OFFSET + LONG_SIZE;
	protected static final int SIZE_IN_BYTES = COUNT_OFFSET + INT_SIZE;

	public SuperVoxel id = new SuperVoxel()
	{
		@Override
		public long id()
		{
			return getId();
		}
	};

	public SuperVoxelMultisetEntry()
	{
		super(
			LongMappedAccessData.factory.createStorage( SIZE_IN_BYTES ).createAccess(),
			LongMappedAccessData.factory );
	}

	public SuperVoxelMultisetEntry( final long superVoxelId, final int numOccurrences )
	{
		this();
		setId( superVoxelId );
		setCount( numOccurrences );
	}

	protected SuperVoxelMultisetEntry( final LongMappedAccess access )
	{
		super( access, LongMappedAccessData.factory );
	}

	@Override
	public SuperVoxel getElement()
	{
		return id;
	}

	public long getId()
	{
		return access.getLong( SUPERVOXEL_ID_OFFSET );
	}

	@Override
	public int getCount()
	{
		return access.getInt( COUNT_OFFSET );
	}

	@Override
	public int getSizeInBytes()
	{
		return SIZE_IN_BYTES;
	}

	@Override
	public boolean equals( final Object obj )
	{
		if ( ! ( obj instanceof SuperVoxelMultisetEntry ) )
			return false;

		final SuperVoxelMultisetEntry svo = ( SuperVoxelMultisetEntry ) obj;
		return svo.getId() == getId() && svo.getCount() == getCount();
	}

	@Override
	public int hashCode()
	{
		return Long.hashCode( getId() ) + Integer.hashCode( getCount() );
	}

	@Override
	public String toString()
	{
		return id.id() + " x " + getCount();
	}

	@Override
	protected SuperVoxelMultisetEntry createRef()
	{
		return new SuperVoxelMultisetEntry( new LongMappedAccess( null, 0 ) );
	}

	void setId( final long id )
	{
		access.putLong( id, SUPERVOXEL_ID_OFFSET );
	}

	void setCount( final int count )
	{
		access.putInt( count, COUNT_OFFSET );
	}
}