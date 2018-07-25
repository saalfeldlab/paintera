package org.janelia.saalfeldlab.paintera.data;

import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;

import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.BooleanType;
import net.imglib2.type.logic.BoolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PredicateDataSource<D, T extends Volatile<D>, P extends Predicate<D>>
		extends ConvertedDataSource<D, T, BoolType, Volatile<BoolType>>
{

	private final P predicate;

	public PredicateDataSource(
			final DataSource<D, T> source,
			final P predicate,
			final String name)
	{
		super(
				source,
				new PredicateConverter<>(predicate),
				new VolatilePredicateConverter<>(predicate),
				() -> new BoolType(false),
				() -> new Volatile<>(new BoolType(false), true),
				Interpolations.nearestNeighbor(),
				Interpolations.nearestNeighbor(),
				name
		     );
		this.predicate = predicate;
	}

	public P getPredicate()
	{
		return predicate;
	}

	public static class PredicateConverter<T, B extends BooleanType<B>> implements Converter<T, B>
	{
		private final Predicate<T> predicate;

		public PredicateConverter(final Predicate<T> predicate)
		{
			super();
			this.predicate = predicate;
		}

		@Override
		public void convert(final T input, final B output)
		{
			output.set(predicate.test(input));
		}
	}

	public static class VolatilePredicateConverter<T, V extends Volatile<T>, B extends BooleanType<B>>
			implements Converter<V, Volatile<B>>
	{

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private final Predicate<T> predicate;

		public VolatilePredicateConverter(final Predicate<T> predicate)
		{
			super();
			this.predicate = predicate;
		}

		@Override
		public void convert(final V input, final Volatile<B> output)
		{
			final boolean isValid = input.isValid();
			output.setValid(isValid);
			if (isValid)
			{
				final boolean test = predicate.test(input.get());
				output.get().set(test);
			}
		}
	}

}
