package bdv.util.bytearray;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Convenience functions for dealing with {@link ByteArrayConversion}
 *         and translations between {@link ByteArrayConversion} and
 *         {@link Iterable}.
 * 
 *
 */
public class ByteArrayConversions
{

	@SuppressWarnings( "unchecked" )
	public static < T extends RealType< T > > ByteArrayConversion< T > create( int capacity, T type )
	{
		if ( type instanceof IntType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionInt( capacity );

		else if ( type instanceof LongType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionLong( capacity );

		else if ( type instanceof FloatType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionFloat( capacity );

		else if ( type instanceof DoubleType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionDouble( capacity );

		else
			return null;
	}

	@SuppressWarnings( "unchecked" )
	public static < T extends RealType< T > > ByteArrayConversion< T > create( byte[] array, T type )
	{
		if ( type instanceof IntType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionInt( array );

		else if ( type instanceof LongType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionLong( array );

		else if ( type instanceof FloatType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionFloat( array );

		else if ( type instanceof DoubleType )
			return ( ByteArrayConversion< T > ) new ByteArrayConversionDouble( array );

		else
			return null;
	}

	public static < T extends RealType< T > > void toByteBuffer(
			Iterable< T > input,
			ByteArrayConversion< T > buffer )
	{
		for ( T i : input )
			buffer.put( i );
	}

	public static < T extends RealType< T > > ByteArrayConversion< T > toByteBuffer(
			RandomAccessibleInterval< T > interval
			)
	{
		int capacity = 1;
		for ( int d = 0; d < interval.numDimensions(); ++d )
		{
			capacity *= interval.dimension( d );
		}
		ByteArrayConversion< T > buffer = create( capacity, interval.randomAccess().get().copy() );
		toByteBuffer( Views.flatIterable( interval ), buffer );
		return buffer;
	}

	public static < T extends RealType< T > > void toIterable(
			Iterable< T > iterable,
			ByteArrayConversion< T > buffer
			)
	{
		for ( T i : iterable )
			buffer.getNext( i );
	}

	public static < T extends RealType< T > > void toIterable(
			Iterable< T > iterable,
			byte[] array )
	{
		toIterable( iterable, create( array, iterable.iterator().next() ) );
	}

}
