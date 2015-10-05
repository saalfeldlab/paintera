package bdv.labels.labelset;

import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.AbstractVolatileArray;


public class VolatileLabelMultisetArray extends AbstractVolatileArray< VolatileLabelMultisetArray > implements VolatileAccess
{
	protected int[] data;

	final protected LongMappedAccessData listData;

	public VolatileLabelMultisetArray( final int numEntities, final boolean isValid )
	{
		super( isValid );
		this.data = new int[ numEntities ];
		listData = LongMappedAccessData.factory.createStorage( 16 );
		new MappedObjectArrayList<>( SuperVoxelMultisetEntry.type, listData, 0 ).add( new SuperVoxelMultisetEntry() );
	}

	public VolatileLabelMultisetArray( final int[] data, final LongMappedAccessData listData, final boolean isValid )
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
	public VolatileLabelMultisetArray createArray( final int numEntities )
	{
		return new VolatileLabelMultisetArray( numEntities, true );
	}

	@Override
	public int[] getCurrentStorageArray()
	{
		return data;
	}
}