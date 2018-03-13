package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileLongAccess;

public class DirtyVolatileDelegateLongAccess extends DirtyDelegateLongAccess implements VolatileLongAccess
{

	private final boolean isValid;

	public DirtyVolatileDelegateLongAccess( final LongAccess access, final boolean isValid )
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
