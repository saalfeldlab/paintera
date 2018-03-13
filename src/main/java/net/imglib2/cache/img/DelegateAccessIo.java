package net.imglib2.cache.img;

import net.imglib2.cache.img.delegate.ByteDelegates;
import net.imglib2.cache.img.delegate.CharDelegates;
import net.imglib2.cache.img.delegate.DoubleDelegates;
import net.imglib2.cache.img.delegate.FloatDelegates;
import net.imglib2.cache.img.delegate.IntDelegates;
import net.imglib2.cache.img.delegate.LongDelegates;
import net.imglib2.cache.img.delegate.ShortDelegates;

public class DelegateAccessIo
{
	@SuppressWarnings( "unchecked" )
	public static < A > AccessIo< A > get( final PrimitiveType primitiveType, final AccessFlags... flags )
	{
		final boolean dirty = AccessFlags.isDirty( flags );
		final boolean volatil = AccessFlags.isVolatile( flags );

		switch ( primitiveType )
		{
		case BYTE:
			return dirty
					? volatil
							? ( AccessIo< A > ) ByteDelegates.dirtyVolatileDelegateIo
							: ( AccessIo< A > ) ByteDelegates.dirtyDelegateIo
					: volatil
							? ( AccessIo< A > ) ByteDelegates.volatileDelegateIo
							: ( AccessIo< A > ) ByteDelegates.delegateIo;
		case CHAR:
			return dirty
					? volatil
							? ( AccessIo< A > ) CharDelegates.dirtyVolatileDelegateIo
							: ( AccessIo< A > ) CharDelegates.dirtyDelegateIo
					: volatil
							? ( AccessIo< A > ) CharDelegates.volatileDelegateIo
							: ( AccessIo< A > ) CharDelegates.delegateIo;
		case DOUBLE:
			return dirty
					? volatil
							? ( AccessIo< A > ) DoubleDelegates.dirtyVolatileDelegateIo
							: ( AccessIo< A > ) DoubleDelegates.dirtyDelegateIo
					: volatil
							? ( AccessIo< A > ) DoubleDelegates.volatileDelegateIo
							: ( AccessIo< A > ) DoubleDelegates.delegateIo;
		case FLOAT:
			return dirty
					? volatil
							? ( AccessIo< A > ) FloatDelegates.dirtyVolatileDelegateIo
							: ( AccessIo< A > ) FloatDelegates.dirtyDelegateIo
					: volatil
							? ( AccessIo< A > ) FloatDelegates.volatileDelegateIo
							: ( AccessIo< A > ) FloatDelegates.delegateIo;
		case INT:
			return dirty
					? volatil
							? ( AccessIo< A > ) IntDelegates.dirtyVolatileDelegateIo
							: ( AccessIo< A > ) IntDelegates.dirtyDelegateIo
					: volatil
							? ( AccessIo< A > ) IntDelegates.volatileDelegateIo
							: ( AccessIo< A > ) IntDelegates.delegateIo;
		case LONG:
			return dirty
					? volatil
							? ( AccessIo< A > ) LongDelegates.dirtyVolatileDelegateIo
							: ( AccessIo< A > ) LongDelegates.dirtyDelegateIo
					: volatil
							? ( AccessIo< A > ) LongDelegates.volatileDelegateIo
							: ( AccessIo< A > ) LongDelegates.delegateIo;
		case SHORT:
			return dirty
					? volatil
							? ( AccessIo< A > ) ShortDelegates.dirtyVolatileDelegateIo
							: ( AccessIo< A > ) ShortDelegates.dirtyDelegateIo
					: volatil
							? ( AccessIo< A > ) ShortDelegates.volatileDelegateIo
							: ( AccessIo< A > ) ShortDelegates.delegateIo;
		default:
			return null;
		}

	}

}
