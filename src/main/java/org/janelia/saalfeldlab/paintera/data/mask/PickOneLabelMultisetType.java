package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.converter.Converter;
import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Triple;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PickOneLabelMultisetType<M extends IntegerType<M>>
		implements PickOne.PickAndConvert<LabelMultisetType, M, M, LabelMultisetType> {

  private final Predicate<M> pickThird;

  private final BiPredicate<M, M> pickSecond;

  private final LabelMultisetType scalarValue;

  private final Converter<M, LabelMultisetType> converter;

  public PickOneLabelMultisetType(
		  final Predicate<M> pickThird,
		  final BiPredicate<M, M> pickSecond) {

	this(pickThird, pickSecond, FromIntegerTypeConverter.getAppropriateType());
  }

  public PickOneLabelMultisetType(
		  final Predicate<M> pickThird,
		  final BiPredicate<M, M> pickSecond,
		  final int numOccurrences) {

	this(
			pickThird,
			pickSecond,
			new LabelMultisetType(new LabelMultisetEntry(Label.INVALID, numOccurrences)));
  }

  private PickOneLabelMultisetType(
		  final Predicate<M> pickThird,
		  final BiPredicate<M, M> pickSecond,
		  final LabelMultisetType scalarValue) {

	super();
	this.pickThird = pickThird;
	this.pickSecond = pickSecond;
	this.scalarValue = scalarValue;
	this.converter = new FromIntegerTypeConverter<>();
  }

  @Override
  public LabelMultisetType apply(final Triple<LabelMultisetType, M, M> t) {

	final LabelMultisetType a = t.getA();
	final M b = t.getB();
	final M c = t.getC();

	if (pickThird.test(c)) {
	  converter.convert(c, scalarValue);
	  return scalarValue;
	}

	if (pickSecond.test(b, c)) {
	  converter.convert(b, scalarValue);
	  return scalarValue;
	}

	return a;

  }

  @Override
  public PickAndConvert<LabelMultisetType, M, M, LabelMultisetType> copy() {

	return new PickOneLabelMultisetType<>(pickThird, pickSecond, this.scalarValue.copy());
  }

  @Override
  public PickAndConvert<LabelMultisetType, M, M, LabelMultisetType> copyWithDifferentNumOccurences(int numOccurrences) {

	return new PickOneLabelMultisetType<>(pickThird, pickSecond, numOccurrences);
  }

}
