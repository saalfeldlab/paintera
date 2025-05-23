package org.janelia.saalfeldlab.paintera.id;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;


import java.util.stream.LongStream;

public class N5IdService implements IdService {

	private final N5Reader n5;

	private final String dataset;

	private long next;
	private long nextTemp = IdService.FIRST_TEMPORARY_ID;

	public N5IdService(final N5Reader n5, final String dataset, final long next) {

		super();
		this.n5 = n5;
		this.dataset = dataset;
		this.next = next;
	}

	public String getDataset() {

		return dataset;
	}

	@Override
	public synchronized void invalidate(final long id) {

		final long oldNext = next;
		next = IdService.max(next, id + 1);
		if (next != oldNext) {
			serializeMaxId();
		}
	}

	@Override
	public synchronized long next() {

		++next;
		serializeMaxId();
		return next;
	}

	@Override
	public synchronized long[] next(final int n) {

		final long[] ids = LongStream.range(next, next + n).toArray();
		next += n;
		serializeMaxId();
		return ids;
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

	private void serializeMaxId() {
		if (n5 instanceof N5Writer) {
			((N5Writer)n5).setAttribute(dataset, "maxId", next);
		}
	}

	@Override
	public boolean isInvalidated(final long id) {

		return id < next;
	}

}
