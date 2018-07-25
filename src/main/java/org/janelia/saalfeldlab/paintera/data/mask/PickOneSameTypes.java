package org.janelia.saalfeldlab.paintera.data.mask;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import net.imglib2.util.Triple;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;

public class PickOneSameTypes<T> implements PickOne.PickAndConvert<T, T, T, T>
{

	private final Predicate<T> pickThird;

	private final BiPredicate<T, T> pickSecond;

	public PickOneSameTypes(final Predicate<T> pickThird, final BiPredicate<T, T> pickSecond)
	{
		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
	}

	@Override
	public T apply(final Triple<T, T, T> t)
	{
		return pickThird.test(t.getC()) ? t.getC() : pickSecond.test(t.getB(), t.getC()) ? t.getB() : t.getA();
	}

	@Override
	public PickAndConvert<T, T, T, T> copy()
	{
		return new PickOneSameTypes<>(pickThird, pickSecond);
	}

}
