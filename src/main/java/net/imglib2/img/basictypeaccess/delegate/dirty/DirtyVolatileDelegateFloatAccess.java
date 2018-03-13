package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileFloatAccess;

public class DirtyVolatileDelegateFloatAccess extends DirtyDelegateFloatAccess implements VolatileFloatAccess
{

	private final boolean isValid;

	public DirtyVolatileDelegateFloatAccess( final FloatAccess access, final boolean isValid )
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
