package net.imglib2.cache.img.delegate;

import java.nio.ByteBuffer;

import net.imglib2.cache.img.AccessIo;
import net.imglib2.img.basictypeaccess.ByteAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.constant.ConstantByteAccess;
import net.imglib2.img.basictypeaccess.delegate.Delegate;
import net.imglib2.img.basictypeaccess.delegate.DelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateByteAccess;

public class ByteDelegates
{

	public static final byte NO_FLAGS = 0;

	public static final byte SINGLE_VALUE_FLAG = 0x00000001;

	public static final DelegateType delegateIo = new DelegateType();

	public static final VolatileDelegateType volatileDelegateIo = new VolatileDelegateType();

	public static final DirtyDelegateType dirtyDelegateIo = new DirtyDelegateType();

	public static final DirtyVolatileDelegateType dirtyVolatileDelegateIo = new DirtyVolatileDelegateType();

	public static abstract class AbstractByteDelegateIo< B extends Delegate< ByteAccess > > implements AccessIo< B >
	{

		@Override
		public long getNumBytes( final long numElements )
		{
			return getBytesPerElement() * numElements + 1;
		}

		@Override
		public int getBytesPerElement()
		{
			return Byte.BYTES;
		}

		private boolean isSingleValue( final byte b )
		{
			return ( b & SINGLE_VALUE_FLAG ) != 0;
		}

		private boolean isSingleValue( final ByteAccess a )
		{
			return a instanceof ConstantByteAccess;
		}

		@Override
		public void save( final B access, final ByteBuffer out, final int numElements )
		{
			final ByteAccess delegate = access.getDelegate();
			final boolean isSingleValue = isSingleValue( delegate );
			if ( isSingleValue )
			{
				out.put( SINGLE_VALUE_FLAG );
				out.put( delegate.getValue( 0 ) );
			}
			else
			{
				out.put( NO_FLAGS );
				for ( int i = 0; i < numElements; ++i )
					out.put( delegate.getValue( i ) );
			}
		}

		public ByteAccess loadDelegate( final ByteBuffer bytes, final int numElements )
		{
			final byte options = bytes.get();
			if ( isSingleValue( options ) )
				return new ConstantByteAccess( bytes.get() );
			else
			{
				final byte[] data = new byte[ numElements ];
				ByteBuffer.wrap( data ).put( bytes );
				return new ByteArray( data );
			}
		}

	}

	public static class DelegateType extends AbstractByteDelegateIo< DelegateByteAccess >
	{
		@Override
		public DelegateByteAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DelegateByteAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class VolatileDelegateType extends AbstractByteDelegateIo< VolatileDelegateByteAccess >
	{
		@Override
		public VolatileDelegateByteAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new VolatileDelegateByteAccess( loadDelegate( bytes, numElements ), true );
		}
	}

	public static class DirtyDelegateType extends AbstractByteDelegateIo< DirtyDelegateByteAccess >
	{
		@Override
		public DirtyDelegateByteAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyDelegateByteAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class DirtyVolatileDelegateType extends AbstractByteDelegateIo< DirtyVolatileDelegateByteAccess >
	{
		@Override
		public DirtyVolatileDelegateByteAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyVolatileDelegateByteAccess( loadDelegate( bytes, numElements ), true );
		}
	}
}
