package net.imglib2.img.basictypeaccess.delegate;

import net.imglib2.img.basictypeaccess.CharAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileCharAccess;

public class VolatileDelegateCharAccess extends DelegateCharAccess implements VolatileCharAccess
{

	private final boolean isValid;

	public VolatileDelegateCharAccess( final CharAccess access, final boolean isValid )
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
