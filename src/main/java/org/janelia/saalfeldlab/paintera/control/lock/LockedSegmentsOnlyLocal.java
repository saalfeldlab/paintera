package org.janelia.saalfeldlab.paintera.control.lock;

import java.util.function.Consumer;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

public class LockedSegmentsOnlyLocal extends LockedSegmentsState
{

	private final TLongSet lockedSegments = new TLongHashSet();

	private final Consumer<long[]> persister;

	public LockedSegmentsOnlyLocal(final Consumer<long[]> persister, final long... lockedSegments)
	{
		super();
		this.lockedSegments.addAll(lockedSegments);
		this.persister = persister;
	}

	public long[] lockedSegmentsCopy()
	{
		return this.lockedSegments.toArray();
	}

	@Override
	public void persist()
	{
		persister.accept(lockedSegments.toArray());
	}

	@Override
	protected void lockImpl(final long segment)
	{
		this.lockedSegments.add(segment);
	}

	@Override
	protected void unlockImpl(final long segment)
	{
		this.lockedSegments.remove(segment);
	}

	@Override
	public boolean isLocked(final long segment)
	{
		return this.lockedSegments.contains(segment);
	}

}
