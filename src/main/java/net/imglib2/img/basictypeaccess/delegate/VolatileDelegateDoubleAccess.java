package net.imglib2.img.basictypeaccess.delegate;

import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileDoubleAccess;

public class VolatileDelegateDoubleAccess extends DelegateDoubleAccess implements VolatileDoubleAccess
{

	private final boolean isValid;

	public VolatileDelegateDoubleAccess( final DoubleAccess access, final boolean isValid )
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
