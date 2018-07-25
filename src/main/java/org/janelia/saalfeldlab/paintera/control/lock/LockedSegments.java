package org.janelia.saalfeldlab.paintera.control.lock;

public interface LockedSegments
{

	public void lock(long segment);

	public void unlock(long segment);

	public boolean isLocked(long segment);

}
