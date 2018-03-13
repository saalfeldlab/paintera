package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileIntAccess;

public class DirtyVolatileDelegateIntAccess extends DirtyDelegateIntAccess implements VolatileIntAccess
{

	private final boolean isValid;

	public DirtyVolatileDelegateIntAccess( final IntAccess access, final boolean isValid )
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
