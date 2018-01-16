package bdv.bigcat.viewer.atlas;

import java.util.Optional;

import bdv.bigcat.viewer.stream.SetSeed;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class CurrentModeConverter< T, C extends Converter< T, ARGBType > & SetSeed > implements Converter< T, ARGBType >
{

	private C streamConverter;

	private final LongProperty seed = new SimpleLongProperty( 1 );

	public CurrentModeConverter()
	{
		super();
		this.streamConverter = null;
		seed.addListener( ( obs, oldv, newv ) -> Optional.ofNullable( streamConverter ).ifPresent( c -> c.setSeed( newv.longValue() ) ) );
	}

	@Override
	public void convert( final T s, final ARGBType t )
	{
		// TODO Auto-generated method stub
		streamConverter.convert( s, t );
	}

	public void setConverter( final C converter )
	{
		this.streamConverter = converter;
	}

}
