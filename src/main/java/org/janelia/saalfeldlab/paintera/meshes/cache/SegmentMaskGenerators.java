package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.converter.Converter;
import net.imglib2.type.BooleanType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.numeric.IntegerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SegmentMaskGenerators
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static <T, B extends BooleanType<B>> Function<TLongHashSet, Converter<T, B>> forType(final T t)
	{
		if (t instanceof LabelMultisetType) { return new LabelMultisetTypeMaskGenerator(); }

		if (t instanceof IntegerType<?>) { return new IntegerTypeMaskGenerator(); }

		return null;
	}

	private static class LabelMultisetTypeMaskGenerator<B extends BooleanType<B>>
			implements Function<TLongHashSet, Converter<LabelMultisetType, B>>
	{

		@Override
		public Converter<LabelMultisetType, B> apply(final TLongHashSet validLabels)
		{
			return new LabelMultisetTypeMask<>(validLabels);
		}

	}

	private static class IntegerTypeMaskGenerator<I extends IntegerType<I>, B extends BooleanType<B>>
			implements Function<TLongHashSet, Converter<I, B>>
	{

		@Override
		public Converter<I, B> apply(final TLongHashSet validLabels)
		{
			return new IntegerTypeMask<>(validLabels);
		}

	}

	private static class LabelMultisetTypeMask<B extends BooleanType<B>> implements Converter<LabelMultisetType, B>
	{

		private final TLongHashSet validLabels;

		public LabelMultisetTypeMask(final TLongHashSet validLabels)
		{
			super();
			this.validLabels = validLabels;
		}

		@Override
		public void convert(final LabelMultisetType input, final B output)
		{
			final Set<Entry<Label>> inputSet        = input.entrySet();
			final int               validLabelsSize = validLabels.size();
			final int               inputSize       = inputSet.size();
			// no primitive type support for slf4j
			// http://mailman.qos.ch/pipermail/slf4j-dev/2005-August/000241.html
			if (LOG.isTraceEnabled())
			{
				LOG.trace("input size={}, validLabels size={}", inputSize, validLabelsSize);
			}
			if (validLabelsSize < inputSize)
			{
				for (final TLongIterator it = validLabels.iterator(); it.hasNext(); )
				{
					if (input.contains(it.next()))
					{
						output.set(true);
						return;
					}
				}
			}
			else
			{
				for (final Iterator<Entry<Label>> it = inputSet.iterator(); it.hasNext(); )
				{
					if (validLabels.contains(it.next().getElement().id()))
					{
						output.set(true);
						return;
					}
				}
			}
			output.set(false);
		}

	}

	private static class IntegerTypeMask<I extends IntegerType<I>, B extends BooleanType<B>> implements Converter<I, B>
	{

		private final TLongHashSet validLabels;

		public IntegerTypeMask(final TLongHashSet validLabels)
		{
			super();
			this.validLabels = validLabels;
		}

		@Override
		public void convert(final I input, final B output)
		{
			output.set(validLabels.contains(input.getIntegerLong()));
		}

	}

}
