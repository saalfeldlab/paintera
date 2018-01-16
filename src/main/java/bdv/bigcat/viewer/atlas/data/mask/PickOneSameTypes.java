package bdv.bigcat.viewer.atlas.data.mask;

import java.util.function.Predicate;

import bdv.bigcat.viewer.atlas.data.mask.PickOne.PickAndConvert;
import bdv.net.imglib2.util.Triple;

public class PickOneSameTypes< T > implements PickOne.PickAndConvert< T, T, T, T >
{

	private final Predicate< T > pickThird;

	private final Predicate< T > pickSecond;

	public PickOneSameTypes( final Predicate< T > pickThird, final Predicate< T > pickSecond )
	{
		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
	}

	@Override
	public T apply( final Triple< T, T, T > t )
	{
		return pickThird.test( t.getC() ) ? t.getC() : pickSecond.test( t.getB() ) ? t.getB() : t.getA();
	}

	@Override
	public PickAndConvert< T, T, T, T > copy()
	{
		return new PickOneSameTypes<>( pickThird, pickSecond );
	}

}
