package org.janelia.saalfeldlab.paintera.control.lock;

public interface LockedSegments
{

	void lock(long segment);

	void unlock(long segment);

	boolean isLocked(long segment);

	long[] lockedSegmentsCopy();

}
