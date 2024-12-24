package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.net.imglib2.util.Triple;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PickOneVolatileLabelMultisetType<M extends IntegerType<M>, VM extends Volatile<M>>
		implements PickOne.PickAndConvert<VolatileLabelMultisetType, VM, VM, VolatileLabelMultisetType> {

	private final Predicate<M> pickThird;

	private final BiPredicate<M, M> pickSecond;

	private final VolatileLabelMultisetType scalarValue;

	private final Converter<M, LabelMultisetType> converter;

	public PickOneVolatileLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond) {

		this(pickThird, pickSecond, VolatileLabelMultisetType.singleEntryWithSingleOccurrence());
	}

	public PickOneVolatileLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond,
			final int numOccurrences) {

		this(
				pickThird,
				pickSecond,
				new VolatileLabelMultisetType(new LabelMultisetEntry(Label.INVALID, numOccurrences)));
	}

	private PickOneVolatileLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond,
			final VolatileLabelMultisetType scalarValue) {

		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
		this.scalarValue = scalarValue;
		this.converter = new FromIntegerTypeConverter<>();
	}

	@Override
	public VolatileLabelMultisetType apply(final Triple<VolatileLabelMultisetType, VM, VM> t) {

		final VolatileLabelMultisetType a = t.getA();
		if (!a.isValid()) {
			scalarValue.setValid(false);
			return scalarValue;
		}
		final VM vb = t.getB();
		if (!vb.isValid()) {
			scalarValue.setValid(false);
			return scalarValue;
		}
		final VM vc = t.getC();
		if (!vc.isValid()) {
			scalarValue.setValid(false);
			return scalarValue;
		}

		scalarValue.setValid(true);

		final M c = vc.get();
		if (pickThird.test(c)) {
			converter.convert(c, scalarValue.get());
			return scalarValue;
		}

		final M b = vb.get();
		if (pickSecond.test(b, c)) {
			converter.convert(b, scalarValue.get());
			return scalarValue;
		}

		return a;

	}

	@Override
	public PickAndConvert<VolatileLabelMultisetType, VM, VM, VolatileLabelMultisetType> copy() {

		return new PickOneVolatileLabelMultisetType<>(pickThird, pickSecond, scalarValue.copy());
	}

	@Override
	public PickAndConvert<VolatileLabelMultisetType, VM, VM, VolatileLabelMultisetType> copyWithDifferentNumOccurences(int numOccurrences) {

		return new PickOneVolatileLabelMultisetType<>(pickThird, pickSecond, numOccurrences);
	}

}
