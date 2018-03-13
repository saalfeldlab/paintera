package net.imglib2.cache.img.delegate;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import net.imglib2.cache.img.AccessIo;
import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.constant.ConstantDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.Delegate;
import net.imglib2.img.basictypeaccess.delegate.DelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateDoubleAccess;

public class DoubleDelegates
{

	public static final byte NO_FLAGS = 0;

	public static final byte SINGLE_VALUE_FLAG = 0x00000001;

	public static final DelegateType delegateIo = new DelegateType();

	public static final VolatileDelegateType volatileDelegateIo = new VolatileDelegateType();

	public static final DirtyDelegateType dirtyDelegateIo = new DirtyDelegateType();

	public static final DirtyVolatileDelegateType dirtyVolatileDelegateIo = new DirtyVolatileDelegateType();

	public static abstract class AbstractDoubleDelegateIo< B extends Delegate< DoubleAccess > > implements AccessIo< B >
	{

		@Override
		public int getBytesPerElement()
		{
			return Double.BYTES;
		}

		private boolean isSingleValue( final byte b )
		{
			return ( b & SINGLE_VALUE_FLAG ) != 0;
		}

		private boolean isSingleValue( final DoubleAccess a )
		{
			return a instanceof ConstantDoubleAccess;
		}

		@Override
		public long getNumBytes( final long numEntitites )
		{
			return 1 + getBytesPerElement() * numEntitites;
		}

		@Override
		public void save( final B access, final ByteBuffer out, final int numElements )
		{
			final DoubleAccess delegate = access.getDelegate();
			final boolean isSingleValue = isSingleValue( delegate );
			if ( isSingleValue )
			{
				out.put( SINGLE_VALUE_FLAG );
				out.putDouble( delegate.getValue( 0 ) );
			}
			else
			{
				out.put( NO_FLAGS );
				for ( int i = 0; i < numElements; ++i )
					out.putDouble( delegate.getValue( i ) );
			}
		}

		public DoubleAccess loadDelegate( final ByteBuffer bytes, final int numElements )
		{
			final byte options = bytes.get();
			final DoubleBuffer doubles = bytes.asDoubleBuffer();
			if ( isSingleValue( options ) )
				return new ConstantDoubleAccess( doubles.get() );
			else
			{
				final double[] data = new double[ numElements ];
				DoubleBuffer.wrap( data ).put( doubles );
				return new DoubleArray( data );
			}
		}

	}

	public static class DelegateType extends AbstractDoubleDelegateIo< DelegateDoubleAccess >
	{
		@Override
		public DelegateDoubleAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DelegateDoubleAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class VolatileDelegateType extends AbstractDoubleDelegateIo< VolatileDelegateDoubleAccess >
	{
		@Override
		public VolatileDelegateDoubleAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new VolatileDelegateDoubleAccess( loadDelegate( bytes, numElements ), true );
		}
	}

	public static class DirtyDelegateType extends AbstractDoubleDelegateIo< DirtyDelegateDoubleAccess >
	{
		@Override
		public DirtyDelegateDoubleAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyDelegateDoubleAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class DirtyVolatileDelegateType extends AbstractDoubleDelegateIo< DirtyVolatileDelegateDoubleAccess >
	{
		@Override
		public DirtyVolatileDelegateDoubleAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyVolatileDelegateDoubleAccess( loadDelegate( bytes, numElements ), true );
		}
	}
}
