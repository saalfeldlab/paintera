package bdv.labels.labelset;

import net.imglib2.Volatile;
import net.imglib2.img.NativeImg;
import net.imglib2.img.NativeImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

public class VolatileSuperVoxelMultisetType
	extends Volatile< SuperVoxelMultisetType >
	implements NativeType< VolatileSuperVoxelMultisetType >
{
	public static final VolatileSuperVoxelMultisetType type = new VolatileSuperVoxelMultisetType();

	// this is the constructor if you want it to read from an array
	public VolatileSuperVoxelMultisetType( final NativeImg< ?, VolatileSuperVoxelMultisetArray > img )
	{
		super( new SuperVoxelMultisetType( img ) );
	}

	// this is the constructor if you want to specify the dataAccess
	public VolatileSuperVoxelMultisetType( final VolatileSuperVoxelMultisetArray access, final boolean isValid )
	{
		super( new SuperVoxelMultisetType( access ), isValid );
	}

	// this is the constructor if you want it to be a variable
	public VolatileSuperVoxelMultisetType()
	{
		super( new SuperVoxelMultisetType() );
	}

	protected VolatileSuperVoxelMultisetType( final SuperVoxelMultisetType t )
	{
		super( t );
	}

	@Override
	public Fraction getEntitiesPerPixel()
	{
		return t.getEntitiesPerPixel();
	}

	@Override
	public void updateIndex( final int i )
	{
		t.updateIndex( i );
	}

	@Override
	public int getIndex()
	{
		return t.getIndex();
	}

	@Override
	public void incIndex()
	{
		t.incIndex();
	}

	@Override
	public void incIndex( final int increment )
	{
		t.incIndex( increment );
	}

	@Override
	public void decIndex()
	{
		t.decIndex();
	}

	@Override
	public void decIndex( final int decrement )
	{
		t.decIndex( decrement );
	}

	@Override
	public VolatileSuperVoxelMultisetType createVariable()
	{
		return new VolatileSuperVoxelMultisetType();
	}

	@Override
	public VolatileSuperVoxelMultisetType copy()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void set( final VolatileSuperVoxelMultisetType c )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public NativeImg< VolatileSuperVoxelMultisetType, ? > createSuitableNativeImg( final NativeImgFactory< VolatileSuperVoxelMultisetType > storageFactory, final long[] dim )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public VolatileSuperVoxelMultisetType duplicateTypeOnSameNativeImg()
	{
		return new VolatileSuperVoxelMultisetType( t.duplicateTypeOnSameNativeImg() );
	}

	@Override
	public void updateContainer( final Object c )
	{
		t.updateContainer( c );
		setValid( t.isValid() );
	}
}
