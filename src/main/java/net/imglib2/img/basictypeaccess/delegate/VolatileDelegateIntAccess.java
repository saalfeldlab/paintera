package net.imglib2.img.basictypeaccess.delegate;

import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileIntAccess;

public class VolatileDelegateIntAccess extends DelegateIntAccess implements VolatileIntAccess
{

	private final boolean isValid;

	public VolatileDelegateIntAccess( final IntAccess access, final boolean isValid )
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
