package net.imglib2.img.basictypeaccess.delegate;

import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileFloatAccess;

public class VolatileDelegateFloatAccess extends DelegateFloatAccess implements VolatileFloatAccess
{

	private final boolean isValid;

	public VolatileDelegateFloatAccess( final FloatAccess access, final boolean isValid )
	{
		super( access );
		this.isValid = isValid;
	}

	@Override
	public boolean isValid()
	{
		return isValid;
	}

}
