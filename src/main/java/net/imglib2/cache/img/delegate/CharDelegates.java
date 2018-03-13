package net.imglib2.cache.img.delegate;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import net.imglib2.cache.img.AccessIo;
import net.imglib2.img.basictypeaccess.CharAccess;
import net.imglib2.img.basictypeaccess.array.CharArray;
import net.imglib2.img.basictypeaccess.constant.ConstantCharAccess;
import net.imglib2.img.basictypeaccess.delegate.Delegate;
import net.imglib2.img.basictypeaccess.delegate.DelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateCharAccess;

public class CharDelegates
{

	public static final byte NO_FLAGS = 0;

	public static final byte SINGLE_VALUE_FLAG = 0x00000001;

	public static final DelegateType delegateIo = new DelegateType();

	public static final VolatileDelegateType volatileDelegateIo = new VolatileDelegateType();

	public static final DirtyDelegateType dirtyDelegateIo = new DirtyDelegateType();

	public static final DirtyVolatileDelegateType dirtyVolatileDelegateIo = new DirtyVolatileDelegateType();

	public static abstract class AbstractCharDelegateIo< B extends Delegate< CharAccess > > implements AccessIo< B >
	{

		@Override
		public int getBytesPerElement()
		{
			return Character.BYTES;
		}

		private boolean isSingleValue( final byte b )
		{
			return ( b & SINGLE_VALUE_FLAG ) != 0;
		}

		private boolean isSingleValue( final CharAccess a )
		{
			return a instanceof ConstantCharAccess;
		}

		@Override
		public void save( final B access, final ByteBuffer out, final int numElements )
		{
			final CharAccess delegate = access.getDelegate();
			final boolean isSingleValue = isSingleValue( delegate );
			if ( isSingleValue )
			{
				out.put( SINGLE_VALUE_FLAG );
				out.putChar( delegate.getValue( 0 ) );
			}
			else
			{
				out.put( NO_FLAGS );
				for ( int i = 0; i < numElements; ++i )
					out.putChar( delegate.getValue( i ) );
			}
		}

		public CharAccess loadDelegate( final ByteBuffer bytes, final int numElements )
		{
			final byte options = bytes.get();
			final CharBuffer chars = bytes.asCharBuffer();
			if ( isSingleValue( options ) )
				return new ConstantCharAccess( chars.get() );
			else
			{
				final char[] data = new char[ numElements ];
				CharBuffer.wrap( data ).put( chars );
				return new CharArray( data );
			}
		}

	}

	public static class DelegateType extends AbstractCharDelegateIo< DelegateCharAccess >
	{
		@Override
		public DelegateCharAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DelegateCharAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class VolatileDelegateType extends AbstractCharDelegateIo< VolatileDelegateCharAccess >
	{
		@Override
		public VolatileDelegateCharAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new VolatileDelegateCharAccess( loadDelegate( bytes, numElements ), true );
		}
	}

	public static class DirtyDelegateType extends AbstractCharDelegateIo< DirtyDelegateCharAccess >
	{
		@Override
		public DirtyDelegateCharAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyDelegateCharAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class DirtyVolatileDelegateType extends AbstractCharDelegateIo< DirtyVolatileDelegateCharAccess >
	{
		@Override
		public DirtyVolatileDelegateCharAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyVolatileDelegateCharAccess( loadDelegate( bytes, numElements ), true );
		}
	}
}
