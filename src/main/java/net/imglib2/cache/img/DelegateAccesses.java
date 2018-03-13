package net.imglib2.cache.img;

import net.imglib2.img.basictypeaccess.constant.ConstantByteAccess;
import net.imglib2.img.basictypeaccess.constant.ConstantCharAccess;
import net.imglib2.img.basictypeaccess.constant.ConstantDoubleAccess;
import net.imglib2.img.basictypeaccess.constant.ConstantFloatAccess;
import net.imglib2.img.basictypeaccess.constant.ConstantIntAccess;
import net.imglib2.img.basictypeaccess.constant.ConstantLongAccess;
import net.imglib2.img.basictypeaccess.constant.ConstantShortAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateShortAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.VolatileDelegateShortAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyDelegateShortAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateByteAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateCharAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateDoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateFloatAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateIntAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateLongAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateShortAccess;

public class DelegateAccesses
{
	@SuppressWarnings( "unchecked" )
	public static < A > A getSingleValue(
			final PrimitiveType primitiveType,
			final AccessFlags... flags )
	{
		final boolean dirty = AccessFlags.isDirty( flags );
		final boolean volatil = AccessFlags.isVolatile( flags );
		switch ( primitiveType )
		{
		case BYTE:
			return dirty
					? volatil
							? ( A ) new DirtyVolatileDelegateByteAccess( new ConstantByteAccess(), true )
							: ( A ) new DirtyDelegateByteAccess( new ConstantByteAccess() )
					: volatil
							? ( A ) new VolatileDelegateByteAccess( new ConstantByteAccess(), true )
							: ( A ) new DelegateByteAccess( new ConstantByteAccess() );
		case CHAR:
			return dirty
					? volatil
							? ( A ) new DirtyVolatileDelegateCharAccess( new ConstantCharAccess(), true )
							: ( A ) new DirtyDelegateCharAccess( new ConstantCharAccess() )
					: volatil
							? ( A ) new VolatileDelegateCharAccess( new ConstantCharAccess(), true )
							: ( A ) new DelegateCharAccess( new ConstantCharAccess() );
		case DOUBLE:
			return dirty
					? volatil
							? ( A ) new DirtyVolatileDelegateDoubleAccess( new ConstantDoubleAccess(), true )
							: ( A ) new DirtyDelegateDoubleAccess( new ConstantDoubleAccess() )
					: volatil
							? ( A ) new VolatileDelegateDoubleAccess( new ConstantDoubleAccess(), true )
							: ( A ) new DelegateDoubleAccess( new ConstantDoubleAccess() );
		case FLOAT:
			return dirty
					? volatil
							? ( A ) new DirtyVolatileDelegateFloatAccess( new ConstantFloatAccess(), true )
							: ( A ) new DirtyDelegateFloatAccess( new ConstantFloatAccess() )
					: volatil
							? ( A ) new VolatileDelegateFloatAccess( new ConstantFloatAccess(), true )
							: ( A ) new DelegateFloatAccess( new ConstantFloatAccess() );
		case INT:
			return dirty
					? volatil
							? ( A ) new DirtyVolatileDelegateIntAccess( new ConstantIntAccess(), true )
							: ( A ) new DirtyDelegateIntAccess( new ConstantIntAccess() )
					: volatil
							? ( A ) new VolatileDelegateIntAccess( new ConstantIntAccess(), true )
							: ( A ) new DelegateIntAccess( new ConstantIntAccess() );
		case LONG:
			return dirty
					? volatil
							? ( A ) new DirtyVolatileDelegateLongAccess( new ConstantLongAccess(), true )
							: ( A ) new DirtyDelegateLongAccess( new ConstantLongAccess() )
					: volatil
							? ( A ) new VolatileDelegateLongAccess( new ConstantLongAccess(), true )
							: ( A ) new DelegateLongAccess( new ConstantLongAccess() );
		case SHORT:
			return dirty
					? volatil
							? ( A ) new DirtyVolatileDelegateShortAccess( new ConstantShortAccess(), true )
							: ( A ) new DirtyDelegateShortAccess( new ConstantShortAccess() )
					: volatil
							? ( A ) new VolatileDelegateShortAccess( new ConstantShortAccess(), true )
							: ( A ) new DelegateShortAccess( new ConstantShortAccess() );
		default:
			return null;
		}
	}
}
