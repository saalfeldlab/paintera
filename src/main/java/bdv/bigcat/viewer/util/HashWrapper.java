package bdv.bigcat.viewer.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;

public class HashWrapper< T > implements Serializable
{

	private final T t;

	private final ToIntFunction< T > hash;

	private final BiPredicate< T, T > equals;

	private int hashValue;

	public HashWrapper( final T t, final ToIntFunction< T > hash, final BiPredicate< T, T > equals )
	{
		super();
		this.t = t;
		this.hash = hash;
		this.equals = equals;
		updateHashValue();
	}

	public void updateHashValue()
	{
		this.hashValue = hash.applyAsInt( this.t );
	}

	public T getData()
	{
		return this.t;
	}

	public ToIntFunction< T > getHash()
	{
		return hash;
	}

	public BiPredicate< T, T > getEquals()
	{
		return equals;
	}

	@Override
	public int hashCode()
	{
		return hashValue;
	}

	@Override
	public boolean equals( final Object o )
	{
		if ( o instanceof HashWrapper )
		{
			final Object t = ( (bdv.bigcat.viewer.util.HashWrapper< ? > ) o ).getData();
			return this.t.getClass().isInstance( t ) && equals.test( this.t, ( T ) t );
		}
		return false;
	}

	public static class LongArrayHash implements ToIntFunction< long[] >
	{

		@Override
		public int applyAsInt( final long[] arr )
		{
			return Arrays.hashCode( arr );
		}

	}

	public static class LongArrayEquals implements BiPredicate< long[], long[] >
	{

		@Override
		public boolean test( final long[] t, final long[] u )
		{
			return Arrays.equals( t, u );
		}

	}

	public static HashWrapper< long[] > longArray( final long... array )
	{
		return new HashWrapper<>( array, new LongArrayHash(), new LongArrayEquals() );
	}

	public static HashWrapper< Interval > interval( final Interval interval )
	{
		final LongArrayHash hash = new LongArrayHash();
		return new HashWrapper<>(
				interval,
				i -> 31 * hash.applyAsInt( Intervals.minAsLongArray( i ) ) + hash.applyAsInt( Intervals.maxAsLongArray( i ) ),
				( i1, i2 ) -> Intervals.contains( i1, i2 ) && Intervals.contains( i2, i1 ) );
	}

}
