package net.imglib2.cache.img.delegate;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import net.imglib2.cache.img.AccessIo;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.basictypeaccess.constant.ConstantLongAccess;
import net.imglib2.img.basictypeaccess.delegate.Delegate;
import net.imglib2.img.basictypeaccess.delegate.DelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateLongAccess;

public class LongDelegates
{

	public static final byte NO_FLAGS = 0;

	public static final byte SINGLE_VALUE_FLAG = 0x00000001;

	public static final DelegateType delegateIo = new DelegateType();

	public static final VolatileDelegateType volatileDelegateIo = new VolatileDelegateType();

	public static final DirtyDelegateType dirtyDelegateIo = new DirtyDelegateType();

	public static final DirtyVolatileDelegateType dirtyVolatileDelegateIo = new DirtyVolatileDelegateType();

	public static abstract class AbstractLongDelegateIo< B extends Delegate< LongAccess > > implements AccessIo< B >
	{

		@Override
		public long getNumBytes( final long numElements )
		{
			return getBytesPerElement() * numElements + 1;
		}

		@Override
		public int getBytesPerElement()
		{
			return Long.BYTES;
		}

		private boolean isSingleValue( final byte b )
		{
			return ( b & SINGLE_VALUE_FLAG ) != 0;
		}

		private boolean isSingleValue( final LongAccess a )
		{
			return a instanceof ConstantLongAccess;
		}

		@Override
		public void save( final B access, final ByteBuffer out, final int numElements )
		{
			final LongAccess delegate = access.getDelegate();
			final boolean isSingleValue = isSingleValue( delegate );
			if ( isSingleValue )
			{
				out.put( SINGLE_VALUE_FLAG );
				out.putLong( delegate.getValue( 0 ) );
			}
			else
			{
				out.put( NO_FLAGS );
				for ( int i = 0; i < numElements; ++i )
					out.putLong( delegate.getValue( i ) );
			}
		}

		public LongAccess loadDelegate( final ByteBuffer bytes, final int numElements )
		{
			final byte options = bytes.get();
			final LongBuffer longs = bytes.asLongBuffer();
			if ( isSingleValue( options ) )
				return new ConstantLongAccess( longs.get() );
			else
			{
				final long[] data = new long[ numElements ];
				LongBuffer.wrap( data ).put( longs );
				return new LongArray( data );
			}
		}

	}

	public static class DelegateType extends AbstractLongDelegateIo< DelegateLongAccess >
	{
		@Override
		public DelegateLongAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DelegateLongAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class VolatileDelegateType extends AbstractLongDelegateIo< VolatileDelegateLongAccess >
	{
		@Override
		public VolatileDelegateLongAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new VolatileDelegateLongAccess( loadDelegate( bytes, numElements ), true );
		}
	}

	public static class DirtyDelegateType extends AbstractLongDelegateIo< DirtyDelegateLongAccess >
	{
		@Override
		public DirtyDelegateLongAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyDelegateLongAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class DirtyVolatileDelegateType extends AbstractLongDelegateIo< DirtyVolatileDelegateLongAccess >
	{
		@Override
		public DirtyVolatileDelegateLongAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyVolatileDelegateLongAccess( loadDelegate( bytes, numElements ), true );
		}
	}
}
