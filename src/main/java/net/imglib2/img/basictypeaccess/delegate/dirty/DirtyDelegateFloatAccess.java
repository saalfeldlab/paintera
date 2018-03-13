package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.Dirty;
import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateFloatAccess;

public class DirtyDelegateFloatAccess extends DelegateFloatAccess implements Dirty
{

	public DirtyDelegateFloatAccess( final FloatAccess access )
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
	public void setValue( final int index, final float value )
	{
		setDirty();
		super.setValue( index, value );
	}

	@Override
	public void setDelegate( final FloatAccess access )
	{
		setDirty();
		super.setDelegate( access );
	}

}
