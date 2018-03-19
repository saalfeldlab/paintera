package net.imglib2.cache.img.delegate;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import net.imglib2.cache.img.AccessIo;
import net.imglib2.img.basictypeaccess.ShortAccess;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.basictypeaccess.constant.ConstantShortAccess;
import net.imglib2.img.basictypeaccess.delegate.Delegate;
import net.imglib2.img.basictypeaccess.delegate.DelegateShortAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateShortAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateShortAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateShortAccess;

public class ShortDelegates
{

	public static final byte NO_FLAGS = 0;

	public static final byte SINGLE_VALUE_FLAG = 0x00000001;

	public static final DelegateType delegateIo = new DelegateType();

	public static final VolatileDelegateType volatileDelegateIo = new VolatileDelegateType();

	public static final DirtyDelegateType dirtyDelegateIo = new DirtyDelegateType();

	public static final DirtyVolatileDelegateType dirtyVolatileDelegateIo = new DirtyVolatileDelegateType();

	public static abstract class AbstractShortDelegateIo< B extends Delegate< ShortAccess > > implements AccessIo< B >
	{

		@Override
		public long getNumBytes( final long numElements )
		{
			return getBytesPerElement() * numElements + 1;
		}

		@Override
		public int getBytesPerElement()
		{
			return Short.BYTES;
		}

		private boolean isSingleValue( final byte b )
		{
			return ( b & SINGLE_VALUE_FLAG ) != 0;
		}

		private boolean isSingleValue( final ShortAccess a )
		{
			return a instanceof ConstantShortAccess;
		}

		@Override
		public void save( final B access, final ByteBuffer out, final int numElements )
		{
			final ShortAccess delegate = access.getDelegate();
			final boolean isSingleValue = isSingleValue( delegate );
			if ( isSingleValue )
			{
				out.put( SINGLE_VALUE_FLAG );
				out.putShort( delegate.getValue( 0 ) );
			}
			else
			{
				out.put( NO_FLAGS );
				for ( int i = 0; i < numElements; ++i )
					out.putShort( delegate.getValue( i ) );
			}
		}

		public ShortAccess loadDelegate( final ByteBuffer bytes, final int numElements )
		{
			final byte options = bytes.get();
			final ShortBuffer shorts = bytes.asShortBuffer();
			if ( isSingleValue( options ) )
				return new ConstantShortAccess( shorts.get() );
			else
			{
				final short[] data = new short[ numElements ];
				ShortBuffer.wrap( data ).put( shorts );
				return new ShortArray( data );
			}
		}

	}

	public static class DelegateType extends AbstractShortDelegateIo< DelegateShortAccess >
	{
		@Override
		public DelegateShortAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DelegateShortAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class VolatileDelegateType extends AbstractShortDelegateIo< VolatileDelegateShortAccess >
	{
		@Override
		public VolatileDelegateShortAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new VolatileDelegateShortAccess( loadDelegate( bytes, numElements ), true );
		}
	}

	public static class DirtyDelegateType extends AbstractShortDelegateIo< DirtyDelegateShortAccess >
	{
		@Override
		public DirtyDelegateShortAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyDelegateShortAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class DirtyVolatileDelegateType extends AbstractShortDelegateIo< DirtyVolatileDelegateShortAccess >
	{
		@Override
		public DirtyVolatileDelegateShortAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyVolatileDelegateShortAccess( loadDelegate( bytes, numElements ), true );
		}
	}
}
