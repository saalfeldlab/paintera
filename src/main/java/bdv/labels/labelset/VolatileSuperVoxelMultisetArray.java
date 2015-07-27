package bdv.labels.labelset;

import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.AbstractVolatileArray;

public class VolatileSuperVoxelMultisetArray extends AbstractVolatileArray< VolatileSuperVoxelMultisetArray > implements VolatileAccess
{
	protected int[] data;

	protected final LongMappedAccessData listData;

	public VolatileSuperVoxelMultisetArray( final int numEntities, final boolean isValid )
	{
		super( isValid );
		this.data = new int[ numEntities ];
		listData = LongMappedAccessData.factory.createStorage( 16 );
		new MappedObjectArrayList<>( SuperVoxelMultisetEntry.type, listData, 0 ).add( new SuperVoxelMultisetEntry() );
	}

	public VolatileSuperVoxelMultisetArray( final int[] data, final LongMappedAccessData listData, final boolean isValid )
	{
		super( isValid );
		this.data = data;
		this.listData = listData;
	}

	public void getValue( final int index, final SuperVoxelMultisetEntryList ref )
	{
		ref.referToDataAt( listData, data[ index ] );
	}

	@Override
	public VolatileSuperVoxelMultisetArray createArray( final int numEntities )
	{
		return new VolatileSuperVoxelMultisetArray( numEntities, true );
	}

	@Override
	public int[] getCurrentStorageArray()
	{
		return data;
	}
}