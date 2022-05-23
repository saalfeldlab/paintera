package org.janelia.saalfeldlab.paintera.meshes.cache;

import bdv.viewer.Source;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.converter.Converter;
import net.imglib2.type.BooleanType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Util;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SegmentMaskGenerators {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static <T, B extends BooleanType<B>> BiFunction<TLongHashSet, Double, Converter<T, B>> create(
		  final DataSource<T, ?> source,
		  final int level) {

	final T t = source.getDataType();

	if (t instanceof LabelMultisetType)
	  return new LabelMultisetTypeMaskGenerator(source, level);

	if (t instanceof IntegerType<?>) {
	  final IntegerTypeMaskGenerator integerTypeMaskGenerator = new IntegerTypeMaskGenerator();
	  return (l, minLabelRatio) -> integerTypeMaskGenerator.apply(l);
	}

	return null;
  }

  private static class LabelMultisetTypeMaskGenerator<B extends BooleanType<B>>
		  implements BiFunction<TLongHashSet, Double, Converter<LabelMultisetType, B>> {

	private final long numFullResPixels;

	LabelMultisetTypeMaskGenerator(final Source<?> source, final int level) {

	  final double[] scales = DataSource.getRelativeScales(source, 0, 0, level);
	  // check that all scales are integers
	  assert Arrays.stream(scales).allMatch(scale -> Util.isApproxEqual(scale, Math.round(scale), 1e-7));
	  numFullResPixels = Arrays.stream(scales).mapToLong(Math::round).reduce(1, Math::multiplyExact);
	}

	@Override
	public Converter<LabelMultisetType, B> apply(final TLongHashSet validLabels, final Double minLabelRatio) {

	  return minLabelRatio == null || minLabelRatio == 0.0
			  ? new LabelMultisetTypeMask<>(validLabels)
			  : new LabelMultisetTypeMaskWithMinLabelRatio<>(validLabels, minLabelRatio, numFullResPixels);
	}

  }

  private static class IntegerTypeMaskGenerator<I extends IntegerType<I>, B extends BooleanType<B>>
		  implements Function<TLongHashSet, Converter<I, B>> {

	@Override
	public Converter<I, B> apply(final TLongHashSet validLabels) {

	  return new IntegerTypeMask<>(validLabels);
	}

  }

  // basic implementation that only checks if any of the labels are contained in the current pixel
  private static class LabelMultisetTypeMask<B extends BooleanType<B>> implements Converter<LabelMultisetType, B> {

	private final TLongSet validLabels;

	public LabelMultisetTypeMask(final TLongSet validLabels) {

	  LOG.debug("Creating {} with valid labels: {}", this.getClass().getSimpleName(), validLabels);
	  this.validLabels = validLabels;
	}

	@Override
	public void convert(final LabelMultisetType input, final B output) {

	  final Set<LabelMultisetEntry> inputSet = input.entrySetWithRef(new LabelMultisetEntry());
	  final int validLabelsSize = validLabels.size();
	  final int inputSize = inputSet.size();
	  // no primitive type support for slf4j
	  // http://mailman.qos.ch/pipermail/slf4j-dev/2005-August/000241.html
	  if (LOG.isTraceEnabled()) {
		LOG.trace("input size={}, validLabels size={}", inputSize, validLabelsSize);
	  }

	  if (validLabelsSize < inputSize) {
		for (final TLongIterator it = validLabels.iterator(); it.hasNext(); ) {
		  if (input.contains(it.next())) {
			output.set(true);
			return;
		  }
		}
	  } else {
		for (final Entry<Label> labelEntry : inputSet) {
		  if (validLabels.contains(labelEntry.getElement().id())) {
			output.set(true);
			return;
		  }
		}
	  }
	  output.set(false);
	}
  }

  // Count occurrences of the labels in the current pixel to see if it's above the specified min label pixel ratio
  private static class LabelMultisetTypeMaskWithMinLabelRatio<B extends BooleanType<B>> implements Converter<LabelMultisetType, B> {

	private final TLongSet validLabels;
	private final long minNumRequiredPixels;

	public LabelMultisetTypeMaskWithMinLabelRatio(final TLongSet validLabels, final double minLabelRatio, final long numFullResPixels) {

	  assert numFullResPixels > 0;
	  LOG.debug(
			  "Creating {} with min label ratio: {}, numFullResPixels: {}, valid labels: {}",
			  this.getClass().getSimpleName(),
			  validLabels,
			  minLabelRatio,
			  numFullResPixels);
	  this.validLabels = validLabels;
	  this.minNumRequiredPixels = (long)Math.ceil(numFullResPixels * minLabelRatio);
	}

	@Override
	public void convert(final LabelMultisetType input, final B output) {

	  final Set<LabelMultisetEntry> inputSet = input.entrySetWithRef(new LabelMultisetEntry());
	  final int validLabelsSize = validLabels.size();
	  final int inputSize = inputSet.size();
	  // no primitive type support for slf4j
	  // http://mailman.qos.ch/pipermail/slf4j-dev/2005-August/000241.html
	  if (LOG.isTraceEnabled()) {
		LOG.trace("input size={}, validLabels size={}", inputSize, validLabelsSize);
	  }
	  long validLabelsContainedCount = 0;
	  if (validLabelsSize < inputSize) {
		final var ref = new LabelMultisetEntry();
		for (final TLongIterator it = validLabels.iterator(); it.hasNext(); ) {
		  validLabelsContainedCount += input.countWithRef(it.next(), ref);
		  if (validLabelsContainedCount >= minNumRequiredPixels) {
			output.set(true);
			return;
		  }
		}
	  } else {
		for (final Entry<Label> labelEntry : inputSet) {
		  if (validLabels.contains(labelEntry.getElement().id())) {
			validLabelsContainedCount += labelEntry.getCount();
			if (validLabelsContainedCount >= minNumRequiredPixels) {
			  output.set(true);
			  return;
			}
		  }
		}
	  }
	  output.set(false);
	}
  }

  private static class IntegerTypeMask<I extends IntegerType<I>, B extends BooleanType<B>> implements Converter<I, B> {

	private final TLongHashSet validLabels;

	public IntegerTypeMask(final TLongHashSet validLabels) {

	  super();
	  this.validLabels = validLabels;
	}

	@Override
	public void convert(final I input, final B output) {

	  output.set(validLabels.contains(input.getIntegerLong()));
	}

  }

}
