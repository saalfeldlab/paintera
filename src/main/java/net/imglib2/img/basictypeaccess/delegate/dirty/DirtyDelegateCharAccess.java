package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.Dirty;
import net.imglib2.img.basictypeaccess.CharAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateCharAccess;

public class DirtyDelegateCharAccess extends DelegateCharAccess implements Dirty
{

	public DirtyDelegateCharAccess( final CharAccess access )
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
	public void setValue( final int index, final char value )
	{
		setDirty();
		super.setValue( index, value );
	}

	@Override
	public void setDelegate( final CharAccess access )
	{
		setDirty();
		super.setDelegate( access );
	}

}
