package net.imglib2.cache.img;

import net.imglib2.Dirty;
import net.imglib2.cache.img.LoadedCellCacheLoader.AccessWrapper;
import net.imglib2.img.basictypeaccess.ByteAccess;
import net.imglib2.img.basictypeaccess.CharAccess;
import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.ShortAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateShortAccess;

public class DelegateAccessWrappers< A, W >
{

	@SuppressWarnings( "unchecked" )
	static < A > AccessWrapper< A, ? > getWrapper(
			final PrimitiveType primitiveType,
			final AccessFlags... flags )
	{
		final boolean dirty = AccessFlags.isDirty( flags );
		switch ( primitiveType )
		{
		case BYTE:
			return dirty
					? ( AccessWrapper< A, ? > ) new ByteAccessWrapper<>()
					: ( AccessWrapper< A, ? > ) new PassThrough<>();
		case CHAR:
			return dirty
					? ( AccessWrapper< A, ? > ) new CharAccessWrapper<>()
					: ( AccessWrapper< A, ? > ) new PassThrough<>();
		case DOUBLE:
			return dirty
					? ( AccessWrapper< A, ? > ) new DoubleAccessWrapper<>()
					: ( AccessWrapper< A, ? > ) new PassThrough<>();
		case FLOAT:
			return dirty
					? ( AccessWrapper< A, ? > ) new FloatAccessWrapper<>()
					: ( AccessWrapper< A, ? > ) new PassThrough<>();
		case INT:
			return dirty
					? ( AccessWrapper< A, ? > ) new IntAccessWrapper<>()
					: ( AccessWrapper< A, ? > ) new PassThrough<>();
		case LONG:
			return dirty
					? ( AccessWrapper< A, ? > ) new LongAccessWrapper<>()
					: ( AccessWrapper< A, ? > ) new PassThrough<>();
		case SHORT:
			return dirty
					? ( AccessWrapper< A, ? > ) new ShortAccessWrapper<>()
					: ( AccessWrapper< A, ? > ) new PassThrough<>();
		default:
			return null;
		}
	}

	public static class PassThrough< A > implements AccessWrapper< A, A >
	{
		@Override
		public A wrap( final A access )
		{
			return access;
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return new Dirty()
			{
				@Override
				public boolean isDirty()
				{
					return false;
				}

				@Override
				public void setDirty()
				{}
			};
		}
	}

	public static class ByteAccessWrapper< A extends DelegateByteAccess & Dirty > implements AccessWrapper< A, ByteAccess >
	{
		@Override
		public ByteAccess wrap( final A access )
		{
			return access.getDelegate();
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return access;
		}
	}

	public static class CharAccessWrapper< A extends DelegateCharAccess & Dirty > implements AccessWrapper< A, CharAccess >
	{
		@Override
		public CharAccess wrap( final A access )
		{
			return access.getDelegate();
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return access;
		}
	}

	public static class DoubleAccessWrapper< A extends DelegateDoubleAccess & Dirty > implements AccessWrapper< A, DoubleAccess >
	{
		@Override
		public DoubleAccess wrap( final A access )
		{
			return access.getDelegate();
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return access;
		}
	}

	public static class FloatAccessWrapper< A extends DelegateFloatAccess & Dirty > implements AccessWrapper< A, FloatAccess >
	{
		@Override
		public FloatAccess wrap( final A access )
		{
			return access.getDelegate();
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return access;
		}
	}

	public static class IntAccessWrapper< A extends DelegateIntAccess & Dirty > implements AccessWrapper< A, IntAccess >
	{
		@Override
		public IntAccess wrap( final A access )
		{
			return access.getDelegate();
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return access;
		}
	}

	public static class LongAccessWrapper< A extends DelegateLongAccess & Dirty > implements AccessWrapper< A, LongAccess >
	{
		@Override
		public LongAccess wrap( final A access )
		{
			return access.getDelegate();
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return access;
		}
	}

	public static class ShortAccessWrapper< A extends DelegateShortAccess & Dirty > implements AccessWrapper< A, ShortAccess >
	{
		@Override
		public ShortAccess wrap( final A access )
		{
			return access.getDelegate();
		}

		@Override
		public Dirty wrapDirty( final A access )
		{
			return access;
		}
	}

}
