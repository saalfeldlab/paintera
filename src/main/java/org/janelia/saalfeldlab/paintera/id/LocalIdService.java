package org.janelia.saalfeldlab.paintera.id;

import java.util.Arrays;
import java.util.stream.LongStream;

public class LocalIdService implements IdService {

	private long current;

	public LocalIdService() {

		this(1);
	}

	public LocalIdService(final long current) {

		this.current = current;
	}

	@Override public long nextTemporary() {

		return IdService.randomTemps.next();
	}

	@Override public long[] nextTemporary(int n) {

		final long[] tempIds = new long[n];
		Arrays.setAll(tempIds, it -> IdService.randomTemps.next());
		return tempIds;
	}

	@Override
	public synchronized void invalidate(final long id) {

		current = IdService.max(current, id + 1);
	}

	public void setCurrent(final long id) {

		current = id;
	}

	@Override
	public synchronized long current() {

		/* this should always return the previous value of `next()`.
		* That is `next() == current()` should always be true. */
		return current;
	}

	@Override
	public synchronized long next() {

		return ++current;
	}

	@Override
	public synchronized long[] next(final int n) {

		return LongStream.range(1 + current, 1 + (current += n)).toArray();
	}

	@Override
	public synchronized boolean isInvalidated(final long id) {

		return id < this.current;
	}
}
