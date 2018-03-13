package net.imglib2.cache.img.delegate;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.imglib2.cache.img.AccessIo;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.constant.ConstantIntAccess;
import net.imglib2.img.basictypeaccess.delegate.Delegate;
import net.imglib2.img.basictypeaccess.delegate.DelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateIntAccess;

public class IntDelegates
{

	public static final byte NO_FLAGS = 0;

	public static final byte SINGLE_VALUE_FLAG = 0x00000001;

	public static final DelegateType delegateIo = new DelegateType();

	public static final VolatileDelegateType volatileDelegateIo = new VolatileDelegateType();

	public static final DirtyDelegateType dirtyDelegateIo = new DirtyDelegateType();

	public static final DirtyVolatileDelegateType dirtyVolatileDelegateIo = new DirtyVolatileDelegateType();

	public static abstract class AbstractIntDelegateIo< B extends Delegate< IntAccess > > implements AccessIo< B >
	{

		@Override
		public int getBytesPerElement()
		{
			return Integer.BYTES;
		}

		private boolean isSingleValue( final byte b )
		{
			return ( b & SINGLE_VALUE_FLAG ) != 0;
		}

		private boolean isSingleValue( final IntAccess a )
		{
			return a instanceof ConstantIntAccess;
		}

		@Override
		public void save( final B access, final ByteBuffer out, final int numElements )
		{
			final IntAccess delegate = access.getDelegate();
			final boolean isSingleValue = isSingleValue( delegate );
			if ( isSingleValue )
			{
				out.put( SINGLE_VALUE_FLAG );
				out.putInt( delegate.getValue( 0 ) );
			}
			else
			{
				out.put( NO_FLAGS );
				for ( int i = 0; i < numElements; ++i )
					out.putInt( delegate.getValue( i ) );
			}
		}

		public IntAccess loadDelegate( final ByteBuffer bytes, final int numElements )
		{
			final byte options = bytes.get();
			final IntBuffer ints = bytes.asIntBuffer();
			if ( isSingleValue( options ) )
				return new ConstantIntAccess( ints.get() );
			else
			{
				final int[] data = new int[ numElements ];
				IntBuffer.wrap( data ).put( ints );
				return new IntArray( data );
			}
		}

	}

	public static class DelegateType extends AbstractIntDelegateIo< DelegateIntAccess >
	{
		@Override
		public DelegateIntAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DelegateIntAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class VolatileDelegateType extends AbstractIntDelegateIo< VolatileDelegateIntAccess >
	{
		@Override
		public VolatileDelegateIntAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new VolatileDelegateIntAccess( loadDelegate( bytes, numElements ), true );
		}
	}

	public static class DirtyDelegateType extends AbstractIntDelegateIo< DirtyDelegateIntAccess >
	{
		@Override
		public DirtyDelegateIntAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyDelegateIntAccess( loadDelegate( bytes, numElements ) );
		}
	}

	public static class DirtyVolatileDelegateType extends AbstractIntDelegateIo< DirtyVolatileDelegateIntAccess >
	{
		@Override
		public DirtyVolatileDelegateIntAccess load( final ByteBuffer bytes, final int numElements )
		{
			return new DirtyVolatileDelegateIntAccess( loadDelegate( bytes, numElements ), true );
		}
	}
}
