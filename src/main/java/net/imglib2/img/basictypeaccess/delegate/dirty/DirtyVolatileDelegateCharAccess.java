package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.img.basictypeaccess.CharAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileCharAccess;

public class DirtyVolatileDelegateCharAccess extends DirtyDelegateCharAccess implements VolatileCharAccess
{

	private final boolean isValid;

	public DirtyVolatileDelegateCharAccess( final CharAccess access, final boolean isValid )
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
