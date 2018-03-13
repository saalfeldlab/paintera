package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.Dirty;
import net.imglib2.img.basictypeaccess.ShortAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateShortAccess;

public class DirtyDelegateShortAccess extends DelegateShortAccess implements Dirty
{

	public DirtyDelegateShortAccess( final ShortAccess access )
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
	public void setValue( final int index, final short value )
	{
		setDirty();
		super.setValue( index, value );
	}

	@Override
	public void setDelegate( final ShortAccess access )
	{
		setDirty();
		super.setDelegate( access );
	}

}
