package bdv.bigcat.viewer.atlas;

import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class CurrentModeConverter< T extends Volatile< ? > & Type< T > > implements Converter< T, VolatileARGBType >
{

	private Converter< T, ARGBType > streamConverter;

	public CurrentModeConverter()
	{
		super();
		this.streamConverter = ( s, t ) -> {};
	}

	@Override
	public void convert( final T s, final VolatileARGBType t )
	{
		// TODO Auto-generated method stub
		final boolean isValid = s.isValid();
		t.setValid( isValid );
		if ( isValid )
			streamConverter.convert( s, t.get() );
	}

	public void setConverter( final Converter< T, ARGBType > converter )
	{
		this.streamConverter = converter;
	}

}
