package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.Dirty;
import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateDoubleAccess;

public class DirtyDelegateDoubleAccess extends DelegateDoubleAccess implements Dirty
{

	public DirtyDelegateDoubleAccess( final DoubleAccess access )
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
	public void setValue( final int index, final double value )
	{
		setDirty();
		super.setValue( index, value );
	}

	@Override
	public void setDelegate( final DoubleAccess access )
	{
		setDirty();
		super.setDelegate( access );
	}

}
