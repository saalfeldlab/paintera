package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.Dirty;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateLongAccess;

public class DirtyDelegateLongAccess extends DelegateLongAccess implements Dirty
{

	public DirtyDelegateLongAccess( final LongAccess access )
	{
		super( access );
	}

	private boolean dirty = false;

	@Override
	public boolean isDirty()
	{
		return this.dirty;
	}

	@Override
	public void setDirty()
	{
		this.dirty = true;
	}

	@Override
	public void setValue( final int index, final long value )
	{
		super.setValue( index, value );
		setDirty();
	}

	@Override
	public void setDelegate( final LongAccess access )
	{
		super.setDelegate( access );
		setDirty();
	}

}
