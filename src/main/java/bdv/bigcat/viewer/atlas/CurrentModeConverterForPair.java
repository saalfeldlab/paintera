package bdv.bigcat.viewer.atlas;

import java.util.function.Predicate;

import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Pair;

public class CurrentModeConverterForPair< T1, T2, V1 extends Volatile< T1 >, V2 extends Volatile< T2 > > implements Converter< Pair< V1, V2 >, VolatileARGBType >
{

	private Converter< V1, ARGBType > streamConverter1;

	private Converter< V2, ARGBType > streamConverter2;

	private final Predicate< V2 > predicateOnSecond;

	public CurrentModeConverterForPair( final Predicate< V2 > predicateOnSecond )
	{
		super();
		this.streamConverter1 = ( s, t ) -> {};
		this.streamConverter2 = ( s, t ) -> {};
		this.predicateOnSecond = predicateOnSecond;
	}

	@Override
	public void convert( final Pair< V1, V2 > s, final VolatileARGBType t )
	{
		// TODO Auto-generated method stub
		final V1 a = s.getA();
		final V2 b = s.getB();
		final boolean isValid = a.isValid() && b.isValid();
		t.setValid( isValid );
		if ( isValid )
			if ( predicateOnSecond.test( b ) )
				streamConverter2.convert( b, t.get() );
			else
				streamConverter1.convert( a, t.get() );
	}

	public void setConverter( final Converter< V1, ARGBType > converter1, final Converter< V2, ARGBType > converter2 )
	{
		this.streamConverter1 = converter1;
		this.streamConverter2 = converter2;
	}

}
