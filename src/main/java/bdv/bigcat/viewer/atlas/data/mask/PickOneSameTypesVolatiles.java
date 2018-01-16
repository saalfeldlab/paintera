package bdv.bigcat.viewer.atlas.data.mask;

import java.util.function.Predicate;

import bdv.bigcat.viewer.atlas.data.mask.PickOne.PickAndConvert;
import bdv.net.imglib2.util.Triple;
import net.imglib2.Volatile;
import net.imglib2.type.Type;

public class PickOneSameTypesVolatiles< T extends Type< T >, V extends Volatile< T > & Type< V > > implements PickOne.PickAndConvert< V, V, V, V >
{

	private final Predicate< T > pickThird;

	private final Predicate< T > pickSecond;

	private final V v;

	public PickOneSameTypesVolatiles( final Predicate< T > pickThird, final Predicate< T > pickSecond, final V v )
	{
		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
		this.v = v;
	}

	@Override
	public V apply( final Triple< V, V, V > t )
	{
		final V va = t.getA();
		final V vb = t.getB();
		final V vc = t.getC();
		final boolean isValid = va.isValid() && vb.isValid() && vc.isValid();
		v.setValid( isValid );
		if ( isValid )
		{
			final T a = va.get();
			final T b = vb.get();
			final T c = vc.get();
			v.get().set( pickThird.test( c ) ? c : pickSecond.test( b ) ? b : a );
		}
		return v;
	}

	@Override
	public PickAndConvert< V, V, V, V > copy()
	{
		return new PickOneSameTypesVolatiles<>( pickThird, pickSecond, v.copy() );
	}

}
