package org.janelia.saalfeldlab.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;

public class HashWrapper<T> implements Serializable
{

	/**
	 *
	 */
	private static final long serialVersionUID = -2571523935606311437L;

	private final T t;

	private final ToIntFunction<T> hash;

	private final BiPredicate<T, T> equals;

	private int hashValue;

	private Function<T, String> toString;

	public HashWrapper(final T t, final ToIntFunction<T> hash, final BiPredicate<T, T> equals)
	{
		this(t, hash, equals, T::toString);
	}

	public HashWrapper(final T t, final ToIntFunction<T> hash, final BiPredicate<T, T> equals, final Function<T,
			String> toString)
	{
		super();
		this.t = t;
		this.hash = hash;
		this.equals = equals;
		this.toString = toString;
		updateHashValue();
	}

	public void updateHashValue()
	{
		this.hashValue = hash.applyAsInt(this.t);
	}

	public T getData()
	{
		return this.t;
	}

	public ToIntFunction<T> getHash()
	{
		return hash;
	}

	public BiPredicate<T, T> getEquals()
	{
		return equals;
	}

	@Override
	public int hashCode()
	{
		return hashValue;
	}

	@Override
	public boolean equals(final Object o)
	{
		if (o instanceof HashWrapper)
		{
			final HashWrapper<?> hw  = ((HashWrapper<?>) o);
			final Object         obj = hw.getData();
			if (this.t.getClass().isInstance(obj))
			{
				@SuppressWarnings("unchecked") final HashWrapper<T> that = (HashWrapper<T>) hw;
				return this.dataEquals(that);
			}
		}
		return false;
	}

	public boolean dataEquals(final HashWrapper<T> that)
	{
		return equals.test(this.t, that.t);
	}

	public static class LongArrayHash implements ToIntFunction<long[]>
	{

		@Override
		public int applyAsInt(final long[] arr)
		{
			return Arrays.hashCode(arr);
		}

	}

	public static class LongArrayEquals implements BiPredicate<long[], long[]>
	{

		@Override
		public boolean test(final long[] t, final long[] u)
		{
			return Arrays.equals(t, u);
		}

	}

	public static HashWrapper<long[]> longArray(final long... array)
	{
		return new HashWrapper<>(array, new LongArrayHash(), new LongArrayEquals());
	}

	public static HashWrapper<Interval> interval(final Interval interval)
	{
		return HashWrapper.interval(
				interval,
				i -> Arrays.toString(Intervals.minAsLongArray(i)) + " " + Arrays.toString(Intervals.maxAsLongArray(i))
		                           );

	}


	public static HashWrapper<Interval> interval(final Interval interval, final Function<Interval, String> toString)
	{
		final LongArrayHash hash = new LongArrayHash();
		return new HashWrapper<>(
				interval,
				i -> 31 * hash.applyAsInt(Intervals.minAsLongArray(i)) + hash.applyAsInt(Intervals.maxAsLongArray(i)),
				(i1, i2) -> Intervals.contains(i1, i2) && Intervals.contains(i2, i1),
				toString
		);
	}

	@Override
	public String toString()
	{
		return "{HashWrapper: " + this.toString.apply(this.t) + "}";
	}

}
