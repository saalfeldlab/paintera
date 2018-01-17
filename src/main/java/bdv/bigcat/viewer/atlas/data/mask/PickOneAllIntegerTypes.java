package bdv.bigcat.viewer.atlas.data.mask;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import bdv.bigcat.viewer.atlas.data.mask.PickOne.PickAndConvert;
import bdv.net.imglib2.util.Triple;
import net.imglib2.type.numeric.IntegerType;

public class PickOneAllIntegerTypes< I extends IntegerType< I >, M extends IntegerType< M > > implements PickOne.PickAndConvert< I, M, M, I >
{

	private final Predicate< M > pickThird;

	private final BiPredicate< M, M > pickSecond;

	private final I i;

	public PickOneAllIntegerTypes( final Predicate< M > pickThird, final BiPredicate< M, M > pickSecond, final I i )
	{
		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
		this.i = i;
	}

	@Override
	public I apply( final Triple< I, M, M > t )
	{
		final I a = t.getA();
		final M b = t.getB();
		final M c = t.getC();
		i.setInteger( pickThird.test( c ) ? c.getIntegerLong() : pickSecond.test( b, c ) ? b.getIntegerLong() : a.getIntegerLong() );
		return i;
	}

	@Override
	public PickAndConvert< I, M, M, I > copy()
	{
		return new PickOneAllIntegerTypes<>( pickThird, pickSecond, i.copy() );
	}

}
