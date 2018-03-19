package net.imglib2.cache.img.delegate;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import net.imglib2.cache.img.AccessIo;
import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.constant.ConstantFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.Delegate;
import net.imglib2.img.basictypeaccess.delegate.DelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateFloatAccess;

public class FloatDelegates
{

	public static final byte NO_FLAGS = 0;

	public static final byte SINGLE_VALUE_FLAG = 0x00000001;

	public static final DelegateType delegateIo = new DelegateType();

	public static final VolatileDelegateType volatileDelegateIo = new VolatileDelegateType();

	public static final DirtyDelegateType dirtyDelegateIo = new DirtyDelegateType();

	public static final DirtyVolatileDelegateType dirtyVolatileDelegateIo = new DirtyVolatileDelegateType();

	public static abstract class AbstractFloatDelegateIo< B extends Delegate< FloatAccess > > implements AccessIo< B >
	{

		@Override
		public long getNumBytes( final long numElements )
		{
			return getBytesPerElement() * numElements + 1;
		}

		@Override
		public int getBytesPerElement()
		{
			return Float.BYTES;
		}

		private boolean isSingleValue( final byte b )
		{
			return ( b & SINGLE_VALUE_FLAG ) != 0;
		}

		private boolean isSingleValue( final FloatAccess a )
		{
			return a instanceof ConstantFloatAccess;
		}

		@Override
		public void save( final B access, final ByteBuffer out, final int numElements )
		{
			final FloatAccess delegate = access.getDelegate();
			final boolean isSingleValue = isSingleValue( delegate );
			if ( isSingleValue )
			{
				out.put( SINGLE_VALUE_FLAG );
				out.putFloat( delegate.getValue( 0 ) );
			}
			else
			{
				out.put( NO_FLAGS );
				for ( int i = 0; i < numElements; ++i )
					out.putFloat( delegate.getValue( i ) );
			}
		}

		public FloatAccess loadDelegate( final ByteBuffer bytes, final int numElements )
		{
			final byte options = bytes.get();
			final FloatBuffer floats = bytes.asFloatBuffer();
			if ( isSingleValue( options ) )
				return new ConstantFloatAccess( floats.get() );
			else
			{
				final float[] data = new float[ numElements ];
				FloatBuffer.wrap( data ).put( floats );
				return new FloatArray( data );
			}
		}

	}

	public static class DelegateType extends AbstractFloatDelegateIo< DelegateFloatAccess >
	{
		@Override
		public DelegateFloatAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DelegateFloatAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class VolatileDelegateType extends AbstractFloatDelegateIo< VolatileDelegateFloatAccess >
	{
		@Override
		public VolatileDelegateFloatAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new VolatileDelegateFloatAccess( loadDelegate( bytes, numElements ), true );
		}
	}

	public static class DirtyDelegateType extends AbstractFloatDelegateIo< DirtyDelegateFloatAccess >
	{
		@Override
		public DirtyDelegateFloatAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyDelegateFloatAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class DirtyVolatileDelegateType extends AbstractFloatDelegateIo< DirtyVolatileDelegateFloatAccess >
	{
		@Override
		public DirtyVolatileDelegateFloatAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyVolatileDelegateFloatAccess( loadDelegate( bytes, numElements ), true );
		}
	}
}
