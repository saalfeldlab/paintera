package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.img.basictypeaccess.ByteAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileByteAccess;

public class DirtyVolatileDelegateByteAccess extends DirtyDelegateByteAccess implements VolatileByteAccess
{

	private final boolean isValid;

	public DirtyVolatileDelegateByteAccess( final ByteAccess access, final boolean isValid )
	{
		super( access );
		this.isValid = isValid;
	}

	@Override
	public boolean isValid()
	{
		return this.isValid;
	}

}
