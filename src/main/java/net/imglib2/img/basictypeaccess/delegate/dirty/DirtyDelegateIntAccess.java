package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.Dirty;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateIntAccess;

public class DirtyDelegateIntAccess extends DelegateIntAccess implements Dirty
{

	public DirtyDelegateIntAccess( final IntAccess access )
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
	public void setValue( final int index, final int value )
	{
		setDirty();
		super.setValue( index, value );
	}

	@Override
	public void setDelegate( final IntAccess access )
	{
		setDirty();
		super.setDelegate( access );
	}

}
