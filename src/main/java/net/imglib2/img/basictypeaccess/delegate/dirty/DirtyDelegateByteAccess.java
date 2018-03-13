package net.imglib2.img.basictypeaccess.delegate.dirty;

import net.imglib2.Dirty;
import net.imglib2.img.basictypeaccess.ByteAccess;
import net.imglib2.img.basictypeaccess.delegate.DelegateByteAccess;

public class DirtyDelegateByteAccess extends DelegateByteAccess implements Dirty
{

	public DirtyDelegateByteAccess( final ByteAccess access )
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
	public void setValue( final int index, final byte value )
	{
		setDirty();
		super.setValue( index, value );
	}

	@Override
	public void setDelegate( final ByteAccess access )
	{
		setDirty();
		super.setDelegate( access );
	}

}
