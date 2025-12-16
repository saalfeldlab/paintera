package org.janelia.saalfeldlab.paintera.data.mask;

import kotlin.Triple;
import net.imglib2.Volatile;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PickOneAllIntegerTypesVolatile<I extends IntegerType<I>, M extends IntegerType<M>, VI extends
		Volatile<I> & Type<VI>, VM extends Volatile<M>>
		implements PickOne.PickAndConvert<VI, VM, VM, VI> {

	private final Predicate<M> pickThird;

	private final BiPredicate<M, M> pickSecond;

	private final VI i;

	public PickOneAllIntegerTypesVolatile(final Predicate<M> pickThird, final BiPredicate<M, M> pickSecond, final VI i) {

		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
		this.i = i;
	}

	@Override
	public VI apply(final Triple<VI, VM, VM> t) {

		final VI va = t.getFirst();
		final VM vb = t.getSecond();
		final VM vc = t.getThird();
		final boolean isValid = va.isValid() && vb.isValid() && vc.isValid();
		i.setValid(isValid);
		if (isValid) {
			final I a = va.get();
			final M b = vb.get();
			final M c = vc.get();
			i.get().setInteger(pickThird.test(c) ? c.getIntegerLong()
					: pickSecond.test(b, c) ? b.getIntegerLong()
					: a.getIntegerLong()
			);
		}
		return i;
	}

	@Override
	public PickAndConvert<VI, VM, VM, VI> copy() {

		return new PickOneAllIntegerTypesVolatile<>(pickThird, pickSecond, i.copy());
	}

}
