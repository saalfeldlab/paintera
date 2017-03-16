package bdv.labels.labelset;

import bdv.img.cache.DefaultEmptyArrayCreator;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess;


public class VolatileLabelMultisetArray implements VolatileAccess, VolatileArrayDataAccess< VolatileLabelMultisetArray >
{
	private boolean isValid = false;

	private final int[] data;

	private final MappedAccessData< LongMappedAccess > listData;

	private final long listDataUsedSizeInBytes;

	public VolatileLabelMultisetArray( final int numEntities, final boolean isValid )
	{
		this.data = new int[ numEntities ];
		listData = LongMappedAccessData.factory.createStorage( 16 );
		listDataUsedSizeInBytes = 0;
		new MappedObjectArrayList<>( LabelMultisetEntry.type, listData, 0 ).add( new LabelMultisetEntry() );
		this.isValid = isValid;
	}

	public VolatileLabelMultisetArray(
			final int[] data,
			final MappedAccessData< LongMappedAccess > listData,
			final boolean isValid )
	{
		this( data, listData, -1, isValid );
	}

	public VolatileLabelMultisetArray(
			final int[] data,
			final MappedAccessData< LongMappedAccess > listData,
			final long listDataUsedSizeInBytes,
			final boolean isValid )
	{
		this.data = data;
		this.listData = listData;
		this.listDataUsedSizeInBytes = listDataUsedSizeInBytes;
		this.isValid = isValid;
	}

	public void getValue( final int index, final LabelMultisetEntryList ref )
	{
		ref.referToDataAt( listData, data[ index ] );
	}

	@Override
	public VolatileLabelMultisetArray createArray( final int numEntities )
	{
		return new VolatileLabelMultisetArray( numEntities, true );
	}

	@Override
	public VolatileLabelMultisetArray createArray( final int numEntities, final boolean isValid )
	{
		return new VolatileLabelMultisetArray( numEntities, isValid );
	}

	@Override
	public int[] getCurrentStorageArray()
	{
		return data;
	}

	public MappedAccessData< LongMappedAccess > getListData()
	{
		return listData;
	}

	public long getListDataUsedSizeInBytes()
	{
		return listDataUsedSizeInBytes;
	}

	@Override
	public boolean isValid()
	{
		return isValid;
	}

	public static DefaultEmptyArrayCreator< VolatileLabelMultisetArray > emptyArrayCreator = new DefaultEmptyArrayCreator<>( new VolatileLabelMultisetArray( 1, false ) );
}
