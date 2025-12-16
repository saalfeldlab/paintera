package org.janelia.saalfeldlab.paintera.meshes.cache;

import bdv.viewer.Source;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.converter.Converter;
import net.imglib2.type.BooleanType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetEntryList;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Util;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SegmentMaskGenerators {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static <T, B extends BooleanType<B>> BiFunction<Long, Double, Converter<T, B>> create(
			final DataSource<T, ?> source,
			final int level,
			final Function<Long, TLongHashSet> fragmentsForSegment
	) {

		final T t = source.getDataType();

		if (t instanceof LabelMultisetType) {
			final LabelMultisetTypeMaskGenerator labelMultisetTypeMaskGenerator = new LabelMultisetTypeMaskGenerator(source, level);
			return (l, minLabelRatio) -> labelMultisetTypeMaskGenerator.apply(fragmentsForSegment.apply(l), minLabelRatio);
		}

		if (t instanceof IntegerType<?>) {
			final IntegerTypeMaskGenerator integerTypeMaskGenerator = new IntegerTypeMaskGenerator();
			return (l, minLabelRatio) -> integerTypeMaskGenerator.apply(fragmentsForSegment.apply(l));
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
		final LabelMultisetEntry reusableReference = new LabelMultisetEntryList().createRef();

		@Override
		public void convert(final LabelMultisetType input, final B output) {

			final Set<Entry<Label>> inputSet = input.entrySetWithRef(reusableReference);
			final int validLabelsSize = validLabels.size();
			final int inputSize = inputSet.size();

			if (inputSize == 0) {
				output.set(false);
				return;
			}

			if (validLabelsSize <= inputSize) {
				/* using an enhanced for-loop is slower!
				 * `hasNext` on the trove iterator is surprisingly expensive :( */
				final TLongIterator it = validLabels.iterator();
				if (inputSize == 1) {
					final long inputLabel = input.argMax();
					for (int i = 0; i < validLabelsSize; i++) {
						if (inputLabel == it.next()) {
							output.set(true);
							return;
						}
					}
				} else {
					for (int i = 0; i < validLabelsSize; i++) {
						if (input.contains(it.next())) {
							output.set(true);
							return;
						}
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

		private final LabelMultisetEntry reusableReference = new LabelMultisetEntryList().createRef();

		public LabelMultisetTypeMaskWithMinLabelRatio(final TLongSet validLabels, final double minLabelRatio, final long numFullResPixels) {

			assert numFullResPixels > 0;
			LOG.debug(
					"Creating {} with min label ratio: {}, numFullResPixels: {}, valid labels: {}",
					this.getClass().getSimpleName(),
					validLabels,
					minLabelRatio,
					numFullResPixels);
			this.validLabels = validLabels;
			this.minNumRequiredPixels = (long) Math.ceil(numFullResPixels * minLabelRatio);
		}

		@Override
		public void convert(final LabelMultisetType input, final B output) {

			final Set<Entry<Label>> inputSet = input.entrySetWithRef(reusableReference);
			final int validLabelsSize = validLabels.size();
			final int inputSize = inputSet.size();
			// no primitive type support for slf4j
			// http://mailman.qos.ch/pipermail/slf4j-dev/2005-August/000241.html
			if (LOG.isTraceEnabled()) {
				LOG.trace("input size={}, validLabels size={}", inputSize, validLabelsSize);
			}
			final var validLabelsContainedCount = new AtomicLong(0);
			if (validLabelsSize < inputSize) {
				final var breakEarly = !validLabels.forEach(label -> {
					final var count = validLabelsContainedCount.addAndGet(input.countWithRef(label, reusableReference));
					if (count >= minNumRequiredPixels) {
						output.set(true);
						return false;
					}
					return true;
				});
				if (breakEarly) {
					return;
				}
			} else {
				Iterator<Entry<Label>> iterator = inputSet.iterator();
				while (iterator.hasNext()) {
					Entry<Label> labelEntry = iterator.next();
					final long id = labelEntry.getElement().id();
					if (validLabels.contains(id)) {
						final var count = validLabelsContainedCount.addAndGet(labelEntry.getCount());
						if (count >= minNumRequiredPixels) {
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
