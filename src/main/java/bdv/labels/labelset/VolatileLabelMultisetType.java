package bdv.labels.labelset;

import net.imglib2.Volatile;
import net.imglib2.img.NativeImg;
import net.imglib2.img.NativeImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

public class VolatileLabelMultisetType
	extends Volatile< LabelMultisetType >
	implements NativeType< VolatileLabelMultisetType >
{
	public static final VolatileLabelMultisetType type = new VolatileLabelMultisetType();

	// this is the constructor if you want it to read from an array
	public VolatileLabelMultisetType( final NativeImg< ?, VolatileLabelMultisetArray > img )
	{
		super( new LabelMultisetType( img ) );
	}

	// this is the constructor if you want to specify the dataAccess
	public VolatileLabelMultisetType( final VolatileLabelMultisetArray access, final boolean isValid )
	{
		super( new LabelMultisetType( access ), isValid );
	}

	// this is the constructor if you want it to be a variable
	public VolatileLabelMultisetType()
	{
		super( new LabelMultisetType() );
	}

	protected VolatileLabelMultisetType( final LabelMultisetType t )
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
	public VolatileLabelMultisetType createVariable()
	{
		return new VolatileLabelMultisetType();
	}

	@Override
	public VolatileLabelMultisetType copy()
	{
		return new VolatileLabelMultisetType( t.copy() );
	}

	@Override
	public void set( final VolatileLabelMultisetType c )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public NativeImg< VolatileLabelMultisetType, ? > createSuitableNativeImg( final NativeImgFactory< VolatileLabelMultisetType > storageFactory, final long[] dim )
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public VolatileLabelMultisetType duplicateTypeOnSameNativeImg()
	{
		return new VolatileLabelMultisetType( t.duplicateTypeOnSameNativeImg() );
	}

	@Override
	public void updateContainer( final Object c )
	{
		t.updateContainer( c );
		setValid( t.isValid() );
	}

	@Override
	public boolean valueEquals( VolatileLabelMultisetType other )
	{
		return isValid() && other.isValid() && t.valueEquals( other.t );
	}
}
