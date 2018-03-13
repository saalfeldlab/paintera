package net.imglib2.img.basictypeaccess.delegate;

import net.imglib2.img.basictypeaccess.ByteAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileByteAccess;

public class VolatileDelegateByteAccess extends DelegateByteAccess implements VolatileByteAccess
{

	private final boolean isValid;

	public VolatileDelegateByteAccess( final ByteAccess access, final boolean isValid )
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
