package org.janelia.saalfeldlab.paintera.id;

import java.util.stream.LongStream;

public class LocalIdService implements IdService {

	private long next;
	private long nextTemp = IdService.FIRST_TEMPORARY_ID;

	public LocalIdService() {

		this(1);
	}

	public LocalIdService(final long next) {

		this.next = next;
	}

	@Override public long nextTemporary() {

		final var temp = nextTemp;
		nextTemp += 1;
		return temp;
	}

	@Override public long[] nextTemporary(int n) {

		final long[] tempIds = LongStream.range(nextTemp, nextTemp + n).toArray();
		nextTemp += n;
		return tempIds;
	}

	@Override
	public synchronized void invalidate(final long id) {

		next = IdService.max(next, id + 1);
	}

	public void setNext(final long id) {

		next = id;
	}

	@Override
	public synchronized long next() {

		return next++;
	}

	@Override
	public synchronized long[] next(final int n) {

		final long[] ids = LongStream.range(next, next + n).toArray();
		next += n;
		return ids;
	}

	@Override
	public synchronized boolean isInvalidated(final long id) {

		return id < this.next;
	}
}
