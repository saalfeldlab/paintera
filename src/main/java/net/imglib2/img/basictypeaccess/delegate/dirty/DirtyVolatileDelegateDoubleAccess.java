package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileDoubleAccess;

public class DirtyVolatileDelegateDoubleAccess extends DirtyDelegateDoubleAccess implements VolatileDoubleAccess
{

	private final boolean isValid;

	public DirtyVolatileDelegateDoubleAccess( final DoubleAccess access, final boolean isValid )
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
