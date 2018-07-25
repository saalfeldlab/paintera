package org.janelia.saalfeldlab.paintera.id;

import java.util.stream.LongStream;

public class LocalIdService implements IdService
{

	private long next;

	public LocalIdService()
	{
		this(0);
	}

	public LocalIdService(final long maxId)
	{
		this.next = maxId;
	}

	@Override
	public synchronized void invalidate(final long id)
	{
		next = IdService.max(next, id + 1);
	}

	public void setNext(final long id)
	{
		next = id;
	}

	@Override
	public synchronized long next()
	{
		return next++;
	}

	@Override
	public synchronized long[] next(final int n)
	{
		final long[] ids = LongStream.range(next, next + n).toArray();
		next += n;
		return ids;
	}

	@Override
	public synchronized boolean isInvalidated(final long id)
	{
		return id < this.next;
	}
}
