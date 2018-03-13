package net.imglib2.img.basictypeaccess.delegate;

import net.imglib2.img.basictypeaccess.ShortAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileShortAccess;

public class VolatileDelegateShortAccess extends DelegateShortAccess implements VolatileShortAccess
{

	private final boolean isValid;

	public VolatileDelegateShortAccess( final ShortAccess access, final boolean isValid )
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
