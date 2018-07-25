package org.janelia.saalfeldlab.paintera.control.lock;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;

public abstract class LockedSegmentsState extends ObservableWithListenersList implements LockedSegments
{

	public abstract void persist();

	protected abstract void lockImpl(long segment);

	protected abstract void unlockImpl(long segment);

	@Override
	public void lock(final long segment)
	{
		lockImpl(segment);
		stateChanged();
	}

	@Override
	public void unlock(final long segment)
	{
		unlockImpl(segment);
		stateChanged();
	}

}
