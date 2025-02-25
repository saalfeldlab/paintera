package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.converter.Converter;
import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.net.imglib2.util.Triple;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PickOneLabelMultisetType<M extends IntegerType<M>>
		implements PickOne.PickAndConvert<LabelMultisetType, M, M, LabelMultisetType> {

	private final Predicate<M> pickThird;

	private final BiPredicate<M, M> pickSecond;

	private final LabelMultisetType scalarValue;
	private final int numOccurrences;

	private final Converter<M, LabelMultisetType> converter;

	public PickOneLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond) {

		this(pickThird, pickSecond, LabelMultisetType.singleEntryWithSingleOccurrence(), 1);
	}

	public PickOneLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond,
			final int numOccurrences) {

		this(
				pickThird,
				pickSecond,
				new LabelMultisetType(new LabelMultisetEntry(Label.INVALID, numOccurrences)),
				numOccurrences);
	}

	private PickOneLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond,
			final LabelMultisetType scalarValue,
			final int numOccurrences) {

		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
		this.scalarValue = scalarValue;
		this.converter = new FromIntegerTypeConverter<>() {

			@Override public void convert(M input, LabelMultisetType output) {
				output.set(input.getIntegerLong(), numOccurrences);
			}
		};
		this.numOccurrences = numOccurrences;

		/* Seems like a no-op, but this provides a reference for the scalarValue to use
		 * when doing operations like add,count,sort, etc.
		 *
		 * Should be better documented :( */
		final LabelMultisetEntry ref = new LabelMultisetEntry();
		this.scalarValue.entrySetWithRef(ref);
	}


	@Override
	public LabelMultisetType apply(final Triple<LabelMultisetType, M, M> t) {

		final M c = t.getC();
		if (pickThird.test(c)) {
			converter.convert(c, scalarValue);
			return scalarValue;
		}

		final M b = t.getB();
		if (pickSecond.test(b, c)) {
			converter.convert(b, scalarValue);
			return scalarValue;
		}

		return t.getA();

	}

	@Override
	public PickAndConvert<LabelMultisetType, M, M, LabelMultisetType> copy() {

		return new PickOneLabelMultisetType<>(pickThird, pickSecond, scalarValue.copy(), numOccurrences);
	}

	@Override
	public PickAndConvert<LabelMultisetType, M, M, LabelMultisetType> copyWithDifferentNumOccurences(int numOccurrences) {

		return new PickOneLabelMultisetType<>(pickThird, pickSecond, numOccurrences);
	}

}
