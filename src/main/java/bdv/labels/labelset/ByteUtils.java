package bdv.labels.labelset;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

import sun.misc.Unsafe;

/**
 * Helper methods to encode and decode different data types ({@code long, double}
 * etc.) from bytes at an offset in a {@code long[]} array.
 *
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
//@SuppressWarnings( "restriction" )
public class ByteUtils
{
	public static final int BYTE_SIZE = 1;

	public static final int BOOLEAN_SIZE = 1;

	public static final int INT_SIZE = 4;

	public static final int LONG_SIZE = 8;

	public static final int FLOAT_SIZE = 4;

	public static final int DOUBLE_SIZE = 8;

	public static void putByte( final byte value, final long[] array, final long offset )
	{
		UNSAFE.putByte( array, LONG_ARRAY_OFFSET + offset, value );
	}

	public static byte getByte( final long[] array, final long offset )
	{
		return UNSAFE.getByte( array, LONG_ARRAY_OFFSET + offset );
	}

	public static void putBoolean( final boolean value, final long[] array, final long offset )
	{
		putByte( value ? ( byte ) 1 : ( byte ) 0, array, offset );
	}

	public static boolean getBoolean( final long[] array, final long offset )
	{
		return getByte( array, offset ) == ( byte ) 0 ? false : true;
	}

	public static void putInt( final int value, final long[] array, final long offset )
	{
		UNSAFE.putInt( array, LONG_ARRAY_OFFSET + offset, value );
	}

	public static int getInt( final long[] array, final long offset )
	{
		return UNSAFE.getInt( array, LONG_ARRAY_OFFSET + offset );
	}

	public static void putLong( final long value, final long[] array, final long offset )
	{
		UNSAFE.putLong( array, LONG_ARRAY_OFFSET + offset, value );
	}

	public static long getLong( final long[] array, final long offset )
	{
		return UNSAFE.getLong( array, LONG_ARRAY_OFFSET + offset );
	}

	public static void putFloat( final float value, final long[] array, final long offset )
	{
		UNSAFE.putFloat( array, LONG_ARRAY_OFFSET + offset, value );
	}

	public static float getFloat( final long[] array, final long offset )
	{
		return UNSAFE.getFloat( array, LONG_ARRAY_OFFSET + offset );
	}

	public static void putDouble( final double value, final long[] array, final long offset )
	{
		UNSAFE.putDouble( array, LONG_ARRAY_OFFSET + offset, value );
	}

	public static double getDouble( final long[] array, final long offset )
	{
		return UNSAFE.getDouble( array, LONG_ARRAY_OFFSET + offset );
	}

	// Note: offsets in bytes!
	public static void copyBytes( final long[] srcArray, final long srcOffset, final long[] dstArray, final long dstOffset, final int size )
	{
		UNSAFE.copyMemory( srcArray, LONG_ARRAY_OFFSET + srcOffset, dstArray, LONG_ARRAY_OFFSET + dstOffset, size );
	}

	private static final Unsafe UNSAFE;

	static
	{
		try
		{
			final PrivilegedExceptionAction< Unsafe > action = new PrivilegedExceptionAction< Unsafe >()
			{
				@Override
				public Unsafe run() throws Exception
				{
					final Field field = Unsafe.class.getDeclaredField( "theUnsafe" );
					field.setAccessible( true );
					return ( Unsafe ) field.get( null );
				}
			};

			UNSAFE = AccessController.doPrivileged( action );
		}
		catch ( final Exception ex )
		{
			throw new RuntimeException( ex );
		}
	}

	private static final long LONG_ARRAY_OFFSET = UNSAFE.arrayBaseOffset( long[].class );
}
