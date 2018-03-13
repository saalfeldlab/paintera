package net.imglib2.img.basictypeaccess.delegate;

import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileLongAccess;

public class VolatileDelegateLongAccess extends DelegateLongAccess implements VolatileLongAccess
{

	private final boolean isValid;

	public VolatileDelegateLongAccess( final LongAccess access, final boolean isValid )
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
